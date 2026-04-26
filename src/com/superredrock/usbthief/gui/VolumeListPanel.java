package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
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

public class VolumeListPanel extends JPanel implements I18NManager.LocaleChangeListener {

    private final I18NManager i18n = I18NManager.getInstance();
    private final JPanel devicesPanel;
    private final Map<Volume, VolumeCard> volumeCards = new HashMap<>();
    private final DeviceManager deviceManager;
    private EmptyStatePanel emptyStatePanel;

    private Timer updateTimer;
    private JFrame parentFrame;
    private MainFrame mainFrame;

    private JPopupMenu moreActionsMenu;
    private final JButton moreButton;
    private JMenuItem selectAllMenuItem;
    private JMenuItem deselectAllMenuItem;
    private JMenuItem batchEnableMenuItem;
    private JMenuItem batchDisableMenuItem;
    private JMenuItem batchBlacklistMenuItem;
    private JMenuItem blacklistManageMenuItem;

    public VolumeListPanel() {
        this.deviceManager = DeviceManager.getInstance();
        setLayout(new BorderLayout());

        devicesPanel = new JPanel();
        devicesPanel.setLayout(new BoxLayout(devicesPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(devicesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        JPanel topPanel = new JPanel(new BorderLayout());

        createMoreActionsMenu();

        moreButton = new JButton("⋮");
        moreButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        moreButton.setToolTipText(i18n.getMessage("device.menu.more"));
        moreButton.setFocusPainted(false);
        moreButton.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        moreButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        moreButton.addActionListener(e -> moreActionsMenu.show(moreButton, 0, moreButton.getHeight()));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.add(moreButton);

        topPanel.add(rightPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        createEmptyStatePanel();
        updateEmptyState();

        initializeExistingVolumes();
        registerEventListeners();
        startUpdateTimer();

        i18n.addLocaleChangeListener(this);
    }

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

    private void initializeExistingVolumes() {
        SwingUtilities.invokeLater(() -> {
            for (Volume volume : deviceManager.getAllVolumes()) {
                addVolume(volume);
            }
        });
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        eventBus.register(VolumeInsertedEvent.class, this::onVolumeInserted);
        eventBus.register(VolumeRemovedEvent.class, this::onVolumeRemoved);
        eventBus.register(VolumeStateChangedEvent.class, this::onVolumeStateChanged);
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, _ -> {
            if (mainFrame != null) {
                mainFrame.updateStatusBar();
            }
        });
        updateTimer.start();
    }

    public void setMainFrame(MainFrame frame) {
        this.mainFrame = frame;
    }

    public void stop() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }

    private void createEmptyStatePanel() {
        emptyStatePanel = new EmptyStatePanel(
            "\uD83D\uDD0C",
            i18n.getMessage("empty.devices.title"),
            i18n.getMessage("empty.devices.description")
        );
        emptyStatePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private void updateEmptyState() {
        SwingUtilities.invokeLater(() -> {
            if (volumeCards.isEmpty()) {
                if (emptyStatePanel.getParent() == null) {
                    devicesPanel.add(emptyStatePanel);
                    devicesPanel.revalidate();
                    devicesPanel.repaint();
                }
            } else {
                if (emptyStatePanel.getParent() != null) {
                    devicesPanel.remove(emptyStatePanel);
                    devicesPanel.revalidate();
                    devicesPanel.repaint();
                }
            }
        });
    }

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

            selectAllMenuItem.setText(i18n.getMessage("device.menu.selectAll"));
            deselectAllMenuItem.setText(i18n.getMessage("device.menu.deselectAll"));
            batchEnableMenuItem.setText(i18n.getMessage("device.button.batchEnable"));
            batchDisableMenuItem.setText(i18n.getMessage("device.button.batchDisable"));
            batchBlacklistMenuItem.setText(i18n.getMessage("device.button.batchBlacklist"));
            blacklistManageMenuItem.setText(i18n.getMessage("device.button.blacklistManage"));

            if (emptyStatePanel != null) {
                emptyStatePanel.setTitle(i18n.getMessage("empty.devices.title"));
                emptyStatePanel.setDescription(i18n.getMessage("empty.devices.message"));
            }

            for (VolumeCard card : volumeCards.values()) {
                card.refreshLanguage();
            }
        });
    }

    private void onVolumeInserted(VolumeInsertedEvent event) {
        SwingUtilities.invokeLater(() -> addVolume(event.volume()));
    }

