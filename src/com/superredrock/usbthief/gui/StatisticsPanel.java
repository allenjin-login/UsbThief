package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsPanel extends JPanel implements I18NManager.LocaleChangeListener {
    private final I18NManager i18n = I18NManager.getInstance();
    private final Statistics stats = Statistics.getInstance();

    private JLabel totalFilesLabel;
    private JLabel totalSizeLabel;
    private JLabel totalErrorsLabel;
    private JLabel totalFoldersLabel;
    private JLabel totalDevicesLabel;

    private JLabel discoveredSizeLabel;
    private JLabel copiedSizeLabel;
    private JProgressBar progressBar;

    private ExtensionTableModel extensionModel;

    private Timer updateTimer;

    private JPanel persistentPanel;
    private JPanel sessionPanel;
    private JPanel extensionPanel;

    public StatisticsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBackground(ThemeManager.BACKGROUND_PRIMARY);
        setBorder(new EmptyBorder(15, 15, 15, 15));

        i18n.addLocaleChangeListener(this);

        persistentPanel = createPersistentPanel();
        sessionPanel = createSessionPanel();
        extensionPanel = createExtensionPanel();

        add(persistentPanel, BorderLayout.NORTH);
        add(sessionPanel, BorderLayout.CENTER);
        add(extensionPanel, BorderLayout.SOUTH);

        startUpdateTimer();
    }

    private JPanel createPersistentPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 12, 8));
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new TitledBorder(i18n.getMessage("stats.persistent.border")),
            new EmptyBorder(10, 10, 10, 10)
        ));

        totalFilesLabel = createStatLabel();
        totalSizeLabel = createStatLabel();
        totalErrorsLabel = createStatLabel();
        totalFoldersLabel = createStatLabel();
        totalDevicesLabel = createStatLabel();

        panel.add(createStatCard("📊", totalFilesLabel));
        panel.add(createStatCard("💾", totalSizeLabel));
        panel.add(createStatCard("📁", totalFoldersLabel));
        panel.add(createStatCard("🔌", totalDevicesLabel));
        panel.add(createStatCard("❌", totalErrorsLabel));
        panel.add(Box.createGlue());

        return panel;
    }

    private JLabel createStatLabel() {
        JLabel label = new JLabel();
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        label.setForeground(ThemeManager.TEXT_PRIMARY);
        return label;
    }

    private JPanel createStatCard(String icon, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(ThemeManager.CARD_BACKGROUND);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1, true),
            new EmptyBorder(12, 16, 12, 16)
        ));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        iconLabel.setForeground(ThemeManager.ACCENT_PRIMARY);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createSessionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 10));
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new TitledBorder(i18n.getMessage("stats.session.border")),
            new EmptyBorder(10, 10, 10, 10)
        ));

        discoveredSizeLabel = new JLabel();
        discoveredSizeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        discoveredSizeLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        
        copiedSizeLabel = new JLabel();
        copiedSizeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        copiedSizeLabel.setForeground(ThemeManager.TEXT_PRIMARY);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(0, 24));
        progressBar.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

        JPanel labelsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        labelsPanel.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        labelsPanel.add(discoveredSizeLabel);
        labelsPanel.add(copiedSizeLabel);

        panel.add(labelsPanel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createExtensionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new TitledBorder(i18n.getMessage("stats.extension.border")),
            new EmptyBorder(10, 10, 10, 10)
        ));

        extensionModel = new ExtensionTableModel();
        JTable extensionTable = new JTable(extensionModel);
        extensionTable.setAutoCreateRowSorter(true);
        extensionTable.setRowHeight(24);
        extensionTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(extensionTable);
        scrollPane.setPreferredSize(new Dimension(200, 150));
        scrollPane.setBackground(ThemeManager.CARD_BACKGROUND);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, _ -> updateDisplay());
        updateTimer.start();
    }

    private void updateDisplay() {
        SwingUtilities.invokeLater(() -> {
            totalFilesLabel.setText(i18n.getMessage("stats.totalFiles", stats.getTotalFilesCopied()));
            totalSizeLabel.setText(i18n.getMessage("stats.totalSize", SizeFormatter.format(stats.getTotalBytesCopied())));
            totalFoldersLabel.setText(i18n.getMessage("stats.totalFolders", stats.getTotalFoldersCopied()));
            totalDevicesLabel.setText(i18n.getMessage("stats.totalDevices", stats.getTotalDevicesCopied()));
            totalErrorsLabel.setText(i18n.getMessage("stats.totalErrors", stats.getTotalErrors()));

            discoveredSizeLabel.setText(i18n.getMessage("stats.discoveredSize", SizeFormatter.format(stats.getSessionBytesDiscovered())));
            copiedSizeLabel.setText(i18n.getMessage("stats.copiedSize", SizeFormatter.format(stats.getSessionBytesCopied())));

            int progress = stats.getProgressPercentage();
            progressBar.setValue(progress);
            progressBar.setString(progress + "%");

            extensionModel.updateData(stats.getExtensionCounts());
        });
    }

    public void stop() {
        if (updateTimer != null) updateTimer.stop();
    }

    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            ((TitledBorder) persistentPanel.getBorder()).setTitle(i18n.getMessage("stats.persistent.border"));
            ((TitledBorder) sessionPanel.getBorder()).setTitle(i18n.getMessage("stats.session.border"));
            ((TitledBorder) extensionPanel.getBorder()).setTitle(i18n.getMessage("stats.extension.border"));
            extensionModel.fireTableStructureChanged();
            updateDisplay();
            revalidate();
            repaint();
        });
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        refreshLanguage();
    }

    private class ExtensionTableModel extends AbstractTableModel {
        private List<Map.Entry<String, Long>> data = new ArrayList<>();

        void updateData(Map<String, Long> counts) {
            data = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(20)
                .toList();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return data.size(); }
        @Override
        public int getColumnCount() { return 2; }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? i18n.getMessage("stats.extension.type") : i18n.getMessage("stats.extension.count");
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= data.size()) return "";
            return columnIndex == 0 ? data.get(rowIndex).getKey() : data.get(rowIndex).getValue();
        }
    }
}
