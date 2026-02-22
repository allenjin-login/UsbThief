package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages sniffer lifecycle and restart scheduling.
 * <p>
 * Handles delayed restarts after sniffer completion or errors, with different delays
 * based on the restart reason. Thread-safe singleton implementation.
 */
public class SnifferLifecycleManager {

    private static volatile SnifferLifecycleManager INSTANCE;

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Device, ScheduledFuture<?>> pendingRestarts;
    private final DeviceManager deviceManager;
    protected static final Logger logger = Logger.getLogger(SnifferLifecycleManager.class.getName());

    /**
     * Reason for scheduling a sniffer restart.
     */
    public enum RestartReason {
        /**
         * Sniffer finished normally (all files copied).
         * Uses long delay (SNIFFER_WAIT_NORMAL_MINUTES, default 30 minutes).
         */
        NORMAL_COMPLETION,

        /**
         * Sniffer encountered an error.
         * Uses short delay (SNIFFER_WAIT_ERROR_MINUTES, default 5 minutes).
         */
        ERROR,

        /**
         * Sniffer paused due to storage constraints.
         * No automatic restart - waits for manual resume via StorageController.
         */
        STORAGE_PAUSE
    }

    /**
     * Private constructor for singleton.
     * Creates single-threaded scheduler for restart delays.
     */
    private SnifferLifecycleManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SnifferLifecycleManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.pendingRestarts = new ConcurrentHashMap<>();
        this.deviceManager  = QueueManager.getDeviceManager();
    }

    /**
     * Get singleton instance of SnifferLifecycleManager.
     *
     * @return the singleton instance
     */
    public static synchronized SnifferLifecycleManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SnifferLifecycleManager();
        }
        return INSTANCE;
    }

    public void sleepDevice(Device device, RestartReason reason){
        if (deviceManager != null) {
            deviceManager.pauseScanner(device);
        }
        scheduleResume(device,reason);
    }

    /**
     * Schedule a sniffer restart for the given device with the specified reason.
     * <p>
     * The delay is determined by the restart reason:
     * <ul>
     *   <li>NORMAL_COMPLETION: SNIFFER_WAIT_NORMAL_MINUTES (default 30)</li>
     *   <li>ERROR: SNIFFER_WAIT_ERROR_MINUTES (default 5)</li>
     *   <li>STORAGE_PAUSE: No delay, only tracks for manual resumption</li>
     * </ul>
     * <p>
     * The callback to restart the scanner will be wired up in Task 9.
     * For now, this just tracks the pending restart.
     *
     * @param device the device whose sniffer should be restarted
     * @param reason the reason for restart (determines delay)
     */
    public void scheduleResume(Device device, RestartReason reason) {
        if (device == null) {
            logger.warning("Cannot schedule restart for null device");
            return;
        }

        // Cancel any existing restart for this device
        cancelRestart(device);

        long delayMinutes = getRestartDelay(reason);

        if (delayMinutes <= 0) {
            // STORAGE_PAUSE or zero delay - just track without scheduling
            logger.fine("Restart for device " + device.getSerialNumber() +
                " tracked with reason " + reason + " (no scheduled delay)");
            // Could store in a separate map for tracking if needed
            return;
        }

        // Schedule restart after delay
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                logger.info("Restart delay elapsed for device " + device.getSerialNumber() +
                    " (reason: " + reason + ")");

                // Call DeviceManager to restart the scanner
                DeviceManager dm = deviceManager;
                if (dm != null) {
                    dm.resumeScanner(device);
                } else {
                    logger.warning("DeviceManager not set, cannot restart scanner for device " +
                        device.getSerialNumber());
                }
            } catch (Exception e) {
                logger.warning("Error during scheduled restart for device " +
                    device.getSerialNumber() + ": " + e.getMessage());
            } finally {
                pendingRestarts.remove(device);
            }
        }, delayMinutes, TimeUnit.MINUTES);

        pendingRestarts.put(device, future);
        logger.fine("Scheduled restart for device " + device.getSerialNumber() +
            " in " + delayMinutes + " minutes (reason: " + reason + ")");
    }

    /**
     * Cancel a pending restart for the given device.
     *
     * @param device the device whose restart should be cancelled
     */
    public void cancelRestart(Device device) {
        if (device == null) {
            return;
        }

        ScheduledFuture<?> future = pendingRestarts.remove(device);
        if (future != null) {
            boolean cancelled = future.cancel(false);
            logger.fine("Cancelled restart for device " + device.getSerialNumber() +
                (cancelled ? " (successfully)" : " (already completed)"));
        }
    }

    /**
     * Check if a restart is pending for the given device.
     *
     * @param device the device to check
     * @return true if a restart is scheduled and not yet completed
     */
    public boolean isRestartPending(Device device) {
        if (device == null) {
            return false;
        }

        ScheduledFuture<?> future = pendingRestarts.get(device);
        return future != null && !future.isDone();
    }

    /**
     * Get the restart delay in minutes for a given restart reason.
     * Reads delay values from ConfigSchema.
     *
     * @param reason the restart reason
     * @return delay in minutes (0 for STORAGE_PAUSE)
     */
    public long getRestartDelay(RestartReason reason) {
        ConfigManager config = ConfigManager.getInstance();

        return switch (reason) {
            case NORMAL_COMPLETION ->
                config.get(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES);
            case ERROR ->
                config.get(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES);
            case STORAGE_PAUSE ->
                0; // No automatic delay for storage pause
        };
    }

    /**
     * Shutdown the scheduler and cancel all pending restarts.
     * Call this during application shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down SnifferLifecycleManager");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Scheduler did not terminate in time, forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warning("Shutdown interrupted: " + e.getMessage());
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
