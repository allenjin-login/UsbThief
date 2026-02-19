package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.storage.StorageLevel;
import com.superredrock.usbthief.worker.StorageController;
import com.superredrock.usbthief.worker.StorageController.StorageStatus;
import com.superredrock.usbthief.core.SizeFormatter;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Storage Management Panel - Configuration panel for storage settings with live status display.
 * <p>
 * Provides UI for configuring storage management parameters including reserved space,
 * maximum copy space, wait times, and recycling strategy. Displays live storage status
 * with progress bar and detailed space information.
 * <p>
 * Implements LocaleChangeListener for hot language switching support.
 */
public class StorageManagementPanel extends JPanel implements I18NManager.LocaleChangeListener {

    private static final I18NManager i18n = I18NManager.getInstance();

    // UI Components - Status Section
    private JProgressBar storageProgressBar;
    private JLabel freeSpaceLabel;
    private JLabel usedSpaceLabel;
    private JLabel totalSpaceLabel;
    private JLabel statusLabel;

    // UI Components - Configuration
    private JSpinner reservedSpaceSpinner;
    private JSpinner maxSpaceSpinner;
    private JSpinner normalWaitSpinner;
    private JSpinner errorWaitSpinner;
    private JComboBox<String> strategyComboBox;
    private JSpinner protectedAgeSpinner;
    private JCheckBox warningEnabledCheckBox;

    // UI Components - Buttons
    private JButton saveButton;
    private JButton resetButton;

    // Timer for periodic storage status updates
    private final Timer statusUpdateTimer;

    /**
     * Create the storage management panel.
     * Initializes all UI components, loads current configuration,
     * and starts the status update timer.
     */
    public StorageManagementPanel() {
        initComponents();
        loadCurrentConfig();
        updateStorageStatus();

        // Timer to update storage status every 5 seconds (EDT-safe)
        statusUpdateTimer = new Timer(5000, e -> updateStorageStatus());
        statusUpdateTimer.start();

        // Register for locale change events
        i18n.addLocaleChangeListener(this);
    }

