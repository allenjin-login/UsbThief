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
    private JSlider reservedSpaceSlider;
    private JLabel reservedSpaceValueLabel;
    private JSlider maxSpaceSlider;
    private JLabel maxSpaceValueLabel;
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
        JLabel configTitleLabel = new JLabel("⚙️ " + i18n.getMessage("storage.title"));
        configTitleLabel.setFont(configTitleLabel.getFont().deriveFont(Font.BOLD, 12f));
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(configTitleLabel, gbc);
        row++;

        // Reserved Space (GB) with slider
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel reservedSpaceLabel = new JLabel("💾 " + i18n.getMessage("storage.reservedSpace") + ":");
        reservedSpaceLabel.setToolTipText(i18n.getMessage("storage.reservedSpace.tooltip"));
        add(reservedSpaceLabel, gbc);

        // Get disk info for dynamic slider range
        StorageStatus diskStatus = StorageController.getInstance().getStorageStatus();
        long freeGB = diskStatus.freeBytes() / (1024L * 1024L * 1024L);
        long totalGB = diskStatus.totalBytes() / (1024L * 1024L * 1024L);
        
        // Reserved space: max is min(free space, 500GB), default 10GB or half of max
        int reservedMax = (int) Math.max(1, Math.min(freeGB, 500));
        int reservedDefault = Math.min(10, reservedMax / 2);
        
        JPanel reservedPanel = new JPanel(new BorderLayout(5, 0));
        reservedSpaceSlider = new JSlider(0, reservedMax, reservedDefault);
        reservedSpaceSlider.setMajorTickSpacing(Math.max(1, reservedMax / 5));
        reservedSpaceSlider.setMinorTickSpacing(Math.max(1, reservedMax / 20));
        reservedSpaceSlider.setPaintTicks(true);
        reservedSpaceSlider.setPaintLabels(false);
        
        reservedSpaceValueLabel = new JLabel(reservedDefault + " " + i18n.getMessage("storage.unit.gb"));
        reservedSpaceValueLabel.setPreferredSize(new Dimension(80, reservedSpaceValueLabel.getPreferredSize().height));
        
        reservedSpaceSlider.addChangeListener(e -> {
            int value = reservedSpaceSlider.getValue();
            reservedSpaceValueLabel.setText(value + " " + i18n.getMessage("storage.unit.gb"));
            // Ensure max >= reserved
            if (maxSpaceSlider.getValue() < value) {
                maxSpaceSlider.setValue(value);
            }
        });
        
        reservedPanel.add(reservedSpaceSlider, BorderLayout.CENTER);
        reservedPanel.add(reservedSpaceValueLabel, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(reservedPanel, gbc);
        row++;

        // Info text for reserved space
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        JLabel reservedInfoLabel = new JLabel("<html><i><font color='gray'>" + i18n.getMessage("storage.info.reservedSpace") + "</font></i></html>");
        reservedInfoLabel.setFont(reservedInfoLabel.getFont().deriveFont(10f));
        add(reservedInfoLabel, gbc);
        row++;

        // Max Copy Space (GB) with slider
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel maxSpaceLabel = new JLabel("📊 " + i18n.getMessage("storage.maxSpace") + ":");
        maxSpaceLabel.setToolTipText(i18n.getMessage("storage.maxSpace.tooltip"));
        add(maxSpaceLabel, gbc);

        // Max space: max is min(total space, 2000GB), default 100GB or half of max
        int maxMax = (int) Math.max(1, Math.min(totalGB, 2000));
        int maxDefault = Math.min(100, maxMax / 2);
        
        JPanel maxPanel = new JPanel(new BorderLayout(5, 0));
        maxSpaceSlider = new JSlider(1, maxMax, maxDefault);
        maxSpaceSlider.setMajorTickSpacing(Math.max(1, maxMax / 5));
        maxSpaceSlider.setMinorTickSpacing(Math.max(1, maxMax / 20));
        maxSpaceSlider.setPaintTicks(true);
        maxSpaceSlider.setPaintLabels(false);
        
        maxSpaceValueLabel = new JLabel(maxDefault + " " + i18n.getMessage("storage.unit.gb"));
        maxSpaceValueLabel.setPreferredSize(new Dimension(80, maxSpaceValueLabel.getPreferredSize().height));
        
        maxSpaceSlider.addChangeListener(e -> {
            int value = maxSpaceSlider.getValue();
            maxSpaceValueLabel.setText(value + " " + i18n.getMessage("storage.unit.gb"));
            // Ensure reserved <= max
            if (reservedSpaceSlider.getValue() > value) {
                reservedSpaceSlider.setValue(value);
            }
        });
        
        maxPanel.add(maxSpaceSlider, BorderLayout.CENTER);
        maxPanel.add(maxSpaceValueLabel, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(maxPanel, gbc);
        row++;

        // Info text for max space
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        JLabel maxInfoLabel = new JLabel("<html><i><font color='gray'>" + i18n.getMessage("storage.info.maxSpace") + "</font></i></html>");
        maxInfoLabel.setFont(maxInfoLabel.getFont().deriveFont(10f));
        add(maxInfoLabel, gbc);
        row++;

        // Normal Wait Time (minutes) with unit label
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel normalWaitLabel = new JLabel("⏱️ " + i18n.getMessage("storage.normalWait") + ":");
        normalWaitLabel.setToolTipText(i18n.getMessage("storage.normalWait.tooltip"));
        add(normalWaitLabel, gbc);

        JPanel normalWaitPanel = new JPanel(new BorderLayout(5, 0));
        normalWaitSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 10000, 1));
        JLabel normalWaitUnitLabel = new JLabel(i18n.getMessage("storage.unit.minutes"));
        normalWaitUnitLabel.setForeground(java.awt.Color.GRAY);
        normalWaitPanel.add(normalWaitSpinner, BorderLayout.CENTER);
        normalWaitPanel.add(normalWaitUnitLabel, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(normalWaitPanel, gbc);
        row++;

        // Error Wait Time (minutes) with unit label
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel errorWaitLabel = new JLabel("⚠️ " + i18n.getMessage("storage.errorWait") + ":");
        errorWaitLabel.setToolTipText(i18n.getMessage("storage.errorWait.tooltip"));
        add(errorWaitLabel, gbc);

        JPanel errorWaitPanel = new JPanel(new BorderLayout(5, 0));
        errorWaitSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
        JLabel errorWaitUnitLabel = new JLabel(i18n.getMessage("storage.unit.minutes"));
        errorWaitUnitLabel.setForeground(java.awt.Color.GRAY);
        errorWaitPanel.add(errorWaitSpinner, BorderLayout.CENTER);
        errorWaitPanel.add(errorWaitUnitLabel, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(errorWaitPanel, gbc);
        row++;

        // Recycle Strategy
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel strategyLabel = new JLabel("🔄 " + i18n.getMessage("storage.strategy") + ":");
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

        // Protected File Age (hours) with unit label
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel protectedAgeLabel = new JLabel("🛡️ " + i18n.getMessage("storage.protectedAge") + ":");
        protectedAgeLabel.setToolTipText(i18n.getMessage("storage.protectedAge.tooltip"));
        add(protectedAgeLabel, gbc);

        JPanel protectedAgePanel = new JPanel(new BorderLayout(5, 0));
        protectedAgeSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10000, 1));
        JLabel protectedAgeUnitLabel = new JLabel(i18n.getMessage("storage.unit.hours"));
        protectedAgeUnitLabel.setForeground(java.awt.Color.GRAY);
        protectedAgePanel.add(protectedAgeSpinner, BorderLayout.CENTER);
        protectedAgePanel.add(protectedAgeUnitLabel, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(protectedAgePanel, gbc);
        row++;

        // Enable Warnings checkbox
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        warningEnabledCheckBox = new JCheckBox("🔔 " + i18n.getMessage("storage.warningEnabled"));
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
     * Load current configuration values from ConfigManager.
     */
    private void loadCurrentConfig() {
        ConfigManager config = ConfigManager.getInstance();

        // Reserved Space (convert bytes to GB)
        long reservedBytes = config.get(ConfigSchema.STORAGE_RESERVED_BYTES);
        int reservedGB = (int) (reservedBytes / (1024 * 1024 * 1024));
        reservedSpaceSlider.setValue(Math.min(reservedGB, reservedSpaceSlider.getMaximum()));
        reservedSpaceValueLabel.setText(reservedSpaceSlider.getValue() + " " + i18n.getMessage("storage.unit.gb"));

        // Max Copy Space (convert bytes to GB)
        long maxBytes = config.get(ConfigSchema.STORAGE_MAX_BYTES);
        int maxGB = (int) (maxBytes / (1024 * 1024 * 1024));
        maxSpaceSlider.setValue(Math.min(maxGB, maxSpaceSlider.getMaximum()));
        maxSpaceValueLabel.setText(maxSpaceSlider.getValue() + " " + i18n.getMessage("storage.unit.gb"));

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
     * Validates input values before saving.
     */
    private void saveConfig() {
        // Get current storage status for validation
        StorageStatus status = StorageController.getInstance().getStorageStatus();
        long freeBytes = status.freeBytes();
        long totalBytes = status.totalBytes();

        // Get values from sliders
        int reservedGB = reservedSpaceSlider.getValue();
        int maxGB = maxSpaceSlider.getValue();

        // Convert to bytes for validation
        long reservedBytes = (long) reservedGB * 1024L * 1024L * 1024L;
        long maxBytes = (long) maxGB * 1024L * 1024L * 1024L;

        // Validation: reserved space cannot exceed free space
        if (reservedBytes > freeBytes) {
            JOptionPane.showMessageDialog(this,
                    i18n.getMessage("storage.error.reservedExceedsFree",
                            SizeFormatter.format(reservedBytes),
                            SizeFormatter.format(freeBytes)),
                    i18n.getMessage("common.error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validation: max space cannot exceed total disk space
        if (maxBytes > totalBytes) {
            JOptionPane.showMessageDialog(this,
                    i18n.getMessage("storage.error.maxExceedsTotal",
                            SizeFormatter.format(maxBytes),
                            SizeFormatter.format(totalBytes)),
                    i18n.getMessage("common.error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validation: max space must be greater than reserved space
        if (maxBytes < reservedBytes) {
            JOptionPane.showMessageDialog(this,
                    i18n.getMessage("storage.error.maxBelowReserved",
                            SizeFormatter.format(maxBytes),
                            SizeFormatter.format(reservedBytes)),
                    i18n.getMessage("common.error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        ConfigManager config = ConfigManager.getInstance();

        // Reserved Space (convert GB to bytes)
        config.set(ConfigSchema.STORAGE_RESERVED_BYTES, reservedBytes);

        // Max Copy Space (convert GB to bytes)
        config.set(ConfigSchema.STORAGE_MAX_BYTES, maxBytes);

        // Wait times
        config.set(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES, ((Number) normalWaitSpinner.getValue()).intValue());
        config.set(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES, ((Number) errorWaitSpinner.getValue()).intValue());

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
        config.set(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS, ((Number) protectedAgeSpinner.getValue()).intValue());

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
            ConfigManager config = ConfigManager.getInstance();
            // Reset only storage-related config keys, not all config
            config.clear(ConfigSchema.STORAGE_RESERVED_BYTES);
            config.clear(ConfigSchema.STORAGE_MAX_BYTES);
            config.clear(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES);
            config.clear(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES);
            config.clear(ConfigSchema.RECYCLER_STRATEGY);
            config.clear(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS);
            config.clear(ConfigSchema.STORAGE_WARNING_ENABLED);

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
            reservedSpaceSlider.setToolTipText(i18n.getMessage("storage.reservedSpace.tooltip"));
            maxSpaceSlider.setToolTipText(i18n.getMessage("storage.maxSpace.tooltip"));
            normalWaitSpinner.setToolTipText(i18n.getMessage("storage.normalWait.tooltip"));
            errorWaitSpinner.setToolTipText(i18n.getMessage("storage.errorWait.tooltip"));
            strategyComboBox.setToolTipText(i18n.getMessage("storage.strategy.tooltip"));
            protectedAgeSpinner.setToolTipText(i18n.getMessage("storage.protectedAge.tooltip"));
            warningEnabledCheckBox.setToolTipText(i18n.getMessage("storage.warningEnabled.tooltip"));

            // Update slider value labels
            reservedSpaceValueLabel.setText(reservedSpaceSlider.getValue() + " " + i18n.getMessage("storage.unit.gb"));
            maxSpaceValueLabel.setText(maxSpaceSlider.getValue() + " " + i18n.getMessage("storage.unit.gb"));

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
