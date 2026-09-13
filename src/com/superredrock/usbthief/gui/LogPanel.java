package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.gui.theme.ThemeManager;

import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Log panel fed by EventBus events and by direct {@link #log(String, LogLevel)} calls.
 *
 * <p>Entries are buffered on the calling thread and drained into the table model by a
 * Swing timer, so a flood of events (e.g. one {@code FileIndexedEvent} per scanned file)
 * costs one table update per refresh interval instead of one {@code invokeLater} plus a
 * full filter recomputation per entry. The row filter is only recomputed when the user
 * changes a filter control.
 */
public class LogPanel extends JPanel {

    private final I18nManager i18n = I18nManager.getInstance();
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

    /** Refresh interval of the batched table update (milliseconds). */
    private static final int REFRESH_INTERVAL_MS = 150;

    /** Upper bound of the pending buffer, so a stalled EDT cannot grow it without limit. */
    private static final int MAX_PENDING_ENTRIES = 2 * MAX_LOG_ENTRIES;

    /** Entries waiting to be flushed into the table model. Guarded by itself. */
    private final ArrayDeque<LogEntry> pendingEntries = new ArrayDeque<>();

    /** Number of buffered entries dropped because the buffer was full. Guarded by pendingEntries. */
    private int droppedEntries;

    private final Timer refreshTimer;

    public LogPanel() {
        setLayout(new BorderLayout());

        // Table model
        tableModel = new LogTableModel();
        logTable = new JTable(tableModel);
        logTable.setFont(ThemeManager.FONT_MONO);
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
        searchField.setToolTipText(i18n.getMessage("log.search.tooltip"));
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

        JButton searchButton = new JButton(i18n.getMessage("log.search.button"));
        searchButton.addActionListener(e -> applyFilter());

        JButton clearButton = new JButton(i18n.getMessage("log.clear.button"));
        clearButton.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(searchButton);

        searchPanel.add(new JLabel(i18n.getMessage("log.search.label")), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Level checkboxes
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        infoCheckBox = new JCheckBox(i18n.getMessage("log.level.info"), true);
        infoCheckBox.addActionListener(e -> applyFilter());
        infoCheckBox.setForeground(INFO_COLOR);

        warningCheckBox = new JCheckBox(i18n.getMessage("log.level.warning"), true);
        warningCheckBox.addActionListener(e -> applyFilter());
        warningCheckBox.setForeground(WARNING_COLOR);

        errorCheckBox = new JCheckBox(i18n.getMessage("log.level.error"), true);
        errorCheckBox.addActionListener(e -> applyFilter());
        errorCheckBox.setForeground(ERROR_COLOR);

        successCheckBox = new JCheckBox(i18n.getMessage("log.level.success"), true);
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
        scrollPane.setBorder(new TitledBorder(i18n.getMessage("log.border")));

        // Count label
        countLabel = new JLabel(i18n.getMessage("log.count", 0, 0));

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register event listeners
        registerEventListeners();

        // Batched refresh: drain the pending buffer once per interval on the EDT
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> flushPendingEntries());
        refreshTimer.setRepeats(true);
        refreshTimer.start();
    }

    private void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        // Index events
        eventBus.register(FileIndexedEvent.class, this::onFileIndexed);
        eventBus.register(DuplicateDetectedEvent.class, this::onDuplicateDetected);

        // Volume events
        eventBus.register(VolumeInsertedEvent.class, this::onVolumeInserted);
        eventBus.register(VolumeRemovedEvent.class, this::onVolumeRemoved);
        eventBus.register(VolumeStateChangedEvent.class, this::onVolumeStateChanged);
    }

    // Index event handlers
    private void onFileIndexed(FileIndexedEvent event) {
        String message = i18n.getMessage("log.message.indexed",
                event.filePath().getFileName(),
                SizeFormatter.format(event.fileSize()),
                event.totalIndexed());
        log(message, LogLevel.SUCCESS);
    }

    private void onDuplicateDetected(DuplicateDetectedEvent event) {
        String message = i18n.getMessage("log.message.duplicate", event.filePath().getFileName());
        log(message, LogLevel.WARNING);
    }

    // Volume event handlers
    private void onVolumeInserted(VolumeInsertedEvent event) {
        String message = i18n.getMessage("log.message.deviceInserted", event.volume().getRootPath());
        log(message, LogLevel.INFO);
    }

    private void onVolumeRemoved(VolumeRemovedEvent event) {
        String message = i18n.getMessage("log.message.deviceRemoved", event.volume().getRootPath());
        log(message, LogLevel.WARNING);
    }

    private void onVolumeStateChanged(VolumeStateChangedEvent event) {
        String message = i18n.getMessage("log.message.deviceStateChanged", event.oldState(), event.newState());
        log(message, LogLevel.INFO);
    }

    /**
     * Buffers a log entry. Safe to call from any thread: the entry is queued and the EDT
     * picks it up on the next refresh, so callers are never blocked on the UI.
     */
    public void log(String message, LogLevel level) {
        LogEntry entry = new LogEntry(LocalDateTime.now().format(timeFormatter), level, message);

        synchronized (pendingEntries) {
            pendingEntries.addLast(entry);
            while (pendingEntries.size() > MAX_PENDING_ENTRIES) {
                pendingEntries.removeFirst();
                droppedEntries++;
            }
        }
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

    /**
     * Drains the pending buffer into the table model in one batch. Runs on the EDT.
     */
    private void flushPendingEntries() {
        List<LogEntry> batch;
        synchronized (pendingEntries) {
            if (pendingEntries.isEmpty() && droppedEntries == 0) {
                return;
            }
            batch = new ArrayList<>(pendingEntries);
            pendingEntries.clear();
            droppedEntries = 0;
        }

        tableModel.addLogEntries(batch);
        updateCountLabel();
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            synchronized (pendingEntries) {
                pendingEntries.clear();
                droppedEntries = 0;
            }
            tableModel.clear();
            applyFilter();
        });
    }

    /**
     * Installs the row filter. Only called when the user changes a filter control, or when
     * the table content is cleared - never from the event refresh path.
     */
    private void applyFilter() {
        // Text filter
        String searchText = searchField.getText().trim();

        // Level filter
        List<RowFilter<LogTableModel, Integer>> filters = new ArrayList<>();

        if (!searchText.isEmpty()) {
            // Pattern.quote keeps arbitrary user input from raising PatternSyntaxException
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText), 2)); // Message column
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
        countLabel.setText(i18n.getMessage("log.count", filteredCount, totalCount));
    }

    private static class LogTableModel extends AbstractTableModel {

        private final List<LogEntry> logEntries = new ArrayList<>();

        private final String[] columnNames = {
                I18nManager.getInstance().getMessage("log.table.time"),
                I18nManager.getInstance().getMessage("log.table.level"),
                I18nManager.getInstance().getMessage("log.table.message")
        };
        private final Class<?>[] columnTypes = {String.class, LogLevel.class, String.class};

        /**
         * Appends a batch of entries with a single insert event, then trims the model back
         * to {@link #MAX_LOG_ENTRIES} with a single delete event.
         */
        public void addLogEntries(List<LogEntry> entries) {
            if (entries.isEmpty()) {
                return;
            }

            int firstRow = logEntries.size();
            logEntries.addAll(entries);
            fireTableRowsInserted(firstRow, logEntries.size() - 1);

            int excess = logEntries.size() - MAX_LOG_ENTRIES;
            if (excess > 0) {
                logEntries.subList(0, excess).clear();
                fireTableRowsDeleted(0, excess - 1);
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
            if (columnIndex == 0) {
                return entry.timestamp();
            } else if (columnIndex == 1) {
                return entry.level();
            } else if (columnIndex == 2) {
                return entry.message();
            }
            return null;
        }
    }

    private static class LogLevelRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof LogLevel) {
                LogLevel level = (LogLevel) value;
                setText(level.name());
                setForeground(getLevelColor(level));
                setFont(getFont().deriveFont(Font.BOLD));
            }

            return this;
        }

        private Color getLevelColor(LogLevel level) {
            if (level == LogLevel.INFO) {
                return INFO_COLOR;
            } else if (level == LogLevel.WARNING) {
                return WARNING_COLOR;
            } else if (level == LogLevel.ERROR) {
                return ERROR_COLOR;
            }
            return SUCCESS_COLOR;
        }
    }

    private static final class LogEntry {

        private final String timestamp;
        private final LogLevel level;
        private final String message;

        LogEntry(String timestamp, LogLevel level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }

        String timestamp() {
            return timestamp;
        }

        LogLevel level() {
            return level;
        }

        String message() {
            return message;
        }
    }

    public enum LogLevel {
        INFO, WARNING, ERROR, SUCCESS
    }
}
