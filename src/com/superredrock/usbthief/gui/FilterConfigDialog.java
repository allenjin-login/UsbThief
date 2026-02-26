package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.filter.FilterPreset;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Dialog for configuring file filter settings.
 * Contains two tabs: Basic Filter and Suffix Filter.
 */
public class FilterConfigDialog extends JDialog implements I18NManager.LocaleChangeListener {

    private static final I18NManager i18n = I18NManager.getInstance();
    private final ConfigManager configManager;

    // Basic filter controls - Size
    private JSlider maxSizeSlider;
    private JSpinner maxSizeSpinner;
    private JComboBox<String> sizeUnitComboBox;
    private JLabel maxSizeLabel;

    // Basic filter controls - Time
    private JCheckBox timeEnabledCheckBox;
    private JSlider timeSlider;
    private JSpinner timeSpinner;
    private JComboBox<String> timeUnitComboBox;
    private JLabel timeWithinLabel;

    // Basic filter controls - Other
    private JCheckBox includeHiddenCheckBox;
    private JCheckBox skipSymlinksCheckBox;

    // Suffix filter controls
    private JComboBox<String> modeComboBox;
    private JComboBox<String> presetComboBox;
    private JCheckBox allowNoExtCheckBox;
    private JList<String> extensionList;
    private DefaultListModel<String> extensionListModel;
    private JTextField extensionField;

    // Buttons
    private JButton saveButton;
    private JButton resetButton;

    // Time unit ranges: [min, max, majorTick, minorTick, labelStep]
    private static final int[][] TIME_RANGES = {
        {1, 168, 24, 6, 48},   // HOURS: 1小时 - 1周, 标签: 1, 48, 96, 144, 168
        {1, 90, 15, 5, 30},    // DAYS: 1天 - 3个月, 标签: 1, 30, 60, 90
        {1, 52, 10, 2, 13},    // WEEKS: 1周 - 1年, 标签: 1, 13, 26, 39, 52
        {1, 36, 6, 1, 12},     // MONTHS: 1月 - 3年, 标签: 1, 12, 24, 36
        {1, 10, 2, 1, 3}       // YEARS: 1年 - 10年, 标签: 1, 3, 6, 9, 10
    };

    // Size unit ranges: [min, max, labelStep]
    private static final int[][] SIZE_RANGES = {
        {1, 1000, 250},  // MB: 标签 1, 250, 500, 750, 1000
        {1, 100, 25}     // GB: 标签 1, 25, 50, 75, 100
    };

    // Size unit multipliers (to bytes)
    private static final long[] SIZE_MULTIPLIERS = {
        1024 * 1024,        // MB
        1024L * 1024 * 1024 // GB
    };

