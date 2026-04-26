package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.gui.components.EmptyStatePanel;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public class DeviceInfoDialog extends JDialog implements I18NManager.LocaleChangeListener {

    private final I18NManager i18n = I18NManager.getInstance();
    private final DeviceManager deviceManager = DeviceManager.getInstance();
    private final Statistics stats = Statistics.getInstance();

    private final JPanel volumeCardsPanel;
    private final JLabel globalFilesLabel;
    private final JLabel globalSizeLabel;
    private final JLabel globalErrorsLabel;
    private final JLabel globalDevicesLabel;

    private final Timer updateTimer;

    public DeviceInfoDialog(JFrame owner) {
        super(owner, I18NManager.getInstance().getMessage("deviceinfo.title"), false);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setSize(560, 620);
        setLocationRelativeTo(owner);

        globalFilesLabel = new JLabel("0", SwingConstants.CENTER);
        globalSizeLabel = new JLabel("0 B", SwingConstants.CENTER);
        globalErrorsLabel = new JLabel("0", SwingConstants.CENTER);
        globalDevicesLabel = new JLabel("0", SwingConstants.CENTER);

        JPanel globalBar = createGlobalBar();

        volumeCardsPanel = new JPanel();
        volumeCardsPanel.setLayout(new BoxLayout(volumeCardsPanel, BoxLayout.Y_AXIS));

        refreshVolumes();
        registerListeners();

        setLayout(new BorderLayout());
        add(globalBar, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(volumeCardsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        add(scrollPane, BorderLayout.CENTER);

        updateTimer = new Timer(2000, _ -> updateGlobalBar());
        updateTimer.start();

        i18n.addLocaleChangeListener(this);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowDeactivated(java.awt.event.WindowEvent e) {
                updateTimer.stop();
            }

            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                updateGlobalBar();
                refreshVolumes();
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
        bus.register(VolumeInsertedEvent.class, e -> SwingUtilities.invokeLater(this::refreshVolumes));
        bus.register(VolumeRemovedEvent.class, e -> SwingUtilities.invokeLater(this::refreshVolumes));
        bus.register(VolumeStateChangedEvent.class, e -> SwingUtilities.invokeLater(this::refreshVolumes));
    }

    private void refreshVolumes() {
        volumeCardsPanel.removeAll();
        Collection<Volume> volumes = deviceManager.getAllVolumes();

        if (volumes.isEmpty()) {
            EmptyStatePanel emptyStatePanel = new EmptyStatePanel(
                    "\uD83D\uDD0C",
                    i18n.getMessage("deviceinfo.empty.title"),
                    i18n.getMessage("deviceinfo.empty.description")
            );
            volumeCardsPanel.add(emptyStatePanel);
        } else {
            for (Volume volume : volumes) {
                volumeCardsPanel.add(createVolumeCard(volume));
                volumeCardsPanel.add(Box.createVerticalStrut(6));
            }
        }

        volumeCardsPanel.revalidate();
        volumeCardsPanel.repaint();
    }

    private void updateGlobalBar() {
        SwingUtilities.invokeLater(() -> {
            globalFilesLabel.setText(String.valueOf(stats.getTotalFilesCopied()));
            globalSizeLabel.setText(SizeFormatter.format(stats.getTotalBytesCopied()));
            globalErrorsLabel.setText(String.valueOf(stats.getTotalErrors()));
            globalDevicesLabel.setText(String.valueOf(stats.getCopiedDeviceCount()));
        });
    }

    private JPanel createVolumeCard(Volume volume) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        card.setBackground(ThemeManager.CARD_BACKGROUND);
        card.setOpaque(true);

        // Header: drive letter + total size + state badge
        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setOpaque(false);

        String driveLabel = volume.getDriveLetter().isEmpty()
            ? volume.getRootPath().toString()
            : volume.getDriveLetter();
        String totalStr = formatTotalSpace(volume);
        JLabel driveLabelComp = new JLabel(driveLabel + "  " + totalStr);
        driveLabelComp.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        driveLabelComp.setForeground(ThemeManager.TEXT_PRIMARY);

        JPanel stateBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        stateBadge.setOpaque(true);
        stateBadge.setBackground(ThemeManager.getStateColor(volume.getState()));
        JLabel stateText = new JLabel(getLocalizedState(volume.getState()));
        stateText.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        stateText.setForeground(Color.WHITE);
        stateBadge.add(stateText);
        stateBadge.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));

        headerRow.add(driveLabelComp, BorderLayout.WEST);
        headerRow.add(stateBadge, BorderLayout.EAST);
        card.add(headerRow);

        // Storage progress bar
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

        // Info grid
        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 12, 2));
        infoGrid.setOpaque(false);
        infoGrid.setBorder(new EmptyBorder(6, 0, 0, 0));

        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.fs"), getFsType(volume));
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.serial"), volume.getSerialNumber());

        Device dev = deviceManager.getDeviceBySerial(volume.getSerialNumber());
        if (dev != null) {
            addInfoRow(infoGrid, "VID", dev.getVid() != null ? dev.getVid() : "-");
            addInfoRow(infoGrid, "PID", dev.getPid() != null ? dev.getPid() : "-");
        }

        Statistics.VolumeStats vs = stats.getVolumeStats(volume.getSerialNumber());
        long elapsed = System.currentTimeMillis() - vs.getFirstSeenTime();
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.connected"), formatDuration(elapsed));

        card.add(infoGrid);

        // Per-volume stats (only if there is activity)
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
        refreshVolumes();
    }
}
