package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.index.Index;

import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * 队列和线程池管理器
 * <p>
 * 管理任务队列、复制线程池和磁盘扫描器线程组。
 * 服务生命周期管理已移至 ServiceManager。
 */
public class QueueManager {

    public static final DelayQueue<DelayedPath> RetryQueue = new DelayQueue<>();

    // 设备管理器现在由 ServiceManager 管理
    public static DeviceManager deviceManager;
    public static Index index;


    public static void init(){
        deviceManager = ServiceManager.getInstance().findService(DeviceManager.class);
        index = ServiceManager.getInstance().findService(Index.class);
    }



    public static final ThreadGroup DiskScanners = new ThreadGroup("DiskScanner");
    public static final ArrayBlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(ConfigManager.getInstance().get(ConfigSchema.TASK_QUEUE_CAPACITY));
    public static final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            ConfigManager.getInstance().get(ConfigSchema.CORE_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.MAX_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.KEEP_ALIVE_TIME_SECONDS),
            TimeUnit.SECONDS,
            taskQueue,
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    protected static final Logger logger = Logger.getLogger(QueueManager.class.getName());

    /**
     * 应用程序退出清理
     * <p>
     * 清理 QueueManager 管理的资源（线程池、磁盘扫描器等）。
     * 服务生命周期管理已移至 ServiceManager.shutdown()。
     */
    public static void quit() {
        logger.info("Quitting application");

        try {
            // 1. 停止索引的定时保存服务
            index.stop();
            logger.info("Index ticker stopped");

            // 2. 保存索引
            index.save();
            logger.info("Index saved");

            // 3. 中断所有磁盘扫描器线程
            DiskScanners.interrupt();
            logger.info("DiskScanners interrupted");

            // 4. 优雅关闭线程池
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
}
