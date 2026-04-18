package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.index.Index;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.TaskScheduler;

import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final RejectionAwarePolicy rejectionPolicy = new RejectionAwarePolicy();

    protected static final Logger logger = LogManager.getLogger(QueueManager.class);

    private QueueManager() {
    }

    public static void init(){
        deviceManager = DeviceManager.getInstance();
        index = Index.getInstance();
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


    /**
     * Application exit cleanup
     * <p>
     * Clean up resources managed by QueueManager (thread pools, disk scanners, etc.).
     */
    public static void quit() {
        logger.info("Quitting application");

        try {
            // 1. Stop sniffer lifecycle manager
            SnifferLifecycleManager.getInstance().stopService();
            logger.info("SnifferLifecycleManager stopped");

            // 2. Stop index periodic save service
            index.stopService();
            logger.info("Index ticker stopped");

            // 3. Save index
            index.save();
            logger.info("Index saved");

            // 4. Interrupt all disk scanner threads
            diskScanners.interrupt();
            logger.info("DiskScanners interrupted");

            // 5. Gracefully shutdown thread pool
            TaskScheduler.getInstance().close();

            logger.info("Thread pool shutdown completed");

        } catch (Exception e) {
            logger.error("Error during quit: {}", e);
        }

        logger.info("Quit completed");
    }

    public static RejectionAwarePolicy getRejectionPolicy() {
        return rejectionPolicy;
    }
}
