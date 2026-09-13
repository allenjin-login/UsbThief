package com.superredrock.usbthief.core;

import com.superredrock.usbthief.index.Index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Queue and thread pool manager
 * <p>
 * Manages task queue, copy thread pool, and disk scanner thread group.
 * Service lifecycle management has been moved to {@link ServiceRegistry}.
 */
public class QueueManager {

    private static DeviceManager deviceManager;
    private static Index index;

    private static final ThreadGroup diskScanners = new ThreadGroup("DiskScanner");

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
     * Releases the resources owned by QueueManager (the disk scanner thread group).
     *
     * <p>Service orchestration was deliberately removed from here: services are stopped
     * exactly once by the unified shutdown path, in reverse registration order, via
     * {@link ServiceRegistry#shutdownAll()}. Keeping a second stop list in this class is
     * what previously caused {@code SnifferLifecycleManager}'s stop to be hidden and
     * {@code TaskScheduler} to be stopped twice.
     */
    public static void quit() {
        logger.info("Releasing QueueManager resources");

        try {
            // Interrupt all disk scanner threads
            diskScanners.interrupt();
            logger.info("DiskScanners interrupted");

        } catch (Exception e) {
            logger.error("Error during QueueManager resource cleanup:", e);
        }

        logger.info("QueueManager resource cleanup completed");
    }

}
