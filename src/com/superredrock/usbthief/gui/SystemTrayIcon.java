package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * System tray integration for UsbThief.
 * Provides tray icon with popup menu for window control.
 * All text is hardcoded in English - not affected by i18n.
 */
public class SystemTrayIcon {
    private static final Logger logger = Logger.getLogger(SystemTrayIcon.class.getName());

    private final MainFrame mainFrame;
    private TrayIcon trayIcon;
    private MenuItem showHideItem;
    private MenuItem alwaysHideItem;
    private MenuItem scanItem;

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
            logger.warning("System tray is not supported on this platform");
            return false;
        }

        SystemTray systemTray = SystemTray.getSystemTray();

        PopupMenu popup = new PopupMenu();

        showHideItem = new MenuItem("Show Window");
        showHideItem.addActionListener(this::toggleWindowVisibility);
        popup.add(showHideItem);

        popup.addSeparator();

        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.ALWAYS_HIDDEN);
        alwaysHideItem = new MenuItem("Always Hide: " + (alwaysHidden ? "Yes" : "No"));
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
            logger.warning("Failed to create tray icon image, using default");
            trayImage = createDefaultIcon();
        }

        int iconSize = systemTray.getTrayIconSize().width;
        Image scaledImage = trayImage.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
        trayIcon = new TrayIcon(scaledImage, "UsbThief - USB Device Monitor", popup);

        trayIcon.setImageAutoSize(true);

        trayIcon.addActionListener((ActionEvent e) -> {
            logger.info("Tray icon double-clicked");
            mainFrame.toggleWindowVisibility();
        });

        try {
            systemTray.add(trayIcon);
            logger.info("System tray icon added successfully");
            return true;
        } catch (AWTException e) {
            logger.severe("Failed to add tray icon: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create tray icon image from resources.
     * Tries to load icon.png or icon.gif from classpath.
     */
    private Image createTrayIconImage() {
        // Try to load icon from resources
        try {
            ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icon.png")));
            if (icon.getIconWidth() > 0) {
                return icon.getImage();
            }
        } catch (Exception e) {
            // icon.png not found, try alternative
        }

        try {
            ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icon.gif")));
            if (icon.getIconWidth() > 0) {
                return icon.getImage();
            }
        } catch (Exception e) {
            // icon.gif not found
        }

        return null;
    }

    /**
     * Create a simple default icon (USB symbol).
     * Returns a 16x16 icon with a simple USB-like shape.
     */
    private Image createDefaultIcon() {
        int size = 16;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw USB-like symbol (rectangle with an arrow)
        g2d.setColor(new Color(52, 152, 219)); // Blue color
        g2d.fillRect(3, 6, 10, 6); // Main rectangle

        g2d.setColor(Color.WHITE);
        g2d.fillRect(5, 8, 6, 2); // Inner detail

        g2d.dispose();
        return image;
    }

    /**
     * Toggle window visibility (Show/Hide).
     */
    private void toggleWindowVisibility(ActionEvent e) {
        mainFrame.toggleWindowVisibility();
        updateMenuItems();
    }

    /**
     * Hide window (one-way action).
     */
    private void hideWindow(ActionEvent e) {
        mainFrame.hideWindow();
        updateMenuItems();
    }

    /**
     * Toggle "Always Hidden" setting.
     */
    private void toggleAlwaysHidden(ActionEvent e) {
        boolean currentValue = ConfigManager.getInstance().get(ConfigSchema.ALWAYS_HIDDEN);
        boolean newValue = !currentValue;
        ConfigManager.getInstance().set(ConfigSchema.ALWAYS_HIDDEN, newValue);

        alwaysHideItem.setLabel("Always Hide: " + (newValue ? "Yes" : "No"));

        logger.info("Always Hidden setting changed to: " + newValue);

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

        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.ALWAYS_HIDDEN);
        showHideItem.setLabel(mainFrame.isVisible() ? "Hide Window" : "Show Window");
        alwaysHideItem.setLabel("Always Hide: " + (alwaysHidden ? "Yes" : "No"));
        scanItem.setLabel("Pause Scanning");
    }

    /**
     * Display a notification message in the system tray.
     *
     * @param title   Notification title
     * @param message Notification message
     * @param type    Message type (INFO, WARNING, ERROR)
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
