package com.superredrock.usbthief.gui.dailog.config;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigType;
import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.List;

/**
 * Rendering unit for a single configuration entry: the human readable label, the
 * editor widget and the optional unit/default/range note, plus the logic to read
 * the widget back into a configuration value.
 *
 * <p>Labels, notes and tooltips are resolved from i18n keys derived from the
 * configuration key: {@code config.entry.<key>.label}, {@code config.entry.<key>.default}
 * and {@code config.entry.<key>.hint}. When a key is absent the raw configuration
 * key / description is used as a fallback.</p>
 *
 * <p>Extracted verbatim from {@code ConfigDialog} (architecture-audit [19]). The
 * three cells are added into the caller's {@link GridBagLayout} so that the label,
 * editor and note columns stay aligned across all entries of a page.</p>
 */
class ConfigEntryPanel {

    private static final I18nManager i18n = I18nManager.getInstance();

    private final ConfigEntry<?> entry;
    private final String labelText;
    private final String hintText;
    private final String valueNote;
    private final JComponent valueComponent;

    ConfigEntryPanel(ConfigEntry<?> entry, Object currentValue) {
        this.entry = entry;
        this.labelText = i18nOr("config.entry." + entry.key() + ".label", entry.key());
        this.hintText = i18nOr("config.entry." + entry.key() + ".hint", entry.description());
        this.valueNote = i18nOrNull("config.entry." + entry.key() + ".default");
        this.valueComponent = createValueComponent(entry, currentValue);
        this.valueComponent.setToolTipText(hintText);
    }

    /**
     * Add the label / editor / note cells of this entry as one row of the page.
     */
    void addTo(JPanel panel, GridBagConstraints gbc, int row) {
        // Label (human readable name, no Java identifiers - UI-01)
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel label = new JLabel(labelText);
        label.setToolTipText(hintText);
        panel.add(label, gbc);

        // Value component
        boolean expands = valueComponent instanceof JTextField || valueComponent instanceof JScrollPane;
        gbc.gridx = 1;
        gbc.weightx = expands ? 1.0 : 0.0;
        gbc.fill = expands ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        panel.add(valueComponent, gbc);

        // Unit / default / valid-range note (UI-13)
        if (valueNote != null) {
            JLabel note = new JLabel(valueNote);
            Color muted = UIManager.getColor("Label.disabledForeground");
            if (muted != null) {
                note.setForeground(muted);
            }
            note.setToolTipText(hintText);
            gbc.gridx = 2;
            gbc.weightx = expands ? 0.0 : 1.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.WEST;
            panel.add(note, gbc);
        }
    }

    /**
     * Read the value currently shown in the editor.
     *
     * @return the new value, or {@code null} when the entry type has no editor
     *         (the caller then leaves that entry untouched)
     */
    Object readValue() {
        if (entry.type() == ConfigType.INT) {
            return ((Number) ((JSpinner) valueComponent).getValue()).intValue();
        } else if (entry.type() == ConfigType.LONG) {
            return ((Number) ((JSpinner) valueComponent).getValue()).longValue();
        } else if (entry.type() == ConfigType.BOOLEAN) {
            return ((JCheckBox) valueComponent).isSelected();
        } else if (entry.type() == ConfigType.STRING) {
            return ((JTextField) valueComponent).getText();
        } else if (entry.type() == ConfigType.ENUM) {
            JComboBox<?> comboBox = (JComboBox<?>) valueComponent;
            Object rawOptions = comboBox.getClientProperty("config.rawOptions");
            int selected = comboBox.getSelectedIndex();
            if (rawOptions instanceof List<?> && selected >= 0 && selected < ((List<?>) rawOptions).size()) {
                return ((List<?>) rawOptions).get(selected);
            }
            return comboBox.getSelectedItem();
        } else if (entry.type() == ConfigType.STRING_LIST) {
            JTextArea textArea;
            if (valueComponent instanceof JScrollPane) {
                textArea = (JTextArea) ((JScrollPane) valueComponent).getViewport().getView();
            } else {
                textArea = (JTextArea) valueComponent;
            }
            List<String> list = new ArrayList<>();
            for (String part : textArea.getText().split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
            return list;
        }
        return null;
    }

    /**
     * Read the numeric value of a spinner entry.
     *
     * @return the value, or {@code null} when this entry is not a number spinner
     */
    Number readNumber() {
        if (valueComponent instanceof JSpinner) {
            Object value = ((JSpinner) valueComponent).getValue();
            if (value instanceof Number) {
                return (Number) value;
            }
        }
        return null;
    }

    /**
     * Commit any text typed into the spinner editor. See
     * {@code ConfigDialog#saveAndClose} (UI-13).
     */
    void commitEdit() {
        if (!(valueComponent instanceof JSpinner)) {
            return;
        }
        JSpinner spinner = (JSpinner) valueComponent;
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException e) {
            // Invalid text: restore the editor to the last valid value.
            if (spinner.getEditor() instanceof JSpinner.NumberEditor) {
                JSpinner.NumberEditor editor = (JSpinner.NumberEditor) spinner.getEditor();
                editor.getTextField().setValue(spinner.getValue());
            }
        }
    }