    /**
     * Creates a new filter configuration dialog.
     *
     * @param parent parent frame
     */
    public FilterConfigDialog(JFrame parent) {
        super(parent, i18n.getMessage("filter.dialog.title"), true);
        this.configManager = ConfigManager.getInstance();
        
        setSize(600, 550);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));
        
        // Register for locale changes
        i18n.addLocaleChangeListener(this);

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(i18n.getMessage("filter.basic.title"), createBasicFilterPanel());
        tabbedPane.addTab(i18n.getMessage("filter.suffix.title"), createSuffixFilterPanel());

        // Create button panel
        JPanel buttonPanel = createButtonPanel();

        // Add components
        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Load current settings
        loadSettings();
    }

    /**
     * Creates the basic filter panel with size, time, hidden, and symlink controls.
     */
    private JPanel createBasicFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // === Max file size section ===
        maxSizeLabel = new JLabel(i18n.getMessage("filter.basic.maxSize"));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(maxSizeLabel, gbc);

        // Size slider (MB default)
        maxSizeSlider = createSlider(1, 1000, 100, 250, 50, 250);
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        panel.add(maxSizeSlider, gbc);

        // Size spinner
        maxSizeSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 1));
        maxSizeSpinner.setPreferredSize(new Dimension(70, 25));
        gbc.gridx = 2; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(maxSizeSpinner, gbc);

        // Size unit combo box
        sizeUnitComboBox = new JComboBox<>(new String[]{
            i18n.getMessage("filter.basic.unit.mb"),
            i18n.getMessage("filter.basic.unit.gb")
        });
        sizeUnitComboBox.setPreferredSize(new Dimension(60, 25));
        sizeUnitComboBox.addActionListener(e -> updateSizeSliderRange());
        gbc.gridx = 3; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(sizeUnitComboBox, gbc);

        // Sync size slider and spinner
        maxSizeSlider.addChangeListener(e -> {
            int value = maxSizeSlider.getValue();
            if ((Integer) maxSizeSpinner.getValue() != value) {
                maxSizeSpinner.setValue(value);
            }
        });
        maxSizeSpinner.addChangeListener(e -> {
            int value = (Integer) maxSizeSpinner.getValue();
            if (maxSizeSlider.getValue() != value) {
                maxSizeSlider.setValue(value);
            }
        });

        // === Time filter section ===
        row++;
        timeEnabledCheckBox = new JCheckBox(i18n.getMessage("filter.basic.timeEnabled"));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.weightx = 0;
        panel.add(timeEnabledCheckBox, gbc);

        // Time within label
        row++;
        timeWithinLabel = new JLabel(i18n.getMessage("filter.basic.timeWithin"));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(timeWithinLabel, gbc);

        // Time slider (initial range for hours)
        timeSlider = createSlider(1, 168, 24, 24, 6, 48);
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        panel.add(timeSlider, gbc);

        // Time spinner
        timeSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 168, 1));
        timeSpinner.setPreferredSize(new Dimension(70, 25));
        gbc.gridx = 2; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(timeSpinner, gbc);

        // Time unit combo box
        timeUnitComboBox = new JComboBox<>(new String[]{
            i18n.getMessage("filter.basic.unit.hours"),
            i18n.getMessage("filter.basic.unit.days"),
            i18n.getMessage("filter.basic.unit.weeks"),
            i18n.getMessage("filter.basic.unit.months"),
            i18n.getMessage("filter.basic.unit.years")
        });
        timeUnitComboBox.setPreferredSize(new Dimension(60, 25));
        timeUnitComboBox.addActionListener(e -> updateTimeSliderRange());
        gbc.gridx = 3; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(timeUnitComboBox, gbc);

        // Sync time slider and spinner
        timeSlider.addChangeListener(e -> {
            int value = timeSlider.getValue();
            if ((Integer) timeSpinner.getValue() != value) {
                timeSpinner.setValue(value);
            }
        });
        timeSpinner.addChangeListener(e -> {
            int value = (Integer) timeSpinner.getValue();
            if (timeSlider.getValue() != value) {
                timeSlider.setValue(value);
            }
        });

        // === Other options ===
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        includeHiddenCheckBox = new JCheckBox(i18n.getMessage("filter.basic.includeHidden"));
        panel.add(includeHiddenCheckBox, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        skipSymlinksCheckBox = new JCheckBox(i18n.getMessage("filter.basic.skipSymlinks"));
        panel.add(skipSymlinksCheckBox, gbc);

        // Add vertical glue
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    /**
     * Creates a styled JSlider with labels.
     */
    private JSlider createSlider(int min, int max, int value, int majorTick, int minorTick, int labelStep) {
        JSlider slider = new JSlider(min, max, value);
        slider.setMajorTickSpacing(majorTick);
        slider.setMinorTickSpacing(minorTick);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        updateSliderLabels(slider, min, max, labelStep);
        return slider;
    }

    /**
     * Updates slider label table to show only key values.
     */
    private void updateSliderLabels(JSlider slider, int min, int max, int labelStep) {
        Dictionary<Integer, JLabel> labelTable = new Hashtable<>();
        
        // Always show min
        labelTable.put(min, new JLabel(String.valueOf(min)));
        
        // Show intermediate values at labelStep intervals
        for (int i = labelStep; i < max; i += labelStep) {
            labelTable.put(i, new JLabel(String.valueOf(i)));
        }
        
        // Always show max
        labelTable.put(max, new JLabel(String.valueOf(max)));
        
        slider.setLabelTable(labelTable);
    }

    /**
     * Updates the size slider range based on selected unit.
     */
    private void updateSizeSliderRange() {
        int unitIndex = sizeUnitComboBox.getSelectedIndex();
        int[] range = SIZE_RANGES[unitIndex];
        int value = (Integer) maxSizeSpinner.getValue();

        maxSizeSlider.setMinimum(range[0]);
        maxSizeSlider.setMaximum(range[1]);
        maxSizeSlider.setMajorTickSpacing(range[2]);
        maxSizeSlider.setMinorTickSpacing(range[2] / 5);
        updateSliderLabels(maxSizeSlider, range[0], range[1], range[2]);
        
        int clampedValue = Math.max(range[0], Math.min(value, range[1]));
        maxSizeSpinner.setModel(new SpinnerNumberModel(clampedValue, range[0], range[1], 1));
        maxSizeSlider.setValue(clampedValue);
    }

    /**
     * Updates the time slider range based on selected unit.
     */
    private void updateTimeSliderRange() {
        int unitIndex = timeUnitComboBox.getSelectedIndex();
        int[] range = TIME_RANGES[unitIndex];
        int value = (Integer) timeSpinner.getValue();

        timeSlider.setMinimum(range[0]);
        timeSlider.setMaximum(range[1]);
        timeSlider.setMajorTickSpacing(range[2]);
        timeSlider.setMinorTickSpacing(range[3]);
        updateSliderLabels(timeSlider, range[0], range[1], range[4]);
        
        int clampedValue = Math.max(range[0], Math.min(value, range[1]));
        timeSpinner.setModel(new SpinnerNumberModel(clampedValue, range[0], range[1], 1));
        timeSlider.setValue(clampedValue);
    }

    /**
     * Creates the suffix filter panel with mode, preset, and extension list controls.
     */
    private JPanel createSuffixFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Mode selection
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(new JLabel(i18n.getMessage("filter.suffix.mode")), gbc);

        modeComboBox = new JComboBox<>(new String[]{
            i18n.getMessage("filter.suffix.mode.none"),
            i18n.getMessage("filter.suffix.mode.whitelist"),
            i18n.getMessage("filter.suffix.mode.blacklist")
        });
        modeComboBox.addActionListener(e -> updateControlsState());

        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(modeComboBox, gbc);

        // Preset selection
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(i18n.getMessage("filter.suffix.preset")), gbc);

        presetComboBox = new JComboBox<>(new String[]{
            "",
            i18n.getMessage("filter.suffix.preset.documents"),
            i18n.getMessage("filter.suffix.preset.images"),
            i18n.getMessage("filter.suffix.preset.video"),
            i18n.getMessage("filter.suffix.preset.audio"),
            i18n.getMessage("filter.suffix.preset.archives"),
            i18n.getMessage("filter.suffix.preset.all")
        });
        presetComboBox.addActionListener(e -> applyPreset());

        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(presetComboBox, gbc);

        // Allow no extension
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        gbc.weightx = 0;
        allowNoExtCheckBox = new JCheckBox(i18n.getMessage("filter.suffix.allowNoExt"));
        panel.add(allowNoExtCheckBox, gbc);

        // Extension list
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        extensionListModel = new DefaultListModel<>();
        extensionList = new JList<>(extensionListModel);
        extensionList.setVisibleRowCount(8);
        extensionList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JScrollPane scrollPane = new JScrollPane(extensionList);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            i18n.getMessage("filter.suffix.list.border"),
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));

        panel.add(scrollPane, gbc);

        // Extension input and buttons
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        extensionField = new JTextField(15);
        panel.add(extensionField, gbc);

        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        JButton addButton = new JButton(i18n.getMessage("filter.suffix.add"));
        addButton.addActionListener(e -> addExtension());
        panel.add(addButton, gbc);

        gbc.gridx = 2; gbc.gridy = row; gbc.gridwidth = 1;
        JButton removeButton = new JButton(i18n.getMessage("filter.suffix.remove"));
        removeButton.addActionListener(e -> removeExtension());
        panel.add(removeButton, gbc);

        // Initialize control states
        updateControlsState();

        return panel;
    }

    /**
     * Creates the button panel with Save and Reset buttons.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        saveButton = new JButton(i18n.getMessage("filter.button.save"));
        saveButton.addActionListener(e -> saveSettings());

        resetButton = new JButton(i18n.getMessage("filter.button.reset"));
        resetButton.addActionListener(e -> resetToDefaults());

        panel.add(saveButton);
        panel.add(resetButton);

        return panel;
    }

    /**
     * Updates the enabled state of controls based on mode selection.
     */
    private void updateControlsState() {
        int modeIndex = modeComboBox.getSelectedIndex();
        boolean isNone = (modeIndex == 0);
        
        extensionList.setEnabled(!isNone);
        extensionField.setEnabled(!isNone);
        presetComboBox.setEnabled(!isNone);
    }

    /**
     * Applies the selected preset to the extension list.
     */
    private void applyPreset() {
        int presetIndex = presetComboBox.getSelectedIndex();
        if (presetIndex <= 0) {
            return; // No preset selected
        }

        FilterPreset[] presets = FilterPreset.values();
        if (presetIndex - 1 < presets.length) {
            FilterPreset preset = presets[presetIndex - 1];
            extensionListModel.clear();
            for (String ext : preset.getExtensions()) {
                extensionListModel.addElement(ext);
            }
        }
    }

    /**
     * Adds an extension from the text field to the list.
     */
    private void addExtension() {
        String ext = extensionField.getText().trim().toLowerCase(Locale.ROOT);
        if (ext.isEmpty()) {
            return;
        }

        // Remove leading dot if present
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }

        // Check for duplicates
        for (int i = 0; i < extensionListModel.size(); i++) {
            if (extensionListModel.getElementAt(i).equalsIgnoreCase(ext)) {
                return; // Already exists
            }
        }

        extensionListModel.addElement(ext);
        extensionField.setText("");
    }

    /**
     * Removes selected extensions from the list.
     */
    private void removeExtension() {
        int[] selectedIndices = extensionList.getSelectedIndices();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            extensionListModel.remove(selectedIndices[i]);
        }
    }

    /**
     * Loads current settings from ConfigManager.
     */
    private void loadSettings() {
        // Basic filter settings - Size
        long maxSizeBytes = configManager.get(ConfigSchema.FILE_FILTER_MAX_SIZE);

        // Determine if MB or GB
        long maxSizeGB = maxSizeBytes / (1024L * 1024 * 1024);
        long maxSizeMB = maxSizeBytes / (1024 * 1024);

        if (maxSizeGB > 0 && maxSizeBytes % (1024L * 1024 * 1024) == 0 && maxSizeGB <= 100) {
            sizeUnitComboBox.setSelectedIndex(1); // GB
            maxSizeSlider.setValue((int) maxSizeGB);
            maxSizeSpinner.setValue((int) maxSizeGB);
        } else {
            sizeUnitComboBox.setSelectedIndex(0); // MB
            int mb = (int) Math.min(maxSizeMB, 1000);
            maxSizeSlider.setValue(mb);
            maxSizeSpinner.setValue(mb);
        }

        // Time filter settings
        boolean timeEnabled = configManager.get(ConfigSchema.FILE_FILTER_TIME_ENABLED);
        timeEnabledCheckBox.setSelected(timeEnabled);

        long timeValue = configManager.get(ConfigSchema.FILE_FILTER_TIME_VALUE);
        String timeUnit = configManager.get(ConfigSchema.FILE_FILTER_TIME_UNIT);

        int timeUnitIndex = switch (timeUnit.toUpperCase(Locale.ROOT)) {
            case "HOURS" -> 0;
            case "DAYS" -> 1;
            case "WEEKS" -> 2;
            case "MONTHS" -> 3;
            case "YEARS" -> 4;
            default -> 0;
        };
        timeUnitComboBox.setSelectedIndex(timeUnitIndex);

        // Update slider range then set value
        updateTimeSliderRange();
        int timeValueInt = (int) Math.min(timeValue, TIME_RANGES[timeUnitIndex][1]);
        timeSlider.setValue(timeValueInt);
        timeSpinner.setValue(timeValueInt);

        includeHiddenCheckBox.setSelected(configManager.get(ConfigSchema.FILE_FILTER_INCLUDE_HIDDEN));
        skipSymlinksCheckBox.setSelected(configManager.get(ConfigSchema.FILE_FILTER_SKIP_SYMLINKS));

        // Suffix filter settings
        String mode = configManager.get(ConfigSchema.SUFFIX_FILTER_MODE);
        int modeIndex = switch (mode.toUpperCase(Locale.ROOT)) {
            case "NONE" -> 0;
            case "WHITELIST" -> 1;
            case "BLACKLIST" -> 2;
            default -> 0;
        };
        modeComboBox.setSelectedIndex(modeIndex);

        allowNoExtCheckBox.setSelected(configManager.get(ConfigSchema.FILE_FILTER_ALLOW_NO_EXT));

        // Load extensions
        extensionListModel.clear();
        List<String> extensions;
        if (mode.equalsIgnoreCase("WHITELIST")) {
            extensions = configManager.get(ConfigSchema.SUFFIX_FILTER_WHITELIST);
        } else if (mode.equalsIgnoreCase("BLACKLIST")) {
            extensions = configManager.get(ConfigSchema.SUFFIX_FILTER_BLACKLIST);
        } else {
            extensions = List.of();
        }
        for (String ext : extensions) {
            extensionListModel.addElement(ext);
        }

        // Load preset
        String preset = configManager.get(ConfigSchema.SUFFIX_FILTER_PRESET);
        if (!preset.isEmpty()) {
            try {
                FilterPreset filterPreset = FilterPreset.valueOf(preset.toUpperCase(Locale.ROOT));
                presetComboBox.setSelectedIndex(filterPreset.ordinal() + 1);
            } catch (IllegalArgumentException e) {
                presetComboBox.setSelectedIndex(0);
            }
        }
    }

    /**
     * Saves current settings to ConfigManager.
     */
    private void saveSettings() {
        // Basic filter settings - Size
        int sizeValue = (Integer) maxSizeSpinner.getValue();
        int sizeUnitIndex = sizeUnitComboBox.getSelectedIndex();
        long maxSizeBytes = sizeValue * SIZE_MULTIPLIERS[sizeUnitIndex];
        configManager.set(ConfigSchema.FILE_FILTER_MAX_SIZE, maxSizeBytes);

        // Time filter settings
        configManager.set(ConfigSchema.FILE_FILTER_TIME_ENABLED, timeEnabledCheckBox.isSelected());

        int timeValue = (Integer) timeSpinner.getValue();
        configManager.set(ConfigSchema.FILE_FILTER_TIME_VALUE, (long) timeValue);

        int timeUnitIndex = timeUnitComboBox.getSelectedIndex();
        String timeUnit = switch (timeUnitIndex) {
            case 0 -> "HOURS";
            case 1 -> "DAYS";
            case 2 -> "WEEKS";
            case 3 -> "MONTHS";
            case 4 -> "YEARS";
            default -> "HOURS";
        };
        configManager.set(ConfigSchema.FILE_FILTER_TIME_UNIT, timeUnit);

        configManager.set(ConfigSchema.FILE_FILTER_INCLUDE_HIDDEN, includeHiddenCheckBox.isSelected());
        configManager.set(ConfigSchema.FILE_FILTER_SKIP_SYMLINKS, skipSymlinksCheckBox.isSelected());

        // Suffix filter settings
        int modeIndex = modeComboBox.getSelectedIndex();
        String mode = switch (modeIndex) {
            case 0 -> "NONE";
            case 1 -> "WHITELIST";
            case 2 -> "BLACKLIST";
            default -> "NONE";
        };
        configManager.set(ConfigSchema.SUFFIX_FILTER_MODE, mode);

        configManager.set(ConfigSchema.FILE_FILTER_ALLOW_NO_EXT, allowNoExtCheckBox.isSelected());

        // Save extensions to appropriate list
        List<String> extensions = new ArrayList<>();
        for (int i = 0; i < extensionListModel.size(); i++) {
            extensions.add(extensionListModel.getElementAt(i));
        }

        if (mode.equalsIgnoreCase("WHITELIST")) {
            configManager.set(ConfigSchema.SUFFIX_FILTER_WHITELIST, extensions);
            configManager.set(ConfigSchema.SUFFIX_FILTER_BLACKLIST, List.of());
        } else if (mode.equalsIgnoreCase("BLACKLIST")) {
            configManager.set(ConfigSchema.SUFFIX_FILTER_BLACKLIST, extensions);
            configManager.set(ConfigSchema.SUFFIX_FILTER_WHITELIST, List.of());
        } else {
            configManager.set(ConfigSchema.SUFFIX_FILTER_WHITELIST, List.of());
            configManager.set(ConfigSchema.SUFFIX_FILTER_BLACKLIST, List.of());
        }

        // Save preset
        int presetIndex = presetComboBox.getSelectedIndex();
        if (presetIndex > 0) {
            FilterPreset[] presets = FilterPreset.values();
            if (presetIndex - 1 < presets.length) {
                configManager.set(ConfigSchema.SUFFIX_FILTER_PRESET, presets[presetIndex - 1].name());
            }
        } else {
            configManager.set(ConfigSchema.SUFFIX_FILTER_PRESET, "");
        }

        JOptionPane.showMessageDialog(
            this,
            i18n.getMessage("config.success"),
            i18n.getMessage("common.success"),
            JOptionPane.INFORMATION_MESSAGE
        );

        dispose();
    }

    /**
     * Resets all settings to default values.
     */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            i18n.getMessage("config.reset.confirm"),
            i18n.getMessage("config.reset.confirm.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            // Reset to default values
            sizeUnitComboBox.setSelectedIndex(0); // MB
            maxSizeSlider.setValue(100);
            maxSizeSpinner.setValue(100);

            timeEnabledCheckBox.setSelected(false);
            timeUnitComboBox.setSelectedIndex(0); // HOURS
            updateTimeSliderRange();
            timeSlider.setValue(24);
            timeSpinner.setValue(24);

            includeHiddenCheckBox.setSelected(false);
            skipSymlinksCheckBox.setSelected(true);
            modeComboBox.setSelectedIndex(0);
            allowNoExtCheckBox.setSelected(true);
            extensionListModel.clear();
            presetComboBox.setSelectedIndex(0);

            JOptionPane.showMessageDialog(
                this,
                i18n.getMessage("config.reset.success"),
                i18n.getMessage("common.success"),
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    @Override
    public void onLocaleChanged(java.util.Locale newLocale) {
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("filter.dialog.title"));
            // Refresh all component texts
            // In a production system, we would refresh each component's text
        });
    }

    @Override
    public void dispose() {
        i18n.removeLocaleChangeListener(this);
        super.dispose();
    }

    /**
     * Shows the filter configuration dialog.
     *
     * @param parent parent frame
     */
    public static void showFilterConfigDialog(JFrame parent) {
        FilterConfigDialog dialog = new FilterConfigDialog(parent);
        dialog.setVisible(true);
    }
}