    private void onVolumeRemoved(VolumeRemovedEvent event) {
        SwingUtilities.invokeLater(() -> {
            Volume vol = event.volume();
            Volume oldKey = null;
            for (Volume v : volumeCards.keySet()) {
                if (v.getSerialNumber().equals(vol.getSerialNumber())) {
                    oldKey = v;
                    break;
                }
            }
            if (oldKey != null) {
                VolumeCard card = volumeCards.remove(oldKey);
                devicesPanel.remove(card);
                devicesPanel.revalidate();
                devicesPanel.repaint();
            }
            updateEmptyState();
        });
    }

    private void onVolumeStateChanged(VolumeStateChangedEvent event) {
        SwingUtilities.invokeLater(() -> {
            Volume vol = event.volume();
            Volume oldKey = null;
            for (Volume v : volumeCards.keySet()) {
                if (v.getSerialNumber().equals(vol.getSerialNumber())) {
                    oldKey = v;
                    break;
                }
            }
            if (oldKey != null) {
                VolumeCard card = volumeCards.get(oldKey);
                card.updateVolume(vol);
            }
        });
    }

    private void setSelectAll(boolean selected) {
        for (VolumeCard card : volumeCards.values()) {
            if (card.getCheckBox().isEnabled()) {
                card.getCheckBox().setSelected(selected);
            }
        }
        updateBatchButtons();
    }

