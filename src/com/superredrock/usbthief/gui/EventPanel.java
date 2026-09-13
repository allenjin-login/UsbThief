package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.event.Event;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.StorageLowEvent;
import com.superredrock.usbthief.core.event.storage.StorageRecoveredEvent;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.gui.theme.ThemeManager;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Event panel that displays the device/volume/copy lifecycle events from the EventBus.
 * Shows event type, timestamp, and description with filtering capabilities.
 *
 * <p>Events are buffered on the dispatching thread and flushed to the table model by a
 * Swing timer, so a burst of events costs one table update per refresh interval instead
 * of one {@code invokeLater} per event. The row filter is only recomputed when the user
 * changes the filter controls.
 *
 * <p>Subscriptions are limited to the event types this panel renders. The per-file
 * events ({@code FileDiscoveredEvent}, {@code FileIndexedEvent}) are deliberately not
 * subscribed: they fire once per scanned file and are surfaced by {@link LogPanel}.
 */
@Deprecated
public class EventPanel extends JPanel {

    private static final Logger logger = LogManager.getLogger(EventPanel.class);
    private final I18nManager i18n = I18nManager.getInstance();
    private final JTable eventTable;
    private final EventTableModel tableModel;
    private final TableRowSorter<EventTableModel> sorter;
    private JTextField searchField;
    private JLabel searchLabel;
    private JButton clearButton;
    private final JLabel countLabel;
    private JComboBox<String> eventTypeFilter;
    private final JScrollPane scrollPane;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int MAX_EVENT_ENTRIES = 10000;

    /** Refresh interval of the batched table update (milliseconds). */
    private static final int REFRESH_INTERVAL_MS = 150;

    /** Upper bound of the pending buffer, so a stalled EDT cannot grow it without limit. */
    private static final int MAX_PENDING_ENTRIES = 2 * MAX_EVENT_ENTRIES;

    private static final Color INFO_COLOR = ThemeManager.ACCENT_INFO;
    private static final Color SUCCESS_COLOR = ThemeManager.ACCENT_SUCCESS;
    private static final Color WARNING_COLOR = ThemeManager.ACCENT_WARNING;
    private static final Color ERROR_COLOR = ThemeManager.ACCENT_ERROR;

    /** Events waiting to be flushed into the table model. Guarded by itself. */
    private final ArrayDeque<EventEntry> pendingEntries = new ArrayDeque<>();

    /** Number of buffered events dropped because the buffer was full. Guarded by pendingEntries. */
    private int droppedEntries;

