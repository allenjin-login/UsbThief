package com.superredrock.usbthief;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.gui.BlankFrame;
import com.superredrock.usbthief.gui.GuiUtils;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import com.superredrock.usbthief.core.LoggingConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * USB device/volume testing utility.
 */
public class UsbTesting {

    private static final Logger logger = LogManager.getLogger(UsbTesting.class);
    private static volatile boolean running = true;
    private static JFrame f;

    static void main(String[] args) {
        setupLogging();

        logger.info("=== USB Device Testing Utility ===");
        initializeWorkDirectory();
        QueueManager.init();
        registerEventListeners();

        final Object lock = new Object();
        final long[] hwndHolder = new long[1];

        SwingUtilities.invokeLater(() -> {
            try {
                f = new BlankFrame();
                hwndHolder[0] = GuiUtils.getHWND(f);
            } finally {
                synchronized (lock) { lock.notifyAll(); }
            }
        });

        synchronized (lock) {
            try { lock.wait(5000); } catch (InterruptedException _) {}
        }

        if (hwndHolder[0] == 0) {
            logger.error("Failed to create window. Exiting.");
            System.exit(1);
        }

        DeviceManager.getInstance().start();
        logger.info("DeviceManager started. Waiting for USB devices...\n");

        displayConnected();

        logger.info("\nMonitoring events. Press ENTER to exit...\n");
        new Thread(() -> {
            new Scanner(System.in).nextLine();
            running = false;
            shutdown();
        }, "InputWaiter").start();

        while (running) {
            try { Thread.sleep(1000); } catch (InterruptedException _) { break; }
        }
    }

    private static void setupLogging() {
        LoggingConfig.initialize();
    }

    private static void initializeWorkDirectory() {
        try {
            String workPathStr = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
            if (workPathStr != null && !workPathStr.isEmpty()) {
                Path workPath = Paths.get(workPathStr);
                if (!Files.exists(workPath)) Files.createDirectories(workPath);
            }
        } catch (Exception e) {
            logger.warn("Failed to create working directory:", e);
        }
    }

    private static void registerEventListeners() {
        EventBus bus = EventBus.getInstance();

        bus.register(DeviceArrivalEvent.class, e -> {
            Device d = e.device();
            logger.info("\n=== DEVICE ARRIVED ===");
            logger.info("Serial: {}, VID: {}, PID: {}", d.getSerialNumber(), d.getVid(), d.getPid());
        });

        bus.register(DeviceRemovalEvent.class, e -> {
            logger.info("\n=== DEVICE REMOVED ===");
            logger.info("Serial: {}", e.device().getSerialNumber());
        });

        bus.register(VolumeInsertedEvent.class, e -> {
            Volume v = e.volume();
            logger.info("\n=== VOLUME INSERTED ===");
            printVolume(v);
        });

        bus.register(VolumeRemovedEvent.class, e -> {
            Volume v = e.volume();
            logger.info("\n=== VOLUME REMOVED ===");
            logger.info("Serial: {}, State: {}", v.getSerialNumber(), v.getState());
        });

        bus.register(VolumeStateChangedEvent.class, e -> {
            logger.info("\n--- STATE CHANGED: {} {} -> {} ---", e.volume().getSerialNumber(), e.oldState(), e.newState());
        });

        logger.info("Event listeners registered");
    }

    private static void displayConnected() {
        DeviceManager dm = DeviceManager.getInstance();
        var devices = dm.getAllDevices();
        var volumes = dm.getAllVolumes();

        logger.info("\n=== Devices ({}) ===", devices.size());
        for (Device d : devices) {
            logger.info("  Serial: {}, VID: {}, PID: {}", d.getSerialNumber(), d.getVid(), d.getPid());
        }

        logger.info("\n=== Volumes ({}) ===", volumes.size());
        for (Volume v : volumes) {
            printVolume(v);
        }
    }

    private static void printVolume(Volume v) {
        logger.info("  Drive: {}, Serial: {}, State: {}, Accessible: {}", v.getDriveLetter(), v.getSerialNumber(), v.getState(), v.isConnected());
        if (v.getFileStore() != null) {
            try {
                long total = v.getFileStore().getTotalSpace();
                long free = v.getFileStore().getUsableSpace();
                logger.info("  Storage: {} / {}", SizeFormatter.format(total - free), SizeFormatter.format(total));
            } catch (Exception _) {}
        }
    }

    private static void shutdown() {
        logger.info("Shutting down...");
        DeviceManager.getInstance().stopService();
        QueueManager.quit();
        System.exit(0);
    }
}
