package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;
import com.superredrock.usbthief.gui.components.EmptyStatePanel;
import com.superredrock.usbthief.gui.theme.ThemeManager;

import java.util.Locale;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DeviceListPanel extends JPanel implements I18NManager.LocaleChangeListener {

    private final I18NManager i18n = I18NManager.getInstance();
    private final JPanel devicesPanel;
    private final Map<Device, DeviceCard> deviceCards = new HashMap<>();
    private final DeviceManager deviceManager;
    private EmptyStatePanel emptyStatePanel;

    // Timer for updating active task counts and status bar
    private Timer updateTimer;

    // Parent frame for dialogs
    private JFrame parentFrame;

    // MainFrame reference for status bar updates
    private MainFrame mainFrame;

    // Popup menu for batch operations
    private JPopupMenu moreActionsMenu;
    private final JButton moreButton;
    private JMenuItem selectAllMenuItem;
    private JMenuItem deselectAllMenuItem;
    private JMenuItem batchEnableMenuItem;
    private JMenuItem batchDisableMenuItem;
    private JMenuItem batchBlacklistMenuItem;
    private JMenuItem blacklistManageMenuItem;

    public DeviceListPanel() {
        this.deviceManager = QueueManager.getDeviceManager();
        setLayout(new BorderLayout());
        setBackground(ThemeManager.BACKGROUND_PRIMARY);

        devicesPanel = new JPanel();
        devicesPanel.setLayout(new BoxLayout(devicesPanel, BoxLayout.Y_AXIS));
        devicesPanel.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        JScrollPane scrollPane = new JScrollPane(devicesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(10, 10, 10, 10),
            new TitledBorder(i18n.getMessage("device.list.border"))
        ));
        scrollPane.getViewport().setBackground(ThemeManager.BACKGROUND_PRIMARY);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        // Create "..." button with popup menu
        createMoreActionsMenu();

        moreButton = new JButton("⋮");
        moreButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        moreButton.setToolTipText(i18n.getMessage("device.menu.more"));
        moreButton.setFocusPainted(false);
        moreButton.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        moreButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        moreButton.addActionListener(e -> moreActionsMenu.show(moreButton, 0, moreButton.getHeight()));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        rightPanel.add(moreButton);

        topPanel.add(rightPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Create empty state panel
        createEmptyStatePanel();
        updateEmptyState();

        initializeExistingDevices();
        registerEventListeners();
        startUpdateTimer();
        
        // Register for locale changes
        i18n.addLocaleChangeListener(this);
    }

    /**
     * Create the popup menu for batch operations.
     */
    private void createMoreActionsMenu() {
        moreActionsMenu = new JPopupMenu();

        selectAllMenuItem = new JMenuItem(i18n.getMessage("device.menu.selectAll"));
        selectAllMenuItem.addActionListener(_ -> setSelectAll(true));

        deselectAllMenuItem = new JMenuItem(i18n.getMessage("device.menu.deselectAll"));
        deselectAllMenuItem.addActionListener(_ -> setSelectAll(false));

        batchEnableMenuItem = new JMenuItem(i18n.getMessage("device.button.batchEnable"));
        batchEnableMenuItem.addActionListener(_ -> batchEnable());
        batchEnableMenuItem.setEnabled(false);

        batchDisableMenuItem = new JMenuItem(i18n.getMessage("device.button.batchDisable"));
        batchDisableMenuItem.addActionListener(_ -> batchDisable());
        batchDisableMenuItem.setEnabled(false);

        batchBlacklistMenuItem = new JMenuItem(i18n.getMessage("device.button.batchBlacklist"));
        batchBlacklistMenuItem.addActionListener(_ -> batchAddToBlacklist());
        batchBlacklistMenuItem.setEnabled(false);

        blacklistManageMenuItem = new JMenuItem(i18n.getMessage("device.button.blacklistManage"));
        blacklistManageMenuItem.addActionListener(_ -> BlacklistDialog.showBlacklistDialog(parentFrame));

        moreActionsMenu.add(selectAllMenuItem);
        moreActionsMenu.add(deselectAllMenuItem);
        moreActionsMenu.addSeparator();
        moreActionsMenu.add(batchEnableMenuItem);
        moreActionsMenu.add(batchDisableMenuItem);
        moreActionsMenu.add(batchBlacklistMenuItem);
        moreActionsMenu.addSeparator();
        moreActionsMenu.add(blacklistManageMenuItem);
    }

    private void initializeExistingDevices() {
        SwingUtilities.invokeLater(() -> {
            for (Device device : deviceManager.getAllDevices()) {
                addDevice(device);
            }
        });
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        eventBus.register(NewDeviceJoinedEvent.class, this::onDeviceJoined);
        eventBus.register(DeviceInsertedEvent.class, this::onDeviceInserted);
        eventBus.register(DeviceRemovedEvent.class, this::onDeviceRemoved);
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
     * Create the empty state panel shown when no devices are connected.
     */
    private void createEmptyStatePanel() {
        emptyStatePanel = new EmptyStatePanel(
            "\uD83D\uDD0C",  // Plug emoji
            i18n.getMessage("empty.devices.title"),
            i18n.getMessage("empty.devices.description")
        );
        emptyStatePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    /**
     * Update the visibility of the empty state panel.
     */
    private void updateEmptyState() {
        SwingUtilities.invokeLater(() -> {
            if (deviceCards.isEmpty()) {
                // Show empty state
                if (emptyStatePanel.getParent() == null) {
                    devicesPanel.add(emptyStatePanel);
                    devicesPanel.revalidate();
                    devicesPanel.repaint();
                }
            } else {
                // Hide empty state
                if (emptyStatePanel.getParent() != null) {
                    devicesPanel.remove(emptyStatePanel);
                    devicesPanel.revalidate();
                    devicesPanel.repaint();
                }
            }
        });
    }

    /**
     * Sets the parent frame for dialogs.
     *
     * @param frame parent JFrame
     */
    public void setParentFrame(JFrame frame) {
        this.parentFrame = frame;
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        refreshLanguage();
    }

    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) getComponent(1);
            scrollPane.setBorder(new TitledBorder(i18n.getMessage("device.list.border")));
            moreButton.setToolTipText(i18n.getMessage("device.menu.more"));

            // Update popup menu items
            selectAllMenuItem.setText(i18n.getMessage("device.menu.selectAll"));
            deselectAllMenuItem.setText(i18n.getMessage("device.menu.deselectAll"));
            batchEnableMenuItem.setText(i18n.getMessage("device.button.batchEnable"));
            batchDisableMenuItem.setText(i18n.getMessage("device.button.batchDisable"));
            batchBlacklistMenuItem.setText(i18n.getMessage("device.button.batchBlacklist"));
            blacklistManageMenuItem.setText(i18n.getMessage("device.button.blacklistManage"));

            // Update empty state panel if visible
            if (emptyStatePanel != null) {
                emptyStatePanel.setTitle(i18n.getMessage("empty.devices.title"));
                emptyStatePanel.setDescription(i18n.getMessage("empty.devices.message"));
            }

            for (DeviceCard card : deviceCards.values()) {
                card.refreshLanguage();
            }
        });
    }

    private void onDeviceJoined(NewDeviceJoinedEvent event) {
        SwingUtilities.invokeLater(() -> addDevice(event.device()));
    }

    private void onDeviceInserted(DeviceInsertedEvent event) {
        SwingUtilities.invokeLater(() -> {
            Device newDevice = event.device();
            String serial = newDevice.getSerialNumber();
            
            Device oldKey = null;
            for (Device device : deviceCards.keySet()) {
                if (device.getSerialNumber().equals(serial)) {
                    oldKey = device;
                    break;
                }
            }
            
            if (oldKey != null) {
                DeviceCard card = deviceCards.remove(oldKey);
                card.updateDevice(newDevice);
                deviceCards.put(newDevice, card);
            } else {
                addDevice(newDevice);
            }
        });
    }

    private void onDeviceRemoved(DeviceRemovedEvent event) {
        SwingUtilities.invokeLater(() -> {
            String serial = event.device().getSerialNumber();
            
            Device oldKey = null;
            for (Device device : deviceCards.keySet()) {
                if (device.getSerialNumber().equals(serial)) {
                    oldKey = device;
                    break;
                }
            }
            
            if (oldKey != null) {
                Device ghostDevice = findDeviceFromManager(serial);
                if (ghostDevice != null) {
                    // Convert to ghost device
                    DeviceCard card = deviceCards.remove(oldKey);
                    card.updateDevice(ghostDevice);
                    deviceCards.put(ghostDevice, card);
                } else {
                    // No ghost device - remove card from UI (device was manually removed)
                    DeviceCard card = deviceCards.remove(oldKey);
                    devicesPanel.remove(card);
                    devicesPanel.revalidate();
                    devicesPanel.repaint();
                }
            }
        });
    }
    
    private Device findDeviceFromManager(String serial) {
        for (Device device : deviceManager.getAllDevices()) {
            if (device.getSerialNumber().equals(serial)) {
                return device;
            }
        }
        return null;
    }

    private DeviceCard findCardBySerial(String serial) {
        for (Map.Entry<Device, DeviceCard> entry : deviceCards.entrySet()) {
            if (entry.getKey().getSerialNumber().equals(serial)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void onDeviceStateChanged(DeviceStateChangedEvent event) {
        SwingUtilities.invokeLater(() -> updateDeviceState(event.device()));
    }

    /**
     * Set all device checkboxes to the specified state.
     *
     * @param selected true to select all, false to deselect all
     */
    private void setSelectAll(boolean selected) {
        for (DeviceCard card : deviceCards.values()) {
            if (card.getCheckBox().isEnabled()) {
                card.getCheckBox().setSelected(selected);
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
        batchEnableMenuItem.setEnabled(hasSelection);
        batchDisableMenuItem.setEnabled(hasSelection);
        batchBlacklistMenuItem.setEnabled(hasSelection);
    }

    private void batchEnable() {
        int count = 0;
        for (DeviceCard card : deviceCards.values()) {
            if (card.getCheckBox().isSelected()) {
                deviceManager.enableDevice(card.device);
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
                deviceManager.disableDevice(card.device);
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
        updateEmptyState();
    }

    private void updateDeviceState(Device device) {
        DeviceCard card = deviceCards.get(device);
        if (card != null) {
            card.updateState();
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
        private Device device;
        private final JFrame parentFrame;
        private final DeviceManager deviceManager;
        private final JLabel iconLabel;
        private final JLabel pathLabel;
        private final JLabel volumeLabel;
        private final JLabel fsTypeLabel;
        private final JLabel storageLabel;
        private final JPanel stateBadge;
        private final JLabel stateLabel;
        private final JLabel activeTaskLabel;
        private final JCheckBox checkBox;
        private final JButton moreButton;
        private final JPopupMenu cardMenu;
        private final JMenuItem detailMenuItem;
        private final JMenuItem toggleMenuItem;
        private final JMenuItem blacklistMenuItem;
        private final JMenuItem removeMenuItem;

        public DeviceCard(Device device, JFrame parentFrame, DeviceManager deviceManager) {
            this.device = device;
            this.parentFrame = parentFrame;
            this.deviceManager = deviceManager;
            setLayout(new BorderLayout(12, 8));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1, true),
                new EmptyBorder(12, 16, 12, 16)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            setBackground(ThemeManager.CARD_BACKGROUND);

            // Hover effect
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    setBackground(ThemeManager.CARD_BACKGROUND_ALT);
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeManager.ACCENT_PRIMARY, 1, true),
                        new EmptyBorder(12, 16, 12, 16)
                    ));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    setBackground(ThemeManager.CARD_BACKGROUND);
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1, true),
                        new EmptyBorder(12, 16, 12, 16)
                    ));
                }
            });

            boolean isSystemDisk = device.isSystemDisk();

            // Left panel with icon
            JPanel leftPanel = new JPanel(new BorderLayout(10, 0));
            leftPanel.setBackground(ThemeManager.CARD_BACKGROUND);
            leftPanel.setOpaque(false);

            iconLabel = new JLabel(getDeviceIcon(isSystemDisk));
            iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 32));
            leftPanel.add(iconLabel, BorderLayout.WEST);

            // Info panel
            JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 4));
            infoPanel.setBackground(ThemeManager.CARD_BACKGROUND);
            infoPanel.setOpaque(false);

            // Path with styled font
            pathLabel = new JLabel(i18n.getMessage("device.card.path") + ": " + device.getRootPath() + (isSystemDisk ? " " + i18n.getMessage("device.card.systemDisk") : ""));
            pathLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            pathLabel.setForeground(ThemeManager.TEXT_PRIMARY);
            
            String volumeName = device.getVolumeName();
            String volumeDisplay = volumeName != null && !volumeName.isEmpty() ? volumeName : i18n.getMessage("device.card.volume.none");
            volumeLabel = new JLabel(i18n.getMessage("device.card.volume") + ": " + volumeDisplay);
            volumeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            volumeLabel.setForeground(ThemeManager.TEXT_SECONDARY);
            
            fsTypeLabel = new JLabel(i18n.getMessage("device.card.fs") + ": " + getFsType());
            fsTypeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            fsTypeLabel.setForeground(ThemeManager.TEXT_SECONDARY);
            
            storageLabel = new JLabel(i18n.getMessage("device.card.storage") + ": " + getStorageInfo());
            storageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            storageLabel.setForeground(ThemeManager.TEXT_SECONDARY);
            
            // State badge panel
            stateBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            stateBadge.setOpaque(false);
            stateLabel = new JLabel(getLocalizedState(device.getState()));
            stateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            stateBadge.add(stateLabel);
            
            activeTaskLabel = new JLabel(i18n.getMessage("device.card.activeTasks") + ": 0");
            activeTaskLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            activeTaskLabel.setForeground(ThemeManager.TEXT_SECONDARY);
            activeTaskLabel.setVisible(false);

            infoPanel.add(pathLabel);
            infoPanel.add(volumeLabel);
            infoPanel.add(fsTypeLabel);
            infoPanel.add(storageLabel);
            infoPanel.add(stateBadge);
            infoPanel.add(activeTaskLabel);

            leftPanel.add(infoPanel, BorderLayout.CENTER);

            // Right panel with checkbox and more button
            JPanel rightPanel = new JPanel(new BorderLayout(10, 0));
            rightPanel.setBackground(ThemeManager.CARD_BACKGROUND);
            rightPanel.setOpaque(false);

            checkBox = new JCheckBox();
            checkBox.setEnabled(!isSystemDisk);

            // Create popup menu for device actions
            cardMenu = new JPopupMenu();

            detailMenuItem = new JMenuItem(i18n.getMessage("device.card.button.details"));
            detailMenuItem.addActionListener(_ -> showDetailDialog());
            cardMenu.add(detailMenuItem);

            if (!isSystemDisk) {
                toggleMenuItem = new JMenuItem(getToggleButtonText());
                toggleMenuItem.addActionListener(_ -> toggleDevice());
                cardMenu.add(toggleMenuItem);

                blacklistMenuItem = new JMenuItem(i18n.getMessage("device.card.button.blacklist"));
                blacklistMenuItem.addActionListener(_ -> addToBlacklist());
                cardMenu.add(blacklistMenuItem);

                removeMenuItem = new JMenuItem(i18n.getMessage("device.card.button.remove"));
                removeMenuItem.addActionListener(_ -> removeDevice());
                cardMenu.add(removeMenuItem);

                updateButtonEnabled();
            } else {
                toggleMenuItem = null;
                blacklistMenuItem = null;
                removeMenuItem = null;
            }

            // Create "..." button
            moreButton = new JButton("⋮");
            moreButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            moreButton.setToolTipText(i18n.getMessage("device.card.button.more"));
            moreButton.setFocusPainted(false);
            moreButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            moreButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            moreButton.addActionListener(e -> cardMenu.show(moreButton, 0, moreButton.getHeight()));

            rightPanel.add(checkBox, BorderLayout.NORTH);
            rightPanel.add(moreButton, BorderLayout.CENTER);

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

                String totalStr = SizeFormatter.format(total);
                String usedStr = SizeFormatter.format(used);
                String usableStr = SizeFormatter.format(usable);

                double usagePercent = total > 0 ? (used * 100.0 / total) : 0;

                return String.format("%s / %s (%s: %s, %.1f%%)",
                        usedStr, totalStr, i18n.getMessage("device.card.volume.none"), usableStr, usagePercent);
            } catch (IOException e) {
                return i18n.getMessage("device.card.unavailable");
            }
        }

        private String getDeviceIcon(boolean isSystemDisk) {
            return isSystemDisk ? "💾" : "🔌";
        }

        private String getToggleButtonText() {
            return device.getState() == Device.DeviceState.DISABLED ? i18n.getMessage("device.card.button.enable") : i18n.getMessage("device.card.button.disable");
        }

        /**
         * Get localized string for device state.
         */
        private String getLocalizedState(Device.DeviceState state) {
            return switch (state) {
                case OFFLINE -> i18n.getMessage("device.state.offline");
                case UNAVAILABLE -> i18n.getMessage("device.state.unavailable");
                case IDLE -> i18n.getMessage("device.state.idle");
                case SCANNING -> i18n.getMessage("device.state.scanning");
                case PAUSED -> i18n.getMessage("device.state.paused");
                case DISABLED -> i18n.getMessage("device.state.disabled");
            };
        }

        private void toggleDevice() {
            if (device.isGhost()) {
                return;
            }
            if (device.getState() == Device.DeviceState.DISABLED) {
                deviceManager.enableDevice(device);
            } else {
                deviceManager.disableDevice(device);
            }
        }

        private void addToBlacklist() {
            String serialNumber = device.getSerialNumber();
            String devicePath;
            if (device.isGhost()) {
                devicePath = "?";
            }else {
                devicePath = device.getRootPath().toString();
            }


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
            String devicePath;
            if (device.isGhost()) {
                devicePath = i18n.getMessage("device.card.offline");
            } else {
                devicePath = device.getRootPath().toString();
            }

            int confirm = JOptionPane.showConfirmDialog(
                    parentFrame,
                    i18n.getMessage("device.remove.confirm", devicePath, serialNumber),
                    i18n.getMessage("device.remove.confirm.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                deviceManager.removeDevice(device);
                JOptionPane.showMessageDialog(
                        parentFrame,
                        i18n.getMessage("device.remove.success"),
                        i18n.getMessage("device.remove.success.title"),
                        JOptionPane.INFORMATION_MESSAGE);
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
         * Updates menu item enabled state based on device status.
         * Ghost devices have menu items disabled.
         * System disks don't have toggle/blacklist items at all.
         */
        private void updateButtonEnabled() {
            boolean enabled = !device.isGhost();
            checkBox.setEnabled(enabled);
            if (device.isSystemDisk()){
                checkBox.setEnabled(false);
            }
            if (toggleMenuItem != null){
                toggleMenuItem.setEnabled(enabled);
            }
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

                // Update state badge
                Device.DeviceState currentState = device.getState();
                stateLabel.setText(getLocalizedState(currentState));
                Color stateColor = ThemeManager.getStateColor(currentState);
                stateLabel.setForeground(Color.WHITE);
                stateBadge.setBackground(stateColor);
                stateBadge.setOpaque(true);
                stateBadge.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                
                if (toggleMenuItem != null) {
                    toggleMenuItem.setText(getToggleButtonText());
                }

                // Update menu item enabled state
                updateButtonEnabled();
            });
        }

        public void updateState() {
            refreshDeviceInfo();
        }

        public void updateDevice(Device newDevice) {
            this.device = newDevice;
            refreshDeviceInfo();
        }

        // updateActiveTaskCount() removed: task queue now managed by TaskScheduler

        /**
         * Refreshes all language-dependent text on this card.
         * Called when the application language is changed.
         */
        public void refreshLanguage() {
            SwingUtilities.invokeLater(() -> {
                detailMenuItem.setText(i18n.getMessage("device.card.button.details"));
                moreButton.setToolTipText(i18n.getMessage("device.card.button.more"));
                activeTaskLabel.setText(i18n.getMessage("device.card.activeTasks") + ": " + (activeTaskLabel.isVisible() ? "0" : ""));
                if (toggleMenuItem != null) {
                    toggleMenuItem.setText(getToggleButtonText());
                }
                if (blacklistMenuItem != null) {
                    blacklistMenuItem.setText(i18n.getMessage("device.card.button.blacklist"));
                }
                if (removeMenuItem != null) {
                    removeMenuItem.setText(i18n.getMessage("device.card.button.remove"));
                }
                refreshDeviceInfo();
            });
        }
    }
}
