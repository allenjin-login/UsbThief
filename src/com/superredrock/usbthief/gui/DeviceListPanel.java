package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DeviceListPanel extends JPanel {

    private final JPanel devicesPanel;
    private final Map<Device, DeviceCard> deviceCards = new HashMap<>();
    private final DeviceManager deviceManager;

    // Timer for updating active task counts and status bar
    private Timer updateTimer;

    // Parent frame for dialogs
    private JFrame parentFrame;

    // MainFrame reference for status bar updates
    private MainFrame mainFrame;

    // Batch operation controls
    private final JButton selectAllButton;
    private final JButton batchEnableButton;
    private final JButton batchDisableButton;
    private final JButton batchBlacklistButton;

    public DeviceListPanel() {
        this.deviceManager = QueueManager.deviceManager;
        setLayout(new BorderLayout());

        devicesPanel = new JPanel();
        devicesPanel.setLayout(new BoxLayout(devicesPanel, BoxLayout.Y_AXIS));
        devicesPanel.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(devicesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new TitledBorder("设备列表"));

        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectAllButton = new JButton("全选/取消");
        selectAllButton.setToolTipText("全选或取消选择所有设备");
        selectAllButton.addActionListener(_ -> toggleSelectAll());
        leftPanel.add(selectAllButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        batchEnableButton = new JButton("批量启用");
        batchEnableButton.setToolTipText("启用选中的设备");
        batchEnableButton.addActionListener(_ -> batchEnable());
        batchEnableButton.setEnabled(false);

        batchDisableButton = new JButton("批量禁用");
        batchDisableButton.setToolTipText("禁用选中的设备");
        batchDisableButton.addActionListener(_ -> batchDisable());
        batchDisableButton.setEnabled(false);

        batchBlacklistButton = new JButton("批量加入黑名单");
        batchBlacklistButton.setToolTipText("将选中的设备加入黑名单");
        batchBlacklistButton.addActionListener(_ -> batchAddToBlacklist());
        batchBlacklistButton.setEnabled(false);

        JButton blacklistButton = new JButton("黑名单管理");
        blacklistButton.setToolTipText("管理设备黑名单");
        blacklistButton.addActionListener(_ -> BlacklistDialog.showBlacklistDialog(parentFrame));

        rightPanel.add(batchEnableButton);
        rightPanel.add(batchDisableButton);
        rightPanel.add(batchBlacklistButton);
        rightPanel.add(blacklistButton);

        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(rightPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        initializeExistingDevices();
        registerEventListeners();
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

        eventBus.register(NewDeviceJoinedEvent.class, this::onDeviceInserted);
        eventBus.register(DeviceStateChangedEvent.class, this::onDeviceStateChanged);
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, _ -> updateActiveTaskCounts());
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

    private void onDeviceInserted(NewDeviceJoinedEvent event) {
        SwingUtilities.invokeLater(() -> addDevice(event.device()));
    }

    private void onDeviceStateChanged(DeviceStateChangedEvent event) {
        SwingUtilities.invokeLater(() -> updateDeviceState(event.device(), event.newState()));
    }

    private void toggleSelectAll() {
        boolean allSelected = true;
        for (DeviceCard card : deviceCards.values()) {
            if (!card.getCheckBox().isSelected() && card.getCheckBox().isEnabled()) {
                allSelected = false;
                break;
            }
        }

        boolean newState = !allSelected;
        for (DeviceCard card : deviceCards.values()) {
            if (card.getCheckBox().isEnabled()) {
                card.getCheckBox().setSelected(newState);
            }
        }
        updateBatchButtons();
    }

    private void updateBatchButtons() {
        boolean hasSelection = false;
        for (DeviceCard card : deviceCards.values()) {
            if (card.getCheckBox().isSelected()) {
                hasSelection = true;
                break;
            }
        }
        batchEnableButton.setEnabled(hasSelection);
        batchDisableButton.setEnabled(hasSelection);
        batchBlacklistButton.setEnabled(hasSelection);
    }

    private void batchEnable() {
        int count = 0;
        for (DeviceCard card : deviceCards.values()) {
            if (card.getCheckBox().isSelected()) {
                card.device.enable();
                count++;
            }
        }
        if (count > 0) {
            JOptionPane.showMessageDialog(
                    parentFrame,
                    "已启用 " + count + " 个设备",
                    "批量操作",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void batchDisable() {
        int count = 0;
        for (DeviceCard card : deviceCards.values()) {
            if (card.getCheckBox().isSelected()) {
                card.device.disable();
                count++;
            }
        }
        if (count > 0) {
            JOptionPane.showMessageDialog(
                    parentFrame,
                    "已禁用 " + count + " 个设备",
                    "批量操作",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void batchAddToBlacklist() {
        int confirm = JOptionPane.showConfirmDialog(
                parentFrame,
                "确定要将选中的设备加入黑名单吗?",
                "确认批量操作",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            int count = 0;
            ConfigManager config = ConfigManager.getInstance();
            for (DeviceCard card : deviceCards.values()) {
                if (card.getCheckBox().isSelected()) {
                    config.addToDeviceBlacklistBySerial(card.device.getSerialNumber());
                    count++;
                }
            }
            if (count > 0) {
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "已将 " + count + " 个设备加入黑名单",
                        "批量操作",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void addDevice(Device device) {
        if (deviceCards.containsKey(device)) {
            return;
        }

        DeviceCard card = new DeviceCard(device, parentFrame, deviceManager);
        deviceCards.put(device, card);
        devicesPanel.add(card);

        card.getCheckBox().addItemListener(_ -> updateBatchButtons());

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

    /**
     * Refreshes all device information displays.
     * Call this after refreshing device information from system.
     */
    public void updateAllVolumeNames() {
        for (DeviceCard card : deviceCards.values()) {
            card.refreshDeviceInfo();
        }
    }

    private static class DeviceCard extends JPanel {

        private final Device device;
        private final JFrame parentFrame;
        private final DeviceManager deviceManager;
        private final JLabel iconLabel;
        private final JLabel pathLabel;
        private final JLabel volumeLabel;
        private final JLabel fsTypeLabel;
        private final JLabel storageLabel;
        private final JLabel stateLabel;
        private final JLabel activeTaskLabel;
        private JButton toggleButton;
        private JButton blacklistButton;
        private final JButton detailButton;
        private final JCheckBox checkBox;

        public DeviceCard(Device device, JFrame parentFrame, DeviceManager deviceManager) {
            this.device = device;
            this.parentFrame = parentFrame;
            this.deviceManager = deviceManager;
            setLayout(new BorderLayout(10, 5));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
            setBackground(Color.WHITE);

            boolean isSystemDisk = device.isSystemDisk();

            JPanel leftPanel = new JPanel(new BorderLayout(10, 0));
            leftPanel.setBackground(Color.WHITE);

            iconLabel = new JLabel(getDeviceIcon(isSystemDisk));
            leftPanel.add(iconLabel, BorderLayout.WEST);

            JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 5));
            infoPanel.setBackground(Color.WHITE);

            pathLabel = new JLabel("路径: " + device.getRootPath() + (isSystemDisk ? " [系统盘]" : ""));
            
            String volumeName = device.getVolumeName();
            String volumeDisplay = volumeName != null && !volumeName.isEmpty() ? volumeName : "无";
            volumeLabel = new JLabel("卷标: " + volumeDisplay);
            
            fsTypeLabel = new JLabel("文件系统: " + getFsType());
            storageLabel = new JLabel("存储: " + getStorageInfo());
            stateLabel = new JLabel("状态: " + device.getState());
            activeTaskLabel = new JLabel("活跃任务: 0");
            activeTaskLabel.setVisible(false);

            infoPanel.add(pathLabel);
            infoPanel.add(volumeLabel);
            infoPanel.add(fsTypeLabel);
            infoPanel.add(storageLabel);
            infoPanel.add(stateLabel);
            infoPanel.add(activeTaskLabel);

            leftPanel.add(infoPanel, BorderLayout.CENTER);

            JPanel rightPanel = new JPanel(new BorderLayout(10, 0));
            rightPanel.setBackground(Color.WHITE);

            checkBox = new JCheckBox();
            checkBox.setEnabled(!isSystemDisk);

            JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 5, 5));
            buttonPanel.setBackground(Color.WHITE);

            detailButton = new JButton("详细信息");
            detailButton.addActionListener(_ -> showDetailDialog());
            buttonPanel.add(detailButton);

            if (!isSystemDisk) {
                toggleButton = new JButton(getToggleButtonText());
                toggleButton.addActionListener(_ -> toggleDevice());

                blacklistButton = new JButton("加入黑名单");
                blacklistButton.addActionListener(_ -> addToBlacklist());

                JButton removeButton = new JButton("移除设备");
                removeButton.addActionListener(_ -> removeDevice());

                buttonPanel.add(toggleButton);
                buttonPanel.add(blacklistButton);
                buttonPanel.add(removeButton);
            } else {
                toggleButton = null;
                blacklistButton = null;
            }

            rightPanel.add(checkBox, BorderLayout.NORTH);
            rightPanel.add(buttonPanel, BorderLayout.CENTER);

            add(leftPanel, BorderLayout.CENTER);
            add(rightPanel, BorderLayout.EAST);

            refreshDeviceInfo();
        }

        private String getFsType() {
            if (device.getFileStore() == null) {
                return "未知";
            }
            return device.getFileStore().type();
        }

        private String getStorageInfo() {
            if (device.getFileStore() == null) {
                return "未知";
            }
            try {
                long total = device.getFileStore().getTotalSpace();
                long usable = device.getFileStore().getUsableSpace();
                long used = total - usable;

                String totalStr = formatSize(total);
                String usedStr = formatSize(used);
                String usableStr = formatSize(usable);

                double usagePercent = total > 0 ? (used * 100.0 / total) : 0;

                return String.format("%s / %s (可用: %s, %.1f%%)",
                        usedStr, totalStr, usableStr, usagePercent);
            } catch (IOException e) {
                return "无法获取";
            }
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024));
            } else if (bytes < 1024L * 1024 * 1024 * 1024) {
                return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
            } else {
                return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
            }
        }

        private String getDeviceIcon(boolean isSystemDisk) {
            return isSystemDisk ? "💾" : "🔌";
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

        private void removeDevice() {
            String serialNumber = device.getSerialNumber();
            String devicePath = device.getRootPath() != null ? device.getRootPath().toString() : "未知";

            int confirm = JOptionPane.showConfirmDialog(
                    parentFrame,
                    "确定要从设备管理器中彻底移除以下设备吗?\n\n" +
                    "路径: " + devicePath + "\n" +
                    "序列号: " + serialNumber + "\n\n" +
                    "警告: 此操作将从设备列表和持久化存储中移除该设备!",
                    "确认移除设备",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                boolean removed = deviceManager.removeDeviceCompletely(serialNumber);
                if (removed) {
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            "设备已彻底移除",
                            "操作成功",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            "移除设备失败",
                            "操作失败",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void showDetailDialog() {
            JPanel detailPanel = new JPanel(new GridLayout(0, 1, 5, 5));
            detailPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            detailPanel.add(new JLabel("设备路径: " + device.getRootPath()));
            detailPanel.add(new JLabel("序列号: " + device.getSerialNumber()));
            detailPanel.add(new JLabel("文件系统: " + getFsType()));
            detailPanel.add(new JLabel("设备状态: " + device.getState()));
            detailPanel.add(new JLabel("系统盘: " + (device.isSystemDisk() ? "是" : "否")));
            detailPanel.add(new JLabel("幽灵设备: " + (device.isGhost() ? "是" : "否")));
            detailPanel.add(new JLabel("存储信息: " + getStorageInfo()));

            if (device.getFileStore() != null) {
                try {
                    detailPanel.add(new JLabel("卷名称: " + device.getFileStore().name()));
                    detailPanel.add(new JLabel("卷类型: " + device.getFileStore().type()));
                } catch (Exception e) {
                    detailPanel.add(new JLabel("卷信息: 获取失败"));
                }
            }

            JOptionPane.showMessageDialog(
                    parentFrame,
                    detailPanel,
                    "设备详细信息",
                    JOptionPane.INFORMATION_MESSAGE);
        }

        public JCheckBox getCheckBox() {
            return checkBox;
        }

        /**
         * Refreshes all device information displayed on this card.
         * Called when device state changes or device is updated.
         */
        public void refreshDeviceInfo() {
            SwingUtilities.invokeLater(() -> {
                // Update path label (handle ghost devices)
                if (device.isGhost() || device.getRootPath() == null) {
                    pathLabel.setText("路径: 离线");
                } else {
                    pathLabel.setText("路径: " + device.getRootPath() + (device.isSystemDisk() ? " [系统盘]" : ""));
                }

                // Update volume name
                String volumeName = device.getVolumeName();
                String volumeDisplay = volumeName != null && !volumeName.isEmpty() ? volumeName : "无";
                volumeLabel.setText("卷标: " + volumeDisplay);

                // Update filesystem type
                fsTypeLabel.setText("文件系统: " + getFsType());

                // Update storage info
                storageLabel.setText("存储: " + getStorageInfo());

                // Update icon
                iconLabel.setText(getDeviceIcon(device.isSystemDisk()));

                // Update state and buttons
                Device.DeviceState currentState = device.getState();
                stateLabel.setText("状态: " + currentState);
                stateLabel.setForeground(getStateColor(currentState));
                if (toggleButton != null) {
                    toggleButton.setText(getToggleButtonText());
                }
            });
        }

        public void updateState(Device.DeviceState newState) {
            refreshDeviceInfo();
        }

        // Deprecated: Use refreshDeviceInfo() instead
        public void updateIcon() {
            refreshDeviceInfo();
        }

        // Deprecated: Use refreshDeviceInfo() instead
        public void updateVolumeName() {
            refreshDeviceInfo();
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
