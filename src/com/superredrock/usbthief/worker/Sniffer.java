package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.concurrent.ThreadPools;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;
import com.superredrock.usbthief.core.filter.BasicFileFilter;
import com.superredrock.usbthief.core.filter.FileFilter;
import com.superredrock.usbthief.core.filter.SuffixFilter;
import com.superredrock.usbthief.core.filter.SystemDirectoryFilter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    /** Delayed copy: tasks submitted here wait for their file to settle on a scheduler worker. */
    private final FileStabilityGate stabilityGate;

    private final FileFilter systemDirFilter = new SystemDirectoryFilter();
    private final ConcurrentHashMap<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    /** Queued change events awaiting batch processing (never dropped). */
    private final ConcurrentHashMap<Path, WatchEvent.Kind<?>> pendingChanges = new ConcurrentHashMap<>();
    /** Safety cap for per-directory watch registrations (Windows handle cost). */
    private static final int MAX_WATCH_DIRECTORIES = 20000;
    private final AtomicBoolean watchLimitWarned = new AtomicBoolean(false);
    private volatile boolean running = true;
    private volatile SnifferPhase phase = SnifferPhase.INITIAL_SCAN;
    private volatile Instant lastResetTime = Instant.now();
    private volatile Future<?> currentScanTask;

    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

    /**
     * Dedicated bounded pool for the blocking {@code Files.find} walks. Scans used to run on
     * {@link ForkJoinPool#commonPool()} - shared with event listener notification and the
     * recycler's statistics - where a long scan could starve those, and vice versa. One scan
     * per submission also means no nested parallelism inside the pool.
     */
    private static final ExecutorService scanPool = ThreadPools.scanExecutor();

    /**
     * Creates a Sniffer for the given volume.
     *
     * @param volume the volume to scan/monitor
     */
    public Sniffer(Volume volume) {
        this(volume, new FileStabilityGate());
    }

    /**
     * Creates a Sniffer with an explicit stability gate.
     *
     * @param volume        the volume to scan/monitor
     * @param stabilityGate the delayed-copy gate every submitted task has to pass
     */
    Sniffer(Volume volume, FileStabilityGate stabilityGate) {
        super(QueueManager.getDiskScanners(), "DiskScanner: " + volume.getDriveLetter());
        this.volume = volume;
        this.stabilityGate = Objects.requireNonNull(stabilityGate, "stabilityGate");
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

        if (!ConfigManager.getInstance().get(FileWatchConfig.WATCH_ENABLED)) {
            logger.info("File monitoring disabled, scanner finished");
            closeWatchService();
            completionFuture.complete(null);
            return;
        }

        logger.info("Starting file monitoring for {}", root);

        if (monitor == null) {
            logger.warn("WatchService not available, skipping file monitoring");
            completionFuture.complete(null);
        } else {
            startMonitoring();
        }
    }

    private void performInitialScan() {
        logger.info("Scanning Disk {}", root);
        // The initial scan must leave the monitor with live watch keys, otherwise
        // files added after the scan completes are never observed. Only needed when
        // real-time monitoring is enabled (otherwise the rescan cycle covers new files).
        boolean watchEnabled = ConfigManager.getInstance().get(FileWatchConfig.WATCH_ENABLED);
        if (watchEnabled) {
            try {
                registerDirectoryWatch(root);
            } catch (IOException e) {
                logger.warn("Failed to register watch for root {}: {}", root, e);
            }
        }
        FileFilter fileFilter = new SystemDirectoryFilter().and(new BasicFileFilter(ConfigManager.getInstance()));
        SuffixFilter suffixFilter = new SuffixFilter(ConfigManager.getInstance());
        AtomicInteger fileCount = new AtomicInteger(0);

        Future<?> scan = scanPool.submit(
                () -> {
                    // The suffix check runs inside find() so it reuses the attributes
                    // Files.find already read; asPredicate() re-read them per file.
                    // The stream stays sequential: the walk itself already runs on a
                    // dedicated scan thread, and a nested .parallel() inside a pool task
                    // would only split the same IO over the same pool.
                    try (Stream<Path> paths = Files.find(root, Integer.MAX_VALUE,
                            (p, a) -> fileFilter.test(p, a) && (a.isDirectory() || suffixFilter.test(p, a)))) {
                        paths.peek(path -> {
                                    if (Files.isDirectory(path)) {
                                        submitCopyTask(path);
                                        if (watchEnabled) {
                                            try {
                                                registerDirectoryWatch(path);
                                            } catch (IOException e) {
                                                logger.warn("Failed to register watch for {}: {}", path, e);
                                            }
                                        }
                                    };
                                })
                                .filter(Files::isRegularFile)
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


    /**
     * Queues a copy for {@code path}.
     *
     * <p>Delayed copy: the task waits for the file to settle before it reads anything, so a file
     * that is still being written is never copied half-finished. The waiting happens inside the
     * task - i.e. on a scheduler worker - and never on this sniffer's thread.</p>
     *
     * @param path the file or directory to copy
     */
    private void submitCopyTask(Path path) {
        Callable<CopyResult> task = new GatedCopyTask(path, volume.getSerialNumber(), volume, stabilityGate);
        TaskScheduler.getInstance().submit(task);
    }

    private void scanNewDirectory(Path dir) throws IOException {
        // Skip system directories entirely — don't scan or watch
        try {
            BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
            if (!systemDirFilter.test(dir, attrs)) {
                logger.debug("Skipping system directory: {}", dir);
                return;
            }
        } catch (IOException e) {
            return;
        }

        registerDirectoryWatch(dir);

        FileFilter baseFilter = new SystemDirectoryFilter().and(new BasicFileFilter(ConfigManager.getInstance()));
        BiPredicate<Path, BasicFileAttributes> filter = (path, attrs) ->
                attrs.isDirectory() || baseFilter.test(path, attrs);
        SuffixFilter suffixFilter = new SuffixFilter(ConfigManager.getInstance());

        try {
            scanPool.submit(() -> {
                try (Stream<Path> paths = Files.find(dir, Integer.MAX_VALUE, filter)) {
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
        if (watchKeys.isEmpty()) {
            // The initial scan registered no directory watches (no eligible directories,
            // or registrations failed). Finish normally instead of blocking forever on
            // monitor.take(): the lifecycle manager will schedule a fresh rescan after the cooldown.
            logger.info("No watch keys registered for {} - monitor loop skipped, awaiting scheduled rescan", root);
            phase = SnifferPhase.FINISHED;
            running = false;
            closeWatchService();
            completionFuture.complete(null);
            return;
        }
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
                            logger.warn("WatchEvent overflow for {} - scheduling rescan", watchPath);
                            try {
                                scanNewDirectory(watchPath);
                            } catch (Exception ex) {
                                logger.warn("Rescan after overflow failed for {}", watchPath, ex);
                            }
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
        Thread flushThread = new Thread(() -> {
            while (running) {
                try {
                    TimeUnit.SECONDS.sleep(ConfigManager.getInstance().get(FileWatchConfig.WATCH_RESET_INTERVAL_SECONDS));
                    lastResetTime = Instant.now();
                    // Periodic flush: queued changes below the threshold are processed too.
                    flushPendingChanges();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ChangeFlush");
        flushThread.setDaemon(true);
        return flushThread;
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

        enqueueChange(fullPath, kind);
    }

    /**
     * Queues a change for batch processing. Previously a counter was incremented
     * and only the event that reached the threshold was processed - the
     * sub-threshold remainder was silently discarded, so files could be lost in
     * light-traffic scenarios. Queued changes are now never dropped: the batch
     * flushes when it reaches {@code WATCH_THRESHOLD} entries, and the periodic
     * flush tick handles everything below the threshold.
     */
    void enqueueChange(Path fullPath, WatchEvent.Kind<?> kind) {
        pendingChanges.put(fullPath, kind);
        int size = pendingChanges.size();
        logger.trace("Change queued: {} on {} (pending: {})", kind, fullPath, size);

        if (size >= ConfigManager.getInstance().get(FileWatchConfig.WATCH_THRESHOLD)) {
            flushPendingChanges();
        }
    }

    /**
     * Processes every queued change. Entries are removed via the iterator, so
     * concurrent additions are not lost - they stay queued for the next flush.
     */
    void flushPendingChanges() {
        List<Map.Entry<Path, WatchEvent.Kind<?>>> batch = new ArrayList<>();
        for (Iterator<Map.Entry<Path, WatchEvent.Kind<?>>> it = pendingChanges.entrySet().iterator(); it.hasNext(); ) {
            batch.add(it.next());
            it.remove();
        }
        if (batch.isEmpty()) {
            return;
        }
        logger.info("Processing {} queued change(s) for {}", batch.size(), root);
        for (Map.Entry<Path, WatchEvent.Kind<?>> entry : batch) {
            try {
                handleChangedPath(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.warn("Error handling changed path {}", entry.getKey(), e);
            }
        }
    }

    private void handleChangedPath(Path path, WatchEvent.Kind<?> kind) {
        try {
            if (isInsideSystemDirectory(path)) return;

            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                // Drop the (now-invalid) watch registration for deleted directories;
                // any remaining stale keys are cleaned up when their reset() fails.
                if (watchKeys.remove(path) != null) {
                    logger.debug("Removed watch for deleted directory: {}", path);
                }
                return;
            }

            if (Files.isDirectory(path) && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                scanNewDirectory(path);
            } else if (Files.isRegularFile(path)) {
                submitCopyTask(path);
            }
        } catch (IOException e) {
            logger.warn("Error handling changed path: ", e);
        }
    }

    private boolean isInsideSystemDirectory(Path path) {
        for (Path p = path; p != null; p = p.getParent()) {
            if (p.equals(root)) break;
            if (SystemDirectoryFilter.isSystemDirName(p)) return true;
        }
        return false;
    }

    private void registerDirectoryWatch(Path dir) throws IOException {
        if (monitor == null || watchKeys.containsKey(dir)) {
            return;
        }
        if (watchKeys.size() >= MAX_WATCH_DIRECTORIES) {
            if (watchLimitWarned.compareAndSet(false, true)) {
                logger.warn("Watch directory limit ({}) reached; further directories are not watched", MAX_WATCH_DIRECTORIES);
            }
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
        return pendingChanges.size();
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
        int intervalSec = config.get(FileWatchConfig.WATCH_RESET_INTERVAL_SECONDS);
        long elapsedSec = Duration.between(resetTime, Instant.now()).getSeconds();
        int untilReset = Math.max(0, intervalSec - (int) elapsedSec);

        return new SnifferDebugSnapshot(
            volume.getDriveLetter(),
            volume.getSerialNumber(),
            phase,
            pendingChanges.size(),
            config.get(FileWatchConfig.WATCH_THRESHOLD),
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
        // Cancelling a watch key is a cheap local call: no reason to route it through a
        // parallel stream (which would land it on the common pool).
        for (WatchKey key : watchKeys.values()) {
            key.cancel();
        }

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

        Future<?> scan = currentScanTask;
        if (scan != null && !scan.isDone()) {
            // Interrupts the pool worker running the walk; the in-loop running/interrupt
            // checks abort the scan. Future.cancel(true) keeps the previous semantics.
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
