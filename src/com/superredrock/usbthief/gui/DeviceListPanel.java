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

    private final I18NManager i18n = I18NManager.getInstance();
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
        scrollPane.setBorder(new TitledBorder(i18n.getMessage("device.list.border")));

        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectAllButton = new JButton(i18n.getMessage("device.button.selectAll"));
        selectAllButton.setToolTipText(i18n.getMessage("device.button.selectAll.tooltip"));
        selectAllButton.addActionListener(_ -> toggleSelectAll());
        leftPanel.add(selectAllButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        batchEnableButton = new JButton(i18n.getMessage("device.button.batchEnable"));
        batchEnableButton.setToolTipText(i18n.getMessage("device.button.batchEnable.tooltip"));
        batchEnableButton.addActionListener(_ -> batchEnable());
        batchEnableButton.setEnabled(false);

        batchDisableButton = new JButton(i18n.getMessage("device.button.batchDisable"));
        batchDisableButton.setToolTipText(i18n.getMessage("device.button.batchDisable.tooltip"));
        batchDisableButton.addActionListener(_ -> batchDisable());
        batchDisableButton.setEnabled(false);

        batchBlacklistButton = new JButton(i18n.getMessage("device.button.batchBlacklist"));
        batchBlacklistButton.setToolTipText(i18n.getMessage("device.button.batchBlacklist.tooltip"));
        batchBlacklistButton.addActionListener(_ -> batchAddToBlacklist());
        batchBlacklistButton.setEnabled(false);

        JButton blacklistButton = new JButton(i18n.getMessage("device.button.blacklistManage"));
        blacklistButton.setToolTipText(i18n.getMessage("device.button.blacklistManage.tooltip"));
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

    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) getComponent(1);
            scrollPane.setBorder(new TitledBorder(i18n.getMessage("device.list.border")));
            selectAllButton.setText(i18n.getMessage("device.button.selectAll"));
            selectAllButton.setToolTipText(i18n.getMessage("device.button.selectAll.tooltip"));
            batchEnableButton.setText(i18n.getMessage("device.button.batchEnable"));
            batchEnableButton.setToolTipText(i18n.getMessage("device.button.batchEnable.tooltip"));
            batchDisableButton.setText(i18n.getMessage("device.button.batchDisable"));
            batchDisableButton.setToolTipText(i18n.getMessage("device.button.batchDisable.tooltip"));
            batchBlacklistButton.setText(i18n.getMessage("device.button.batchBlacklist"));
            batchBlacklistButton.setToolTipText(i18n.getMessage("device.button.batchBlacklist.tooltip"));
            
            for (DeviceCard card : deviceCards.values()) {
                card.refreshLanguage();
            }
        });
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
                    i18n.getMessage("device.batch.enabled", count),
                    i18n.getMessage("device.batchOperation"),
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
                    i18n.getMessage("device.batch.disabled", count),
                    i18n.getMessage("device.batchOperation"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void batchAddToBlacklist() {
        int confirm = JOptionPane.showConfirmDialog(
                parentFrame,
                i18n.getMessage("device.batchBlacklist.confirm"),
                i18n.getMessage("device.batchBlacklist.confirm.title"),
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
                        i18n.getMessage("device.batchBlacklist.success", count),
                        i18n.getMessage("device.batchOperation"),
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

        private final I18NManager i18n = I18NManager.getInstance();
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
        private final JButton toggleButton;
        private final JButton blacklistButton;
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

            pathLabel = new JLabel(i18n.getMessage("device.card.path") + ": " + device.getRootPath() + (isSystemDisk ? " " + i18n.getMessage("device.card.systemDisk") : ""));
            
            String volumeName = device.getVolumeName();
            String volumeDisplay = volumeName != null && !volumeName.isEmpty() ? volumeName : i18n.getMessage("device.card.volume.none");
            volumeLabel = new JLabel(i18n.getMessage("device.card.volume") + ": " + volumeDisplay);
            
            fsTypeLabel = new JLabel(i18n.getMessage("device.card.fs") + ": " + getFsType());
            storageLabel = new JLabel(i18n.getMessage("device.card.storage") + ": " + getStorageInfo());
            stateLabel = new JLabel(i18n.getMessage("device.card.state") + ": " + device.getState());
            activeTaskLabel = new JLabel(i18n.getMessage("device.card.activeTasks") + ": 0");
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

            detailButton = new JButton(i18n.getMessage("device.card.button.details"));
            detailButton.addActionListener(_ -> showDetailDialog());
            buttonPanel.add(detailButton);

            if (!isSystemDisk) {
                toggleButton = new JButton(getToggleButtonText());
                toggleButton.addActionListener(_ -> toggleDevice());

                blacklistButton = new JButton(i18n.getMessage("device.card.button.blacklist"));
                blacklistButton.addActionListener(_ -> addToBlacklist());

                JButton removeButton = new JButton(i18n.getMessage("device.card.button.remove"));
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
                return i18n.getMessage("device.card.unknown");
            }
            return device.getFileStore().type();
        }

        private String getStorageInfo() {
            if (device.getFileStore() == null) {
                return i18n.getMessage("device.card.unknown");
            }
            try {
                long total = device.getFileStore().getTotalSpace();
                long usable = device.getFileStore().getUsableSpace();
                long used = total - usable;

                String totalStr = formatSize(total);
                String usedStr = formatSize(used);
                String usableStr = formatSize(usable);

                double usagePercent = total > 0 ? (used * 100.0 / total) : 0;

                return String.format("%s / %s (%s: %s, %.1f%%)",
                        usedStr, totalStr, i18n.getMessage("device.card.volume.none"), usableStr, usagePercent);
            } catch (IOException e) {
                return i18n.getMessage("device.card.unavailable");
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
            return device.getState() == Device.DeviceState.DISABLED ? i18n.getMessage("device.card.button.enable") : i18n.getMessage("device.card.button.disable");
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
                    i18n.getMessage("device.card.blacklist.confirm", devicePath, serialNumber),
                    i18n.getMessage("device.card.blacklist.confirm.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                ConfigManager.getInstance().addToDeviceBlacklistBySerial(serialNumber);
                JOptionPane.showMessageDialog(
                        parentFrame,
                        i18n.getMessage("device.card.blacklist.success"),
                        i18n.getMessage("device.card.blacklist.success.title"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void removeDevice() {
            String serialNumber = device.getSerialNumber();
            String devicePath = device.getRootPath() != null ? device.getRootPath().toString() : i18n.getMessage("device.card.unknown");

            int confirm = JOptionPane.showConfirmDialog(
                    parentFrame,
                    i18n.getMessage("device.card.remove.confirm", devicePath, serialNumber),
                    i18n.getMessage("device.card.remove.confirm.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                boolean removed = deviceManager.removeDeviceCompletely(serialNumber);
                if (removed) {
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            i18n.getMessage("device.card.remove.success"),
                            i18n.getMessage("device.card.remove.success.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            i18n.getMessage("device.card.remove.failed"),
                            i18n.getMessage("device.card.remove.failed.title"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void showDetailDialog() {
            JPanel detailPanel = new JPanel(new GridLayout(0, 1, 5, 5));
            detailPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.path") + ": " + device.getRootPath()));
            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.serial") + ": " + device.getSerialNumber()));
            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.fs") + ": " + getFsType()));
            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.state") + ": " + device.getState()));
            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.systemDisk") + ": " + (device.isSystemDisk() ? i18n.getMessage("device.card.detail.yes") : i18n.getMessage("device.card.detail.no"))));
            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.ghost") + ": " + (device.isGhost() ? i18n.getMessage("device.card.detail.yes") : i18n.getMessage("device.card.detail.no"))));
            detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.storage") + ": " + getStorageInfo()));

            if (device.getFileStore() != null) {
                try {
                    detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.volumeName") + ": " + device.getFileStore().name()));
                    detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.volumeType") + ": " + device.getFileStore().type()));
                } catch (Exception e) {
                    detailPanel.add(new JLabel(i18n.getMessage("device.card.detail.volumeName") + ": " + i18n.getMessage("device.card.detail.failed")));
                }
            }

            JOptionPane.showMessageDialog(
                    parentFrame,
                    detailPanel,
                    i18n.getMessage("device.card.detail.title"),
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
                    pathLabel.setText(i18n.getMessage("device.card.path") + ": " + i18n.getMessage("device.card.offline"));
                } else {
                    pathLabel.setText(i18n.getMessage("device.card.path") + ": " + device.getRootPath() + (device.isSystemDisk() ? " " + i18n.getMessage("device.card.systemDisk") : ""));
                }

                // Update volume name
                String volumeName = device.getVolumeName();
                String volumeDisplay = volumeName != null && !volumeName.isEmpty() ? volumeName : i18n.getMessage("device.card.volume.none");
                volumeLabel.setText(i18n.getMessage("device.card.volume") + ": " + volumeDisplay);

                // Update filesystem type
                fsTypeLabel.setText(i18n.getMessage("device.card.fs") + ": " + getFsType());

                // Update storage info
                storageLabel.setText(i18n.getMessage("device.card.storage") + ": " + getStorageInfo());

                // Update icon
                iconLabel.setText(getDeviceIcon(device.isSystemDisk()));

                // Update state and buttons
                Device.DeviceState currentState = device.getState();
                stateLabel.setText(i18n.getMessage("device.card.state") + ": " + currentState);
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

        /**
         * Refreshes all language-dependent text on this card.
         * Called when the application language is changed.
         */
        public void refreshLanguage() {
            SwingUtilities.invokeLater(() -> {
                detailButton.setText(i18n.getMessage("device.card.button.details"));
                activeTaskLabel.setText(i18n.getMessage("device.card.activeTasks") + ": " + (activeTaskLabel.isVisible() ? "0" : ""));
                if (toggleButton != null) {
                    toggleButton.setText(getToggleButtonText());
                }
                if (blacklistButton != null) {
                    blacklistButton.setText(i18n.getMessage("device.card.button.blacklist"));
                }
                refreshDeviceInfo();
            });
        }

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
