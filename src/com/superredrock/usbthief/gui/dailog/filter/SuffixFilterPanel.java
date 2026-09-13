package com.superredrock.usbthief.gui.dailog.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.FileFilterConfig;
import com.superredrock.usbthief.core.config.configs.SuffixFilterConfig;
import com.superredrock.usbthief.core.filter.FilterPreset;
import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Suffix filter" page of {@link FilterConfigDialog}: the whitelist / blacklist
 * mode, the extension presets and the editable extension list.
 *
 * <p>UI-04 - a short hint explains what whitelist and blacklist actually do.</p>
 *
 * <p>Extracted verbatim from {@code FilterConfigDialog} (architecture-audit [19]).</p>
 */
public class SuffixFilterPanel extends JPanel {

    private static final I18nManager i18n = I18nManager.getInstance();

    /** Wire names of the suffix modes, indexed by combo box index. */
    private static final String[] MODE_NAMES = {"NONE", "WHITELIST", "BLACKLIST"};

    /** Combo box indices of the modes above (kept next to the array for clarity). */
    private static final int MODE_INDEX_NONE = 0;
    private static final int MODE_INDEX_WHITELIST = 1;

    private JComboBox<String> modeComboBox;
    private JComboBox<String> presetComboBox;
    private JCheckBox allowNoExtCheckBox;
    private JList<String> extensionList;
    private DefaultListModel<String> extensionListModel;
    private JTextField extensionField;

    /**
     * Guards the combo box listeners while this panel writes widget state itself
     * (load / reset / applying a preset), so a programmatic change is not mistaken
     * for a user edit that would immediately overwrite that state again.
     */
    private boolean suppressPresetApply;

    public SuffixFilterPanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Mode selection
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        add(new JLabel(i18n.getMessage("filter.suffix.mode")), gbc);

        modeComboBox = new JComboBox<>(new String[]{
            i18n.getMessage("filter.suffix.mode.none"),
            i18n.getMessage("filter.suffix.mode.whitelist"),
            i18n.getMessage("filter.suffix.mode.blacklist")
        });
        modeComboBox.addActionListener(e -> {
            if (!suppressPresetApply) {
                presetComboBox.setSelectedIndex(0); // manual mode change drops the preset
            }
            updateControlsState();
        });

        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        add(modeComboBox, gbc);

        // Preset selection
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        add(new JLabel(i18n.getMessage("filter.suffix.preset")), gbc);

        presetComboBox = new JComboBox<>(presetLabels());
        presetComboBox.addActionListener(e -> applySelectedPreset());

        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        add(presetComboBox, gbc);

        // Allow no extension
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        gbc.weightx = 0;
        allowNoExtCheckBox = new JCheckBox(i18n.getMessage("filter.suffix.allowNoExt"));
        add(allowNoExtCheckBox, gbc);

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

        add(scrollPane, gbc);

        // Extension input and buttons
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        extensionField = new JTextField(15);
        add(extensionField, gbc);

        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 1;
        gbc.weightx = 0;
        JButton addButton = new JButton(i18n.getMessage("filter.suffix.add"));
        addButton.addActionListener(e -> addExtension());
        add(addButton, gbc);

        gbc.gridx = 2; gbc.gridy = row; gbc.gridwidth = 1;
        JButton removeButton = new JButton(i18n.getMessage("filter.suffix.remove"));
        removeButton.addActionListener(e -> removeExtension());
        add(removeButton, gbc);

