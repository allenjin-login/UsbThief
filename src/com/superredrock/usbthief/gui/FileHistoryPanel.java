package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.index.FileHistoryRecord;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

public class FileHistoryPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(FileHistoryPanel.class.getName());
    private final I18NManager i18n = I18NManager.getInstance();
    private final JTable historyTable;
    private final HistoryTableModel tableModel;
    private final TableRowSorter<HistoryTableModel> sorter;
    private final JTextField searchField;
    private JLabel searchLabel;
    private JButton searchButton;
    private JButton clearButton;
    private final JLabel countLabel;
    private final JScrollPane scrollPane;

    public FileHistoryPanel() {
        setLayout(new BorderLayout());

        // Read max entries configuration
        int maxEntries = ConfigManager.getInstance().get(ConfigSchema.FILE_HISTORY_MAX_ENTRIES);

        // Table model
        tableModel = new HistoryTableModel(maxEntries);
        historyTable = new JTable(tableModel);

        // Custom renderer for file size
        historyTable.setDefaultRenderer(Long.class, new FileSizeRenderer());

        // Sorter
        sorter = new TableRowSorter<>(tableModel);
        historyTable.setRowSorter(sorter);

        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.setToolTipText(i18n.getMessage("filehistory.search.tooltip"));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });

        searchButton = new JButton(i18n.getMessage("filehistory.search.button"));
        searchButton.addActionListener(_ -> applyFilter());

        clearButton = new JButton(i18n.getMessage("filehistory.clear.button"));
        clearButton.addActionListener(_ -> {
            searchField.setText("");
            applyFilter();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(searchButton);

        searchLabel = new JLabel(i18n.getMessage("filehistory.search.label"));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Table with scroll pane
        scrollPane = new JScrollPane(historyTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new TitledBorder(i18n.getMessage("filehistory.border")));

        // Count label
        countLabel = new JLabel(String.format(i18n.getMessage("filehistory.count"), 0, 0));

        // Layout
        add(searchPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register event listeners
        registerEventListeners();
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();
        eventBus.register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (event.isFailure()) {
            SwingUtilities.invokeLater(() -> {
                String fileName = event.sourcePath().getFileName() != null
                        ? event.sourcePath().getFileName().toString()
                        : event.sourcePath().toString();
                String sourcePath = event.sourcePath().toString();
                String destPath = event.destinationPath() != null ? event.destinationPath().toString() : "N/A";

                FileHistoryRecord record = new FileHistoryRecord(
                        fileName, sourcePath, destPath,
                        event.fileSize(), event.bytesCopied());
                tableModel.addRecordIfNotExists(record);
                updateCountLabel();
            });
        }
    }

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
        updateCountLabel();
    }

    private void updateCountLabel() {
        int filteredCount = historyTable.getRowCount();
        int totalCount = tableModel.getRowCount();
        countLabel.setText(String.format(i18n.getMessage("filehistory.count"), filteredCount, totalCount));
    }

    public void refreshLanguage() {
        logger.info("FileHistoryPanel.refreshLanguage() called");
        SwingUtilities.invokeLater(() -> {
            scrollPane.setBorder(new TitledBorder(i18n.getMessage("filehistory.border")));
            searchLabel.setText(i18n.getMessage("filehistory.search.label"));
            searchField.setToolTipText(i18n.getMessage("filehistory.search.tooltip"));
            searchButton.setText(i18n.getMessage("filehistory.search.button"));
            clearButton.setText(i18n.getMessage("filehistory.clear.button"));
            countLabel.setText(String.format(i18n.getMessage("filehistory.count"), historyTable.getRowCount(), tableModel.getRowCount()));
            tableModel.fireTableStructureChanged();
            logger.info("FileHistoryPanel language refreshed");
        });
    }

    private static class HistoryTableModel extends AbstractTableModel {

        private final List<FileHistoryRecord> records = new ArrayList<>();
        private final int maxEntries;

        private final Class<?>[] columnTypes = {String.class, String.class, String.class, Long.class, Long.class, String.class};

        public HistoryTableModel(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public void addRecord(FileHistoryRecord record) {
            records.add(record);
            fireTableRowsInserted(records.size() - 1, records.size() - 1);
            evictIfNeeded();
        }

        public void addRecordIfNotExists(FileHistoryRecord record) {
            for (FileHistoryRecord existing : records) {
                if (existing.fileName().equals(record.fileName()) &&
                        existing.sourcePath().equals(record.sourcePath()) &&
                        existing.timestamp().equals(record.timestamp())) {
                    return;
                }
            }
            addRecord(record);
        }

        private void evictIfNeeded() {
            while (records.size() > maxEntries) {
                records.removeFirst();
                fireTableRowsDeleted(0, 0);
            }
        }

        public void clear() {
            int size = records.size();
            records.clear();
            if (size > 0) {
                fireTableRowsDeleted(0, size - 1);
            }
        }

        public List<FileHistoryRecord> getRecords() {
            return new ArrayList<>(records);
        }

        @Override
        public int getRowCount() {
            return records.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18NManager.getInstance().getMessage("filehistory.table.fileName");
                case 1 -> I18NManager.getInstance().getMessage("filehistory.table.sourcePath");
                case 2 -> I18NManager.getInstance().getMessage("filehistory.table.destPath");
                case 3 -> I18NManager.getInstance().getMessage("filehistory.table.fileSize");
                case 4 -> I18NManager.getInstance().getMessage("filehistory.table.bytesCopied");
                case 5 -> I18NManager.getInstance().getMessage("filehistory.table.timestamp");
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnTypes[columnIndex];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileHistoryRecord record = records.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> record.fileName();
                case 1 -> record.sourcePath();
                case 2 -> record.destPath();
                case 3 -> record.fileSize();
                case 4 -> record.bytesCopied();
                case 5 -> record.timestamp();
                default -> null;
            };
        }
    }

    private static class FileSizeRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof Long fileSize) {
                setText(SizeFormatter.format(fileSize));
                setHorizontalAlignment(SwingConstants.RIGHT);
            }

            return this;
        }
    }
}
