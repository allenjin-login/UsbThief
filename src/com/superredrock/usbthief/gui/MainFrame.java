package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.Main;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.worker.LoadEvaluator;
import com.superredrock.usbthief.worker.LoadLevel;
import com.superredrock.usbthief.worker.LoadScore;
import com.superredrock.usbthief.worker.TaskScheduler;
import com.superredrock.usbthief.worker.CopyTask;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;

public class MainFrame extends JFrame {

    private static final Logger logger = Logger.getLogger(MainFrame.class.getName());
    private static final String TITLE = "UsbThief - USB Device Monitor";

    private final JMenuBar menuBar;
    private final JLabel statusBar;

    private final DeviceListPanel deviceListPanel;
    private final EventPanel eventPanel;
    private final FileHistoryPanel fileHistoryPanel;

    // Window visibility state
    private boolean windowVisible = true;
    private SystemTrayIcon trayIcon;

    public MainFrame() {
        setTitle(TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Create menu bar
        menuBar = new JMenuBar();
        createMenus();

        // Create panels
        deviceListPanel = new DeviceListPanel();
        deviceListPanel.setParentFrame(this);
        deviceListPanel.setMainFrame(this);
        eventPanel = new EventPanel();
        fileHistoryPanel = new FileHistoryPanel();

        // Create tabbed pane for right panel
        JTabbedPane rightTabbedPane = new JTabbedPane();
        rightTabbedPane.addTab("事件", eventPanel);
        rightTabbedPane.addTab("文件历史", fileHistoryPanel);

        // Create main split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, deviceListPanel, rightTabbedPane);

        // Status bar
        statusBar = new JLabel("Ready");
        statusBar.setBorder(BorderFactory.createEtchedBorder());

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
                // Use unified shutdown logic
                logger.info("Window close requested");
                performShutdown();
            }
        });

        // Apply window visibility settings from configuration
        applyWindowSettings();

        // Initialize system tray icon
        initializeSystemTray();
    }

    private void createMenus() {
        // File menu
        JMenu fileMenu = new JMenu("文件");

        // Save index menu item
        JMenuItem saveIndexItem = new JMenuItem("保存索引");
        saveIndexItem.addActionListener(e -> saveIndex());
        fileMenu.add(saveIndexItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> {
            logger.info("Exit requested from menu");
            performShutdown();
        });
        fileMenu.add(exitItem);

        // Device menu
        JMenu deviceMenu = new JMenu("设备");
        JMenuItem refreshItem = new JMenuItem("刷新");
        refreshItem.addActionListener(e -> refreshDevices());
        deviceMenu.add(refreshItem);

        // Config menu
        JMenu configMenu = new JMenu("配置");
        JMenuItem preferencesItem = new JMenuItem("首选项");
        preferencesItem.addActionListener(e -> showPreferences());
        configMenu.add(preferencesItem);

        // Help menu
        JMenu helpMenu = new JMenu("帮助");
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(deviceMenu);
        menuBar.add(configMenu);
        menuBar.add(helpMenu);
    }

    private void refreshDevices() {
        // The DeviceManager automatically detects new devices on each polling cycle
        updateStatusBar("设备列表刷新中...");
    }

    private void showPreferences() {
        ConfigDialog dialog = new ConfigDialog(this);
        dialog.setVisible(true);
    }

    private void saveIndex() {
        updateStatusBar("正在保存索引...");

        SwingUtilities.invokeLater(() -> {
            try {
                QueueManager.index.save();
                updateStatusBar("索引保存成功");
                // Note: IndexSavedEvent will be automatically displayed in EventPanel
            } catch (Exception e) {
                updateStatusBar("索引保存失败");
                JOptionPane.showMessageDialog(this,
                        "保存索引失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                """
                        UsbThief - USB Device Monitor
                        
                        USB 设备监控和文件复制工具
                        检测 USB 驱动器、实时监控文件变化、
                        基于校验和去重复制文件""",
                "关于 UsbThief",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void updateStatusBar(String message) {
        SwingUtilities.invokeLater(() -> statusBar.setText(message));
    }

    private String formatLoadLevel(LoadLevel level) {
        return switch (level) {
            case LOW -> "\uD83D\uDFE2低";
            case MEDIUM -> "\uD83D\uDFE1中";
            case HIGH -> "🔴高";
        };
    }

    public void updateStatusBar() {
        // Get load level
        LoadScore loadScore = new LoadEvaluator().evaluateLoad();
        String loadInfo = formatLoadLevel(loadScore.level());

        // Get queue depth
        int queueDepth = TaskScheduler.getInstance().getQueueDepth();
        String queueInfo = String.format("队列: %d 任务", queueDepth);

        // Get copy speed from SpeedProbeGroup
        double speed = CopyTask.getSpeedProbeGroup().getTotalSpeed();
        String speedInfo = String.format("速度: %.1f MB/s", speed);

        // Get work path
        String workPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
        String pathInfo = String.format("路径: %s", workPath.isEmpty() ? "当前目录" : workPath);

        // Combine all info
        String message = String.format("负载: %s | %s | %s | %s",
            loadInfo, queueInfo, speedInfo, pathInfo);
        updateStatusBar(message);
    }

    /**
     * Apply window visibility settings from configuration.
     * Supports starting hidden, always hidden, and controlling taskbar visibility.
     */
    private void applyWindowSettings() {
        boolean startHidden = ConfigManager.getInstance().get(ConfigSchema.START_HIDDEN);
        boolean alwaysHidden = ConfigManager.getInstance().get(ConfigSchema.ALWAYS_HIDDEN);
        boolean showInTaskbar = ConfigManager.getInstance().get(ConfigSchema.SHOW_IN_TASKBAR);

        // Always Hidden takes precedence
        boolean shouldStartHidden = alwaysHidden || startHidden;

        if (shouldStartHidden) {
            windowVisible = false;
            setVisible(false);
            logger.info("Application started hidden (startHidden=%s, alwaysHidden=%s)".formatted(startHidden, alwaysHidden));
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
            // Stop device scanning
            deviceListPanel.stop();
            logger.info("DeviceListPanel stopped");

            // Remove tray icon
            if (trayIcon != null) {
                trayIcon.dispose();
                logger.info("Tray icon disposed");
            }

            // Call unified shutdown logic through Main.quit()
            Main.quit();
            logger.info("Unified shutdown completed");

        } catch (Exception e) {
            logger.severe("Error during shutdown: " + e.getMessage());
        }
        System.exit(0);
    }

    /**
     * Toggle window visibility programmatically.
     * Can be called from external triggers or menu actions.
     */
    public void toggleWindowVisibility() {
        windowVisible = !windowVisible;

        SwingUtilities.invokeLater(() -> {
            if (windowVisible) {
                setVisible(true);
                setState(JFrame.NORMAL);
                logger.info("Window shown");
            } else {
                setVisible(false);
                setState(JFrame.ICONIFIED);
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
                // Show notification
                trayIcon.displayMessage(
                    "UsbThief 已启动",
                    "右键托盘图标查看选项，双击显示/隐藏窗口",
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
        });
    }
}