    private final Timer refreshTimer;

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
        scrollPane = new JScrollPane(eventTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(new TitledBorder(i18n.getMessage("event.border")));

        // Count label
        countLabel = new JLabel(i18n.getMessage("event.count", 0, 0));

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register event listener
        registerEventListener();

        // Batched refresh: drain the pending buffer once per interval on the EDT
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> flushPendingEntries());
        refreshTimer.setRepeats(true);
        refreshTimer.start();
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new BorderLayout(10, 0));

        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(300, 28));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        searchField.setToolTipText(i18n.getMessage("event.search.tooltip"));
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

        clearButton = new JButton(i18n.getMessage("event.clear.button"));
        clearButton.addActionListener(e -> {
            searchField.setText("");
            eventTypeFilter.setSelectedItem(i18n.getMessage("event.filter.all"));
            applyFilter();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearButton);

        searchLabel = new JLabel(i18n.getMessage("event.search.label"));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Event type filter
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        eventTypeFilter = new JComboBox<>();
        eventTypeFilter.addItem(i18n.getMessage("event.filter.all"));
        eventTypeFilter.addActionListener(e -> applyFilter());
        eventTypeFilter.setPreferredSize(new Dimension(200, 28));

        filterPanel.add(new JLabel(i18n.getMessage("event.filter.label")));
        filterPanel.add(eventTypeFilter);

        controlPanel.add(searchPanel, BorderLayout.WEST);
        controlPanel.add(filterPanel, BorderLayout.CENTER);

        return controlPanel;
    }

    private void registerEventListener() {
        EventBus eventBus = EventBus.getInstance();

        // Subscribe only to the event types this table renders, instead of the Event
        // interface. Per-file events are excluded on purpose (see class javadoc).
        eventBus.register(DeviceArrivalEvent.class, this::onEvent);
        eventBus.register(DeviceRemovalEvent.class, this::onEvent);
        eventBus.register(NewDeviceJoinedEvent.class, this::onEvent);
        eventBus.register(VolumeInsertedEvent.class, this::onEvent);
        eventBus.register(VolumeRemovedEvent.class, this::onEvent);
        eventBus.register(VolumeStateChangedEvent.class, this::onEvent);
        eventBus.register(DuplicateDetectedEvent.class, this::onEvent);
        eventBus.register(CopyCompletedEvent.class, this::onEvent);
        eventBus.register(StorageLowEvent.class, this::onEvent);
        eventBus.register(StorageRecoveredEvent.class, this::onEvent);
        eventBus.register(FilesRecycledEvent.class, this::onEvent);
        eventBus.register(EmptyFoldersDeletedEvent.class, this::onEvent);
    }

    /**
     * Buffers the event. Called on the dispatching thread, which may be any thread, so it
     * only formats the row and appends it to the pending buffer.
     */
    private void onEvent(Event event) {
        EventEntry entry = new EventEntry(
                getEventType(event),
                formatTimestamp(event.timestamp()),
                event.description());

        synchronized (pendingEntries) {
            pendingEntries.addLast(entry);
            while (pendingEntries.size() > MAX_PENDING_ENTRIES) {
                pendingEntries.removeFirst();
                droppedEntries++;
            }
        }
    }

    /**
     * Drains the pending buffer into the table model in one batch. Runs on the EDT.
     * Deliberately does not touch the row filter: the sorter picks up inserted rows by
     * itself, and re-installing the filter on every refresh is what froze the UI.
     */
    private void flushPendingEntries() {
        List<EventEntry> batch;
        int dropped;
        synchronized (pendingEntries) {
            if (pendingEntries.isEmpty() && droppedEntries == 0) {
                return;
            }
            batch = new ArrayList<>(pendingEntries);
            dropped = droppedEntries;
            pendingEntries.clear();
            droppedEntries = 0;
        }

        tableModel.addEventEntries(batch);

        for (int i = 0; i < batch.size(); i++) {
            updateEventTypeFilter(batch.get(i).eventType());
        }

        if (dropped > 0) {
            logger.debug("EventPanel dropped {} buffered events (buffer full)", dropped);
        }

        updateCountLabel();
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

    /**
     * Installs the row filter. Only called when the user changes the filter controls, or
     * when the table content is cleared - never from the event refresh path.
     */
    private void applyFilter() {
        List<RowFilter<EventTableModel, Integer>> filters = new ArrayList<>();

        // Text filter (search in description)
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            // Pattern.quote keeps arbitrary user input (e.g. "[", "(") from raising
            // PatternSyntaxException out of the row filter.
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText), 2)); // Description column
        }

        // Event type filter
        String selectedType = (String) eventTypeFilter.getSelectedItem();
        if (selectedType != null && !selectedType.equals(i18n.getMessage("event.filter.all"))) {
            filters.add(RowFilter.regexFilter(Pattern.quote(selectedType), 0)); // Event type column
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
        countLabel.setText(i18n.getMessage("event.count", filteredCount, totalCount));
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

    public void refreshLanguage() {
        logger.info("EventPanel.refreshLanguage() called");
        SwingUtilities.invokeLater(() -> {
            scrollPane.setBorder(new TitledBorder(i18n.getMessage("event.border")));
            searchLabel.setText(i18n.getMessage("event.search.label"));
            searchField.setToolTipText(i18n.getMessage("event.search.tooltip"));
            clearButton.setText(i18n.getMessage("event.clear.button"));
            countLabel.setText(i18n.getMessage("event.count", eventTable.getRowCount(), tableModel.getRowCount()));
            tableModel.fireTableStructureChanged();
            logger.info("EventPanel language refreshed");
        });
    }

    private static class EventTableModel extends AbstractTableModel {

        private final List<EventEntry> eventEntries = new ArrayList<>();

        private final Class<?>[] columnTypes = {String.class, String.class, String.class};

        /**
         * Appends a batch of entries with a single insert event, then trims the model back
         * to {@link #MAX_EVENT_ENTRIES} with a single delete event. Trimming a contiguous
         * prefix is one array copy, independent of the number of entries in the batch.
         */
        public void addEventEntries(List<EventEntry> entries) {
            if (entries.isEmpty()) {
                return;
            }

            int firstRow = eventEntries.size();
            eventEntries.addAll(entries);
            fireTableRowsInserted(firstRow, eventEntries.size() - 1);

            int excess = eventEntries.size() - MAX_EVENT_ENTRIES;
            if (excess > 0) {
                eventEntries.subList(0, excess).clear();
                fireTableRowsDeleted(0, excess - 1);
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
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) {
                return I18nManager.getInstance().getMessage("event.table.type");
            } else if (column == 1) {
                return I18nManager.getInstance().getMessage("event.table.time");
            } else if (column == 2) {
                return I18nManager.getInstance().getMessage("event.table.description");
            }
            return "";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnTypes[columnIndex];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EventEntry entry = eventEntries.get(rowIndex);
            if (columnIndex == 0) {
                return entry.eventType();
            } else if (columnIndex == 1) {
                return entry.timestamp();
            } else if (columnIndex == 2) {
                return entry.description();
            }
            return null;
        }
    }

    private static class EventTypeRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof String) {
                String eventType = (String) value;
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

    private static final class EventEntry {

        private final String eventType;
        private final String timestamp;
        private final String description;

        EventEntry(String eventType, String timestamp, String description) {
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.description = description;
        }

        String eventType() {
            return eventType;
        }

        String timestamp() {
            return timestamp;
        }

        String description() {
            return description;
        }
    }
}
