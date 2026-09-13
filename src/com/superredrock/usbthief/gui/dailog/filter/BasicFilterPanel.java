package com.superredrock.usbthief.gui.dailog.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.FileFilterConfig;
import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;

/**
 * "Basic filter" page of {@link FilterConfigDialog}: the maximum file size and
 * the modification-time window, plus the hidden-file and symlink switches.
 *
 * <p>Humanization notes:</p>
 * <ul>
 *   <li>UI-04 - labels explain which time stamp and which direction of the size
 *       comparison the rule uses.</li>
 *   <li>UI-05 - slider labels are evenly spaced from the maximum upwards and
 *       carry a unit suffix, so two labels can no longer collide ("144168").</li>
 *   <li>UI-06 - a visible warning when the filter would silently skip files.</li>
 *   <li>UI-13 - spinner text is committed explicitly before saving.</li>
 * </ul>
 *
 * <p>Extracted verbatim from {@code FilterConfigDialog} (architecture-audit [19]).</p>
 */
public class BasicFilterPanel extends JPanel {

    private static final I18nManager i18n = I18nManager.getInstance();

    private JCheckBox maxSizeEnabledCheckBox;
    private JSlider maxSizeSlider;
    private JSpinner maxSizeSpinner;
    private JComboBox<String> sizeUnitComboBox;
    private JLabel maxSizeLabel;

    private JCheckBox timeEnabledCheckBox;
    private JSlider timeSlider;
    private JSpinner timeSpinner;
    private JComboBox<String> timeUnitComboBox;

    private JCheckBox includeHiddenCheckBox;
    private JCheckBox skipSymlinksCheckBox;

    /** Warning banner ("this filter silently skips files") - UI-06. */
    private FilterWarningBanner warningLabel;

    public BasicFilterPanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // === Warning banner (UI-06) ===
        // Sits above everything so that "this filter will silently skip files" is
        // impossible to miss, also for the default (size filter enabled / 100 MB).
        warningLabel = new FilterWarningBanner();
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        add(warningLabel, gbc);

        // === Max file size section ===
        row++;
        maxSizeEnabledCheckBox = new JCheckBox(i18n.getMessage("filter.basic.maxSizeEnabled"));
        maxSizeEnabledCheckBox.addActionListener(e -> {
            updateSizeControlsState();
            updateWarnings();
        });
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(maxSizeEnabledCheckBox, gbc);

        row++;
        maxSizeLabel = new JLabel(i18n.getMessage("filter.basic.maxSize"));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        add(maxSizeLabel, gbc);

        // Size slider (MB default)
        maxSizeSlider = FilterRanges.createSlider(1, 1000, 100, 250, 50, 250, "");
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(maxSizeSlider, gbc);

