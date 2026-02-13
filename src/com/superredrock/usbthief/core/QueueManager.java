package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.index.Index;

import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Queue and thread pool manager
 * <p>
 * Manages task queue, copy thread pool, and disk scanner thread group.
 * Service lifecycle management has been moved to ServiceManager.
 */
public class QueueManager {

    private static DeviceManager deviceManager;
    private static Index index;

    private static final ThreadGroup diskScanners = new ThreadGroup("DiskScanner");
    private static final ArrayBlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(ConfigManager.getInstance().get(ConfigSchema.TASK_QUEUE_CAPACITY));
    private static final RejectionAwarePolicy rejectionPolicy = new RejectionAwarePolicy();
    private static final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            ConfigManager.getInstance().get(ConfigSchema.CORE_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.MAX_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.KEEP_ALIVE_TIME_SECONDS),
            TimeUnit.SECONDS,
            taskQueue,
            rejectionPolicy
    );

    protected static final Logger logger = Logger.getLogger(QueueManager.class.getName());

    private QueueManager() {
    }

    public static void init(){
        deviceManager = ServiceManager.getInstance().findService(DeviceManager.class)
                .orElseGet(() -> {
                    DeviceManager dm = new DeviceManager();
                    ServiceManager.getInstance().registerService(dm);
                    return dm;
                });
        index = ServiceManager.getInstance().findService(Index.class)
                .orElseGet(() -> {
                    Index idx = new Index();
                    ServiceManager.getInstance().registerService(idx);
                    return idx;
                });
    }

    public static DeviceManager getDeviceManager() {
        return deviceManager;
    }

    public static Index getIndex() {
        return index;
    }

    public static ThreadGroup getDiskScanners() {
        return diskScanners;
    }

    public static ThreadPoolExecutor getPool() {
        return pool;
    }

    /**
     * Application exit cleanup
     * <p>
     * Clean up resources managed by QueueManager (thread pools, disk scanners, etc.).
     * Service lifecycle management has been moved to ServiceManager.shutdown().
     */
    public static void quit() {
        logger.info("Quitting application");

        try {
            // 1. Stop index periodic save service
            index.stopService();
            logger.info("Index ticker stopped");

            // 2. Save index
            index.save();
            logger.info("Index saved");

            // 3. Interrupt all disk scanner threads
            diskScanners.interrupt();
            logger.info("DiskScanners interrupted");

            // 4. Gracefully shutdown thread pool
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Thread pool did not terminate in time, forcing shutdown");
                pool.shutdownNow();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warning("Thread pool could not be forced to terminate");
                }
            }
            logger.info("Thread pool shutdown completed");

        } catch (InterruptedException e) {
            logger.warning("Quit interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        } catch (Exception e) {
            logger.severe("Error during quit: " + e.getMessage());
        }

        logger.info("Quit completed");
    }

    public static int getQueueSize() {
        return taskQueue.size();
    }

    public static int getActiveThreadCount() {
        return pool.getActiveCount();
    }

    public static double getActiveRatio() {
        int activeCount = pool.getActiveCount();
        int maximumPoolSize = pool.getMaximumPoolSize();
        if (maximumPoolSize == 0) return 0.0;
        return (double) activeCount / maximumPoolSize;
    }

    public static ThreadPoolExecutor getCopyExecutor() {
        return pool;
    }

    public static RejectionAwarePolicy getRejectionPolicy() {
        return rejectionPolicy;
    }
}