        // Short explanation of what whitelist / blacklist actually do (UI-04)
        row++;
        JLabel suffixHintLabel = new JLabel(i18n.getMessage("filter.suffix.hint"));
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            suffixHintLabel.setForeground(muted);
        }
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        gbc.weighty = 0; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(suffixHintLabel, gbc);

        // Initialize control states
        updateControlsState();
    }

    /**
     * Loads the current settings from ConfigManager.
     */
    public void load(ConfigManager configManager) {
        suppressPresetApply = true;
        try {
            String mode = configManager.get(SuffixFilterConfig.SUFFIX_FILTER_MODE);
            modeComboBox.setSelectedIndex(modeIndex(mode));

            allowNoExtCheckBox.setSelected(configManager.get(FileFilterConfig.FILE_FILTER_ALLOW_NO_EXT));

            // Load extensions
            extensionListModel.clear();
            List<String> extensions;
            if (mode.equalsIgnoreCase("WHITELIST")) {
                extensions = configManager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST);
            } else if (mode.equalsIgnoreCase("BLACKLIST")) {
                extensions = configManager.get(SuffixFilterConfig.SUFFIX_FILTER_BLACKLIST);
            } else {
                extensions = List.of();
            }
            for (String ext : extensions) {
                extensionListModel.addElement(ext);
            }

            // Load preset
            String preset = configManager.get(SuffixFilterConfig.SUFFIX_FILTER_PRESET);
            // A stored preset is display state only: it is shown when the mode is the
            // whitelist (a preset always writes a whitelist). The loaded extension list
            // above stays the single source of truth, so hand-edited lists survive.
            if (!preset.isBlank()) {
                FilterPreset stored = FilterPreset.safeValueOf(preset);
                boolean displayable = mode.equalsIgnoreCase("WHITELIST") || stored == FilterPreset.ALL;
                presetComboBox.setSelectedIndex(displayable ? stored.ordinal() + 1 : 0);
            } else {
                presetComboBox.setSelectedIndex(0);
            }
        } finally {
            suppressPresetApply = false;
        }

        updateControlsState();
    }

    /**
     * One-click preset application: selects the matching mode and writes the preset's
     * extensions into the editable list. The list is written, not locked - the user can
     * keep adding or removing extensions straight away.
     *
     * @param preset the preset to apply
     */
    public void selectPreset(FilterPreset preset) {
        if (preset == null) {
            return;
        }
        presetComboBox.setSelectedIndex(preset.ordinal() + 1);
    }

    /**
     * Human readable preset names, indexed like the combo box (index 0 = no preset).
     */
    private static String[] presetLabels() {
        FilterPreset[] presets = FilterPreset.values();
        String[] labels = new String[presets.length + 1];
        labels[0] = i18n.getMessage("filter.suffix.preset.custom");
        for (int i = 0; i < presets.length; i++) {
            labels[i + 1] = i18n.getMessage(presets[i].getDisplayNameKey());
        }
        return labels;
    }

    /**
     * Back to index 0 of the preset combo: the extension list no longer equals any
     * preset, so the combo must not keep claiming one is active.
     */
    private void markPresetAsCustom() {
        if (presetComboBox.getSelectedIndex() != 0) {
            suppressPresetApply = true;
            try {
                presetComboBox.setSelectedIndex(0);
            } finally {
                suppressPresetApply = false;
            }
        }
    }

    /**
     * Writes the settings of this page to ConfigManager.
     */
    public void save(ConfigManager configManager) {
        String mode = modeName(modeComboBox.getSelectedIndex());
        configManager.set(SuffixFilterConfig.SUFFIX_FILTER_MODE, mode);

        configManager.set(FileFilterConfig.FILE_FILTER_ALLOW_NO_EXT, allowNoExtCheckBox.isSelected());

        // Save extensions to appropriate list
        List<String> extensions = new ArrayList<>();
        for (int i = 0; i < extensionListModel.size(); i++) {
            extensions.add(extensionListModel.getElementAt(i));
        }

        if (mode.equalsIgnoreCase("WHITELIST")) {
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, extensions);
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_BLACKLIST, List.of());
        } else if (mode.equalsIgnoreCase("BLACKLIST")) {
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_BLACKLIST, extensions);
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, List.of());
        } else {
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, List.of());
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_BLACKLIST, List.of());
        }

        // Save preset
        int presetIndex = presetComboBox.getSelectedIndex();
        FilterPreset[] presets = FilterPreset.values();
        if (presetIndex > 0 && presetIndex - 1 < presets.length) {
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_PRESET, presets[presetIndex - 1].name());
        } else {
            configManager.set(SuffixFilterConfig.SUFFIX_FILTER_PRESET, "");
        }
    }

    /**
     * Restores the widgets of this page to the filter defaults.
     */
    public void resetToDefaults() {
        suppressPresetApply = true;
        try {
            modeComboBox.setSelectedIndex(0);
            allowNoExtCheckBox.setSelected(true);
            extensionListModel.clear();
            presetComboBox.setSelectedIndex(0);
        } finally {
            suppressPresetApply = false;
        }

        updateControlsState();
    }

    /**
     * Append the values shown on this page to a snapshot used for dirty tracking.
     */
    public void snapshot(Map<String, Object> target) {
        target.put("mode", modeComboBox.getSelectedIndex());
        target.put("allowNoExt", allowNoExtCheckBox.isSelected());
        target.put("extensions", Collections.list(extensionListModel.elements()));
        target.put("preset", presetComboBox.getSelectedIndex());
    }

    /**
     * Updates the enabled state of controls based on mode selection.
     */
    private void updateControlsState() {
        int modeIndex = modeComboBox.getSelectedIndex();
        boolean isNone = (modeIndex == 0);

        extensionList.setEnabled(!isNone);
        extensionField.setEnabled(!isNone);
        // The preset combo stays reachable in "none" mode: it is the one-click entry
        // point that switches the whitelist on again.
        presetComboBox.setEnabled(true);
    }

    /**
     * Applies the selected preset as a one-click shortcut: switches to whitelist mode
     * and writes the preset's extensions into the editable list. {@code ALL} clears the
     * list and turns filtering off, so it means "no filtering" rather than "whitelist
     * nothing".
     */
    private void applySelectedPreset() {
        if (suppressPresetApply) {
            return;
        }
        int presetIndex = presetComboBox.getSelectedIndex();
        if (presetIndex <= 0) {
            return; // No preset selected
        }

        FilterPreset[] presets = FilterPreset.values();
        if (presetIndex - 1 < presets.length) {
            FilterPreset preset = presets[presetIndex - 1];
            suppressPresetApply = true;
            try {
                if (preset.filtersExtensions()) {
                    modeComboBox.setSelectedIndex(MODE_INDEX_WHITELIST);
                    extensionListModel.clear();
                    for (String ext : preset.getExtensions()) {
                        extensionListModel.addElement(ext);
                    }
                } else {
                    // ALL: nothing to filter, so clear the list and stop filtering
                    modeComboBox.setSelectedIndex(MODE_INDEX_NONE);
                    extensionListModel.clear();
                }
            } finally {
                suppressPresetApply = false;
            }
        }
        updateControlsState();
    }

    /**
     * Adds an extension from the text field to the list.
     */
    private void addExtension() {
        if (addExtensionEntry(extensionField.getText())) {
            extensionField.setText("");
        }
    }

    /**
     * Normalises and appends one extension to the editable list; the package-private
     * entry point lets tests exercise the "edit after picking a preset" path.
     *
     * @param rawExtension extension as typed by the user, with or without a leading dot
     * @return true when the entry was added, false when blank or already present
     */
    boolean addExtensionEntry(String rawExtension) {
        String ext = rawExtension == null ? "" : rawExtension.trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (ext.isEmpty()) {
            return false;
        }

        // Check for duplicates
        for (int i = 0; i < extensionListModel.size(); i++) {
            if (extensionListModel.getElementAt(i).equalsIgnoreCase(ext)) {
                return false; // Already exists
            }
        }

        extensionListModel.addElement(ext);
        markPresetAsCustom();
        return true;
    }

    /**
     * Removes selected extensions from the list.
     */
    private void removeExtension() {
        int[] selectedIndices = extensionList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            return;
        }
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            extensionListModel.remove(selectedIndices[i]);
        }
        markPresetAsCustom();
    }

    /**
     * Combo box index of a suffix mode wire name, defaulting to "none".
     */
    private static int modeIndex(String mode) {
        if (mode == null) {
            return 0;
        }
        String normalized = mode.toUpperCase(Locale.ROOT);
        for (int i = 0; i < MODE_NAMES.length; i++) {
            if (MODE_NAMES[i].equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Suffix mode wire name for a combo box index, defaulting to "none".
     */
    private static String modeName(int index) {
        if (index < 0 || index >= MODE_NAMES.length) {
            return MODE_NAMES[0];
        }
        return MODE_NAMES[index];
    }
}
