package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeviceListPanel extends JPanel {

    private final JPanel devicesPanel;
    private final Map<Device, DeviceCard> deviceCards = new HashMap<>();

    // Timer for updating active task counts and status bar
    private Timer updateTimer;

    // Parent frame for dialogs
    private JFrame parentFrame;

    // MainFrame reference for status bar updates
    private MainFrame mainFrame;

    public DeviceListPanel() {
        setLayout(new BorderLayout());

        devicesPanel = new JPanel();
        devicesPanel.setLayout(new BoxLayout(devicesPanel, BoxLayout.Y_AXIS));
        devicesPanel.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(devicesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new TitledBorder("设备列表"));

        // Create blacklist button
        JButton blacklistButton = new JButton("黑名单管理");
        blacklistButton.setToolTipText("管理设备黑名单");
        blacklistButton.addActionListener(e -> BlacklistDialog.showBlacklistDialog(parentFrame));

        // Create top panel for blacklist button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topPanel.add(blacklistButton);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Initialize with existing devices
        initializeExistingDevices();

        // Register event listeners
        registerEventListeners();

        // Start timer for updating active task counts
        startUpdateTimer();
    }

    private void initializeExistingDevices() {
        SwingUtilities.invokeLater(() -> {
            for (Device device : QueueManager.deviceManager.getAllDevices()) {
                addDevice(device);
            }
        });
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        eventBus.register(DeviceInsertedEvent.class, this::onDeviceInserted);
        eventBus.register(DeviceRemovedEvent.class, this::onDeviceRemoved);
        eventBus.register(DeviceStateChangedEvent.class, this::onDeviceStateChanged);
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, e -> updateActiveTaskCounts());
        updateTimer.start();
    }

    private void updateActiveTaskCounts() {
        SwingUtilities.invokeLater(() -> {
            // Update status bar with load, queue, and speed
            if (mainFrame != null) {
                mainFrame.updateStatusBar();
            }
        });
    }

    /**
     * Sets the MainFrame reference for status bar updates.
     *
     * @param frame MainFrame instance
     */
    public void setMainFrame(MainFrame frame) {
        this.mainFrame = frame;
    }

    public void stop() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }

    /**
     * Sets the parent frame for dialogs.
     *
     * @param frame parent JFrame
     */
    public void setParentFrame(JFrame frame) {
        this.parentFrame = frame;
    }

    private void onDeviceInserted(DeviceInsertedEvent event) {
        SwingUtilities.invokeLater(() -> addDevice(event.device()));
    }

    private void onDeviceRemoved(DeviceRemovedEvent event) {
        SwingUtilities.invokeLater(() -> removeDevice(event.device()));
    }

    private void onDeviceStateChanged(DeviceStateChangedEvent event) {
        SwingUtilities.invokeLater(() -> updateDeviceState(event.device(), event.newState()));
    }

    private void addDevice(Device device) {
        if (deviceCards.containsKey(device)) {
            return;
        }

        DeviceCard card = new DeviceCard(device, parentFrame);
        deviceCards.put(device, card);
        devicesPanel.add(card);
        devicesPanel.revalidate();
        devicesPanel.repaint();
    }

    private void removeDevice(Device device) {
        DeviceCard card = deviceCards.remove(device);
        if (card != null) {
            devicesPanel.remove(card);
            devicesPanel.revalidate();
            devicesPanel.repaint();
        }
    }

    private void updateDeviceState(Device device, Device.DeviceState newState) {
        DeviceCard card = deviceCards.get(device);
        if (card != null) {
            card.updateState(newState);
        }
    }

    private static class DeviceCard extends JPanel {

        private final Device device;
        private final JFrame parentFrame;
        private final JLabel pathLabel;
        private final JLabel fsTypeLabel;
        private final JLabel stateLabel;
        private final JLabel activeTaskLabel;
        private final JButton toggleButton;
        private final JButton blacklistButton;

        public DeviceCard(Device device, JFrame parentFrame) {
            this.device = device;
            this.parentFrame = parentFrame;
            setLayout(new BorderLayout(10, 5));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            setBackground(Color.WHITE);

            // Left panel: device info
            JPanel infoPanel = new JPanel(new GridLayout(4, 1, 0, 5));
            infoPanel.setBackground(Color.WHITE);

            pathLabel = new JLabel("路径: " + device.getRootPath());
            fsTypeLabel = new JLabel("文件系统: " + getFsType());
            stateLabel = new JLabel("状态: " + device.getState());
            activeTaskLabel = new JLabel("活跃任务: 0");
            activeTaskLabel.setVisible(false);  // Hidden: task queue moved to TaskScheduler

            infoPanel.add(pathLabel);
            infoPanel.add(fsTypeLabel);
            infoPanel.add(stateLabel);
            infoPanel.add(activeTaskLabel);

            // Right panel: buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            buttonPanel.setBackground(Color.WHITE);

            toggleButton = new JButton(getToggleButtonText());
            toggleButton.addActionListener(e -> toggleDevice());

            blacklistButton = new JButton("加入黑名单");
            blacklistButton.addActionListener(e -> addToBlacklist());

            buttonPanel.add(blacklistButton);
            buttonPanel.add(toggleButton);

            add(infoPanel, BorderLayout.WEST);
            add(buttonPanel, BorderLayout.EAST);

            updateState(device.getState());
        }

        private String getFsType() {
            if (device.getFileStore() == null) {
                return "未知";
            }
            return device.getFileStore().type();
        }

        private String getToggleButtonText() {
            return device.getState() == Device.DeviceState.DISABLED ? "启用" : "禁用";
        }

        private void toggleDevice() {
            if (device.getState() == Device.DeviceState.DISABLED) {
                device.enable();
            } else {
                device.disable();
            }
        }

        private void addToBlacklist() {
            String serialNumber = device.getSerialNumber();
            String devicePath = device.getRootPath().toString();

            int confirm = JOptionPane.showConfirmDialog(
                    parentFrame,
                    "确定要将以下设备加入黑名单吗?\n\n" +
                    "路径: " + devicePath + "\n" +
                    "序列号: " + serialNumber,
                    "确认加入黑名单",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                ConfigManager.getInstance().addToDeviceBlacklistBySerial(serialNumber);
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "设备已加入黑名单",
                        "操作成功",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }

        public void updateState(Device.DeviceState newState) {
            SwingUtilities.invokeLater(() -> {
                stateLabel.setText("状态: " + newState);
                stateLabel.setForeground(getStateColor(newState));
                toggleButton.setText(getToggleButtonText());
            });
        }

        // updateActiveTaskCount() removed: task queue now managed by TaskScheduler

        private Color getStateColor(Device.DeviceState state) {
            return switch (state) {
                case IDLE, SCANNING -> new Color(0, 128, 0); // Green
                case OFFLINE -> Color.RED;
                case UNAVAILABLE -> new Color(255, 140, 0); // Orange
                case DISABLED -> Color.GRAY;
            };
        }
    }
}
