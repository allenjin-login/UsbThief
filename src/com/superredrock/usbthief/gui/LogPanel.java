package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogPanel extends JPanel {

    private final JTable logTable;
    private final LogTableModel tableModel;
    private final TableRowSorter<LogTableModel> sorter;
    private final JTextField searchField;
    private final JCheckBox infoCheckBox;
    private final JCheckBox warningCheckBox;
    private final JCheckBox errorCheckBox;
    private final JCheckBox successCheckBox;
    private final JLabel countLabel;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Color INFO_COLOR = Color.BLACK;
    private static final Color WARNING_COLOR = new Color(255, 140, 0);
    private static final Color ERROR_COLOR = Color.RED;
    private static final Color SUCCESS_COLOR = new Color(0, 128, 0);

    private static final int MAX_LOG_ENTRIES = 10000;

    public LogPanel() {
        setLayout(new BorderLayout());

        // Table model
        tableModel = new LogTableModel();
        logTable = new JTable(tableModel);
        logTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logTable.setRowHeight(20);

        // Custom renderer for log level
        logTable.setDefaultRenderer(LogLevel.class, new LogLevelRenderer());

        // Sorter
        sorter = new TableRowSorter<>(tableModel);
        logTable.setRowSorter(sorter);

        // Search and filter panel
        JPanel controlPanel = new JPanel(new BorderLayout(5, 0));

        // Search field
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(400, 28));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        searchField.setToolTipText("输入关键字进行筛选...");
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
        searchButton.addActionListener(e -> applyFilter());

        JButton clearButton = new JButton("清除");
        clearButton.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(searchButton);

        searchPanel.add(new JLabel("搜索:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Level checkboxes
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        infoCheckBox = new JCheckBox("INFO", true);
        infoCheckBox.addActionListener(e -> applyFilter());
        infoCheckBox.setForeground(INFO_COLOR);

        warningCheckBox = new JCheckBox("WARNING", true);
        warningCheckBox.addActionListener(e -> applyFilter());
        warningCheckBox.setForeground(WARNING_COLOR);

        errorCheckBox = new JCheckBox("ERROR", true);
        errorCheckBox.addActionListener(e -> applyFilter());
        errorCheckBox.setForeground(ERROR_COLOR);

        successCheckBox = new JCheckBox("SUCCESS", true);
        successCheckBox.addActionListener(e -> applyFilter());
        successCheckBox.setForeground(SUCCESS_COLOR);

        levelPanel.add(infoCheckBox);
        levelPanel.add(warningCheckBox);
        levelPanel.add(errorCheckBox);
        levelPanel.add(successCheckBox);

        JPanel filterPanel = new JPanel(new BorderLayout(10, 0));
        filterPanel.add(searchPanel, BorderLayout.WEST);
        filterPanel.add(levelPanel, BorderLayout.CENTER);

        controlPanel.add(filterPanel, BorderLayout.CENTER);

        // Table with scroll pane
        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(new TitledBorder("日志"));

        // Count label
        countLabel = new JLabel("显示: 0 / 总计: 0 条");

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register event listeners
        registerEventListeners();
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        // Index events
        eventBus.register(FileIndexedEvent.class, this::onFileIndexed);
        eventBus.register(DuplicateDetectedEvent.class, this::onDuplicateDetected);
        eventBus.register(IndexLoadedEvent.class, this::onIndexLoaded);
        eventBus.register(IndexSavedEvent.class, this::onIndexSaved);

        // Device events
        eventBus.register(DeviceInsertedEvent.class, this::onDeviceInserted);
        eventBus.register(DeviceRemovedEvent.class, this::onDeviceRemoved);
        eventBus.register(DeviceStateChangedEvent.class, this::onDeviceStateChanged);
    }

    // Index event handlers
    private void onFileIndexed(FileIndexedEvent event) {
        String message = String.format("已索引文件: %s (大小: %s, 总计: %d 文件)",
            event.filePath().getFileName(),
            formatBytes(event.fileSize()),
            event.totalIndexed());
        log(message, LogLevel.SUCCESS);
    }

    private void onDuplicateDetected(DuplicateDetectedEvent event) {
        String message = String.format("检测到重复文件: %s", event.filePath().getFileName());
        log(message, LogLevel.WARNING);
    }

    private void onIndexLoaded(IndexLoadedEvent event) {
        String message = String.format("索引加载完成: %d 条记录", event.loadedCount());
        log(message, LogLevel.INFO);
    }

    private void onIndexSaved(IndexSavedEvent event) {
        String message = String.format("索引保存完成: %d 条记录", event.savedCount());
        log(message, LogLevel.INFO);
    }

    // Device event handlers
    private void onDeviceInserted(DeviceInsertedEvent event) {
        String message = String.format("设备已插入: %s", event.device().getRootPath());
        log(message, LogLevel.INFO);
    }

    private void onDeviceRemoved(DeviceRemovedEvent event) {
        String message = String.format("设备已移除: %s", event.device().getRootPath());
        log(message, LogLevel.WARNING);
    }

    private void onDeviceStateChanged(DeviceStateChangedEvent event) {
        String message = String.format("设备状态变更: %s -> %s", event.oldState(), event.newState());
        log(message, LogLevel.INFO);
    }

    public void log(String message, LogLevel level) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            tableModel.addLogEntry(new LogEntry(timestamp, level, message));

            // Limit log entries to prevent memory issues
            while (tableModel.getRowCount() > MAX_LOG_ENTRIES) {
                tableModel.removeFirstEntry();
            }

            applyFilter();
        });
    }

    public void info(String message) {
        log(message, LogLevel.INFO);
    }

    public void warning(String message) {
        log(message, LogLevel.WARNING);
    }

    public void error(String message) {
        log(message, LogLevel.ERROR);
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            tableModel.clear();
            applyFilter();
        });
    }

    private void applyFilter() {
        // Text filter
        String searchText = searchField.getText().trim().toLowerCase();

        // Level filter
        List<RowFilter<LogTableModel, Integer>> filters = new ArrayList<>();

        if (!searchText.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + searchText, 2)); // Message column
        }

        if (!infoCheckBox.isSelected() || !warningCheckBox.isSelected() ||
            !errorCheckBox.isSelected() || !successCheckBox.isSelected()) {
            List<LogLevel> allowedLevels = new ArrayList<>();
            if (infoCheckBox.isSelected()) allowedLevels.add(LogLevel.INFO);
            if (warningCheckBox.isSelected()) allowedLevels.add(LogLevel.WARNING);
            if (errorCheckBox.isSelected()) allowedLevels.add(LogLevel.ERROR);
            if (successCheckBox.isSelected()) allowedLevels.add(LogLevel.SUCCESS);

            filters.add(RowFilter.regexFilter(getLevelFilterRegex(allowedLevels), 1)); // Level column
        }

        if (!filters.isEmpty()) {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        } else {
            sorter.setRowFilter(null);
        }

        updateCountLabel();
    }

    private String getLevelFilterRegex(List<LogLevel> allowedLevels) {
        StringBuilder regex = new StringBuilder(".*(");
        for (int i = 0; i < allowedLevels.size(); i++) {
            if (i > 0) regex.append("|");
            regex.append(allowedLevels.get(i).name());
        }
        regex.append(").*");
        return regex.toString();
    }

    private void updateCountLabel() {
        int filteredCount = logTable.getRowCount();
        int totalCount = tableModel.getRowCount();
        countLabel.setText(String.format("显示: %d / 总计: %d 条", filteredCount, totalCount));
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

    private static class LogTableModel extends AbstractTableModel {

        private final List<LogEntry> logEntries = new ArrayList<>();

        private final String[] columnNames = {"时间", "级别", "消息"};
        private final Class<?>[] columnTypes = {String.class, LogLevel.class, String.class};

        public void addLogEntry(LogEntry entry) {
            logEntries.add(entry);
            fireTableRowsInserted(logEntries.size() - 1, logEntries.size() - 1);
        }

        public void removeFirstEntry() {
            if (!logEntries.isEmpty()) {
                logEntries.removeFirst();
                fireTableRowsDeleted(0, 0);
            }
        }

        public void clear() {
            int size = logEntries.size();
            logEntries.clear();
            if (size > 0) {
                fireTableRowsDeleted(0, size - 1);
            }
        }

        @Override
        public int getRowCount() {
            return logEntries.size();
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
            LogEntry entry = logEntries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.timestamp();
                case 1 -> entry.level();
                case 2 -> entry.message();
                default -> null;
            };
        }
    }

    private static class LogLevelRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof LogLevel level) {
                setText(level.name());
                setForeground(getLevelColor(level));
                setFont(getFont().deriveFont(Font.BOLD));
            }

            return this;
        }

        private Color getLevelColor(LogLevel level) {
            return switch (level) {
                case INFO -> INFO_COLOR;
                case WARNING -> WARNING_COLOR;
                case ERROR -> ERROR_COLOR;
                case SUCCESS -> SUCCESS_COLOR;
            };
        }
    }

    private record LogEntry(String timestamp, LogLevel level, String message) {}

    public enum LogLevel {
        INFO, WARNING, ERROR, SUCCESS
    }
}
