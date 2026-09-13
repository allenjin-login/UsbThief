package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.concurrent.CooldownTimer;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.StorageConfig;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * Pending restart cooldowns keyed by volume serial number. Backed by the shared
     * {@code ScheduledExecutorService}: a cooldown is a delayed task, not a polling thread.
     */
    private final CooldownTimer cooldowns = new CooldownTimer();

    /** Whether init check has been done */
    private volatile boolean deviceManagerReady = false;

    /** Guards {@link #initialize()} so the global listeners are registered exactly once. */
    private final AtomicBoolean listenersRegistered = new AtomicBoolean(false);

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

    /**
     * Deliberately side-effect free: the constructor used to register three listeners into the
     * global {@link EventBus}, so merely constructing (or class-loading) this service mutated
     * process-wide state. {@link #initialize()} performs the registration instead, and the
     * assembly point calls it before the service starts.
     */
    private SnifferLifecycleManager() {
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

    /**
     * Registers the global {@link EventBus} listeners.
     *
     * <p>Idempotent and safe to call from more than one thread. The assembly point
     * ({@code AppContext.initialize()}) calls it before the services start; {@link #tick()} calls
     * it as well, so a service that was started without the assembly point still reacts to events.
     */
    public void initialize() {
        if (listenersRegistered.compareAndSet(false, true)) {
            registerEventListeners();
            logger.debug("Sniffer lifecycle listeners registered");
        }
    }

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
            var newState = event.newState();

            switch (newState) {
                case OFFLINE, EJECTING -> {
                    logger.debug("Volume {} , stopping sniffer: {}", newState, serial);
                    stopSnifferOnly(serial);
                    if (!cooldowns.isPending(serial)) {
                        scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
                    }
                }
                case DISABLED -> {
                    logger.debug("Volume DISABLED, stopping sniffer: {}", serial);
                    stop(serial);
                }
                case IDLE -> {
                    if (!event.oldState().isPresent()) {
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
        // Defensive: the assembly point normally registers the listeners before start(), but a
        // running service must react to events no matter how it was brought up.
        initialize();

        if (!deviceManagerReady) {
            if (QueueManager.getDeviceManager() == null) return;
            deviceManagerReady = true;
        }

        Collection<Volume> volumes = QueueManager.getDeviceManager().getAllVolumes();
        for (Volume volume : volumes) {
            String serial = volume.getSerialNumber();
            if (volume.isActive() &&
                !sniffers.containsKey(serial) &&
                !cooldowns.isPending(serial)) {
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
     * Schedules a restart after the cooldown configured for the given reason.
     */
    private void scheduleRestart(String serial, RestartReason reason) {
        long delayMs = getRestartDelayMs(reason);

        if (delayMs <= 0) {
            Volume vol = getVolumeBySerial(serial);
            if (vol != null && vol.isActive() && !sniffers.containsKey(serial)) {
                logger.info("No delay, restarting sniffer for: {}", serial);
                createSniffer(vol);
            }
            return;
        }

        scheduleRestart(serial, delayMs, reason);
    }

    /**
     * Schedules a restart after an explicit delay.
     *
     * <p>The cooldown itself runs on the shared scheduler thread and only performs the
     * eligibility check plus {@link #createSniffer(Volume)}, which merely spawns the
     * Sniffer thread - the scan work stays on the Sniffer/scan threads. Package-private so
     * a test can drive the restart path without waiting the configured minutes.
     *
     * @param serial  the volume serial number
     * @param delayMs the cooldown in milliseconds, must be positive
     * @param reason  the reason the restart was scheduled (for logging)
     */
    void scheduleRestart(String serial, long delayMs, RestartReason reason) {
        cooldowns.schedule(serial, delayMs, () -> {
            Volume vol = getVolumeBySerial(serial);
            if (vol != null && vol.isActive() && !sniffers.containsKey(serial)) {
                logger.info("Cooldown elapsed, restarting sniffer for: {}", serial);
                createSniffer(vol);
            } else {
                logger.debug("Skipping restart for {}: volume not IDLE or sniffer already active", serial);
            }
        });
        logger.info("Scheduled restart for {} in {} min (reason: {})", serial, TimeUnit.MILLISECONDS.toMinutes(delayMs), reason);
    }

    private void cancelTimer(String serial) {
        cooldowns.cancel(serial);
    }

    private Volume getVolumeBySerial(String serial) {
        DeviceManager dm = QueueManager.getDeviceManager();
        return dm != null ? dm.getVolumeBySerial(serial) : null;
    }


    /**
     * Gets the restart delay in milliseconds based on the reason.
     *
     * <p>Package-private for testing: the reason-to-delay mapping is an acceptance criterion
     * of the cooldown behaviour (NORMAL_COMPLETION and ERROR differ, STORAGE_PAUSE restarts
     * immediately).
     */
    long getRestartDelayMs(RestartReason reason) {
        ConfigManager config = ConfigManager.getInstance();
        return switch (reason) {
            case NORMAL_COMPLETION ->
                TimeUnit.MINUTES.toMillis(config.get(StorageConfig.SNIFFER_WAIT_NORMAL_MINUTES));
            case ERROR ->
                TimeUnit.MINUTES.toMillis(config.get(StorageConfig.SNIFFER_WAIT_ERROR_MINUTES));
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
        return cooldowns.isPending(serialNumber);
    }

    /**
     * Gets the remaining cooldown time in milliseconds for a volume, or 0 if not in cooldown.
     */
    public long getRemainingCooldownMs(String serialNumber) {
        return cooldowns.remainingMs(serialNumber);
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

        for (Map.Entry<String, Long> entry : cooldowns.remainingMsSnapshot().entrySet()) {
            String serial = entry.getKey();
            boolean hasActive = snapshots.stream().anyMatch(s -> s.serialNumber().equals(serial));
            if (!hasActive) {
                long remaining = entry.getValue();
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
        cooldowns.cancelAll();

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
