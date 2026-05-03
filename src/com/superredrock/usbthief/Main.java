package com.superredrock.usbthief;


import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.LoggingConfig;
import com.superredrock.usbthief.core.QueueManager;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.index.Index;
import com.superredrock.usbthief.worker.RecyclerService;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.TaskScheduler;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.StorageLowEvent;
import com.superredrock.usbthief.core.event.storage.StorageRecoveredEvent;
import com.superredrock.usbthief.gui.MainFrame;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.prefs.Preferences;

public class Main {
    static final Logger logger = LogManager.getLogger(Main.class);
    static final Preferences config = Preferences.userNodeForPackage(Main.class);

    static boolean hasLaunched = config.getBoolean("hasLaunched", false);

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

        // Load index
        Index.getInstance().load();

        // Initialize statistics (eager init ensures collectors register before services start)
        Statistics.getInstance();

        // Register logging listeners for storage events
        registerStorageEventListeners();

        // Start services
        DeviceManager.getInstance().start();
        TaskScheduler.getInstance().start();
        Index.getInstance().start();
        SnifferLifecycleManager.getInstance().start();
        RecyclerService.getInstance().start();

        // 显示主窗口
        MainFrame.launch();

    }

    /**
     * Registers default logging listeners for storage events.
     * These listeners log key storage events for monitoring and debugging.
     */
    private static void registerStorageEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        // Register listener for storage low events
        eventBus.register(StorageLowEvent.class, event -> logger.warn("Storage low: {} bytes free, threshold: {} bytes, level: {}", event.freeBytes(), event.thresholdBytes(), event.level()));

        // Register listener for storage recovered events
        eventBus.register(StorageRecoveredEvent.class, event -> logger.info("Storage recovered: {} bytes free", event.freeBytes()));

        // Register listener for files recycled events
        eventBus.register(FilesRecycledEvent.class, event -> logger.info("Files recycled: {} files (strategy: {}), {} bytes freed", event.fileCount(), event.strategy(), event.bytesFreed()));

        // Register listener for empty folders deleted events
        eventBus.register(EmptyFoldersDeletedEvent.class, event -> logger.info("Empty folders deleted: {} folders", event.count()));
    }

    public static void quit() {
        System.out.println("Quitting");
        Statistics.getInstance().shutdown();
        
        // Stop services
        DeviceManager.getInstance().stopService();
        TaskScheduler.getInstance().stopService();
        Index.getInstance().stopService();
        RecyclerService.getInstance().stopService();
        
        QueueManager.quit();
    }

}