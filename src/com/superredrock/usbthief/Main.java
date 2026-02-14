package com.superredrock.usbthief;


import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.ServiceManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.gui.MainFrame;
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
        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.loadServices();

        if (!hasLaunched){
            //initializeFirstTime();
            config.putBoolean("hasLaunched", true);
            hasLaunched = true;
        }

        logger.info("Starting");
        QueueManager.init();

        QueueManager.getIndex().load();


        // 启动所有服务
        serviceManager.startAll();

        // 显示主窗口
        MainFrame.launch();

    }

    public static void quit() {
        System.out.println("Quitting");
        Statistics.getInstance().save();
        ServiceManager.getInstance().shutdown();
        QueueManager.quit();
    }

}