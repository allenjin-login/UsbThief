package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.statistics.Statistics;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * System tray integration for UsbThief.
 * Provides tray icon with popup menu for window control.
 * Enhanced with dynamic icon states via TrayIconManager.
 * All text is hardcoded in English - not affected by i18n.
 */
public class SystemTrayIcon {
    private static final Logger logger = LogManager.getLogger(SystemTrayIcon.class);

    private final MainFrame mainFrame;
    private TrayIcon trayIcon;
    private MenuItem showHideItem;
    private MenuItem alwaysHideItem;
    private MenuItem scanItem;
    private MenuItem speedItem;
    private MenuItem copiedItem;
    private TrayIconManager trayIconManager;
    private Timer stateTimer;

    public SystemTrayIcon(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * Initialize and show system tray icon.
     *
     * @return true if successful, false if system tray is not supported
     */
    public boolean initialize() {
        if (!SystemTray.isSupported()) {
            logger.warn("System tray is not supported on this platform");
            return false;
        }

        SystemTray systemTray = SystemTray.getSystemTray();
        int iconSize = systemTray.getTrayIconSize().width;

        // Initialize TrayIconManager
        trayIconManager = new TrayIconManager();
        trayIconManager.initIcons(iconSize);

        PopupMenu popup = new PopupMenu();

        showHideItem = new MenuItem("Show Window");
        showHideItem.addActionListener(this::toggleWindowVisibility);
        popup.add(showHideItem);

        popup.addSeparator();

        speedItem = new MenuItem("Speed: 0.0 MB/s");
        speedItem.setEnabled(false);
        popup.add(speedItem);

        copiedItem = new MenuItem("Copied: 0 B (0 files)");
        copiedItem.setEnabled(false);
        popup.add(copiedItem);

        popup.addSeparator();

        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.START_HIDDEN);
        alwaysHideItem = new MenuItem("Start Hide: " + (alwaysHidden ? "Yes" : "No"));
        alwaysHideItem.addActionListener(this::toggleAlwaysHidden);
        popup.add(alwaysHideItem);

        popup.addSeparator();

        scanItem = new MenuItem("Pause Scanning");
        scanItem.addActionListener(this::toggleScanning);
        popup.add(scanItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(this::exitApplication);
        popup.add(exitItem);

        Image trayImage = createTrayIconImage();
        if (trayImage == null) {
            logger.warn("Failed to create tray icon image, using generated icon");
            trayImage = trayIconManager.generateIcon(TrayIconManager.TrayState.IDLE, iconSize);
        }

        Image scaledImage = trayImage.getScaledInstance(iconSize, iconSize, Image.SCALE_DEFAULT);
        trayIcon = new TrayIcon(scaledImage, "UsbThief - USB Device Monitor", popup);

        trayIcon.setImageAutoSize(true);

        trayIcon.addActionListener((ActionEvent _) -> {
            logger.info("Tray icon double-clicked");
            mainFrame.toggleWindowVisibility();
        });

        try {
            systemTray.add(trayIcon);
            logger.info("System tray icon added successfully");

            // Configure TrayIconManager
            trayIconManager.setTrayIcon(trayIcon);
            trayIconManager.registerEventListeners();

            // Periodic state update
            stateTimer = new Timer(1000, _ -> {
                trayIconManager.updateState();
                updateDynamicMenuItems();
            });
            stateTimer.start();

            return true;
        } catch (AWTException e) {
            logger.error("Failed to add tray icon: {}", e);
            return false;
        }
    }

    private void updateDynamicMenuItems() {
        double speed = Statistics.getInstance().getSpeedCollector().getProbeGroup().getTotalSpeed();
        speedItem.setLabel(String.format("Speed: %.1f MB/s", speed));

        long bytes = Statistics.getInstance().getSpeedCollector().getProbeGroup().getTotalBytes();
        long files = Statistics.getInstance().getTotalFilesCopied();
        copiedItem.setLabel(String.format("Copied: %s (%d files)", SizeFormatter.format(bytes), files));
    }

    /**
     * Create tray icon image from resources.
     * Tries to load icon.png, icon.gif, or icon.ico from classpath.
     */
    private Image createTrayIconImage() {
        String[] iconNames = {"icon.png", "icon.gif", "icon.ico"};

        for (String name : iconNames) {
            try {
                ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(name)));
                if (icon.getIconWidth() > 0) {
                    logger.debug("Loaded tray icon: {}", name);
                    return icon.getImage();
                }
            } catch (Exception _) {
                // Continue to next format
            }
        }

        return null;
    }

    /**
     * Toggle window visibility (Show/Hide).
     */
    private void toggleWindowVisibility(ActionEvent e) {
        mainFrame.toggleWindowVisibility();
        updateMenuItems();
    }

    /**
     * Toggle "Always Hidden" setting.
     */
    private void toggleAlwaysHidden(ActionEvent e) {
        boolean currentValue = ConfigManager.getInstance().get(ConfigSchema.START_HIDDEN);
        boolean newValue = !currentValue;
        ConfigManager.getInstance().set(ConfigSchema.START_HIDDEN, newValue);

        alwaysHideItem.setLabel("Start Hide: " + (newValue ? "Yes" : "No"));

        logger.info("Always Hidden setting changed to: {}", newValue);

        if (newValue && mainFrame.isVisible()) {
            mainFrame.hideWindow();
        }
    }

    /**
     * Toggle device scanning (Start/Stop).
     */
    private void toggleScanning(ActionEvent e) {
        MenuItem item = (MenuItem) e.getSource();
        if (item.getLabel().equals("Pause Scanning")) {
            item.setLabel("Start Scanning");
            logger.info("Scanning paused (menu toggle)");
        } else {
            item.setLabel("Pause Scanning");
            logger.info("Scanning resumed (menu toggle)");
        }
    }

    private void exitApplication(ActionEvent e) {
        logger.info("Exit requested from system tray");

        int confirm = JOptionPane.showConfirmDialog(
            mainFrame,
            "Are you sure you want to exit UsbThief?",
            "Confirm Exit",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            mainFrame.performShutdown();
        }
    }

    private void updateMenuItems() {
        if (trayIcon != null && trayIcon.getPopupMenu() != null) {
            showHideItem.setLabel(mainFrame.isVisible() ? "Hide Window" : "Show Window");
        }
    }

    public void updateShowHideMenuItem() {
        updateMenuItems();
    }

    public void refreshLanguage() {
        if (trayIcon == null) return;

        trayIcon.setToolTip("UsbThief - USB Device Monitor");

        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.START_HIDDEN);
        showHideItem.setLabel(mainFrame.isVisible() ? "Hide Window" : "Show Window");
        alwaysHideItem.setLabel("Start Hide: " + (alwaysHidden ? "Yes" : "No"));
        scanItem.setLabel("Pause Scanning");
    }

    /**
     * Display a notification message in the system tray.
     */
    public void displayMessage(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    /**
     * Remove the tray icon.
     */
    public void dispose() {
        if (stateTimer != null) stateTimer.stop();
        if (trayIcon != null) {
            SystemTray systemTray = SystemTray.getSystemTray();
            systemTray.remove(trayIcon);
            trayIcon = null;
            logger.info("System tray icon removed");
        }
    }

    /**
     * Check if tray icon is currently displayed.
     */
    public boolean isActive() {
        return trayIcon != null;
    }
}
