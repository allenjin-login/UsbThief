package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.statistics.Statistics;
import com.superredrock.usbthief.worker.RecyclerService;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.TaskScheduler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Explicit assembly point for the application's core collaborating objects (AR-10, phase 1).
 *
 * <p>Before this class existed every manager fetched its collaborators through its own
 * {@code getInstance()}, so the wiring was invisible and the order in which the object graph was
 * built depended on which class happened to be loaded first. {@link #initialize()} collects the
 * core objects once and performs the start-up side effects that used to hide inside constructors
 * ({@code Statistics}: load persisted metrics and bind the HTTP API; {@code SnifferLifecycleManager}:
 * register the global event listeners).
 *
 * <p><b>No side effects in the constructor.</b> The constructor only stores the references it is
 * handed; everything else happens in {@link #initialize()}.
 *
 * <p><b>Transitional facade.</b> The individual {@code getInstance()} accessors keep working and
 * return the same instances this context holds, so callers can be migrated one at a time. New code
 * should receive its collaborators as constructor parameters and be assembled here instead of
 * reaching for a singleton. This class is intentionally <em>not</em> a replacement for the 13
 * existing {@code getInstance()} methods - that migration is a separate, incremental effort.
 */
public final class AppContext {

    private static final Logger logger = LogManager.getLogger(AppContext.class);

    /** The assembled context, or {@code null} while the application has not been wired up yet. */
    private static volatile AppContext current;

    private final ConfigManager configManager;
    private final EventBus eventBus;
    private final ServiceRegistry serviceRegistry;
    private final DeviceManager deviceManager;
    private final TaskScheduler taskScheduler;
    private final SnifferLifecycleManager snifferLifecycleManager;
    private final RecyclerService recyclerService;
    private final Statistics statistics;

    /**
     * Stores the assembled components. Deliberately free of side effects: no component is
     * constructed, started, registered or otherwise touched here, which keeps the construction
     * order of the graph in {@link #initialize()} where it is visible.
     */
    private AppContext(ConfigManager configManager,
                       EventBus eventBus,
                       ServiceRegistry serviceRegistry,
                       DeviceManager deviceManager,
                       TaskScheduler taskScheduler,
                       SnifferLifecycleManager snifferLifecycleManager,
                       RecyclerService recyclerService,
                       Statistics statistics) {
        this.configManager = configManager;
        this.eventBus = eventBus;
        this.serviceRegistry = serviceRegistry;
        this.deviceManager = deviceManager;
        this.taskScheduler = taskScheduler;
        this.snifferLifecycleManager = snifferLifecycleManager;
        this.recyclerService = recyclerService;
        this.statistics = statistics;
    }

    /**
     * Assembles the core object graph and runs its start-up side effects.
     *
     * <p>Idempotent: the first call does the work, every later call returns the same instance.
     * Must be called before the services are started, so that the statistics collectors and the
     * sniffer lifecycle listeners are in place before any service can dispatch an event.
     *
     * <p>The context is only published (and therefore only visible through {@link #current()})
     * after the whole graph has been assembled, so a failed start-up never leaves a half-built
     * context behind.
     *
     * @return the application-wide context
     */
    public static AppContext initialize() {
        AppContext existing = current;
        if (existing != null) {
            return existing;
        }
        synchronized (AppContext.class) {
            if (current != null) {
                return current;
            }

            AppContext context = new AppContext(
                    ConfigManager.getInstance(),
                    EventBus.getInstance(),
                    ServiceRegistry.getInstance(),
                    DeviceManager.getInstance(),
                    TaskScheduler.getInstance(),
                    SnifferLifecycleManager.getInstance(),
                    RecyclerService.getInstance(),
                    Statistics.getInstance());

            // Start-up side effects, moved out of the constructors above: collectors must be
            // registered (Statistics construction does that) and persisted metrics loaded before
            // the services start producing events; the sniffer listeners likewise.
            context.statistics.start();
            context.snifferLifecycleManager.initialize();

            current = context;
            logger.info("AppContext assembled");
            return context;
        }
    }

    /**
     * @return the assembled context, or {@code null} when {@link #initialize()} has not run yet
     */
    public static AppContext current() {
        return current;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    public DeviceManager getDeviceManager() {
        return deviceManager;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public SnifferLifecycleManager getSnifferLifecycleManager() {
        return snifferLifecycleManager;
    }

    public RecyclerService getRecyclerService() {
        return recyclerService;
    }

    public Statistics getStatistics() {
        return statistics;
    }
}