    /**
     * Resolve an i18n key, returning {@code fallback} when the key is not defined
     * in any bundle (I18nManager signals missing keys with {@code !key!}).
     */
    private static String i18nOr(String key, String fallback) {
        String value = i18n.getMessage(key);
        if (value == null || (value.length() > 1 && value.startsWith("!") && value.endsWith("!"))) {
            return fallback;
        }
        return value;
    }

    /**
     * Resolve an optional i18n key, returning {@code null} when it is not defined.
     */
    private static String i18nOrNull(String key) {
        String value = i18n.getMessage(key);
        if (value == null || (value.length() > 1 && value.startsWith("!") && value.endsWith("!"))) {
            return null;
        }
        return value;
    }

    /**
     * Create appropriate UI component based on configuration entry type.
     */
    @SuppressWarnings("unchecked")
    private static JComponent createValueComponent(ConfigEntry<?> entry, Object currentValue) {
        if (entry.type() == ConfigType.INT) {
            return createSpinner((Integer) currentValue, entry.description());
        } else if (entry.type() == ConfigType.LONG) {
            return createSpinner((Long) currentValue, entry.description());
        } else if (entry.type() == ConfigType.BOOLEAN) {
            return createCheckBox((Boolean) currentValue, entry.description());
        } else if (entry.type() == ConfigType.STRING) {
            return createTextField((String) currentValue, entry.description());
        } else if (entry.type() == ConfigType.ENUM) {
            return createComboBox(entry, (String) currentValue);
        } else if (entry.type() == ConfigType.STRING_LIST) {
            return createTextArea((List<String>) currentValue, entry.description());
        }
        return new JLabel("?");
    }

    /**
     * Create spinner for integer/long values.
     */
    private static JSpinner createSpinner(Number value, String description) {
        JSpinner spinner;
        if (value instanceof Integer) {
            int intValue = (Integer) value;
            SpinnerNumberModel intModel = new SpinnerNumberModel(
                    intValue, 0, Integer.MAX_VALUE, 1
            );
            spinner = new JSpinner(intModel);
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
            spinner.setEditor(editor);
        } else {
            long longValue = (Long) value;
            SpinnerNumberModel longModel = new SpinnerNumberModel(
                    longValue, 0L, Long.MAX_VALUE, 1L
            );
            spinner = new JSpinner(longModel);
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
            spinner.setEditor(editor);
        }
        spinner.setToolTipText(description);
        return spinner;
    }

    /**
     * Create checkbox for boolean values.
     */
    private static JCheckBox createCheckBox(Boolean value, String description) {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(value);
        checkBox.setToolTipText(description);
        return checkBox;
    }

    /**
     * Create text field for string values.
     */
    private static JTextField createTextField(String value, String description) {
        JTextField textField = new JTextField(value != null ? value : "", 30);
        textField.setToolTipText(description);
        return textField;
    }

    /**
     * Create combo box for enum values. The visible items are localized (UI-01);
     * the raw option values are kept as a client property so saving still writes
     * the value the application expects.
     */
    private static JComboBox<String> createComboBox(ConfigEntry<?> entry, String currentValue) {
        List<String> options = entry.options();
        List<String> displayed = new ArrayList<>();
        for (String option : options) {
            displayed.add(i18nOr("config.option." + entry.key() + "." + option, option));
        }
        JComboBox<String> comboBox = new JComboBox<>(displayed.toArray(new String[0]));
        comboBox.putClientProperty("config.rawOptions", options);
        int index = options.indexOf(currentValue);
        comboBox.setSelectedIndex(index >= 0 ? index : 0);
        return comboBox;
    }

    /**
     * Create text area for string list values.
     */
    private static JComponent createTextArea(List<String> values, String description) {
        JTextArea textArea = new JTextArea(values != null ? String.join(";", values) : "", 5, 30);
        textArea.setToolTipText(description + " (" + i18n.getMessage("config.tooltip.separator") + ")");
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }
}
