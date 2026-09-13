package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.statistics.api.StatsHttpServer;
import com.superredrock.usbthief.statistics.collector.*;
import com.superredrock.usbthief.statistics.store.PreferencesMetricStore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Statistics {
    private static final Logger logger = LogManager.getLogger(Statistics.class);
    private static volatile Statistics INSTANCE;

    private final MetricRegistry registry;
    private final PreferencesMetricStore store;

    private final TotalDevicesCopiedCollector devicesCollector;
    private final ExtensionCountCollector extensionCollector;
    private final SessionProgressCollector sessionCollector;
    private final SpeedCollector speedCollector;
    private final VolumeStatsCollector volumeStatsCollector;
    private final DeviceHistoryCollector deviceHistoryCollector;
    private final StatsHttpServer httpServer;

    /** Guards {@link #start()} so persistence is loaded and the HTTP API bound exactly once. */
    private final AtomicBoolean started = new AtomicBoolean(false);

    private Statistics() {
        registry = new MetricRegistry();
        store = new PreferencesMetricStore(Statistics.class);

        // Register all collectors (each registers its own EventBus listeners)
        registry.register(new TotalFilesCopiedCollector());
        registry.register(new TotalBytesCopiedCollector());
        registry.register(new TotalErrorsCollector());
        registry.register(new TotalFoldersCopiedCollector());

        devicesCollector = new TotalDevicesCopiedCollector();
        registry.register(devicesCollector);

        extensionCollector = new ExtensionCountCollector();
        registry.register(extensionCollector);

        sessionCollector = new SessionProgressCollector();
        registry.register(sessionCollector);

        speedCollector = new SpeedCollector();
        registry.register(speedCollector);

        volumeStatsCollector = new VolumeStatsCollector();
        registry.register(volumeStatsCollector);

        deviceHistoryCollector = new DeviceHistoryCollector();
        registry.register(deviceHistoryCollector);

        // No I/O and no socket binding happen here: reading the persisted metrics and starting
        // the HTTP API moved to start(), which the assembly point (AppContext) calls explicitly
        // before the services start. Constructing a Statistics instance is now side-effect free.
        httpServer = new StatsHttpServer();
    }

    /**
     * Loads the persisted metrics and starts the statistics HTTP API (when enabled).
     *
     * <p>Idempotent. Called once by the assembly point ({@code AppContext.initialize()}) before
     * the services start producing events; {@link #getInstance()} alone does <em>not</em> perform
     * these actions any more.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            logger.debug("Statistics already started, ignoring duplicate start()");
            return;
        }

        // Load persistent data
        load();

        // Start HTTP API
        httpServer.start(registry);

        logger.info("Statistics loaded: {} files, {}",
                getTotalFilesCopied(), SizeFormatter.format(getTotalBytesCopied()));
    }

    public static Statistics getInstance() {
        if (INSTANCE == null) {
            synchronized (Statistics.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Statistics();
                }
            }
        }
        return INSTANCE;
    }

    // --- Persistence lifecycle ---

    public void load() {
        registry.loadAll(store);
    }

    public void save() {
        registry.saveAll(store);
        logger.info("Statistics saved");
    }

    public void shutdown() {
        httpServer.stop();
        registry.saveAll(store);
        logger.info("Statistics shutdown complete");
    }

    // --- Total counters (delegated to collectors via registry) ---

    public long getTotalFilesCopied() {
        return registry.getSnapshot(TotalFilesCopiedCollector.ID).longValue();
    }

    public long getTotalBytesCopied() {
        return registry.getSnapshot(TotalBytesCopiedCollector.ID).longValue();
    }

    public long getTotalErrors() {
        return registry.getSnapshot(TotalErrorsCollector.ID).longValue();
    }

    public long getTotalFoldersCopied() {
        return registry.getSnapshot(TotalFoldersCopiedCollector.ID).longValue();
    }

    public long getTotalDevicesCopied() {
        return registry.getSnapshot(TotalDevicesCopiedCollector.ID).longValue();
    }

    public int getCopiedDeviceCount() {
        return devicesCollector.getCopiedDeviceCount();
    }

    public Map<String, Long> getExtensionCounts() {
        return extensionCollector.getExtensionCounts();
    }

    // --- Session progress ---

    public long getSessionBytesDiscovered() {
        return sessionCollector.getBytesDiscovered();
    }

    public long getSessionBytesCopied() {
        return sessionCollector.getBytesCopied();
    }

    public long getSessionFilesCopied() {
        return sessionCollector.getFilesCopied();
    }

    public long getSessionFoldersCopied() {
        return sessionCollector.getFoldersCopied();
    }

    public int getProgressPercentage() {
        return sessionCollector.getProgressPercentage();
    }

    // --- Speed ---

    public double getCurrentSpeed() {
        return registry.getSnapshot(SpeedCollector.ID).doubleValue();
    }

    public MetricSnapshot getSpeedSnapshot() {
        return registry.getSnapshot(SpeedCollector.ID);
    }

    // --- Volume stats ---

    public VolumeStats getVolumeStats(String serial) {
        return volumeStatsCollector.getVolumeStats(serial);
    }

    public Map<String, VolumeStats> getAllVolumeStats() {
        return volumeStatsCollector.getAllVolumeStats();
    }

    // --- Device history ---

    public DeviceHistoryEntry getDeviceHistory(String serial) {
        return deviceHistoryCollector.getDeviceHistory(serial);
    }

    public Map<String, DeviceHistoryEntry> getAllDeviceHistory() {
        return deviceHistoryCollector.getAllDeviceHistory();
    }

    public boolean isDeviceLive(String serial) {
        Device device = DeviceManager.getInstance().getDeviceBySerial(serial);
        if (device == null) return false;
        return device.getVolumes().stream().anyMatch(Volume::isPresent);
    }

    // --- Registry access (for HTTP API) ---

    public MetricRegistry getRegistry() {
        return registry;
    }

    public SpeedCollector getSpeedCollector() {
        return speedCollector;
    }

    // --- Reset ---

    public void resetSession() {
        sessionCollector.reset();
    }

    public void resetAll() {
        registry.resetAll();
        // Persist the wipe right away: with the diff write path this removes exactly the keys that
        // existed and leaves the store consistent even if the process dies before the next save.
        registry.saveAll(store);
        logger.info("Statistics reset");
    }
}
