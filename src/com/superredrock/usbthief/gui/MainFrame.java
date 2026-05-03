package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.AutoStartManager;

import com.superredrock.usbthief.Main;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Version;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.gui.dailog.*;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;
import com.superredrock.usbthief.worker.TaskScheduler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.imageio.ImageIO;

public class MainFrame extends JFrame implements I18nManager.LocaleChangeListener, I18nManager.LanguageListChangeListener {

    private static final Logger logger = LogManager.getLogger(MainFrame.class);
    private final I18nManager i18n = I18nManager.getInstance();

    private final JMenuBar menuBar;
    private final JLabel statusBar;

    private final SpeedChartPanel speedChartPanel;
    private final VolumeListPanel volumeListPanel;
    private final StatisticsPanel statisticsPanel;
    private LogWindow logWindow;
    private DeviceInfoDialog deviceInfoDialog;
    private SnifferDebugDialog debugDialog;

    // Compact stats bar labels
    private JLabel totalLabel;
    private JLabel filesLabel;
    private JLabel foldersLabel;
    private JLabel queueLabel;

    // Window visibility state
    private boolean windowVisible = true;
    private SystemTrayIcon trayIcon;

    public MainFrame() {
        setTitle(i18n.getMessage("main.title") + " v" + Version.getVersion());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(500, 400);
        setMinimumSize(new Dimension(400, 600));
        setLocationRelativeTo(null);
        setResizable(false);

        // Set window icon
        setWindowIcon();

        // Create menu bar
        menuBar = new JMenuBar();

        // Register locale change listener
        i18n.addLocaleChangeListener(this);
        i18n.addLanguageListChangeListener(this);

        createMenus();

        speedChartPanel = new SpeedChartPanel();
        volumeListPanel = new VolumeListPanel();
        volumeListPanel.setParentFrame(this);
        volumeListPanel.setMainFrame(this);
        statisticsPanel = new StatisticsPanel();

        // Status bar with compact styling
        statusBar = new JLabel(i18n.getMessage("main.statusbar.ready"));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.BORDER_COLOR),
            new EmptyBorder(4, 8, 4, 8)
        ));
        statusBar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        statusBar.setForeground(ThemeManager.TEXT_SECONDARY);

        // Layout
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

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

    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setMinimumSize(new Dimension(300, 250));

        // Speed chart at top with titled border
        speedChartPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.BLACK, 1),
            i18n.getMessage("chart.speed.title")
        ));
        speedChartPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(speedChartPanel);

        // Compact stats bar with titled border
        JPanel statsBar = createCompactStatsBar();
        statsBar.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.BLACK, 1),
            i18n.getMessage("chart.stats.title")
        ));
        statsBar.setMinimumSize(new Dimension(200, 50));
        statsBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        statsBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(statsBar);

        // Device list at bottom (scrollable) with titled border
        JScrollPane deviceScroll = new JScrollPane(volumeListPanel);
        deviceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        deviceScroll.setMinimumSize(new Dimension(200, 60));
        deviceScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.BLACK, 1),
            i18n.getMessage("device.list.border")
        ));
        deviceScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(deviceScroll);

        return centerPanel;
    }

    private JPanel createCompactStatsBar() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 4, 0));
        panel.setOpaque(false);

        totalLabel = new JLabel("0 B", SwingConstants.CENTER);
        filesLabel = new JLabel("0", SwingConstants.CENTER);
        foldersLabel = new JLabel("0", SwingConstants.CENTER);
        queueLabel = new JLabel("0", SwingConstants.CENTER);

        Font statFont = new Font(Font.SANS_SERIF, Font.BOLD, 11);
        Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 8);

        for (var entry : new Object[][]{
                {i18n.getMessage("chart.stats.total"), totalLabel},
                {i18n.getMessage("chart.stats.files"), filesLabel},
                {"Folders", foldersLabel},
                {i18n.getMessage("chart.stats.queue"), queueLabel}
        }) {
            JPanel card = getCard(entry, labelFont, statFont);
            panel.add(card);
        }

        // Timer to update stats
        Timer statsTimer = new Timer(1000, ignored -> updateCompactStats());
        statsTimer.start();

        return panel;
    }

    private static JPanel getCard(Object[] entry, Font labelFont, Font statFont) {
        JPanel card = new JPanel(new BorderLayout(2, 0));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1, true));
        JLabel lbl = new JLabel((String) entry[0], SwingConstants.CENTER);
        lbl.setFont(labelFont);
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        JLabel val = (JLabel) entry[1];
        val.setFont(statFont);
        card.add(lbl, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);
        return card;
    }

    private void updateCompactStats() {
        Statistics stats = Statistics.getInstance();
        totalLabel.setText(SizeFormatter.format(stats.getSessionBytesCopied()));
        filesLabel.setText(String.valueOf(stats.getSessionFilesCopied()));
        foldersLabel.setText(String.valueOf(stats.getSessionFoldersCopied()));
        queueLabel.setText(String.valueOf(TaskScheduler.getInstance().getQueueDepth()));
    }

    private void setWindowIcon() {
        try {
            java.net.URL iconUrl = getClass().getResource("App.png");
            if (iconUrl != null) {
                BufferedImage image = ImageIO.read(iconUrl);
                if (image != null) {
                    setIconImage(image);
                    logger.debug("Window icon loaded from App.png");
                    return;
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to load window icon:", e);
        }

        BufferedImage defaultIcon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = defaultIcon.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(52, 152, 219));
        g2d.fillRoundRect(4, 4, 24, 24, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(10, 12, 12, 8);
        g2d.dispose();
        setIconImage(defaultIcon);
        logger.debug("Using default window icon");
    }

    private void createMenus() {
        JMenu actionMenu = new JMenu(i18n.getMessage("menu.action"));

        JMenuItem saveIndexItem = new JMenuItem(i18n.getMessage("menu.action.saveIndex"));
        saveIndexItem.addActionListener(ignored -> saveIndex());
        actionMenu.add(saveIndexItem);

        actionMenu.addSeparator();

        JMenuItem clearStatsItem = new JMenuItem(i18n.getMessage("menu.action.clearStats"));
        clearStatsItem.addActionListener(ignored -> clearStatistics());
        actionMenu.add(clearStatsItem);

        JMenuItem clearIndexItem = new JMenuItem(i18n.getMessage("menu.action.clearIndex"));
        clearIndexItem.addActionListener(ignored -> clearIndex());
        actionMenu.add(clearIndexItem);

        actionMenu.addSeparator();

        JMenuItem deviceInfoItem = new JMenuItem(i18n.getMessage("menu.action.deviceInfo"));
        deviceInfoItem.addActionListener(ignored -> showDeviceInfoDialog());
        actionMenu.add(deviceInfoItem);

        JMenuItem statsWindowItem = new JMenuItem(i18n.getMessage("menu.action.statistics"));
        statsWindowItem.addActionListener(ignored -> showStatisticsWindow());
        actionMenu.add(statsWindowItem);

        actionMenu.addSeparator();

        JMenuItem hideItem = new JMenuItem(i18n.getMessage("menu.action.hide"));
        hideItem.addActionListener(ignored -> hideWindow());
        actionMenu.add(hideItem);

        JMenuItem logWindowItem = new JMenuItem(i18n.getMessage("menu.view.logwindow"));
        logWindowItem.addActionListener(ignored -> showLogWindow());
        actionMenu.add(logWindowItem);

        actionMenu.addSeparator();

        JMenuItem debugMonitorItem = new JMenuItem("Debug Monitor");
        debugMonitorItem.addActionListener(ignored -> showDebugMonitor());
        actionMenu.add(debugMonitorItem);

        actionMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem(i18n.getMessage("menu.action.exit"));
        exitItem.addActionListener(ignored -> {
            logger.info("Exit requested from menu");
            performShutdown();
        });
        actionMenu.add(exitItem);

        JMenu configMenu = new JMenu(i18n.getMessage("menu.config"));
        JMenuItem preferencesItem = new JMenuItem(i18n.getMessage("menu.config.preferences"));
        preferencesItem.addActionListener(ignored -> showPreferences());
        configMenu.add(preferencesItem);

        JMenuItem clearStatsConfigItem = new JMenuItem(i18n.getMessage("menu.config.clearStats"));
        clearStatsConfigItem.addActionListener(ignored -> clearStatistics());
        configMenu.add(clearStatsConfigItem);

        JMenuItem storageItem = new JMenuItem(i18n.getMessage("menu.config.storageManagement"));
        storageItem.addActionListener(ignored -> showStorageManagement());
        configMenu.add(storageItem);

        configMenu.addSeparator();

        JMenuItem filterConfigItem = new JMenuItem(i18n.getMessage("filter.menu.item"));
        filterConfigItem.addActionListener(ignored -> FilterConfigDialog.showFilterConfigDialog(this));
        configMenu.add(filterConfigItem);

        JMenuItem rateLimitItem = new JMenuItem(i18n.getMessage("menu.settings.ratelimit"));
        rateLimitItem.addActionListener(ignored -> {
            RateLimitConfigDialog dialog = new RateLimitConfigDialog(this);
            dialog.setVisible(true);
        });
        configMenu.add(rateLimitItem);

        configMenu.addSeparator();

        JMenuItem themeItem = new JMenuItem(i18n.getMessage("theme.toggle"));
        themeItem.addActionListener(ignored -> toggleTheme());
        configMenu.add(themeItem);

        JCheckBoxMenuItem autoStartItem = new JCheckBoxMenuItem(
            i18n.getMessage("autostart.toggle"),
            AutoStartManager.getInstance().isAutoStartEnabled()
        );
        autoStartItem.addActionListener(ignored -> toggleAutoStart(autoStartItem));
        configMenu.add(autoStartItem);

        JCheckBoxMenuItem startHiddenItem = new JCheckBoxMenuItem(
            i18n.getMessage("starthidden.toggle"),
            ConfigManager.getInstance().get(ConfigSchema.START_HIDDEN)
        );
        startHiddenItem.addActionListener(ignored -> toggleStartHidden(startHiddenItem));
        configMenu.add(startHiddenItem);

        JMenu helpMenu = new JMenu(i18n.getMessage("menu.help"));
        JMenuItem aboutItem = new JMenuItem(i18n.getMessage("menu.help.about"));
        aboutItem.addActionListener(ignored -> showAbout());
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
            JCheckBoxMenuItem languageItem = getLanguageBoxMenuItem(langInfo, currentLocale, languageConfig);
            languageMenu.add(languageItem);
        }

        menuBar.add(languageMenu);
    }

    private JCheckBoxMenuItem getLanguageBoxMenuItem(LanguageInfo langInfo, Locale currentLocale, LanguageConfig languageConfig) {
        String displayText = langInfo.nativeName() + " (" + langInfo.displayName() + ")";
        JCheckBoxMenuItem languageItem = new JCheckBoxMenuItem(displayText);
        languageItem.setSelected(langInfo.locale().equals(currentLocale));
        languageItem.addActionListener(ignored -> {
            logger.info("Switching to language: {}", langInfo.locale());
            languageConfig.setDefaultLanguage(langInfo.localeString());
            languageConfig.save();
            i18n.setLocale(langInfo.locale());
        });
        return languageItem;
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

    private void showDeviceInfoDialog() {
        if (deviceInfoDialog == null) {
            deviceInfoDialog = new DeviceInfoDialog(this);
        }
        deviceInfoDialog.setVisible(true);
    }

    private void showStatisticsWindow() {
        JDialog dialog = new JDialog(this, i18n.getMessage("tab.statistics"), false);
        dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        dialog.add(statisticsPanel);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showLogWindow() {
        if (logWindow == null) {
            logWindow = new LogWindow(this);
        }
        logWindow.setVisible(true);
    }

    private void showDebugMonitor() {
        if (debugDialog == null) {
            debugDialog = new SnifferDebugDialog(this);
            debugDialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    debugDialog = null;
                }
            });
        }
        debugDialog.setVisible(true);
    }

    private void toggleTheme() {
        ThemeManager.getInstance().toggleTheme();
        updateStatusBar(i18n.getMessage("theme.toggled"));
    }

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

    private void toggleStartHidden(JCheckBoxMenuItem item) {
        boolean enabled = item.isSelected();
        ConfigManager.getInstance().set(ConfigSchema.START_HIDDEN, enabled);
        String status = enabled ?
            i18n.getMessage("starthidden.enabled") :
            i18n.getMessage("starthidden.disabled");
        updateStatusBar(status);
        logger.info("Start hidden setting changed to: {}", enabled);
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

    private void clearStatistics() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                i18n.getMessage("message.clearStatsConfirm"),
                i18n.getMessage("title.clearStatsConfirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            Statistics.getInstance().resetAll();
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
                logger.error("Failed to clear index cache:", e);
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

    public void updateStatusBar() {
        int queueDepth = TaskScheduler.getInstance().getQueueDepth();
        String queueInfo = i18n.getMessage("status.queue.format", queueDepth);

        int poolQueueSize = TaskScheduler.getInstance().getPool().getQueue().size();
        String poolQueueInfo = i18n.getMessage("status.poolQueue.format", poolQueueSize);

        double speed = Statistics.getInstance().getSpeedCollector().getProbeGroup().getTotalSpeed();
        String speedInfo = i18n.getMessage("status.speed.format", speed);

        String workPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
        String pathInfo = i18n.getMessage("status.path.format", workPath.isEmpty() ? i18n.getMessage("status.currentDir") : workPath);

        String message = i18n.getMessage("status.combined", queueInfo, poolQueueInfo, speedInfo, pathInfo);
        updateStatusBar(message);
    }

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

        if (!showInTaskbar) {
            logger.info("Taskbar visibility setting requires JNA (not implemented)");
        }
    }

    public void performShutdown() {
        logger.info("Performing unified shutdown");

        try {
            speedChartPanel.stop();
            logger.info("SpeedChartPanel stopped");

            volumeListPanel.stop();
            logger.info("VolumeListPanel stopped");

            statisticsPanel.stop();
            logger.info("StatisticsPanel stopped");
            if (trayIcon != null) {
                trayIcon.dispose();
                logger.info("Tray icon disposed");
            }

            this.setVisible(false);

            Main.quit();
            logger.info("Unified shutdown completed");

        } catch (Exception e) {
            logger.error("Error during shutdown:", e);
        }
        System.exit(0);
    }

    private void handleWindowClose() {
        ConfigManager config = ConfigManager.getInstance();
        String closeAction = config.get(ConfigSchema.CLOSE_ACTION);
        boolean rememberChoice = config.get(ConfigSchema.CLOSE_ACTION_REMEMBER);

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

        showCloseDialog();
    }

    private void showCloseDialog() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel messageLabel = new JLabel(i18n.getMessage("dialog.close.message"));
        panel.add(messageLabel, BorderLayout.NORTH);

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

        JCheckBox rememberCheckbox = new JCheckBox(i18n.getMessage("dialog.close.rememberChoice"));
        panel.add(rememberCheckbox, BorderLayout.SOUTH);

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

            if (shouldRemember) {
                ConfigManager config = ConfigManager.getInstance();
                config.set(ConfigSchema.CLOSE_ACTION, shouldMinimize ? "MINIMIZE_TO_TRAY" : "EXIT");
                config.set(ConfigSchema.CLOSE_ACTION_REMEMBER, true);
                logger.info("Close action saved: {}", (shouldMinimize ? "MINIMIZE_TO_TRAY" : "EXIT"));
            }

            if (shouldMinimize) {
                logger.info("Minimizing to tray");
                hideWindow();
                if (trayIcon != null) {
                    trayIcon.updateShowHideMenuItem();
                    trayIcon.displayMessage("UsbThief", "Minimized to tray", TrayIcon.MessageType.INFO);
                }
            } else {
                logger.info("Exiting application");
                performShutdown();
            }
        }
    }

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

    public void showWindow() {
        windowVisible = true;
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            setState(JFrame.NORMAL);
            toFront();
            requestFocus();
        });
    }

    public void hideWindow() {
        windowVisible = false;
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            setState(JFrame.ICONIFIED);
        });
    }

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
            logger.warn("Failed to initialize system tray:", e);
            trayIcon = null;
        }
    }

    public SystemTrayIcon getTrayIcon() {
        return trayIcon;
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.updateStatusBar();
            frame.setVisible(true);

            WelcomeDialog.showIfFirstRun(frame);
        });
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        logger.info("MainFrame received locale change event: {}", newLocale);
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("main.title") + " v" + Version.getVersion());
            menuBar.removeAll();
            createMenus();
            updateStatusBar();

            logger.info("Refreshing child panels...");
            volumeListPanel.refreshLanguage();
            statisticsPanel.refreshLanguage();

            if (deviceInfoDialog != null) {
                deviceInfoDialog.refreshLanguage();
            }

            if (trayIcon != null) {
                trayIcon.refreshLanguage();
            }
            logger.info("Locale change complete");
            this.repaint();
        });
    }

    @Override
    public void onLanguageListChanged(List<LanguageInfo> languages) {
        logger.info("Language list changed, refreshing menu: {} languages", languages.size());
        SwingUtilities.invokeLater(() -> {
            menuBar.removeAll();
            createMenus();
        });
    }
}
