package com.superredrock.usbthief.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

/**
 * Independent log window that captures java.util.logging output.
 * Registers a custom Handler to receive all log records.
 */
public class LogWindow extends JDialog {

    private static final I18NManager i18n = I18NManager.getInstance();
    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    private final JTextPane logTextPane;
    private final StyledDocument doc;
    private JTextField searchField;
    private JCheckBox fineCheckBox;
    private JCheckBox infoCheckBox;
    private JCheckBox warningCheckBox;
    private JCheckBox severeCheckBox;
    private final JLabel countLabel;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final List<LogEntry> allEntries = new ArrayList<>();

    private static final int MAX_LOG_ENTRIES = 10000;

    // Styles for different log levels
    private final Style fineStyle;
    private final Style infoStyle;
    private final Style warningStyle;
    private final Style severeStyle;
    private final Style timestampStyle;

    // Custom handler to capture JUL logs
    private final GuiLogHandler guiHandler;

    public LogWindow(JFrame parent) {
        super(parent, i18n.getMessage("logwindow.title"), false);
        setSize(900, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        // Initialize text pane
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logTextPane.setBackground(new Color(30, 30, 30));
        logTextPane.setForeground(Color.LIGHT_GRAY);
        doc = logTextPane.getStyledDocument();

        // Create styles
        Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        timestampStyle = doc.addStyle("timestamp", defaultStyle);
        StyleConstants.setForeground(timestampStyle, new Color(120, 120, 120));

        fineStyle = doc.addStyle("fine", defaultStyle);
        StyleConstants.setForeground(fineStyle, new Color(150, 150, 150));

        infoStyle = doc.addStyle("info", defaultStyle);
        StyleConstants.setForeground(infoStyle, Color.LIGHT_GRAY);

        warningStyle = doc.addStyle("warning", defaultStyle);
        StyleConstants.setForeground(warningStyle, new Color(255, 180, 0));
        StyleConstants.setBold(warningStyle, true);

        severeStyle = doc.addStyle("severe", defaultStyle);
        StyleConstants.setForeground(severeStyle, new Color(255, 80, 80));
        StyleConstants.setBold(severeStyle, true);

        // Control panel
        JPanel controlPanel = createControlPanel();

        // Scroll pane
        JScrollPane scrollPane = new JScrollPane(logTextPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(new TitledBorder(i18n.getMessage("logwindow.border")));

        // Count label
        countLabel = new JLabel(i18n.getMessage("logwindow.count", 0, 0));
        countLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Layout
        setLayout(new BorderLayout(5, 5));
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);

        // Register custom handler to capture logs
        guiHandler = new GuiLogHandler();
        ROOT_LOGGER.addHandler(guiHandler);

        // ESC to close
        getRootPane().registerKeyboardAction(
                _ -> setVisible(false),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
        controlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(300, 28));
        searchField.setToolTipText(i18n.getMessage("logwindow.search.tooltip"));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        JButton searchButton = new JButton(i18n.getMessage("logwindow.search.button"));
        searchButton.addActionListener(e -> applyFilter());

        JButton clearSearchButton = new JButton(i18n.getMessage("logwindow.clear.button"));
        clearSearchButton.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
        });

        JButton clearLogsButton = new JButton(i18n.getMessage("logwindow.clearlogs.button"));
        clearLogsButton.addActionListener(e -> clear());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(clearSearchButton);
        buttonPanel.add(searchButton);
        buttonPanel.add(clearLogsButton);

