package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.LoggingConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

public class LogWindow extends JDialog {

    private static final I18NManager i18n = I18NManager.getInstance();

    private final JTextPane logTextPane;
    private final StyledDocument doc;
    private JTextField searchField;
    private JCheckBox fineCheckBox;
    private JCheckBox infoCheckBox;
    private JCheckBox warningCheckBox;
    private JCheckBox severeCheckBox;
    private final JLabel countLabel;

    private int visibleCount = 0;
    private int totalCount = 0;

    // Styles for different log levels
    private final Style fineStyle;
    private final Style infoStyle;
    private final Style warningStyle;
    private final Style severeStyle;
    private final Style timestampStyle;

    // Listener reference for cleanup
    private Consumer<LogBufferAppender.LogEntry> listener;

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

        // Load existing entries from buffer
        loadExistingEntries();

        // Register listener for new entries
        listener = entry -> SwingUtilities.invokeLater(() -> {
            totalCount++;
            if (isLevelVisible(entry.level()) && matchesSearch(entry.message())) {
                appendLogEntry(entry);
                visibleCount++;
            }
            updateCountLabel();
        });
        LoggingConfig.BUFFER_APPENDER.setListener(listener);

        // ESC to close
        getRootPane().registerKeyboardAction(
                _ -> setVisible(false),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void loadExistingEntries() {
        List<LogBufferAppender.LogEntry> entries = LoggingConfig.BUFFER_APPENDER.getEntries();
        totalCount = entries.size();
        visibleCount = 0;

        for (LogBufferAppender.LogEntry entry : entries) {
            if (isLevelVisible(entry.level()) && matchesSearch(entry.message())) {
                appendLogEntry(entry);
                visibleCount++;
            }
        }
        updateCountLabel();
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

    private void appendLogEntry(LogBufferAppender.LogEntry entry) {
        try {
            doc.insertString(doc.getLength(), "[" + entry.timestamp() + "] ", timestampStyle);
            Style levelStyle = getStyleForLevel(entry.level());
            String levelText = String.format("[%-7s] ", entry.level());
            doc.insertString(doc.getLength(), levelText, levelStyle);
            doc.insertString(doc.getLength(), "[" + entry.loggerName() + "] ", timestampStyle);
            doc.insertString(doc.getLength(), entry.message() + "\n", levelStyle);
            logTextPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private Style getStyleForLevel(String level) {
        return switch (level) {
            case "DEBUG", "TRACE", "FINE" -> fineStyle;
            case "WARN" -> warningStyle;
            case "ERROR", "FATAL" -> severeStyle;
            default -> infoStyle;
        };
    }

    private boolean isLevelVisible(String level) {
        return switch (level) {
            case "DEBUG", "TRACE", "FINE" -> fineCheckBox.isSelected();
            case "WARN" -> warningCheckBox.isSelected();
            case "ERROR", "FATAL" -> severeCheckBox.isSelected();
            default -> infoCheckBox.isSelected();
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
                visibleCount = 0;

                for (LogBufferAppender.LogEntry entry : LoggingConfig.BUFFER_APPENDER.getEntries()) {
                    if (isLevelVisible(entry.level()) && matchesSearch(entry.message())) {
                        appendLogEntry(entry);
                        visibleCount++;
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
            LoggingConfig.BUFFER_APPENDER.clear();
            totalCount = 0;
            visibleCount = 0;
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException e) {
                // Ignore
            }
            updateCountLabel();
        });
    }

    private void updateCountLabel() {
        countLabel.setText(i18n.getMessage("logwindow.count", visibleCount, totalCount));
    }

    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("logwindow.title"));
            searchField.setToolTipText(i18n.getMessage("logwindow.search.tooltip"));
        });
    }

    @Override
    public void dispose() {
        LoggingConfig.BUFFER_APPENDER.setListener(null);
        listener = null;
        super.dispose();
    }
}
