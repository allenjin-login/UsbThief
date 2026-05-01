package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.components.EmptyStatePanel;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class DeviceInfoDialog extends JDialog implements I18nManager.LocaleChangeListener {

    private final I18nManager i18n = I18nManager.getInstance();
    private final DeviceManager deviceManager = DeviceManager.getInstance();
    private final Statistics stats = Statistics.getInstance();

    private final JPanel deviceListPanel;
    private final JLabel globalFilesLabel;
    private final JLabel globalSizeLabel;
    private final JLabel globalErrorsLabel;
    private final JLabel globalDevicesLabel;

    private final Timer updateTimer;
    private final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public DeviceInfoDialog(JFrame owner) {
        super(owner, I18nManager.getInstance().getMessage("deviceinfo.title"), false);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setSize(620, 680);
        setLocationRelativeTo(owner);

        globalFilesLabel = new JLabel("0", SwingConstants.CENTER);
        globalSizeLabel = new JLabel("0 B", SwingConstants.CENTER);
        globalErrorsLabel = new JLabel("0", SwingConstants.CENTER);
        globalDevicesLabel = new JLabel("0", SwingConstants.CENTER);

        JPanel globalBar = createGlobalBar();

        deviceListPanel = new JPanel();
        deviceListPanel.setLayout(new BoxLayout(deviceListPanel, BoxLayout.Y_AXIS));

        refreshDeviceList();
        registerListeners();

        setLayout(new BorderLayout());
        add(globalBar, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(deviceListPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        add(scrollPane, BorderLayout.CENTER);

        updateTimer = new Timer(2000, _ -> {
            updateGlobalBar();
            refreshDeviceList();
        });

        i18n.addLocaleChangeListener(this);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowDeactivated(java.awt.event.WindowEvent e) {
                updateTimer.stop();
            }

            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                updateGlobalBar();
                refreshDeviceList();
                updateTimer.start();
            }
        });
    }

    private JPanel createGlobalBar() {
        JPanel bar = new JPanel(new GridLayout(1, 4, 2, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_COLOR),
            new EmptyBorder(6, 8, 6, 8)
        ));

        Font valFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        Font lblFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);

        for (Object[] entry : new Object[][]{
            {i18n.getMessage("deviceinfo.global.files"), globalFilesLabel},
            {i18n.getMessage("deviceinfo.global.size"), globalSizeLabel},
            {i18n.getMessage("deviceinfo.global.errors"), globalErrorsLabel},
            {i18n.getMessage("deviceinfo.global.devices"), globalDevicesLabel}
        }) {
            JPanel cell = new JPanel(new BorderLayout(2, 0));
            cell.setOpaque(false);
            JLabel lbl = new JLabel((String) entry[0], SwingConstants.CENTER);
            lbl.setFont(lblFont);
            lbl.setForeground(ThemeManager.TEXT_MUTED);
            JLabel val = (JLabel) entry[1];
            val.setFont(valFont);
            cell.add(lbl, BorderLayout.NORTH);
            cell.add(val, BorderLayout.CENTER);
            bar.add(cell);
        }

        return bar;
    }

    private void registerListeners() {
        EventBus bus = EventBus.getInstance();
        bus.register(VolumeInsertedEvent.class, e -> SwingUtilities.invokeLater(this::refreshDeviceList));
        bus.register(VolumeRemovedEvent.class, e -> SwingUtilities.invokeLater(this::refreshDeviceList));
        bus.register(VolumeStateChangedEvent.class, e -> SwingUtilities.invokeLater(this::refreshDeviceList));
        bus.register(DeviceArrivalEvent.class, e -> SwingUtilities.invokeLater(this::refreshDeviceList));
        bus.register(DeviceRemovalEvent.class, e -> SwingUtilities.invokeLater(this::refreshDeviceList));
    }

    private void refreshDeviceList() {
        deviceListPanel.removeAll();

        Map<String, Statistics.DeviceHistoryEntry> allHistory = stats.getAllDeviceHistory();

        if (allHistory.isEmpty()) {
            EmptyStatePanel emptyStatePanel = new EmptyStatePanel(
                    "\uD83D\uDD0C",
                    i18n.getMessage("deviceinfo.empty.title"),
                    i18n.getMessage("deviceinfo.empty.description")
            );
            deviceListPanel.add(emptyStatePanel);
        } else {
            List<Statistics.DeviceHistoryEntry> liveDevices = new ArrayList<>();
            List<Statistics.DeviceHistoryEntry> historicalDevices = new ArrayList<>();

            for (Statistics.DeviceHistoryEntry entry : allHistory.values()) {
                if (stats.isDeviceLive(entry.getSerialNumber())) {
                    liveDevices.add(entry);
                } else {
                    historicalDevices.add(entry);
                }
            }

            liveDevices.sort((a, b) -> Long.compare(b.getLastSeenTime(), a.getLastSeenTime()));
            historicalDevices.sort((a, b) -> Long.compare(b.getLastSeenTime(), a.getLastSeenTime()));

            if (!liveDevices.isEmpty()) {
                deviceListPanel.add(createSectionHeader(i18n.getMessage("deviceinfo.section.live"), true));
                deviceListPanel.add(Box.createVerticalStrut(4));
                for (Statistics.DeviceHistoryEntry entry : liveDevices) {
                    deviceListPanel.add(createDeviceCard(entry, true));
                    deviceListPanel.add(Box.createVerticalStrut(6));
                }
            }

            if (!historicalDevices.isEmpty()) {
                if (!liveDevices.isEmpty()) {
                    deviceListPanel.add(Box.createVerticalStrut(4));
                }
                deviceListPanel.add(createSectionHeader(i18n.getMessage("deviceinfo.section.history"), false));
                deviceListPanel.add(Box.createVerticalStrut(4));
                for (Statistics.DeviceHistoryEntry entry : historicalDevices) {
                    deviceListPanel.add(createDeviceCard(entry, false));
                    deviceListPanel.add(Box.createVerticalStrut(6));
                }
            }
        }

        deviceListPanel.revalidate();
        deviceListPanel.repaint();
    }

    private void updateGlobalBar() {
        SwingUtilities.invokeLater(() -> {
            globalFilesLabel.setText(String.valueOf(stats.getTotalFilesCopied()));
            globalSizeLabel.setText(SizeFormatter.format(stats.getTotalBytesCopied()));
            globalErrorsLabel.setText(String.valueOf(stats.getTotalErrors()));
            globalDevicesLabel.setText(String.valueOf(stats.getCopiedDeviceCount()));
        });
    }

    private JPanel createSectionHeader(String title, boolean isLive) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(8, 4, 4, 4));

        JLabel label = new JLabel(title);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        label.setForeground(isLive ? ThemeManager.ACCENT_SUCCESS : ThemeManager.TEXT_MUTED);
        header.add(label, BorderLayout.WEST);

        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
        separator.setForeground(ThemeManager.BORDER_COLOR);
        header.add(separator, BorderLayout.CENTER);

        return header;
    }

    private JPanel createDeviceCard(Statistics.DeviceHistoryEntry historyEntry, boolean isLive) {
        Volume volume = isLive ? deviceManager.getVolumeBySerial(historyEntry.getSerialNumber()) : null;
        Statistics.VolumeStats vs = stats.getVolumeStats(historyEntry.getSerialNumber());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        Color borderColor = isLive ? ThemeManager.ACCENT_SUCCESS : ThemeManager.BORDER_COLOR;
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, isLive ? 2 : 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        card.setBackground(ThemeManager.CARD_BACKGROUND);
        card.setOpaque(true);

        // Header
        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setOpaque(false);

        StringBuilder idBuilder = new StringBuilder();
        if (volume != null && !volume.getDriveLetter().isEmpty()) {
            idBuilder.append(volume.getDriveLetter()).append(" ");
        }
        String totalStr = volume != null ? formatTotalSpace(volume) : "";
        JLabel idLabel = new JLabel(idBuilder + historyEntry.getSerialNumber() + "  " + totalStr);
        idLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        idLabel.setForeground(isLive ? ThemeManager.TEXT_PRIMARY : ThemeManager.TEXT_SECONDARY);
        headerRow.add(idLabel, BorderLayout.WEST);

        if (isLive && volume != null) {
            JPanel stateBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            stateBadge.setOpaque(true);
            stateBadge.setBackground(ThemeManager.getStateColor(volume.getState()));
            JLabel stateText = new JLabel(getLocalizedState(volume.getState()));
            stateText.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            stateText.setForeground(Color.WHITE);
            stateBadge.add(stateText);
            stateBadge.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
            headerRow.add(stateBadge, BorderLayout.EAST);
        } else {
            JLabel offlineLabel = new JLabel(i18n.getMessage("deviceinfo.card.offline"));
            offlineLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            offlineLabel.setForeground(ThemeManager.TEXT_MUTED);
            headerRow.add(offlineLabel, BorderLayout.EAST);
        }
        card.add(headerRow);

        // Storage bar (live only)
        if (volume != null) {
            JProgressBar storageBar = createStorageBar(volume);
            if (storageBar != null) {
                JPanel barPanel = new JPanel(new BorderLayout(4, 0));
                barPanel.setOpaque(false);
                barPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
                barPanel.add(storageBar, BorderLayout.CENTER);
                JLabel storageText = new JLabel(getStorageDetail(volume));
                storageText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                storageText.setForeground(ThemeManager.TEXT_SECONDARY);
                barPanel.add(storageText, BorderLayout.EAST);
                card.add(barPanel);
            }
        }

        // Info grid
        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 12, 2));
        infoGrid.setOpaque(false);
        infoGrid.setBorder(new EmptyBorder(6, 0, 0, 0));

        if (volume != null) {
            addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.fs"), getFsType(volume));
        }
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.serial"), historyEntry.getSerialNumber());

        if (historyEntry.getVid() != null && !historyEntry.getVid().isEmpty()) {
            addInfoRow(infoGrid, "VID", historyEntry.getVid());
        }
        if (historyEntry.getPid() != null && !historyEntry.getPid().isEmpty()) {
            addInfoRow(infoGrid, "PID", historyEntry.getPid());
        }

        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.insertions"), String.valueOf(historyEntry.getInsertionCount()));
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.firstSeen"), formatTimestamp(historyEntry.getFirstSeenTime()));
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.lastSeen"), formatTimestamp(historyEntry.getLastSeenTime()));

        if (volume != null) {
            long elapsed = System.currentTimeMillis() - vs.getFirstSeenTime();
            addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.connected"), formatDuration(elapsed));
        }

        card.add(infoGrid);

        // Copy stats
        if (vs.getFilesCopied() > 0 || vs.getErrors() > 0) {
            JPanel statsRow = new JPanel(new GridLayout(1, 4, 8, 0));
            statsRow.setOpaque(false);
            statsRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.BORDER_COLOR),
                new EmptyBorder(6, 0, 0, 0)
            ));

            addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.files"), String.valueOf(vs.getFilesCopied()));
            addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.bytes"), SizeFormatter.format(vs.getBytesCopied()));
            addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.errors"), String.valueOf(vs.getErrors()));

            Map<String, Long> exts = vs.getExtensionCounts();
            if (!exts.isEmpty()) {
                String topExts = exts.entrySet().stream()
                    .limit(3)
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
                addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.types"), topExts);
            } else {
                addStatCell(statsRow, "", "");
            }

            card.add(statsRow);
        }

        // Timeline
        Map<Long, String> timeline = historyEntry.getTimelineLog();
        if (!timeline.isEmpty()) {
            JPanel timelineRow = new JPanel(new BorderLayout(4, 0));
            timelineRow.setOpaque(false);
            timelineRow.setBorder(new EmptyBorder(4, 0, 0, 0));

            JButton expandBtn = new JButton(i18n.getMessage("deviceinfo.timeline.show") + " (" + timeline.size() + ")");
            expandBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            expandBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            expandBtn.setContentAreaFilled(false);
            expandBtn.setForeground(ThemeManager.TEXT_MUTED);

            JPanel timelinePanel = new JPanel();
            timelinePanel.setLayout(new BoxLayout(timelinePanel, BoxLayout.Y_AXIS));
            timelinePanel.setVisible(false);

            expandBtn.addActionListener(_ -> {
                boolean visible = !timelinePanel.isVisible();
                timelinePanel.setVisible(visible);
                expandBtn.setText(visible
                    ? i18n.getMessage("deviceinfo.timeline.hide")
                    : i18n.getMessage("deviceinfo.timeline.show") + " (" + timeline.size() + ")");
                deviceListPanel.revalidate();
            });

            List<Map.Entry<Long, String>> sorted = timeline.entrySet().stream()
                .sorted(Map.Entry.<Long, String>comparingByKey().reversed())
                .limit(20)
                .toList();

            for (var te : sorted) {
                String eventKey = "deviceinfo.timeline." + te.getValue().toLowerCase();
                JLabel entryLabel = new JLabel("  " + formatTimestamp(te.getKey()) + "  -  " + i18n.getMessage(eventKey));
                entryLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                entryLabel.setForeground(te.getValue().equals("CONNECTED")
                    ? ThemeManager.ACCENT_SUCCESS
                    : ThemeManager.TEXT_MUTED);
                timelinePanel.add(entryLabel);
            }

            timelineRow.add(expandBtn, BorderLayout.WEST);
            timelineRow.add(timelinePanel, BorderLayout.CENTER);
            card.add(timelineRow);
        }

        return card;
    }

    private void addInfoRow(JPanel grid, String label, String value) {
        JLabel lbl = new JLabel(label + ": ");
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        grid.add(lbl);

        JLabel val = new JLabel(value);
        val.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        val.setForeground(ThemeManager.TEXT_SECONDARY);
        grid.add(val);
    }

    private void addStatCell(JPanel panel, String label, String value) {
        JPanel cell = new JPanel(new BorderLayout(2, 0));
        cell.setOpaque(false);
        JLabel lbl = new JLabel(label, SwingConstants.CENTER);
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        cell.add(lbl, BorderLayout.NORTH);
        cell.add(val, BorderLayout.CENTER);
        panel.add(cell);
    }

    private JProgressBar createStorageBar(Volume volume) {
        if (volume.getFileStore() == null) return null;
        try {
            long total = volume.getFileStore().getTotalSpace();
            long usable = volume.getFileStore().getUsableSpace();
            long used = total - usable;
            int pct = total > 0 ? (int) (used * 100 / total) : 0;

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(pct);
            bar.setStringPainted(false);
            bar.setPreferredSize(new Dimension(200, 12));

            if (pct > 90) bar.setForeground(ThemeManager.ACCENT_ERROR);
            else if (pct > 75) bar.setForeground(ThemeManager.ACCENT_WARNING);
            else bar.setForeground(ThemeManager.ACCENT_SUCCESS);

            return bar;
        } catch (IOException e) {
            return null;
        }
    }

    private String getStorageDetail(Volume volume) {
        if (volume.getFileStore() == null) return i18n.getMessage("deviceinfo.card.unknown");
        try {
            long total = volume.getFileStore().getTotalSpace();
            long usable = volume.getFileStore().getUsableSpace();
            long used = total - usable;
            return SizeFormatter.format(used) + " / " + SizeFormatter.format(total);
        } catch (IOException e) {
            return i18n.getMessage("deviceinfo.card.unknown");
        }
    }

    private String formatTotalSpace(Volume volume) {
        if (volume.getFileStore() == null) return "";
        try {
            return "(" + SizeFormatter.format(volume.getFileStore().getTotalSpace()) + ")";
        } catch (IOException e) {
            return "";
        }
    }

    private String getFsType(Volume volume) {
        if (volume.getFileStore() == null) return i18n.getMessage("deviceinfo.card.unknown");
        return volume.getFileStore().type();
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

    private String formatTimestamp(long ms) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        return dt.format(timestampFormatter);
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        SwingUtilities.invokeLater(this::refreshLanguage);
    }

    public void refreshLanguage() {
        setTitle(i18n.getMessage("deviceinfo.title"));
        refreshDeviceList();
    }
}
