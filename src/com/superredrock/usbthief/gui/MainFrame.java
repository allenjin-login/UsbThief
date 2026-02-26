package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.AutoStartManager;

import com.superredrock.usbthief.Main;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.Version;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.worker.LoadEvaluator;
import com.superredrock.usbthief.worker.LoadLevel;
import com.superredrock.usbthief.worker.LoadScore;
import com.superredrock.usbthief.worker.TaskScheduler;
import com.superredrock.usbthief.worker.CopyTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class MainFrame extends JFrame implements I18NManager.LocaleChangeListener, I18NManager.LanguageListChangeListener {

    private static final Logger logger = Logger.getLogger(MainFrame.class.getName());
    private final I18NManager i18n = I18NManager.getInstance();

    private final JMenuBar menuBar;
    private final JLabel statusBar;

    private final DeviceListPanel deviceListPanel;
    private final EventPanel eventPanel;
    private final FileHistoryPanel fileHistoryPanel;
    private final StatisticsPanel statisticsPanel;
    private final JTabbedPane rightTabbedPane;
    private LogWindow logWindow;

    // Window visibility state
    private boolean windowVisible = true;
    private SystemTrayIcon trayIcon;

    public MainFrame() {
        setTitle(i18n.getMessage("main.title") + " v" + Version.getVersion());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Set window icon
        setWindowIcon();

        // Create menu bar
        menuBar = new JMenuBar();
        
        // Register locale change listener
        i18n.addLocaleChangeListener(this);
        i18n.addLanguageListChangeListener(this);

        createMenus();

        deviceListPanel = new DeviceListPanel();
        deviceListPanel.setParentFrame(this);
        deviceListPanel.setMainFrame(this);
        eventPanel = new EventPanel();
        fileHistoryPanel = new FileHistoryPanel();
        statisticsPanel = new StatisticsPanel();

        rightTabbedPane = new JTabbedPane();
        rightTabbedPane.addTab(i18n.getMessage("tab.events"), eventPanel);
        rightTabbedPane.addTab(i18n.getMessage("tab.fileHistory"), fileHistoryPanel);
        rightTabbedPane.addTab(i18n.getMessage("tab.statistics"), statisticsPanel);

        // Create main split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, deviceListPanel, rightTabbedPane);

        // Status bar with modern styling
        statusBar = new JLabel(i18n.getMessage("main.statusbar.ready"));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.BORDER_COLOR),
            new EmptyBorder(8, 12, 8, 12)
        ));
        statusBar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusBar.setForeground(ThemeManager.TEXT_SECONDARY);

        // Layout
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // Set divider location
        mainSplitPane.setDividerLocation(400);

        // Add window listener to clean up on close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                logger.info("Window close requested");
                handleWindowClose();
            }
        });

        // Apply window visibility settings from configuration
        applyWindowSettings();

        // Initialize system tray icon
        initializeSystemTray();
    }

    /**
     * Set window icon from resource.
     * Tries to load App.png or App.ico from classpath.
     */
    private void setWindowIcon() {
        try {
            // Try PNG first (better Java support)
            java.net.URL iconUrl = getClass().getResource("App.png");
            if (iconUrl != null) {
                BufferedImage image = ImageIO.read(iconUrl);
                if (image != null) {
                    setIconImage(image);
                    logger.fine("Window icon loaded from App.png");
                    return;
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to load window icon: " + e.getMessage());
        }

        // Fallback: create a simple default icon
        BufferedImage defaultIcon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = defaultIcon.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(52, 152, 219));
        g2d.fillRoundRect(4, 4, 24, 24, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(10, 12, 12, 8);
        g2d.dispose();
        setIconImage(defaultIcon);
        logger.fine("Using default window icon");
    }

    private void createMenus() {
        JMenu actionMenu = new JMenu(i18n.getMessage("menu.action"));

        JMenuItem saveIndexItem = new JMenuItem(i18n.getMessage("menu.action.saveIndex"));
        saveIndexItem.addActionListener(_ -> saveIndex());
        actionMenu.add(saveIndexItem);

        actionMenu.addSeparator();

        JMenuItem clearDeviceCacheItem = new JMenuItem(i18n.getMessage("menu.action.clearDeviceCache"));
        clearDeviceCacheItem.addActionListener(_ -> clearDeviceCache());
        actionMenu.add(clearDeviceCacheItem);

        JMenuItem clearStatsItem = new JMenuItem(i18n.getMessage("menu.action.clearStats"));
        clearStatsItem.addActionListener(_ -> clearStatistics());
        actionMenu.add(clearStatsItem);

        JMenuItem clearIndexItem = new JMenuItem(i18n.getMessage("menu.action.clearIndex"));
        clearIndexItem.addActionListener(_ -> clearIndex());
        actionMenu.add(clearIndexItem);

        actionMenu.addSeparator();

        JMenuItem hideItem = new JMenuItem(i18n.getMessage("menu.action.hide"));
        hideItem.addActionListener(_ -> hideWindow());
        actionMenu.add(hideItem);

        JMenuItem logWindowItem = new JMenuItem(i18n.getMessage("menu.view.logwindow"));
        logWindowItem.addActionListener(_ -> showLogWindow());
        actionMenu.add(logWindowItem);

        actionMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem(i18n.getMessage("menu.action.exit"));
        exitItem.addActionListener(_ -> {
            logger.info("Exit requested from menu");
            performShutdown();
        });
        actionMenu.add(exitItem);

        JMenu configMenu = new JMenu(i18n.getMessage("menu.config"));
        JMenuItem preferencesItem = new JMenuItem(i18n.getMessage("menu.config.preferences"));
        preferencesItem.addActionListener(e -> showPreferences());
        configMenu.add(preferencesItem);

        JMenuItem clearCacheConfigItem = new JMenuItem(i18n.getMessage("menu.config.clearCache"));
        clearCacheConfigItem.addActionListener(_ -> clearDeviceCache());
        configMenu.add(clearCacheConfigItem);

        JMenuItem clearStatsConfigItem = new JMenuItem(i18n.getMessage("menu.config.clearStats"));
        clearStatsConfigItem.addActionListener(_ -> clearStatistics());
        configMenu.add(clearStatsConfigItem);

        JMenuItem storageItem = new JMenuItem(i18n.getMessage("menu.config.storageManagement"));
        storageItem.addActionListener(_ -> showStorageManagement());
        configMenu.add(storageItem);

        configMenu.addSeparator();

        JMenuItem filterConfigItem = new JMenuItem(i18n.getMessage("filter.menu.item"));
        filterConfigItem.addActionListener(_ -> FilterConfigDialog.showFilterConfigDialog(this));
        configMenu.add(filterConfigItem);

        JMenuItem rateLimitItem = new JMenuItem(i18n.getMessage("menu.settings.ratelimit"));
        rateLimitItem.addActionListener(_ -> {
            RateLimitConfigDialog dialog = new RateLimitConfigDialog(this);
            dialog.setVisible(true);
        });
        configMenu.add(rateLimitItem);


        configMenu.addSeparator();

        // Theme toggle menu item
        JMenuItem themeItem = new JMenuItem(i18n.getMessage("theme.toggle"));
        themeItem.addActionListener(_ -> toggleTheme());
        configMenu.add(themeItem);

        // Auto-start toggle menu item
        JCheckBoxMenuItem autoStartItem = new JCheckBoxMenuItem(
            i18n.getMessage("autostart.toggle"),
            AutoStartManager.getInstance().isAutoStartEnabled()
        );
        autoStartItem.addActionListener(_ -> toggleAutoStart(autoStartItem));
        configMenu.add(autoStartItem);

        JMenu helpMenu = new JMenu(i18n.getMessage("menu.help"));
        JMenuItem aboutItem = new JMenuItem(i18n.getMessage("menu.help.about"));
        aboutItem.addActionListener(_ -> showAbout());
        helpMenu.add(aboutItem);

        menuBar.add(actionMenu);
        menuBar.add(configMenu);
        createLanguageMenu();
        menuBar.add(helpMenu);
    }

    private void createLanguageMenu() {
        JMenu languageMenu = new JMenu(i18n.getMessage("menu.language"));
        Locale currentLocale = i18n.getCurrentLocale();
        LanguageConfig languageConfig = new LanguageConfig();

        for (LanguageInfo langInfo : i18n.getAvailableLanguages()) {
            String displayText = langInfo.nativeName() + " (" + langInfo.displayName() + ")";
            JCheckBoxMenuItem languageItem = new JCheckBoxMenuItem(displayText);
            languageItem.setSelected(langInfo.locale().equals(currentLocale));
            languageItem.addActionListener(_ -> {
                logger.info("Switching to language: " + langInfo.locale());
                languageConfig.setDefaultLanguage(langInfo.localeString());
                languageConfig.save();
                i18n.setLocale(langInfo.locale());
            });
            languageMenu.add(languageItem);
        }

        menuBar.add(languageMenu);
    }

    private void showPreferences() {
        ConfigDialog dialog = new ConfigDialog(this);
        dialog.setVisible(true);
    }

    private void showStorageManagement() {
        JDialog dialog = new JDialog(this, i18n.getMessage("storage.title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        StorageManagementPanel panel = new StorageManagementPanel();
        dialog.add(panel);

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                panel.cleanup();
            }
        });

        dialog.setSize(500, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showLogWindow() {
        if (logWindow == null) {
            logWindow = new LogWindow(this);
        }
        logWindow.setVisible(true);
    }

    /**
     * Toggle between light and dark themes.
     */
    private void toggleTheme() {
        ThemeManager.getInstance().toggleTheme();
        updateStatusBar(i18n.getMessage("theme.toggled"));
    }

    /**
     * Toggle Windows auto-start setting.
     */
    private void toggleAutoStart(JCheckBoxMenuItem item) {
        boolean success = AutoStartManager.getInstance().toggleAutoStart();
        if (success) {
            item.setSelected(AutoStartManager.getInstance().isAutoStartEnabled());
            String status = item.isSelected() ?
                i18n.getMessage("autostart.enabled") :
                i18n.getMessage("autostart.disabled");
            updateStatusBar(status);
        } else {
            item.setSelected(false);
            JOptionPane.showMessageDialog(this,
                i18n.getMessage("autostart.error"),
                i18n.getMessage("common.error"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveIndex() {
        updateStatusBar(i18n.getMessage("status.savingIndex"));

        SwingUtilities.invokeLater(() -> {
            try {
                QueueManager.getIndex().save();
                updateStatusBar(i18n.getMessage("status.indexSaved"));
            } catch (Exception e) {
                updateStatusBar(i18n.getMessage("status.indexSaveFailed"));
                JOptionPane.showMessageDialog(this,
                        i18n.getMessage("message.saveIndexFailed", e.getMessage()),
                        i18n.getMessage("common.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void clearDeviceCache() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                i18n.getMessage("message.clearCacheConfirm"),
                i18n.getMessage("title.clearCacheConfirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                DeviceManager deviceManager = QueueManager.getDeviceManager();
                deviceManager.clearDeviceRecords();

                JOptionPane.showMessageDialog(
                        this,
                        i18n.getMessage("message.clearCacheSuccess"),
                        i18n.getMessage("title.clearCacheSuccess"),
                        JOptionPane.INFORMATION_MESSAGE);

                logger.info("Device cache cleared from menu");
            } catch (Exception e) {
                logger.severe("Failed to clear device cache: " + e.getMessage());
                JOptionPane.showMessageDialog(
                        this,
                        i18n.getMessage("message.clearCacheFailed", e.getMessage()),
                        i18n.getMessage("common.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearStatistics() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                i18n.getMessage("message.clearStatsConfirm"),
                i18n.getMessage("title.clearStatsConfirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            com.superredrock.usbthief.statistics.Statistics.getInstance().resetAll();
            JOptionPane.showMessageDialog(
                    this,
                    i18n.getMessage("message.clearStatsSuccess"),
                    i18n.getMessage("title.clearStatsSuccess"),
                    JOptionPane.INFORMATION_MESSAGE);
            logger.info("Statistics cleared from menu");
        }
    }

    private void clearIndex() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                i18n.getMessage("message.clearIndexConfirm"),
                i18n.getMessage("title.clearIndexConfirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                QueueManager.getIndex().clear();
                JOptionPane.showMessageDialog(
                        this,
                        i18n.getMessage("message.clearIndexSuccess"),
                        i18n.getMessage("title.clearIndexSuccess"),
                        JOptionPane.INFORMATION_MESSAGE);
                logger.info("Index cache cleared from menu");
            } catch (Exception e) {
                logger.severe("Failed to clear index cache: " + e.getMessage());
                JOptionPane.showMessageDialog(
                        this,
                        i18n.getMessage("message.clearIndexFailed", e.getMessage()),
                        i18n.getMessage("common.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showAbout() {
        String aboutMessage = i18n.getMessage("message.about", 
            Version.getVersion(), 
            Version.getFullVersion(),
            System.getProperty("java.version")
        );
        JOptionPane.showMessageDialog(this,
                aboutMessage,
                i18n.getMessage("title.about") + " v" + Version.getVersion(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void updateStatusBar(String message) {
        SwingUtilities.invokeLater(() -> statusBar.setText(message));
    }

    private String formatLoadLevel(LoadLevel level) {
        return switch (level) {
            case LOW -> "\uD83D\uDFE2" + i18n.getMessage("load.low");
            case MEDIUM -> "\uD83D\uDFE1" + i18n.getMessage("load.medium");
            case HIGH -> "🔴" + i18n.getMessage("load.high");
        };
    }

    public void updateStatusBar() {
        LoadScore loadScore = LoadEvaluator.getInstance().evaluateLoad();
        if (loadScore != null){
            String loadInfo = formatLoadLevel(loadScore.level());

            int queueDepth = TaskScheduler.getInstance().getQueueDepth();
            String queueInfo = i18n.getMessage("status.queue.format", queueDepth);

            int poolQueueSize = QueueManager.getQueueSize();
            String poolQueueInfo = i18n.getMessage("status.poolQueue.format", poolQueueSize);

            double speed = CopyTask.getSpeedProbeGroup().getTotalSpeed();
            String speedInfo = i18n.getMessage("status.speed.format", speed);

            String workPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
            String pathInfo = i18n.getMessage("status.path.format", workPath.isEmpty() ? i18n.getMessage("status.currentDir") : workPath);

            String message = i18n.getMessage("status.combined", loadInfo, queueInfo, poolQueueInfo, speedInfo, pathInfo);
            updateStatusBar(message);
        }
    }

    /**
     * Apply window visibility settings from configuration.
     * Supports starting hidden, always hidden, and controlling taskbar visibility.
     */
    private void applyWindowSettings() {
        boolean startHidden = ConfigManager.getInstance().get(ConfigSchema.START_HIDDEN);
        boolean showInTaskbar = ConfigManager.getInstance().get(ConfigSchema.SHOW_IN_TASKBAR);

        if (startHidden) {
            windowVisible = false;
            setVisible(false);
            logger.info("Application started hidden (startHidden=%s)".formatted(true));
        } else {
            windowVisible = true;
            setVisible(true);
        }

        // Note: setShowInTaskBar() is not directly supported in Swing
        // This would need native Windows API calls via JNA (which we're avoiding)
        // For now, we just log the setting
        if (!showInTaskbar) {
            logger.info("Taskbar visibility setting requires JNA (not implemented)");
        }
    }

    /**
     * Unified shutdown logic for all exit paths.
     * This method ensures all cleanup is performed consistently.
     */
    public void performShutdown() {
        logger.info("Performing unified shutdown");

        try {
            deviceListPanel.stop();
            logger.info("DeviceListPanel stopped");

            statisticsPanel.stop();
            logger.info("StatisticsPanel stopped");

            if (trayIcon != null) {
                trayIcon.dispose();
                logger.info("Tray icon disposed");
            }
            this.setVisible(false);

            // Call unified shutdown logic through Main.quit()
            Main.quit();
            logger.info("Unified shutdown completed");

        } catch (Exception e) {
            logger.severe("Error during shutdown: " + e.getMessage());
        }
        System.exit(0);
    }

    /**
     * Handle window close event.
     * Checks for remembered action or shows dialog with options.
     */
    private void handleWindowClose() {
        ConfigManager config = ConfigManager.getInstance();
        String closeAction = config.get(ConfigSchema.CLOSE_ACTION);
        boolean rememberChoice = config.get(ConfigSchema.CLOSE_ACTION_REMEMBER);

        // If user has previously remembered their choice, use it directly
        if (rememberChoice) {
            if ("MINIMIZE_TO_TRAY".equals(closeAction)) {
                logger.info("Minimizing to tray (remembered choice)");
                hideWindow();
                if (trayIcon != null) {
                    trayIcon.updateShowHideMenuItem();
                }
                return;
            } else if ("EXIT".equals(closeAction)) {
                logger.info("Exiting (remembered choice)");
                performShutdown();
                return;
            }
        }

        // Show dialog with options
        showCloseDialog();
    }

    /**
     * Show close dialog with options to minimize to tray or exit.
     * Includes "remember my choice" checkbox.
     */
    private void showCloseDialog() {
        // Create custom panel with options
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Message label
        JLabel messageLabel = new JLabel(i18n.getMessage("dialog.close.message"));
        panel.add(messageLabel, BorderLayout.NORTH);

        // Options panel
        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        JRadioButton minimizeRadio = new JRadioButton(i18n.getMessage("dialog.close.minimizeToTray"));
        JRadioButton exitRadio = new JRadioButton(i18n.getMessage("dialog.close.exit"));
        minimizeRadio.setSelected(true);

        ButtonGroup group = new ButtonGroup();
        group.add(minimizeRadio);
        group.add(exitRadio);
        optionsPanel.add(minimizeRadio);
        optionsPanel.add(exitRadio);
        panel.add(optionsPanel, BorderLayout.CENTER);

        // Remember checkbox
        JCheckBox rememberCheckbox = new JCheckBox(i18n.getMessage("dialog.close.rememberChoice"));
        panel.add(rememberCheckbox, BorderLayout.SOUTH);

        // Show dialog
        int result = JOptionPane.showConfirmDialog(
            this,
            panel,
            i18n.getMessage("dialog.close.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            boolean shouldMinimize = minimizeRadio.isSelected();
            boolean shouldRemember = rememberCheckbox.isSelected();

            // Save choice if "remember" is checked
            if (shouldRemember) {
                ConfigManager config = ConfigManager.getInstance();
                config.set(ConfigSchema.CLOSE_ACTION, shouldMinimize ? "MINIMIZE_TO_TRAY" : "EXIT");
                config.set(ConfigSchema.CLOSE_ACTION_REMEMBER, true);
                logger.info("Close action saved: " + (shouldMinimize ? "MINIMIZE_TO_TRAY" : "EXIT"));
            }

            // Execute action
            if (shouldMinimize) {
                logger.info("Minimizing to tray");
                hideWindow();
                if (trayIcon != null) {
                    trayIcon.updateShowHideMenuItem();
                    // Tray messages use English only (no i18n for system tray per design)
                trayIcon.displayMessage("UsbThief", "Minimized to tray", TrayIcon.MessageType.INFO);
                }
            } else {
                logger.info("Exiting application");
                performShutdown();
            }
        }
        // If canceled, do nothing (window stays open)
    }

    /**
     * Toggle window visibility programmatically.
     * Can be called from external triggers or menu actions.
     */
    public void toggleWindowVisibility() {
        windowVisible = !windowVisible;

        SwingUtilities.invokeLater(() -> {
            if (windowVisible) {
                showWindow();
                logger.info("Window shown");
            } else {
                hideWindow();
                logger.info("Window hidden");
            }
        });
    }

    /**
     * Show the window if currently hidden.
     */
    public void showWindow() {
        windowVisible = true;
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            setState(JFrame.NORMAL);
        });
    }

    /**
     * Hide the window.
     */
    public void hideWindow() {
        windowVisible = false;
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            setState(JFrame.ICONIFIED);
        });
    }

    /**
     * Initialize system tray icon if supported.
     */
    private void initializeSystemTray() {
        try {
            trayIcon = new SystemTrayIcon(this);
            boolean success = trayIcon.initialize();

            if (success) {
                logger.info("System tray icon initialized");
                trayIcon.displayMessage(
                    "UsbThief Started",
                    "Right-click tray icon for options, double-click to show/hide window",
                    java.awt.TrayIcon.MessageType.INFO
                );
            } else {
                logger.info("System tray not available or initialization failed");
                trayIcon = null;
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize system tray: " + e.getMessage());
            trayIcon = null;
        }
    }

    /**
     * Get system tray icon instance.
     */
    public SystemTrayIcon getTrayIcon() {
        return trayIcon;
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            // Note: setVisible is already called in applyWindowSettings() during construction
            // We don't call setVisible(true) here to respect the startHidden/alwaysHidden settings
            frame.updateStatusBar();
            // Show welcome dialog on first run
            WelcomeDialog.showIfFirstRun(frame);
        });
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        logger.info("MainFrame received locale change event: " + newLocale);
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("main.title") + " v" + Version.getVersion());
            menuBar.removeAll();
            createMenus();
            updateStatusBar();

            logger.info("Refreshing child panels...");
            deviceListPanel.refreshLanguage();
            eventPanel.refreshLanguage();
            fileHistoryPanel.refreshLanguage();
            statisticsPanel.refreshLanguage();

            rightTabbedPane.setTitleAt(0, i18n.getMessage("tab.events"));
            rightTabbedPane.setTitleAt(1, i18n.getMessage("tab.fileHistory"));
            rightTabbedPane.setTitleAt(2, i18n.getMessage("tab.statistics"));

            if (trayIcon != null) {
                trayIcon.refreshLanguage();
            }
            logger.info("Locale change complete");
            this.repaint();
        });
    }

    @Override
    public void onLanguageListChanged(List<LanguageInfo> languages) {
        logger.info("Language list changed, refreshing menu: " + languages.size() + " languages");
        SwingUtilities.invokeLater(() -> {
            menuBar.removeAll();
            createMenus();
        });
    }
}
