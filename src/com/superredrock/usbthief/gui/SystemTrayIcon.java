package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.AppPaths;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.PathConfig;
import com.superredrock.usbthief.statistics.Statistics;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * System tray integration for UsbThief.
 * Provides the tray icon with a popup menu for window control, the copy counters and the
 * batch① actions (open target folder, rescan).
 *
 * <p>The icon image and the hover tooltip are owned by {@link TrayIconManager}; this class owns
 * the menu. All labels come from the active resource bundle.</p>
 */
public class SystemTrayIcon {
    private static final Logger logger = LogManager.getLogger(SystemTrayIcon.class);

    private final MainFrame mainFrame;
    private TrayIcon trayIcon;
    private MenuItem showHideItem;
    private MenuItem speedItem;
    private MenuItem copiedItem;
    private MenuItem openFolderItem;
    private MenuItem rescanItem;
    private MenuItem exitItem;
    private TrayIconManager trayIconManager;
    private Timer stateTimer;

    public SystemTrayIcon(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    private static String i18n(String key, Object... args) {
        return I18nManager.getInstance().getMessage(key, args);
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

        showHideItem = new MenuItem(i18n("tray.menu.show"));
        showHideItem.addActionListener(this::toggleWindowVisibility);
        popup.add(showHideItem);

        popup.addSeparator();

        speedItem = new MenuItem(i18n("tray.menu.speed", "0.0"));
        speedItem.setEnabled(false);
        popup.add(speedItem);

        copiedItem = new MenuItem(i18n("tray.menu.copied", "0 B", "0"));
        copiedItem.setEnabled(false);
        popup.add(copiedItem);

        popup.addSeparator();

        openFolderItem = new MenuItem(i18n("tray.menu.openFolder"));
        openFolderItem.addActionListener(this::openTargetFolder);
        popup.add(openFolderItem);

        rescanItem = new MenuItem(i18n("tray.menu.rescan"));
        rescanItem.addActionListener(this::rescanNow);
        popup.add(rescanItem);

        popup.addSeparator();

        exitItem = new MenuItem(i18n("tray.menu.exit"));
        exitItem.addActionListener(this::exitApplication);
        popup.add(exitItem);

        Image trayImage = createTrayIconImage();
        if (trayImage == null) {
            logger.warn("Failed to create tray icon image, using generated icon");
            trayImage = trayIconManager.generateIcon(TrayIconManager.TrayState.IDLE, iconSize);
        }

        Image scaledImage = trayImage.getScaledInstance(iconSize, iconSize, Image.SCALE_DEFAULT);
        trayIcon = new TrayIcon(scaledImage, i18n("tray.tooltip.idle"), popup);

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
            logger.error("Failed to add tray icon", e);
            return false;
        }
    }

    private void updateDynamicMenuItems() {
        double speed = Statistics.getInstance().getSpeedCollector().getProbeGroup().getTotalSpeed();
        speedItem.setLabel(i18n("tray.menu.speed", TrayIconManager.formatSpeed(speed)));

        long bytes = Statistics.getInstance().getSpeedCollector().getProbeGroup().getTotalBytes();
        long files = Statistics.getInstance().getTotalFilesCopied();
        copiedItem.setLabel(i18n("tray.menu.copied", SizeFormatter.format(bytes), Long.toString(files)));
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
     * Open the configured working directory in the platform file browser.
     */
    private void openTargetFolder(ActionEvent e) {
        Path workPath = AppPaths.resolve(ConfigManager.getInstance().get(PathConfig.WORK_PATH));
        try {
            Files.createDirectories(workPath);
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                logger.warn("Desktop OPEN action is not supported on this platform: {}", workPath);
                return;
            }
            Desktop.getDesktop().open(workPath.toFile());
            logger.info("Opened target folder: {}", workPath);
        } catch (IOException | RuntimeException ex) {
            logger.error("Failed to open target folder {}: {}", workPath, ex.toString());
        }
    }

    /**
     * Restart the scanner for every volume that currently has a live scanner.
     *
     * <p>There is intentionally no "pause scanning" entry: the previous menu item only flipped its
     * own label and never touched the sniffers, so it was removed rather than kept as a lie. A real
     * pause needs per-volume resume semantics that the sniffer lifecycle does not expose yet.</p>
     */
    private void rescanNow(ActionEvent e) {
        SnifferLifecycleManager sniffers = SnifferLifecycleManager.getInstance();
        int restarted = 0;
        for (Volume volume : DeviceManager.getInstance().getAllVolumes()) {
            if (sniffers.isActive(volume.getSerialNumber())) {
                sniffers.restart(volume);
                restarted++;
            }
        }
        logger.info("Manual rescan requested from tray: {} volume(s) restarted", restarted);
    }

    private void exitApplication(ActionEvent e) {
        logger.info("Exit requested from system tray");

        int confirm = JOptionPane.showConfirmDialog(
            mainFrame,
            i18n("tray.exit.confirm"),
            i18n("tray.exit.confirm.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            mainFrame.performShutdown();
        }
    }

    private void updateMenuItems() {
        if (trayIcon != null && trayIcon.getPopupMenu() != null) {
            showHideItem.setLabel(i18n(mainFrame.isVisible() ? "tray.menu.hide" : "tray.menu.show"));
        }
    }

    public void updateShowHideMenuItem() {
        updateMenuItems();
    }

    /**
     * Re-read every menu label after a language change. The tooltip is owned by
     * {@link TrayIconManager} and refreshes itself on the next tick.
     */
    public void refreshLanguage() {
        if (trayIcon == null) return;

        updateMenuItems();
        openFolderItem.setLabel(i18n("tray.menu.openFolder"));
        rescanItem.setLabel(i18n("tray.menu.rescan"));
        exitItem.setLabel(i18n("tray.menu.exit"));
        updateDynamicMenuItems();
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
