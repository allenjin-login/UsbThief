package com.superredrock.usbthief;


import com.superredrock.usbthief.core.AppContext;
import com.superredrock.usbthief.core.LoggingConfig;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.ServiceRegistry;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.StorageLowEvent;
import com.superredrock.usbthief.core.event.storage.StorageRecoveredEvent;
import com.superredrock.usbthief.gui.MainFrame;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

public class Main {
    static final Logger logger = LogManager.getLogger(Main.class);
    static final Preferences config = Preferences.userNodeForPackage(Main.class);

    static boolean hasLaunched = config.getBoolean("hasLaunched", false);

    /** Guards the unified shutdown path so GUI exit + JVM shutdown hook cannot run it twice. */
    private static final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    static void main() {
        // Initialize Log4j2
        LoggingConfig.initialize();

        // Initialize FlatLeaf Look and Feel before any Swing components
        ThemeManager.getInstance();

        if (!hasLaunched){
            //initializeFirstTime();
            config.putBoolean("hasLaunched", true);
            hasLaunched = true;
        }

        logger.info("Starting");
        QueueManager.init();

        // Assemble the core object graph in one explicit place, before any service starts.
        // This also performs the start-up side effects that used to hide in constructors:
        // the statistics collectors are registered and their persisted metrics loaded (plus the
        // stats HTTP API started when enabled), and the sniffer lifecycle listeners are hooked up.
        AppContext context = AppContext.initialize();

        // Register logging listeners for storage events
        registerStorageEventListeners(context.getEventBus());

        // Register services once, in startup order. shutdownAll() reverses that order,
        // so the startup list and the shutdown list can no longer drift apart.
        ServiceRegistry registry = context.getServiceRegistry();
        registry.register(context.getDeviceManager());
        registry.register(context.getTaskScheduler());
        registry.register(context.getSnifferLifecycleManager());
        registry.register(context.getRecyclerService());

        // Cleanup must also happen when the JVM goes down outside the GUI exit path
        // (task manager kill, Windows logoff/shutdown, tray exit). Registered before the
        // services start so a failure during startup is still cleaned up.
        Runtime.getRuntime().addShutdownHook(new Thread(Main::quit, "usbthief-shutdown"));

        registry.startAll();

        // 显示主窗口
        MainFrame.launch();

    }

    /**
     * Registers default logging listeners for storage events.
     * These listeners log key storage events for monitoring and debugging.
     */
    private static void registerStorageEventListeners(EventBus eventBus) {
        // Register listener for storage low events
        eventBus.register(StorageLowEvent.class, event -> logger.warn("Storage low: {} bytes free, threshold: {} bytes, level: {}", event.freeBytes(), event.thresholdBytes(), event.level()));

        // Register listener for storage recovered events
        eventBus.register(StorageRecoveredEvent.class, event -> logger.info("Storage recovered: {} bytes free", event.freeBytes()));

        // Register listener for files recycled events
        eventBus.register(FilesRecycledEvent.class, event -> logger.info("Files recycled: {} files (strategy: {}), {} bytes freed", event.fileCount(), event.strategy(), event.bytesFreed()));

        // Register listener for empty folders deleted events
        eventBus.register(EmptyFoldersDeletedEvent.class, event -> logger.info("Empty folders deleted: {} folders", event.count()));
    }

    /**
     * Unified shutdown path for the whole application.
     *
     * <p>Called by {@code MainFrame.performShutdown()} and by the JVM shutdown hook
     * installed in {@link #main()}. Idempotent: whichever entry point fires first wins,
     * the other becomes a no-op.
     *
     * <p>Order matters: services stop first (so they publish no further events), non-service
     * resources are released next, and the statistics snapshot is written last so that tail
     * events produced during shutdown are still accounted for.
     */
    public static void quit() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            logger.info("Shutdown already in progress, ignoring duplicate quit()");
            return;
        }

        System.out.println("Quitting");

        try {
            // 1. Stop every service, in reverse registration order
            //    (this is the single explicit stop path for SnifferLifecycleManager).
            ServiceRegistry.getInstance().shutdownAll();

            // 2. Release the remaining non-service resources (disk scanner threads).
            QueueManager.quit();
        } finally {
            // 3. Persist statistics last, after event production has stopped.
            Statistics.getInstance().shutdown();
        }
    }

}