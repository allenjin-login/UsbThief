package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.event.Event;
import com.superredrock.usbthief.core.event.EventBus;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Event panel that displays all events from the EventBus.
 * Shows event type, timestamp, and description with filtering capabilities.
 */
public class EventPanel extends JPanel {

    private final JTable eventTable;
    private final EventTableModel tableModel;
    private final TableRowSorter<EventTableModel> sorter;
    private JTextField searchField;
    private final JLabel countLabel;
    private JComboBox<String> eventTypeFilter;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int MAX_EVENT_ENTRIES = 10000;
    private static final Color INFO_COLOR = new Color(0, 100, 200);
    private static final Color SUCCESS_COLOR = new Color(0, 128, 0);
    private static final Color WARNING_COLOR = new Color(255, 140, 0);
    private static final Color ERROR_COLOR = Color.RED;

    public EventPanel() {
        setLayout(new BorderLayout());

        // Table model
        tableModel = new EventTableModel();
        eventTable = new JTable(tableModel);
        eventTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventTable.setRowHeight(22);

        // Custom renderer for event type
        eventTable.setDefaultRenderer(String.class, new EventTypeRenderer());

        // Sorter
        sorter = new TableRowSorter<>(tableModel);
        eventTable.setRowSorter(sorter);

        // Control panel
        JPanel controlPanel = createControlPanel();

        // Table with scroll pane
        JScrollPane scrollPane = new JScrollPane(eventTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(new TitledBorder("事件"));

        // Count label
        countLabel = new JLabel("显示: 0 / 总计: 0 条");

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register event listener for ALL events
        registerEventListener();
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new BorderLayout(10, 0));

        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(300, 28));
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

        JButton clearButton = new JButton("清除");
        clearButton.addActionListener(e -> {
            searchField.setText("");
            eventTypeFilter.setSelectedItem("全部");
            applyFilter();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearButton);

        searchPanel.add(new JLabel("搜索:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Event type filter
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        eventTypeFilter = new JComboBox<>();
        eventTypeFilter.addItem("全部");
        eventTypeFilter.addActionListener(e -> applyFilter());
        eventTypeFilter.setPreferredSize(new Dimension(200, 28));

        filterPanel.add(new JLabel("事件类型:"));
        filterPanel.add(eventTypeFilter);

        controlPanel.add(searchPanel, BorderLayout.WEST);
        controlPanel.add(filterPanel, BorderLayout.CENTER);

        return controlPanel;
    }

    private void registerEventListener() {
        EventBus eventBus = EventBus.getInstance();

        // Register for ALL events using the base Event interface
        eventBus.register(Event.class, this::onEvent);
    }

    private void onEvent(Event event) {
        SwingUtilities.invokeLater(() -> {
            String eventType = getEventType(event);
            String timestamp = formatTimestamp(event.timestamp());
            String description = event.description();

            tableModel.addEventEntry(new EventEntry(eventType, timestamp, description));

            // Limit event entries to prevent memory issues
            while (tableModel.getRowCount() > MAX_EVENT_ENTRIES) {
                tableModel.removeFirstEntry();
            }

            // Update event type filter if this is a new type
            updateEventTypeFilter(eventType);

            applyFilter();
        });
    }

    private String getEventType(Event event) {
        // Get the simple class name without "Event" suffix for cleaner display
        String className = event.getClass().getSimpleName();
        if (className.endsWith("Event")) {
            return className.substring(0, className.length() - 5);
        }
        return className;
    }

    private String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        );
        return dateTime.format(timeFormatter);
    }

    private void updateEventTypeFilter(String eventType) {
        // Check if this event type is already in the filter
        for (int i = 0; i < eventTypeFilter.getItemCount(); i++) {
            if (eventTypeFilter.getItemAt(i).equals(eventType)) {
                return; // Already exists
            }
        }

        // Add new event type to filter
        eventTypeFilter.addItem(eventType);
    }

    private void applyFilter() {
        List<RowFilter<EventTableModel, Integer>> filters = new ArrayList<>();

        // Text filter (search in description)
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + searchText, 2)); // Description column
        }

        // Event type filter
        String selectedType = (String) eventTypeFilter.getSelectedItem();
        if (selectedType != null && !selectedType.equals("全部")) {
            filters.add(RowFilter.regexFilter(selectedType, 0)); // Event type column
        }

        if (!filters.isEmpty()) {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        } else {
            sorter.setRowFilter(null);
        }

        updateCountLabel();
    }

    private void updateCountLabel() {
        int filteredCount = eventTable.getRowCount();
        int totalCount = tableModel.getRowCount();
        countLabel.setText(String.format("显示: %d / 总计: %d 条", filteredCount, totalCount));
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            tableModel.clear();
            applyFilter();
        });
    }

    private static class EventTableModel extends AbstractTableModel {

        private final List<EventEntry> eventEntries = new ArrayList<>();

        private final String[] columnNames = {"事件类型", "时间", "描述"};
        private final Class<?>[] columnTypes = {String.class, String.class, String.class};

        public void addEventEntry(EventEntry entry) {
            eventEntries.add(entry);
            fireTableRowsInserted(eventEntries.size() - 1, eventEntries.size() - 1);
        }

        public void removeFirstEntry() {
            if (!eventEntries.isEmpty()) {
                eventEntries.removeFirst();
                fireTableRowsDeleted(0, 0);
            }
        }

        public void clear() {
            int size = eventEntries.size();
            eventEntries.clear();
            if (size > 0) {
                fireTableRowsDeleted(0, size - 1);
            }
        }

        @Override
        public int getRowCount() {
            return eventEntries.size();
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
            EventEntry entry = eventEntries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.eventType();
                case 1 -> entry.timestamp();
                case 2 -> entry.description();
                default -> null;
            };
        }
    }

    private static class EventTypeRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof String eventType) {
                setText(eventType);
                setForeground(getEventTypeColor(eventType));
                setFont(getFont().deriveFont(Font.BOLD));
            }

            return this;
        }

        private Color getEventTypeColor(String eventType) {
            // Color coding based on event type keywords
            String lowerType = eventType.toLowerCase();

            if (lowerType.contains("inserted") || lowerType.contains("indexed") ||
                lowerType.contains("loaded") || lowerType.contains("saved")) {
                return SUCCESS_COLOR;
            } else if (lowerType.contains("removed") || lowerType.contains("duplicate")) {
                return WARNING_COLOR;
            } else if (lowerType.contains("fail") || lowerType.contains("error")) {
                return ERROR_COLOR;
            } else {
                return INFO_COLOR;
            }
        }
    }

    private record EventEntry(String eventType, String timestamp, String description) {}
}