    /**
     * Initialize all UI components.
     * Uses GridBagLayout for precise positioning with consistent spacing.
     */
    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Title
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel(i18n.getMessage("storage.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(titleLabel, gbc);
        row++;

        gbc.gridwidth = 1;

        // Separator
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createVerticalStrut(10), gbc);
        row++;

        // ========== CURRENT STATUS SECTION ==========
        JLabel statusTitleLabel = new JLabel(i18n.getMessage("storage.currentStatus"));
        statusTitleLabel.setFont(statusTitleLabel.getFont().deriveFont(Font.BOLD, 12f));
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(statusTitleLabel, gbc);
        row++;

        // Progress bar with status
        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        storageProgressBar = new JProgressBar(0, 100);
        storageProgressBar.setStringPainted(true);
        statusPanel.add(storageProgressBar, BorderLayout.NORTH);

        // Space information labels
        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        freeSpaceLabel = new JLabel();
        usedSpaceLabel = new JLabel();
        totalSpaceLabel = new JLabel();
        statusLabel = new JLabel();
        infoPanel.add(freeSpaceLabel);
        infoPanel.add(usedSpaceLabel);
        infoPanel.add(totalSpaceLabel);
        infoPanel.add(statusLabel);
        statusPanel.add(infoPanel, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(statusPanel, gbc);
        row++;

        gbc.gridwidth = 1;

        // Separator
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createVerticalStrut(10), gbc);
        row++;

        gbc.gridwidth = 1;

        // ========== CONFIGURATION SECTION ==========

        // Reserved Space (GB)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel reservedSpaceLabel = new JLabel(i18n.getMessage("storage.reservedSpace") + ":");
        reservedSpaceLabel.setToolTipText(i18n.getMessage("storage.reservedSpace.tooltip"));
        add(reservedSpaceLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        reservedSpaceSpinner = createGigabyteSpinner(10, 1, 10000);
        add(reservedSpaceSpinner, gbc);
        row++;

        // Max Copy Space (GB)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel maxSpaceLabel = new JLabel(i18n.getMessage("storage.maxSpace") + ":");
        maxSpaceLabel.setToolTipText(i18n.getMessage("storage.maxSpace.tooltip"));
        add(maxSpaceLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        maxSpaceSpinner = createGigabyteSpinner(100, 1, 100000);
        add(maxSpaceSpinner, gbc);
        row++;

        // Normal Wait Time (minutes)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel normalWaitLabel = new JLabel(i18n.getMessage("storage.normalWait") + ":");
        normalWaitLabel.setToolTipText(i18n.getMessage("storage.normalWait.tooltip"));
        add(normalWaitLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        normalWaitSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 10000, 1));
        add(normalWaitSpinner, gbc);
        row++;

        // Error Wait Time (minutes)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel errorWaitLabel = new JLabel(i18n.getMessage("storage.errorWait") + ":");
        errorWaitLabel.setToolTipText(i18n.getMessage("storage.errorWait.tooltip"));
        add(errorWaitLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        errorWaitSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
        add(errorWaitSpinner, gbc);
        row++;

        // Recycle Strategy
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel strategyLabel = new JLabel(i18n.getMessage("storage.strategy") + ":");
        strategyLabel.setToolTipText(i18n.getMessage("storage.strategy.tooltip"));
        add(strategyLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        strategyComboBox = new JComboBox<>(new String[]{
                i18n.getMessage("storage.strategy.timeFirst"),
                i18n.getMessage("storage.strategy.sizeFirst"),
                i18n.getMessage("storage.strategy.auto")
        });
        add(strategyComboBox, gbc);
        row++;

        // Protected File Age (hours)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel protectedAgeLabel = new JLabel(i18n.getMessage("storage.protectedAge") + ":");
        protectedAgeLabel.setToolTipText(i18n.getMessage("storage.protectedAge.tooltip"));
        add(protectedAgeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        protectedAgeSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10000, 1));
        add(protectedAgeSpinner, gbc);
        row++;

        // Enable Warnings checkbox
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        warningEnabledCheckBox = new JCheckBox(i18n.getMessage("storage.warningEnabled"));
        warningEnabledCheckBox.setToolTipText(i18n.getMessage("storage.warningEnabled.tooltip"));
        add(warningEnabledCheckBox, gbc);
        row++;

        gbc.gridwidth = 1;

        // Separator
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createVerticalStrut(10), gbc);
        row++;

        // ========== BUTTONS ==========
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        resetButton = new JButton(i18n.getMessage("storage.button.reset"));
        resetButton.addActionListener(e -> resetToDefaults());

        saveButton = new JButton(i18n.getMessage("storage.button.save"));
        saveButton.addActionListener(e -> saveConfig());

        buttonPanel.add(resetButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(buttonPanel, gbc);
        row++;

        // Add vertical glue to push everything up
        gbc.gridy = row;
        gbc.weighty = 1.0;
        add(Box.createVerticalGlue(), gbc);
    }

    /**
     * Create a spinner configured for GB values.
     *
     * @param defaultValue default value in GB
     * @param minValue minimum value in GB
     * @param maxValue maximum value in GB
     * @return configured JSpinner
     */
    private JSpinner createGigabyteSpinner(int defaultValue, int minValue, int maxValue) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(defaultValue, minValue, maxValue, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
        spinner.setEditor(editor);
        return spinner;
    }

    /**
     * Load current configuration values from ConfigManager.
     */
    private void loadCurrentConfig() {
        ConfigManager config = ConfigManager.getInstance();

        // Reserved Space (convert bytes to GB)
        long reservedBytes = config.get(ConfigSchema.STORAGE_RESERVED_BYTES);
        reservedSpaceSpinner.setValue(reservedBytes / (1024 * 1024 * 1024));

        // Max Copy Space (convert bytes to GB)
        long maxBytes = config.get(ConfigSchema.STORAGE_MAX_BYTES);
        maxSpaceSpinner.setValue(maxBytes / (1024 * 1024 * 1024));

        // Wait times (minutes)
        normalWaitSpinner.setValue(config.get(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES));
        errorWaitSpinner.setValue(config.get(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES));

        // Recycler Strategy
        String strategy = config.get(ConfigSchema.RECYCLER_STRATEGY);
        updateStrategyComboBox(strategy);

        // Protected Age (hours)
        protectedAgeSpinner.setValue(config.get(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS));

        // Warning Enabled
        warningEnabledCheckBox.setSelected(config.get(ConfigSchema.STORAGE_WARNING_ENABLED));
    }

    /**
     * Update strategy combo box selection based on current config value.
     *
     * @param strategy current strategy value (TIME_FIRST, SIZE_FIRST, or AUTO)
     */
    private void updateStrategyComboBox(String strategy) {
        int index = switch (strategy) {
            case "TIME_FIRST" -> 0;
            case "SIZE_FIRST" -> 1;
            case "AUTO" -> 2;
            default -> 2; // Default to AUTO
        };
        strategyComboBox.setSelectedIndex(index);
    }

    /**
     * Update storage status display.
     * Calls StorageController to get current status and updates UI components.
     * Wrapped in SwingUtilities.invokeLater for EDT safety.
     */
    private void updateStorageStatus() {
        SwingUtilities.invokeLater(() -> {
            StorageStatus status = StorageController.getInstance().getStorageStatus();

            // Update progress bar (show used percentage)
            long total = status.totalBytes();
            long used = status.usedBytes();
            int usedPercent = total > 0 ? (int) ((used * 100) / total) : 0;
            storageProgressBar.setValue(usedPercent);
            storageProgressBar.setString(usedPercent + "%");

            // Update progress bar color based on storage level
            updateProgressBarColor(status.level());

            // Update space labels
            freeSpaceLabel.setText(i18n.getMessage("storage.freeSpace") + ": " +
                    SizeFormatter.format(status.freeBytes()));
            usedSpaceLabel.setText(i18n.getMessage("storage.usedSpace") + ": " +
                    SizeFormatter.format(status.usedBytes()));
            totalSpaceLabel.setText(i18n.getMessage("storage.totalSpace") + ": " +
                    SizeFormatter.format(status.totalBytes()));

            // Update status label
            String levelText = switch (status.level()) {
                case OK -> i18n.getMessage("storage.level.ok");
                case LOW -> i18n.getMessage("storage.level.low");
                case CRITICAL -> i18n.getMessage("storage.level.critical");
            };
            statusLabel.setText(i18n.getMessage("storage.level") + ": " + levelText);
            statusLabel.setForeground(status.level() == StorageLevel.CRITICAL ? Color.RED :
                    status.level() == StorageLevel.LOW ? Color.ORANGE : Color.GREEN);
        });
    }

    /**
     * Update progress bar color based on storage level.
     *
     * @param level current storage level
     */
    private void updateProgressBarColor(StorageLevel level) {
        Color color = switch (level) {
            case OK -> Color.GREEN;
            case LOW -> Color.ORANGE;
            case CRITICAL -> Color.RED;
        };
        storageProgressBar.setForeground(color);
    }

    /**
     * Save current configuration values to ConfigManager.
     * Converts GB values to bytes for storage configuration entries.
     */
    private void saveConfig() {
        ConfigManager config = ConfigManager.getInstance();

        // Reserved Space (convert GB to bytes)
        int reservedGB = (Integer) reservedSpaceSpinner.getValue();
        config.set(ConfigSchema.STORAGE_RESERVED_BYTES, (long) reservedGB * 1024 * 1024 * 1024);

        // Max Copy Space (convert GB to bytes)
        int maxGB = (Integer) maxSpaceSpinner.getValue();
        config.set(ConfigSchema.STORAGE_MAX_BYTES, (long) maxGB * 1024 * 1024 * 1024);

        // Wait times
        config.set(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES, (Integer) normalWaitSpinner.getValue());
        config.set(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES, (Integer) errorWaitSpinner.getValue());

        // Recycler Strategy
        int strategyIndex = strategyComboBox.getSelectedIndex();
        String strategy = switch (strategyIndex) {
            case 0 -> "TIME_FIRST";
            case 1 -> "SIZE_FIRST";
            case 2 -> "AUTO";
            default -> "AUTO";
        };
        config.set(ConfigSchema.RECYCLER_STRATEGY, strategy);

        // Protected Age
        config.set(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS, (Integer) protectedAgeSpinner.getValue());

        // Warning Enabled
        config.set(ConfigSchema.STORAGE_WARNING_ENABLED, warningEnabledCheckBox.isSelected());

        JOptionPane.showMessageDialog(this,
                i18n.getMessage("config.success"),
                i18n.getMessage("common.success"),
                JOptionPane.INFORMATION_MESSAGE);

        // Refresh storage status after saving
        updateStorageStatus();
    }

    /**
     * Reset configuration to default values.
     * Confirms with user before resetting.
     */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
                i18n.getMessage("config.reset.confirm"),
                i18n.getMessage("config.reset.confirm.title"),
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            ConfigManager.getInstance().resetToDefaults();
            loadCurrentConfig();
            updateStorageStatus();
            JOptionPane.showMessageDialog(this,
                    i18n.getMessage("config.reset.success"),
                    i18n.getMessage("common.success"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Refresh all language-dependent text.
     * Called when locale changes via I18NManager.
     */
    @Override
    public void onLocaleChanged(Locale newLocale) {
        refreshLanguage();
    }

    /**
     * Refresh all UI text with current locale.
     * Must be called on EDT via SwingUtilities.invokeLater.
     */
    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            // Update tooltips
            reservedSpaceSpinner.setToolTipText(i18n.getMessage("storage.reservedSpace.tooltip"));
            maxSpaceSpinner.setToolTipText(i18n.getMessage("storage.maxSpace.tooltip"));
            normalWaitSpinner.setToolTipText(i18n.getMessage("storage.normalWait.tooltip"));
            errorWaitSpinner.setToolTipText(i18n.getMessage("storage.errorWait.tooltip"));
            strategyComboBox.setToolTipText(i18n.getMessage("storage.strategy.tooltip"));
            protectedAgeSpinner.setToolTipText(i18n.getMessage("storage.protectedAge.tooltip"));
            warningEnabledCheckBox.setToolTipText(i18n.getMessage("storage.warningEnabled.tooltip"));

            // Update combo box items
            strategyComboBox.setModel(new DefaultComboBoxModel<>(new String[]{
                    i18n.getMessage("storage.strategy.timeFirst"),
                    i18n.getMessage("storage.strategy.sizeFirst"),
                    i18n.getMessage("storage.strategy.auto")
            }));

            // Update checkbox text
            warningEnabledCheckBox.setText(i18n.getMessage("storage.warningEnabled"));

            // Update button text
            resetButton.setText(i18n.getMessage("storage.button.reset"));
            saveButton.setText(i18n.getMessage("storage.button.save"));

            // Refresh status display
            updateStorageStatus();
        });
    }

    /**
     * Clean up resources when panel is no longer needed.
     * Stops the status update timer and removes locale change listener.
     */
    public void cleanup() {
        statusUpdateTimer.stop();
        i18n.removeLocaleChangeListener(this);
    }
}
