package com.superredrock.usbthief;


import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.index.Index;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.TaskScheduler;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.StorageLowEvent;
import com.superredrock.usbthief.core.event.storage.StorageRecoveredEvent;
import com.superredrock.usbthief.gui.MainFrame;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class Main {
    static final Logger logger = Logger.getLogger(Main.class.getName());
    static final Preferences config = Preferences.userNodeForPackage(Main.class);

    static boolean hasLaunched = config.getBoolean("hasLaunched", false);

    public static void initializeFirstTime() {
        logger.info("Initializing");
        initializeWorkDirectory();
    }

    /**
     * Initialize the working directory (creates it if it doesn't exist).
     * Uses the configured work path from ConfigSchema.WORK_PATH.
     */
    private static void initializeWorkDirectory() {
        try {
            String workPathStr = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
            if (workPathStr != null && !workPathStr.isEmpty()) {
                Path workPath = Paths.get(workPathStr);
                if (!Files.exists(workPath)) {
                    Files.createDirectories(workPath);
                    logger.info("Created working directory: " + workPath.toAbsolutePath());
                } else if (!Files.isDirectory(workPath)) {
                    logger.warning("Work path exists but is not a directory: " + workPath.toAbsolutePath());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to create working directory: " + e.getMessage());
        }
    }

    static void main() {
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

        // Register logging listeners for storage events
        registerStorageEventListeners();

        // Start services
        DeviceManager.getInstance().start();
        TaskScheduler.getInstance().start();
        Index.getInstance().start();
        SnifferLifecycleManager.getInstance().start();

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
        eventBus.register(StorageLowEvent.class, event -> {
            logger.warning("Storage low: " + event.freeBytes() + " bytes free, threshold: " + event.thresholdBytes() + " bytes, level: " + event.level());
        });

        // Register listener for storage recovered events
        eventBus.register(StorageRecoveredEvent.class, event -> {
            logger.info("Storage recovered: " + event.freeBytes() + " bytes free");
        });

        // Register listener for files recycled events
        eventBus.register(FilesRecycledEvent.class, event -> {
            logger.info("Files recycled: " + event.fileCount() + " files (strategy: " + event.strategy() + "), " + event.bytesFreed() + " bytes freed");
        });

        // Register listener for empty folders deleted events
        eventBus.register(EmptyFoldersDeletedEvent.class, event -> {
            logger.info("Empty folders deleted: " + event.count() + " folders");
        });
    }

    public static void quit() {
        System.out.println("Quitting");
        Statistics.getInstance().save();
        
        // Stop services
        DeviceManager.getInstance().stopService();
        TaskScheduler.getInstance().stopService();
        Index.getInstance().stopService();
        
        QueueManager.quit();
    }

}