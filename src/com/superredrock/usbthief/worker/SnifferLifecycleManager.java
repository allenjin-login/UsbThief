package com.superredrock.usbthief.worker;

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

    /** Cooldown tracking: serial → timestamp when cooldown expires */
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    /** Volumes pending restart after cooldown */
    private final Set<String> pendingRestarts = ConcurrentHashMap.newKeySet();

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
            Volume volume = event.volume();
            pendingRestarts.remove(volume.getSerialNumber());
            logger.info("Volume inserted, scheduling sniffer for: {}", volume.getSerialNumber());
            // Don't create sniffer immediately — tick() will pick it up
        });

        bus.register(VolumeRemovedEvent.class, event -> {
            String serial = event.volume().getSerialNumber();
            logger.info("Volume removed, stopping sniffer: {}", serial);
            stop(serial);
            // Keep cooldowns and pendingRestarts to prevent immediate restart on re-insert after error
        });

        bus.register(VolumeStateChangedEvent.class, event -> {
            Volume volume = event.volume();
            String serial = volume.getSerialNumber();
            Volume.VolumeState newState = event.newState();

            switch (newState) {
                case OFFLINE -> {
                    logger.debug("Volume OFFLINE, stopping sniffer: {}", serial);
                    stop(serial);
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

        // 1. Create sniffers for IDLE volumes that don't have one and aren't in cooldown
        Collection<Volume> volumes = QueueManager.getDeviceManager().getAllVolumes();
        for (Volume volume : volumes) {
            String serial = volume.getSerialNumber();
            if (volume.getState() == Volume.VolumeState.IDLE &&
                !sniffers.containsKey(serial) &&
                !isInCooldown(serial) &&
                !pendingRestarts.contains(serial)) {
                createSniffer(volume);
            }
        }

        // 2. Process pending restarts whose cooldown has elapsed
        Iterator<String> it = pendingRestarts.iterator();
        while (it.hasNext()) {
            String serial = it.next();
            if (!isInCooldown(serial)) {
                it.remove();
                Volume volume = QueueManager.getDeviceManager().getVolumeBySerial(serial);
                if (volume != null && volume.getState() == Volume.VolumeState.IDLE && !sniffers.containsKey(serial)) {
                    logger.info("Cooldown elapsed, restarting sniffer for: {}", serial);
                    createSniffer(volume);
                }
            }
        }

        // 3. Cleanup finished sniffers
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
    protected long getTickIntervalMs() {
        return 3000;
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
            Sniffer sniffer = new Sniffer(volume, () -> onSnifferFinished(volume),
                                                     () -> onSnifferError(volume));
            sniffers.put(serial, new SnifferEntry(sniffer, serial));
            sniffer.start();
            logger.info("Sniffer started for volume: {} at {}", serial, volume.getRootPath());
        } catch (Exception e) {
            logger.warn("Failed to create sniffer for {}", serial, e);
            scheduleRestart(serial, RestartReason.ERROR);
        }
    }

    /**
     * Callback when a sniffer finishes normally.
     */
    private void onSnifferFinished(Volume volume) {
        String serial = volume.getSerialNumber();
        logger.info("Sniffer finished for {} (reason: {})", serial, RestartReason.NORMAL_COMPLETION);
        scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
    }

    /**
     * Callback when a sniffer encounters an error.
     */
    private void onSnifferError(Volume volume) {
        String serial = volume.getSerialNumber();
        logger.warn("Sniffer error for {}", serial);
        scheduleRestart(serial, RestartReason.ERROR);
    }

    /**
     * Schedules a restart after the appropriate cooldown delay.
     */
    private void scheduleRestart(String serial, RestartReason reason) {
        long delayMs = getRestartDelayMs(reason);
        if (delayMs <= 0) {
            // No delay — mark for immediate restart on next tick
            pendingRestarts.add(serial);
            return;
        }

        long cooldownEnd = System.currentTimeMillis() + delayMs;
        cooldowns.put(serial, cooldownEnd);
        pendingRestarts.add(serial);
        logger.info("Scheduled restart for {} in {} min (reason: {})", serial, TimeUnit.MILLISECONDS.toMinutes(delayMs), reason);
    }

    /**
     * Checks if a volume serial is still in cooldown period.
     */
    private boolean isInCooldown(String serial) {
        Long endTime = cooldowns.get(serial);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            cooldowns.remove(serial);
            return false;
        }
        return true;
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

    // ========== Public API ==========

    /**
     * Stops the scanner for a given volume serial number.
     */
    public void stop(String serialNumber) {
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
        stop(serial);
        cooldowns.remove(serial);
        pendingRestarts.remove(serial);
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
        return pendingRestarts.contains(serialNumber);
    }

    /**
     * Gets the remaining cooldown time in milliseconds for a volume, or 0 if not in cooldown.
     */
    public long getRemainingCooldownMs(String serialNumber) {
        Long endTime = cooldowns.get(serialNumber);
        if (endTime == null) return 0;
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining);
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

        // Add entries for volumes in cooldown (no active sniffer)
        for (String serial : pendingRestarts) {
            boolean hasActive = snapshots.stream().anyMatch(s -> s.serialNumber().equals(serial));
            if (!hasActive) {
                long remaining = getRemainingCooldownMs(serial);
                String reason = remaining > 0 ? "restart" : "";
                Volume vol = QueueManager.getDeviceManager() != null
                    ? QueueManager.getDeviceManager().getVolumeBySerial(serial)
                    : null;
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
        // Stop all sniffers
        for (SnifferEntry entry : sniffers.values()) {
            try {
                entry.sniffer.close();
            } catch (Exception e) {
                logger.warn("Error closing sniffer for {}: {}", entry.serialNumber, e);
            }
        }
        sniffers.clear();
        cooldowns.clear();
        pendingRestarts.clear();
        logger.info("All sniffers stopped and cleaned up");
    }

    // ========== Compatibility ==========

    /**
     * @deprecated Use {@link #pause(Volume)} instead.
     */
    @Deprecated
    public void sleepVolume(Volume volume, RestartReason reason) {
        String serial = volume.getSerialNumber();
        stop(serial);
        scheduleRestart(serial, reason);
    }

    /**
     * @deprecated Use {@link #isRestartPending(String)} instead.
     */
    @Deprecated
    public boolean isRestartPending(Volume volume) {
        return volume != null && isRestartPending(volume.getSerialNumber());
    }

    /**
     * @deprecated Use {@link #restart(Volume)} instead.
     */
    @Deprecated
    public void cancelRestart(Volume volume) {
        if (volume != null) {
            cooldowns.remove(volume.getSerialNumber());
            pendingRestarts.remove(volume.getSerialNumber());
        }
    }

    /**
     * @deprecated Service lifecycle handles shutdown. Use {@link #stopService()} instead.
     */
    @Deprecated
    public void shutdown() {
        stopService();
    }
}