        // Size spinner (the editor accepts typed input - UI-13)
        maxSizeSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 1));
        maxSizeSpinner.setPreferredSize(new Dimension(70, 25));
        maxSizeSpinner.setToolTipText(i18n.getMessage("filter.basic.maxSize.tooltip"));
        gbc.gridx = 2; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(maxSizeSpinner, gbc);

        // Size unit combo box (width derived from its content - UI-05)
        sizeUnitComboBox = new JComboBox<>(new String[]{
            i18n.getMessage("filter.basic.unit.mb"),
            i18n.getMessage("filter.basic.unit.gb")
        });
        sizeUnitComboBox.setToolTipText(i18n.getMessage("filter.basic.maxSize.tooltip"));
        sizeUnitComboBox.addActionListener(e -> {
            updateSizeSliderRange();
            updateWarnings();
        });
        gbc.gridx = 3; gbc.gridy = row; gbc.gridwidth = 1;
        add(sizeUnitComboBox, gbc);

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
            updateWarnings();
        });

        // === Time filter section ===
        row++;
        timeEnabledCheckBox = new JCheckBox(i18n.getMessage("filter.basic.timeEnabled"));
        timeEnabledCheckBox.setToolTipText(i18n.getMessage("filter.basic.time.tooltip"));
        timeEnabledCheckBox.addActionListener(e -> updateWarnings());
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.weightx = 0;
        add(timeEnabledCheckBox, gbc);

        // Time within label
        row++;
        JLabel timeWithinLabel = new JLabel(i18n.getMessage("filter.basic.timeWithin"));
        timeWithinLabel.setToolTipText(i18n.getMessage("filter.basic.time.tooltip"));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        add(timeWithinLabel, gbc);

        // Time slider (initial range for hours)
        timeSlider = FilterRanges.createSlider(1, 168, 24, 24, 6, 48,
            i18n.getMessage("filter.slider.unit.hours"));
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(timeSlider, gbc);

        // Time spinner
        timeSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 168, 1));
        timeSpinner.setPreferredSize(new Dimension(70, 25));
        timeSpinner.setToolTipText(i18n.getMessage("filter.basic.time.tooltip"));
        gbc.gridx = 2; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(timeSpinner, gbc);

        // Time unit combo box (width derived from its content - UI-05)
        timeUnitComboBox = new JComboBox<>(new String[]{
            i18n.getMessage("filter.basic.unit.hours"),
            i18n.getMessage("filter.basic.unit.days"),
            i18n.getMessage("filter.basic.unit.weeks"),
            i18n.getMessage("filter.basic.unit.months"),
            i18n.getMessage("filter.basic.unit.years")
        });
        timeUnitComboBox.setToolTipText(i18n.getMessage("filter.basic.time.tooltip"));
        timeUnitComboBox.addActionListener(e -> {
            updateTimeSliderRange();
            updateWarnings();
        });
        gbc.gridx = 3; gbc.gridy = row; gbc.gridwidth = 1;
        add(timeUnitComboBox, gbc);

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
            updateWarnings();
        });

        // === Other options ===
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        includeHiddenCheckBox = new JCheckBox(i18n.getMessage("filter.basic.includeHidden"));
        includeHiddenCheckBox.setToolTipText(i18n.getMessage("filter.basic.includeHidden.tooltip"));
        add(includeHiddenCheckBox, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        skipSymlinksCheckBox = new JCheckBox(i18n.getMessage("filter.basic.skipSymlinks"));
        skipSymlinksCheckBox.setToolTipText(i18n.getMessage("filter.basic.skipSymlinks.tooltip"));
        add(skipSymlinksCheckBox, gbc);

        // Add vertical glue
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        add(Box.createVerticalGlue(), gbc);
    }

    /**
     * Loads the current settings from ConfigManager.
     */
    public void load(ConfigManager configManager) {
        // Basic filter settings - Size
        boolean maxSizeEnabled = configManager.get(FileFilterConfig.FILE_FILTER_MAX_SIZE_ENABLED);
        maxSizeEnabledCheckBox.setSelected(maxSizeEnabled);
        updateSizeControlsState();

        long maxSizeBytes = configManager.get(FileFilterConfig.FILE_FILTER_MAX_SIZE);

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
        boolean timeEnabled = configManager.get(FileFilterConfig.FILE_FILTER_TIME_ENABLED);
        timeEnabledCheckBox.setSelected(timeEnabled);

        long timeValue = configManager.get(FileFilterConfig.FILE_FILTER_TIME_VALUE);
        String timeUnit = configManager.get(FileFilterConfig.FILE_FILTER_TIME_UNIT);

        int timeUnitIndex = FilterRanges.timeUnitIndex(timeUnit);
        timeUnitComboBox.setSelectedIndex(timeUnitIndex);

        // Update slider range then set value
        updateTimeSliderRange();
        int timeValueInt = (int) Math.min(timeValue, FilterRanges.TIME_RANGES[timeUnitIndex][1]);
        timeSlider.setValue(timeValueInt);
        timeSpinner.setValue(timeValueInt);

        includeHiddenCheckBox.setSelected(configManager.get(FileFilterConfig.FILE_FILTER_INCLUDE_HIDDEN));
        skipSymlinksCheckBox.setSelected(configManager.get(FileFilterConfig.FILE_FILTER_SKIP_SYMLINKS));

        // Programmatic changes do not fire the listeners, so refresh explicitly
        updateSizeControlsState();
        updateWarnings();
    }

    /**
     * Writes the settings of this page to ConfigManager.
     */
    public void save(ConfigManager configManager) {
        configManager.set(FileFilterConfig.FILE_FILTER_MAX_SIZE_ENABLED, maxSizeEnabledCheckBox.isSelected());

        int sizeValue = (Integer) maxSizeSpinner.getValue();
        int sizeUnitIndex = sizeUnitComboBox.getSelectedIndex();
        long maxSizeBytes = sizeValue * FilterRanges.SIZE_MULTIPLIERS[sizeUnitIndex];
        configManager.set(FileFilterConfig.FILE_FILTER_MAX_SIZE, maxSizeBytes);

        configManager.set(FileFilterConfig.FILE_FILTER_TIME_ENABLED, timeEnabledCheckBox.isSelected());

        int timeValue = (Integer) timeSpinner.getValue();
        configManager.set(FileFilterConfig.FILE_FILTER_TIME_VALUE, (long) timeValue);

        configManager.set(FileFilterConfig.FILE_FILTER_TIME_UNIT,
            FilterRanges.timeUnitName(timeUnitComboBox.getSelectedIndex()));

        configManager.set(FileFilterConfig.FILE_FILTER_INCLUDE_HIDDEN, includeHiddenCheckBox.isSelected());
        configManager.set(FileFilterConfig.FILE_FILTER_SKIP_SYMLINKS, skipSymlinksCheckBox.isSelected());
    }

    /**
     * Restores the widgets of this page to the filter defaults.
     */
    public void resetToDefaults() {
        maxSizeEnabledCheckBox.setSelected(true);
        updateSizeControlsState();
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

        updateSizeControlsState();
        updateWarnings();
    }

    /**
     * Commits text the user typed straight into a spinner editor (UI-13).
     *
     * <p>{@link JSpinner}'s number editor is a formatted text field that only
     * pushes its text into the model on Enter or focus loss. Clicking Save can
     * therefore save the previous value and silently drop the typed one, so the
     * pending edit is committed explicitly first. An unparsable entry is
     * discarded and the editor falls back to the last valid value.</p>
     */
    public void commitEdits() {
        for (JSpinner spinner : new JSpinner[]{maxSizeSpinner, timeSpinner}) {
            try {
                spinner.commitEdit();
            } catch (java.text.ParseException e) {
                if (spinner.getEditor() instanceof JSpinner.NumberEditor) {
                    JSpinner.NumberEditor editor = (JSpinner.NumberEditor) spinner.getEditor();
                    editor.getTextField().setValue(spinner.getValue());
                }
            }
        }
    }

    /**
     * Append the values shown on this page to a snapshot used for dirty tracking.
     */
    public void snapshot(Map<String, Object> target) {
        target.put("maxSizeEnabled", maxSizeEnabledCheckBox.isSelected());
        target.put("maxSize", ((Number) maxSizeSpinner.getValue()).longValue());
        target.put("sizeUnit", sizeUnitComboBox.getSelectedIndex());
        target.put("timeEnabled", timeEnabledCheckBox.isSelected());
        target.put("timeValue", ((Number) timeSpinner.getValue()).longValue());
        target.put("timeUnit", timeUnitComboBox.getSelectedIndex());
        target.put("includeHidden", includeHiddenCheckBox.isSelected());
        target.put("skipSymlinks", skipSymlinksCheckBox.isSelected());
    }

    /**
     * Updates the size slider range based on selected unit.
     */
    private void updateSizeSliderRange() {
        int unitIndex = sizeUnitComboBox.getSelectedIndex();
        int[] range = FilterRanges.SIZE_RANGES[unitIndex];
        int value = (Integer) maxSizeSpinner.getValue();

        maxSizeSlider.setMinimum(range[0]);
        maxSizeSlider.setMaximum(range[1]);
        maxSizeSlider.setMajorTickSpacing(range[2]);
        maxSizeSlider.setMinorTickSpacing(Math.max(1, range[2] / 5));
        FilterRanges.updateSliderLabels(maxSizeSlider, range[0], range[1], range[2], "");

        int clampedValue = Math.max(range[0], Math.min(value, range[1]));
        maxSizeSpinner.setModel(new SpinnerNumberModel(clampedValue, range[0], range[1], 1));
        maxSizeSlider.setValue(clampedValue);
    }

    /**
     * Updates the time slider range based on selected unit.
     */
    private void updateTimeSliderRange() {
        int unitIndex = timeUnitComboBox.getSelectedIndex();
        int[] range = FilterRanges.TIME_RANGES[unitIndex];
        int value = (Integer) timeSpinner.getValue();

        timeSlider.setMinimum(range[0]);
        timeSlider.setMaximum(range[1]);
        timeSlider.setMajorTickSpacing(range[2]);
        timeSlider.setMinorTickSpacing(range[3]);
        FilterRanges.updateSliderLabels(timeSlider, range[0], range[1], range[4],
            FilterRanges.timeUnitSuffix(unitIndex));

        int clampedValue = Math.max(range[0], Math.min(value, range[1]));
        timeSpinner.setModel(new SpinnerNumberModel(clampedValue, range[0], range[1], 1));
        timeSlider.setValue(clampedValue);
    }

    /**
     * Keeps the warning banner in sync with the current settings (UI-06).
     */
    private void updateWarnings() {
        if (warningLabel == null) {
            return;
        }
        warningLabel.update(
            maxSizeEnabledCheckBox.isSelected() ? describeSize() : null,
            timeEnabledCheckBox.isSelected() ? describeTime() : null);
    }

    /**
     * Human readable form of the configured maximum size, e.g. {@code 100 MB}.
     */
    private String describeSize() {
        return maxSizeSpinner.getValue() + " " + sizeUnitComboBox.getSelectedItem();
    }

    /**
     * Human readable form of the configured time window, e.g. {@code 24 小时}.
     */
    private String describeTime() {
        return timeSpinner.getValue() + " " + timeUnitComboBox.getSelectedItem();
    }

    /**
     * Updates the enabled state of size filter controls.
     */
    private void updateSizeControlsState() {
        boolean enabled = maxSizeEnabledCheckBox.isSelected();
        maxSizeSlider.setEnabled(enabled);
        maxSizeSpinner.setEnabled(enabled);
        sizeUnitComboBox.setEnabled(enabled);
        maxSizeLabel.setEnabled(enabled);
    }
}
