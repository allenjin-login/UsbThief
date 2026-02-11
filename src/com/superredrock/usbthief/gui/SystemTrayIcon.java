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
 */
public class SystemTrayIcon {
    private static final Logger logger = Logger.getLogger(SystemTrayIcon.class.getName());

    private final I18NManager i18n = I18NManager.getInstance();
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
        // Check if system tray is supported
        if (!SystemTray.isSupported()) {
            logger.warning("System tray is not supported on this platform");
            return false;
        }

        SystemTray systemTray = SystemTray.getSystemTray();

        // Create popup menu
        PopupMenu popup = new PopupMenu();

        // Show/Hide toggle menu item
        showHideItem = new MenuItem(i18n.getMessage("tray.menu.show"));
        showHideItem.addActionListener(this::toggleWindowVisibility);
        popup.add(showHideItem);


        popup.addSeparator();

        // Always Hide toggle menu item
        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.ALWAYS_HIDDEN);
        alwaysHideItem = new MenuItem(i18n.getMessage("tray.menu.alwaysHide", alwaysHidden ? i18n.getMessage("tray.menu.yes") : i18n.getMessage("tray.menu.no")));
        alwaysHideItem.addActionListener(this::toggleAlwaysHidden);
        popup.add(alwaysHideItem);

        popup.addSeparator();

        // Start/Stop scanning menu item
        scanItem = new MenuItem(i18n.getMessage("tray.menu.pause"));
        scanItem.addActionListener(this::toggleScanning);
        popup.add(scanItem);

        popup.addSeparator();

        // Exit menu item
        MenuItem exitItem = new MenuItem(i18n.getMessage("tray.menu.exit"));
        exitItem.addActionListener(this::exitApplication);
        popup.add(exitItem);

        // Load tray icon image
        Image trayImage = createTrayIconImage();
        if (trayImage == null) {
            logger.warning("Failed to create tray icon image, using default");
            // Use default icon or create a simple colored square
            trayImage = createDefaultIcon();
        }

        // Create tray icon with tooltip
        int iconSize = systemTray.getTrayIconSize().width;
        Image scaledImage = trayImage.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
        trayIcon = new TrayIcon(scaledImage, i18n.getMessage("tray.tooltip"), popup);

        // Set auto-size to true for better appearance
        trayIcon.setImageAutoSize(true);

        // Add double-click listener to toggle window
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

        // Update menu item label
        alwaysHideItem.setLabel(i18n.getMessage("tray.menu.alwaysHide", newValue ? i18n.getMessage("tray.menu.yes") : i18n.getMessage("tray.menu.no")));

        logger.info("Always Hidden setting changed to: " + newValue);

        // If enabled, hide the window immediately
        if (newValue && mainFrame.isVisible()) {
            mainFrame.hideWindow();
        }
    }

    /**
     * Toggle device scanning (Start/Stop).
     */
    private void toggleScanning(ActionEvent e) {
        MenuItem item = (MenuItem) e.getSource();
        // This would need access to deviceListPanel
        // For now, just toggle the label
        if (item.getLabel().equals(i18n.getMessage("tray.menu.pause"))) {
            item.setLabel(i18n.getMessage("tray.menu.start"));
            logger.info("Scanning paused (menu toggle)");
        } else {
            item.setLabel(i18n.getMessage("tray.menu.pause"));
            logger.info("Scanning resumed (menu toggle)");
        }
    }

    /**
     * Exit application with unified shutdown logic.
     */
    private void exitApplication(ActionEvent e) {
        logger.info("Exit requested from system tray");

        int confirm = JOptionPane.showConfirmDialog(
            mainFrame,
            i18n.getMessage("tray.exit.confirm"),
            i18n.getMessage("tray.exit.confirm.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            // Use unified shutdown logic from MainFrame
            mainFrame.performShutdown();
        }
    }

    /**
     * Update menu item labels based on current state.
     */
    private void updateMenuItems() {
        if (trayIcon != null && trayIcon.getPopupMenu() != null) {
            // Update Show/Hide item
            showHideItem.setLabel(mainFrame.isVisible() ? i18n.getMessage("tray.menu.show") : i18n.getMessage("tray.menu.hide"));
        }
    }

    /**
     * Update the Show/Hide menu item label based on window state.
     */
    public void updateShowHideMenuItem() {
        updateMenuItems();
    }

    /**
     * Refresh all menu item labels after locale change.
     */
    public void refreshLanguage() {
        if (trayIcon == null) return;

        trayIcon.setToolTip(i18n.getMessage("tray.tooltip"));

        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.ALWAYS_HIDDEN);
        showHideItem.setLabel(mainFrame.isVisible() ? i18n.getMessage("tray.menu.hide") : i18n.getMessage("tray.menu.show"));
        alwaysHideItem.setLabel(i18n.getMessage("tray.menu.alwaysHide", alwaysHidden ? i18n.getMessage("tray.menu.yes") : i18n.getMessage("tray.menu.no")));
        scanItem.setLabel(i18n.getMessage("tray.menu.pause"));
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