    private void updateBatchButtons() {
        boolean hasSelection = false;
        for (VolumeCard card : volumeCards.values()) {
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
        for (VolumeCard card : volumeCards.values()) {
            if (card.getCheckBox().isSelected()) {
                deviceManager.enable(card.volume);
                count++;
            }
        }
        if (count > 0) {
            JOptionPane.showMessageDialog(parentFrame,
                i18n.getMessage("device.batch.enabled", count),
                i18n.getMessage("device.batchOperation"),
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void batchDisable() {
        int count = 0;
        for (VolumeCard card : volumeCards.values()) {
            if (card.getCheckBox().isSelected()) {
                deviceManager.disable(card.volume);
                count++;
            }
        }
        if (count > 0) {
            JOptionPane.showMessageDialog(parentFrame,
                i18n.getMessage("device.batch.disabled", count),
                i18n.getMessage("device.batchOperation"),
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void batchAddToBlacklist() {
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
            i18n.getMessage("device.batchBlacklist.confirm"),
            i18n.getMessage("device.batchBlacklist.confirm.title"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            int count = 0;
            ConfigManager config = ConfigManager.getInstance();
            for (VolumeCard card : volumeCards.values()) {
                if (card.getCheckBox().isSelected()) {
                    config.addToDeviceBlacklistBySerial(card.volume.getSerialNumber());
                    count++;
                }
            }
            if (count > 0) {
                JOptionPane.showMessageDialog(parentFrame,
                    i18n.getMessage("device.batchBlacklist.success", count),
                    i18n.getMessage("device.batchOperation"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void addVolume(Volume volume) {
        if (volumeCards.containsKey(volume)) {
            return;
        }

        VolumeCard card = new VolumeCard(volume, parentFrame, deviceManager);
        volumeCards.put(volume, card);
        devicesPanel.add(card);
        card.getCheckBox().addItemListener(_ -> updateBatchButtons());
        devicesPanel.revalidate();
        devicesPanel.repaint();
        updateEmptyState();
    }

    public void updateAllVolumeNames() {
        for (VolumeCard card : volumeCards.values()) {
            card.refreshVolumeInfo();
        }
    }

    // ========== VolumeCard — compact single-line card ==========

    private static class VolumeCard extends JPanel {

        private final I18NManager i18n = I18NManager.getInstance();
        private Volume volume;
        private final JFrame parentFrame;
        private final DeviceManager deviceManager;
        private final JLabel pathLabel;
        private final JLabel storageLabel;
        private final JPanel stateBadge;
        private final JLabel stateLabel;
        private final JCheckBox checkBox;
        private final JButton moreButton;
        private final JPopupMenu cardMenu;
        private final JMenuItem detailMenuItem;
        private JMenuItem toggleMenuItem;
        private JMenuItem blacklistMenuItem;
        private JMenuItem removeMenuItem;

        public VolumeCard(Volume volume, JFrame parentFrame, DeviceManager deviceManager) {
            this.volume = volume;
            this.parentFrame = parentFrame;
            this.deviceManager = deviceManager;
            setLayout(new BorderLayout(8, 0));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                new EmptyBorder(4, 8, 4, 8)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setOpaque(true);

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeManager.ACCENT_PRIMARY, 1, true),
                        new EmptyBorder(4, 8, 4, 8)));
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                        new EmptyBorder(4, 8, 4, 8)));
                }
            });

            // Info panel — single horizontal line
            JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            infoPanel.setOpaque(false);

            pathLabel = new JLabel(volume.getRootPath().toString());
            pathLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

            storageLabel = new JLabel(getStorageInfoCompact());
            storageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            storageLabel.setForeground(ThemeManager.TEXT_SECONDARY);

            stateBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            stateBadge.setOpaque(false);
            stateLabel = new JLabel(getLocalizedState(volume.getState()));
            stateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            stateBadge.add(stateLabel);

            infoPanel.add(pathLabel);
            infoPanel.add(storageLabel);
            infoPanel.add(stateBadge);

            // Right panel
            JPanel rightPanel = new JPanel(new BorderLayout(4, 0));
            rightPanel.setOpaque(false);

            checkBox = new JCheckBox();
            checkBox.setEnabled(volume.getState() != Volume.VolumeState.OFFLINE);

            cardMenu = new JPopupMenu();
            detailMenuItem = new JMenuItem(i18n.getMessage("device.card.button.details"));
            detailMenuItem.addActionListener(_ -> showDetailDialog());
            cardMenu.add(detailMenuItem);

            toggleMenuItem = null;
            blacklistMenuItem = null;
            removeMenuItem = null;

            if (volume.getState() != Volume.VolumeState.OFFLINE) {
                toggleMenuItem = new JMenuItem(getToggleButtonText());
                toggleMenuItem.addActionListener(_ -> toggleVolume());
                cardMenu.add(toggleMenuItem);

                blacklistMenuItem = new JMenuItem(i18n.getMessage("device.card.button.blacklist"));
                blacklistMenuItem.addActionListener(_ -> addToBlacklist());
                cardMenu.add(blacklistMenuItem);

                removeMenuItem = new JMenuItem(i18n.getMessage("device.card.button.remove"));
                removeMenuItem.addActionListener(_ -> removeVolume());
                cardMenu.add(removeMenuItem);

                updateButtonEnabled();
            }

            moreButton = new JButton("⋮");
            moreButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            moreButton.setToolTipText(i18n.getMessage("device.card.button.more"));
            moreButton.setFocusPainted(false);
            moreButton.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            moreButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            moreButton.addActionListener(e -> cardMenu.show(moreButton, 0, moreButton.getHeight()));

            rightPanel.add(checkBox, BorderLayout.WEST);
            rightPanel.add(moreButton, BorderLayout.EAST);

            add(infoPanel, BorderLayout.CENTER);
            add(rightPanel, BorderLayout.EAST);
        }

        private String getStorageInfoCompact() {
            if (volume.getFileStore() == null) return "?";
            try {
                long total = volume.getFileStore().getTotalSpace();
                return SizeFormatter.format(total);
            } catch (IOException e) {
                return "?";
            }
        }

        private String getFsType() {
            if (volume.getFileStore() == null) return i18n.getMessage("device.card.unknown");
            return volume.getFileStore().type();
        }

        private String getStorageInfo() {
            if (volume.getFileStore() == null) return i18n.getMessage("device.card.unknown");
            try {
                long total = volume.getFileStore().getTotalSpace();
                long usable = volume.getFileStore().getUsableSpace();
                long used = total - usable;
                double pct = total > 0 ? (used * 100.0 / total) : 0;
                return String.format("%s / %s (%s: %s, %.1f%%)",
                    SizeFormatter.format(used), SizeFormatter.format(total),
                    i18n.getMessage("device.card.volume.none"), SizeFormatter.format(usable), pct);
            } catch (IOException e) {
                return i18n.getMessage("device.card.unavailable");
            }
        }

        private String getToggleButtonText() {
            return volume.getState() == Volume.VolumeState.DISABLED
                ? i18n.getMessage("device.card.button.enable")
                : i18n.getMessage("device.card.button.disable");
        }

        private String getLocalizedState(Volume.VolumeState state) {
            return switch (state) {
                case OFFLINE -> i18n.getMessage("device.state.offline");
                case UNAVAILABLE -> i18n.getMessage("device.state.unavailable");
                case IDLE -> i18n.getMessage("device.state.idle");
                case DISABLED -> i18n.getMessage("device.state.disabled");
                case EJECTING -> i18n.getMessage("device.state.ejecting");
            };
        }

        private void toggleVolume() {
            if (volume.getState() == Volume.VolumeState.OFFLINE) return;
            if (volume.getState() == Volume.VolumeState.DISABLED) {
                deviceManager.enable(volume);
            } else {
                deviceManager.disable(volume);
            }
        }

        private void addToBlacklist() {
            String sn = volume.getSerialNumber();
            String path = volume.getState() == Volume.VolumeState.OFFLINE ? "?" : volume.getRootPath().toString();
            int confirm = JOptionPane.showConfirmDialog(parentFrame,
                i18n.getMessage("device.card.blacklist.confirm", path, sn),
                i18n.getMessage("device.card.blacklist.confirm.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                ConfigManager.getInstance().addToDeviceBlacklistBySerial(sn);
                JOptionPane.showMessageDialog(parentFrame,
                    i18n.getMessage("device.card.blacklist.success"),
                    i18n.getMessage("device.card.blacklist.success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void removeVolume() {
            String sn = volume.getSerialNumber();
            String path = volume.getState() == Volume.VolumeState.OFFLINE
                ? i18n.getMessage("device.card.offline") : volume.getRootPath().toString();
            int confirm = JOptionPane.showConfirmDialog(parentFrame,
                i18n.getMessage("device.remove.confirm", path, sn),
                i18n.getMessage("device.remove.confirm.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                deviceManager.remove(volume);
                JOptionPane.showMessageDialog(parentFrame,
                    i18n.getMessage("device.remove.success"),
                    i18n.getMessage("device.remove.success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void showDetailDialog() {
            JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
            p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            p.add(new JLabel(i18n.getMessage("device.card.detail.path") + ": " + volume.getRootPath()));
            p.add(new JLabel(i18n.getMessage("device.card.detail.serial") + ": " + volume.getSerialNumber()));
            p.add(new JLabel(i18n.getMessage("device.card.detail.fs") + ": " + getFsType()));
            p.add(new JLabel(i18n.getMessage("device.card.detail.state") + ": " + volume.getState()));
            p.add(new JLabel(i18n.getMessage("device.card.detail.ghost") + ": " +
                (volume.getState() == Volume.VolumeState.OFFLINE
                    ? i18n.getMessage("device.card.detail.yes")
                    : i18n.getMessage("device.card.detail.no"))));
            p.add(new JLabel(i18n.getMessage("device.card.detail.storage") + ": " + getStorageInfo()));

            Device dev = deviceManager.getDeviceBySerial(volume.getSerialNumber());
            if (dev != null) {
                p.add(new JLabel("VID: " + (dev.getVid() != null ? dev.getVid() : "-")));
                p.add(new JLabel("PID: " + (dev.getPid() != null ? dev.getPid() : "-")));
            }

            if (volume.getFileStore() != null) {
                try {
                    p.add(new JLabel(i18n.getMessage("device.card.detail.volumeName") + ": " + volume.getFileStore().name()));
                    p.add(new JLabel(i18n.getMessage("device.card.detail.volumeType") + ": " + volume.getFileStore().type()));
                } catch (Exception e) {
                    p.add(new JLabel(i18n.getMessage("device.card.detail.volumeName") + ": " + i18n.getMessage("device.card.detail.failed")));
                }
            }

            JOptionPane.showMessageDialog(parentFrame, p,
                i18n.getMessage("device.card.detail.title"), JOptionPane.INFORMATION_MESSAGE);
        }

        public JCheckBox getCheckBox() {
            return checkBox;
        }

        private void updateButtonEnabled() {
            boolean enabled = volume.getState() != Volume.VolumeState.OFFLINE;
            checkBox.setEnabled(enabled);
            if (toggleMenuItem != null) toggleMenuItem.setEnabled(enabled);
        }

        public void refreshVolumeInfo() {
            SwingUtilities.invokeLater(() -> {
                if (volume.getState() == Volume.VolumeState.OFFLINE || volume.getRootPath() == null) {
                    pathLabel.setText(i18n.getMessage("device.card.offline"));
                } else {
                    pathLabel.setText(volume.getRootPath().toString());
                }
                storageLabel.setText(getStorageInfoCompact());

                Volume.VolumeState cs = volume.getState();
                stateLabel.setText(getLocalizedState(cs));
                stateLabel.setForeground(Color.WHITE);
                stateBadge.setBackground(ThemeManager.getStateColor(cs));
                stateBadge.setOpaque(true);
                stateBadge.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));

                if (toggleMenuItem != null) toggleMenuItem.setText(getToggleButtonText());
                updateButtonEnabled();
            });
        }

        public void updateVolume(Volume newVolume) {
            this.volume = newVolume;
            refreshVolumeInfo();
        }

        public void refreshLanguage() {
            SwingUtilities.invokeLater(() -> {
                detailMenuItem.setText(i18n.getMessage("device.card.button.details"));
                moreButton.setToolTipText(i18n.getMessage("device.card.button.more"));
                if (toggleMenuItem != null) toggleMenuItem.setText(getToggleButtonText());
                if (blacklistMenuItem != null) blacklistMenuItem.setText(i18n.getMessage("device.card.button.blacklist"));
                if (removeMenuItem != null) removeMenuItem.setText(i18n.getMessage("device.card.button.remove"));
                refreshVolumeInfo();
            });
        }
    }
}