        searchPanel.add(new JLabel(i18n.getMessage("logwindow.search.label")), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(buttonPanel, BorderLayout.EAST);

        // Level filter panel
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));

        fineCheckBox = new JCheckBox(i18n.getMessage("logwindow.level.fine"), true);
        fineCheckBox.setForeground(new Color(150, 150, 150));
        fineCheckBox.addActionListener(e -> applyFilter());

        infoCheckBox = new JCheckBox(i18n.getMessage("logwindow.level.info"), true);
        infoCheckBox.setForeground(Color.LIGHT_GRAY);
        infoCheckBox.addActionListener(e -> applyFilter());

        warningCheckBox = new JCheckBox(i18n.getMessage("logwindow.level.warning"), true);
        warningCheckBox.setForeground(new Color(255, 180, 0));
        warningCheckBox.addActionListener(e -> applyFilter());

        severeCheckBox = new JCheckBox(i18n.getMessage("logwindow.level.severe"), true);
        severeCheckBox.setForeground(new Color(255, 80, 80));
        severeCheckBox.addActionListener(e -> applyFilter());

        levelPanel.add(fineCheckBox);
        levelPanel.add(infoCheckBox);
        levelPanel.add(warningCheckBox);
        levelPanel.add(severeCheckBox);

        controlPanel.add(searchPanel, BorderLayout.NORTH);
        controlPanel.add(levelPanel, BorderLayout.SOUTH);

        return controlPanel;
    }

    /**
     * Called by GuiLogHandler when a log record is published.
     */
    private void publishLog(LogRecord record) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String loggerName = shortenLoggerName(record.getLoggerName());
            String message = formatMessage(record);
            LogLevel level = mapLevel(record.getLevel());

            LogEntry entry = new LogEntry(timestamp, level, loggerName, message);
            allEntries.add(entry);

            // Limit entries
            while (allEntries.size() > MAX_LOG_ENTRIES) {
                allEntries.removeFirst();
            }

            // Check if this level is filtered
            if (isLevelVisible(level) && matchesSearch(message)) {
                appendLogEntry(entry);
            }

            updateCountLabel();
        });
    }

    private String shortenLoggerName(String name) {
        if (name == null || name.isEmpty()) return "";
        // com.superredrock.usbthief.worker.DeviceScanner -> c.s.u.worker.DeviceScanner
        String[] parts = name.split("\\.");
        if (parts.length <= 2) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i < parts.length - 2) {
                sb.append(parts[i].charAt(0)).append(".");
            } else {
                sb.append(parts[i]);
                if (i < parts.length - 1) sb.append(".");
            }
        }
        return sb.toString();
    }

    private String formatMessage(LogRecord record) {
        String msg = record.getMessage();
        if (msg == null || msg.isEmpty()) {
            return "";
        }
        Object[] params = record.getParameters();
        if (params != null && params.length > 0) {
            try {
                return String.format(msg, params);
            } catch (Exception e) {
                return msg;
            }
        }
        return msg;
    }

    private LogLevel mapLevel(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return LogLevel.SEVERE;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return LogLevel.WARNING;
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return LogLevel.INFO;
        } else {
            return LogLevel.FINE;
        }
    }

    private void appendLogEntry(LogEntry entry) {
        try {
            // Append timestamp
            doc.insertString(doc.getLength(), "[" + entry.timestamp() + "] ", timestampStyle);
            // Append level
            Style levelStyle = getStyleForLevel(entry.level());
            String levelText = String.format("[%-7s] ", entry.level().name());
            doc.insertString(doc.getLength(), levelText, levelStyle);
            // Append logger name
            doc.insertString(doc.getLength(), "[" + entry.loggerName() + "] ", timestampStyle);
            // Append message
            doc.insertString(doc.getLength(), entry.message() + "\n", levelStyle);

            // Auto-scroll to bottom
            logTextPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private Style getStyleForLevel(LogLevel level) {
        return switch (level) {
            case FINE -> fineStyle;
            case INFO -> infoStyle;
            case WARNING -> warningStyle;
            case SEVERE -> severeStyle;
        };
    }

    private boolean isLevelVisible(LogLevel level) {
        return switch (level) {
            case FINE -> fineCheckBox.isSelected();
            case INFO -> infoCheckBox.isSelected();
            case WARNING -> warningCheckBox.isSelected();
            case SEVERE -> severeCheckBox.isSelected();
        };
    }

    private boolean matchesSearch(String message) {
        String searchText = searchField.getText().trim().toLowerCase();
        if (searchText.isEmpty()) return true;
        return message.toLowerCase().contains(searchText);
    }

    private void applyFilter() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());

                for (LogEntry entry : allEntries) {
                    if (isLevelVisible(entry.level()) && matchesSearch(entry.message())) {
                        appendLogEntry(entry);
                    }
                }

                updateCountLabel();
            } catch (BadLocationException e) {
                // Ignore
            }
        });
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            allEntries.clear();
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException e) {
                // Ignore
            }
            updateCountLabel();
        });
    }

    private void updateCountLabel() {
        int visibleCount = logTextPane.getText().split("\n").length - 1;
        if (logTextPane.getText().isEmpty()) visibleCount = 0;
        int totalCount = allEntries.size();
        countLabel.setText(i18n.getMessage("logwindow.count", visibleCount, totalCount));
    }

    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("logwindow.title"));
            searchField.setToolTipText(i18n.getMessage("logwindow.search.tooltip"));
            // Other components would need field references to update
        });
    }

    /**
     * Cleanup when window is disposed.
     */
    @Override
    public void dispose() {
        ROOT_LOGGER.removeHandler(guiHandler);
        guiHandler.close();
        super.dispose();
    }

    private record LogEntry(String timestamp, LogLevel level, String loggerName, String message) {}

    public enum LogLevel {
        FINE, INFO, WARNING, SEVERE
    }

    /**
     * Custom java.util.logging.Handler that forwards log records to the GUI.
     */
    private class GuiLogHandler extends Handler {

        public GuiLogHandler() {
            setLevel(Level.ALL);
            setFormatter(new SimpleFormatter());
        }

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            publishLog(record);
        }

        @Override
        public void flush() {
            // No buffer to flush
        }

        @Override
        public void close() throws SecurityException {
            // No resources to close
        }
    }
}
