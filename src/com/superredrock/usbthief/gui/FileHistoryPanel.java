package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.index.FileHistoryRecord;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

public class FileHistoryPanel extends JPanel {

    private final JTable historyTable;
    private final HistoryTableModel tableModel;
    private final TableRowSorter<HistoryTableModel> sorter;
    private final JTextField searchField;
    private final JLabel countLabel;
    private final int maxEntries;

    public FileHistoryPanel() {
        setLayout(new BorderLayout());

        // Read max entries configuration
        this.maxEntries = ConfigManager.getInstance().get(ConfigSchema.FILE_HISTORY_MAX_ENTRIES);

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
        searchField.setToolTipText("输入文件名进行筛选...");
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

        JButton searchButton = new JButton("搜索");
        searchButton.addActionListener(_ -> applyFilter());

        JButton clearButton = new JButton("清除");
        clearButton.addActionListener(_ -> {
            searchField.setText("");
            applyFilter();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(searchButton);

        searchPanel.add(new JLabel("搜索文件:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Table with scroll pane
        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new TitledBorder("文件历史"));

        // Count label
        countLabel = new JLabel("总计: 0 文件");

        // Layout
        add(searchPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register event listeners
        registerEventListeners();

        // Initialize with existing files from index
        initializeFromIndex();
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();
        eventBus.register(FileIndexedEvent.class, this::onFileIndexed);
        eventBus.register(IndexLoadedEvent.class, this::onIndexLoaded);
    }

    private void initializeFromIndex() {
        SwingUtilities.invokeLater(() -> {
            tableModel.clear();
            // Load history from index (apply max entries limit)
            List<FileHistoryRecord> history = QueueManager.index.getHistory();
            int startIndex = Math.max(0, history.size() - maxEntries);
            for (int i = startIndex; i < history.size(); i++) {
                tableModel.addRecord(history.get(i));
            }
            updateCountLabel();
        });
    }

    private void onFileIndexed(FileIndexedEvent event) {
        SwingUtilities.invokeLater(() -> {
            // Find the corresponding history record from index
            List<FileHistoryRecord> history = QueueManager.index.getHistory();
            if (!history.isEmpty()) {
                // Get the last record (most recently added)
                FileHistoryRecord lastRecord = history.getLast();
                // Check if this record matches the event
                if (lastRecord.fileName().equals(event.filePath().getFileName().toString()) &&
                        lastRecord.fileSize() == event.fileSize()) {
                    // Only add if not already in table
                    tableModel.addRecordIfNotExists(lastRecord);
                    updateCountLabel();
                }
            }
        });
    }


    private void onIndexLoaded(IndexLoadedEvent event) {
        // Reload history when index is loaded
        SwingUtilities.invokeLater(this::initializeFromIndex);
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
        countLabel.setText(String.format("显示: %d / 总计: %d 文件", filteredCount, totalCount));
    }

    private static class HistoryTableModel extends AbstractTableModel {

        private final List<FileHistoryRecord> records = new ArrayList<>();
        private final int maxEntries;

        private final String[] columnNames = {"文件名", "路径", "大小", "复制时间"};
        private final Class<?>[] columnTypes = {String.class, String.class, Long.class, String.class};

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
                        existing.filePath().equals(record.filePath())) {
                    return; // Already exists
                }
            }
            addRecord(record);
        }

        /**
         * Evict the oldest entries if the record count exceeds maxEntries.
         * Uses FIFO strategy - removes entries from the beginning of the list.
         */
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
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
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
                case 1 -> record.filePath();
                case 2 -> record.fileSize();
                case 3 -> record.timestamp();
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
                setText(formatBytes(fileSize));
                setHorizontalAlignment(SwingConstants.RIGHT);
            }

            return this;
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024));
            } else {
                return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
            }
        }
    }
}
