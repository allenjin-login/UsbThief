package com.superredrock.usbthief;


import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.ServiceManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.gui.MainFrame;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class Main {
    static final Logger logger = Logger.getLogger(Main.class.getName());
    static final Preferences config = Preferences.userNodeForPackage(Main.class);

    static boolean hasLaunched = config.getBoolean("hasLaunched", false);

    public static void initializeFirstTime() {
        logger.info("Initializing");
    }

    static void main(String[] args) {
        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.loadServices();

        if (!hasLaunched){
            initializeFirstTime();
            config.putBoolean("hasLaunched", true);
            hasLaunched = true;
        }

        logger.info("Starting");
        QueueManager.init();

        QueueManager.index.load();


        // 启动所有服务
        serviceManager.startAll();

        // 显示主窗口
        MainFrame.launch();

    }

    public static void quit() {
        System.out.println("Quitting");
        ServiceManager.getInstance().shutdown();
        QueueManager.quit();
    }

}