package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigType;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration dialog that dynamically renders entries from ConfigSchema.
 * Supports import/export of configuration files.
 */
public class ConfigDialog extends JDialog {

    private final Map<String, JComponent> valueComponents = new HashMap<>();
    private final JTabbedPane tabbedPane;
    private final ConfigManager configManager;

    public ConfigDialog(JFrame parent) {
        super(parent, "配置", true);
        setSize(800, 700);
        setLocationRelativeTo(parent);

        this.configManager = ConfigManager.getInstance();

        // Create main tabbed pane
        tabbedPane = new JTabbedPane();

        // Dynamically create tabs for each category
        Map<String, List<ConfigEntry<?>>> entriesByCategory = ConfigSchema.getEntriesByCategory();
        for (String category : entriesByCategory.keySet()) {
            JPanel categoryPanel = createCategoryPanel(category, entriesByCategory.get(category));
            tabbedPane.add(category, categoryPanel);
        }

        // Import/Export buttons
        JPanel importExportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton importButton = new JButton("导入配置");
        importButton.setToolTipText("从文件导入配置");
        importButton.addActionListener(e -> importConfig());

        JButton exportButton = new JButton("导出配置");
        exportButton.setToolTipText("导出配置到文件");
        exportButton.addActionListener(e -> exportConfig());

        importExportPanel.add(importButton);
        importExportPanel.add(exportButton);

        // Action buttons
        JButton saveButton = new JButton("保存");
        saveButton.addActionListener(e -> saveConfig());

        JButton resetButton = new JButton("重置为默认值");
        resetButton.addActionListener(e -> resetToDefaults());

        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(importExportPanel, BorderLayout.WEST);

        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtonPanel.add(resetButton);
        rightButtonPanel.add(cancelButton);
        rightButtonPanel.add(saveButton);
        buttonPanel.add(rightButtonPanel, BorderLayout.EAST);

        setLayout(new BorderLayout(5, 5));
        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Create a panel for a specific category of configuration entries.
     */
    private JPanel createCategoryPanel(String category, List<ConfigEntry<?>> entries) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (ConfigEntry<?> entry : entries) {
            JComponent component = createValueComponent(entry);
            valueComponents.put(entry.key(), component);

            // Label
            gbc.gridx = 0;
            gbc.gridy = row;
            JLabel label = new JLabel(entry.key() + ":");
            label.setToolTipText(entry.description());
            panel.add(label, gbc);

            // Value component
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            panel.add(component, gbc);

            row++;
        }

        // Add empty space at bottom
        gbc.gridy = row;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    /**
     * Create appropriate UI component based on configuration entry type.
     */
    @SuppressWarnings("unchecked")
    private JComponent createValueComponent(ConfigEntry<?> entry) {
        Object currentValue = configManager.get(entry);

        if (entry.type() == ConfigType.INT) {
            return createSpinner((Integer) currentValue, entry.description());
        } else if (entry.type() == ConfigType.LONG) {
            return createSpinner((Long) currentValue, entry.description());
        } else if (entry.type() == ConfigType.BOOLEAN) {
            return createCheckBox((Boolean) currentValue, entry.description());
        } else if (entry.type() == ConfigType.STRING) {
            return createTextField((String) currentValue, entry.description());
        } else if (entry.type() == ConfigType.STRING_LIST) {
            return createTextArea((List<String>) currentValue, entry.description());
        }
        return new JLabel("Unknown type");
    }

    /**
     * Create spinner for integer/long values.
     */
    private JSpinner createSpinner(Number value, String description) {
        JSpinner spinner;
        if (value instanceof Integer) {
            int intValue = (Integer) value;
            // Use 0 as minimum to allow 0 for COPY_RATE_LIMIT (no limit)
            SpinnerNumberModel intModel = new SpinnerNumberModel(
                    intValue,           // value
                    0,                  // minimum
                    Integer.MAX_VALUE,   // maximum
                    1                   // step size
            );
            spinner = new JSpinner(intModel);
            // Ensure spinner uses integer editor to prevent Double conversion
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
            spinner.setEditor(editor);
        } else {
            long longValue = (Long) value;
            // Use 0 as minimum to allow 0 for COPY_RATE_LIMIT (no limit)
            SpinnerNumberModel longModel = new SpinnerNumberModel(
                    longValue,          // value
                    0L,                 // minimum
                    Long.MAX_VALUE,      // maximum
                    1L                  // step size
            );
            spinner = new JSpinner(longModel);
            // Ensure spinner uses integer editor to prevent Double conversion
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
            spinner.setEditor(editor);
        }
        spinner.setToolTipText(description);
        return spinner;
    }

    /**
     * Create checkbox for boolean values.
     */
    private JCheckBox createCheckBox(Boolean value, String description) {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(value);
        checkBox.setToolTipText(description);
        return checkBox;
    }

    /**
     * Create text field for string values.
     */
    private JTextField createTextField(String value, String description) {
        JTextField textField = new JTextField(value != null ? value : "", 30);
        textField.setToolTipText(description);
        return textField;
    }

    /**
     * Create text area for string list values.
     */
    private JTextArea createTextArea(List<String> values, String description) {
        JTextArea textArea = new JTextArea(values != null ? String.join(";", values) : "", 5, 30);
        textArea.setToolTipText(description + " (多个值用分号分隔)");
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return textArea;
    }

    /**
     * Save current values to configuration.
     */
    private void saveConfig() {
        try {
            Map<String, List<ConfigEntry<?>>> entriesByCategory = ConfigSchema.getEntriesByCategory();
            for (List<ConfigEntry<?>> entries : entriesByCategory.values()) {
                for (ConfigEntry<?> entry : entries) {
                    JComponent component = valueComponents.get(entry.key());
                    if (component == null) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    ConfigEntry<Object> typedEntry = (ConfigEntry<Object>) entry;

                    Object newValue;
                    if (entry.type() == ConfigType.INT) {
                        Object spinnerValue = ((JSpinner) component).getValue();
                        // SpinnerNumberModel may return Double, ensure we get Integer
                        newValue = ((Number) spinnerValue).intValue();
                    } else if (entry.type() == ConfigType.LONG) {
                        Object spinnerValue = ((JSpinner) component).getValue();
                        // SpinnerNumberModel may return Double, ensure we get Long
                        newValue = ((Number) spinnerValue).longValue();
                    } else if (entry.type() == ConfigType.BOOLEAN) {
                        newValue = ((JCheckBox) component).isSelected();
                    } else if (entry.type() == ConfigType.STRING) {
                        newValue = ((JTextField) component).getText();
                    } else if (entry.type() == ConfigType.STRING_LIST) {
                        String text = ((JTextArea) component).getText();
                        String[] parts = text.split(";");
                        List<String> list = new ArrayList<>();
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                list.add(trimmed);
                            }
                        }
                        newValue = list;
                    } else {
                        continue;
                    }

                    configManager.set(typedEntry, newValue);
                }
            }

            JOptionPane.showMessageDialog(this, "配置已保存！", "成功", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存配置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Reset all configuration to default values.
     */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要重置所有配置为默认值吗？",
                "确认重置",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            configManager.resetToDefaults();
            JOptionPane.showMessageDialog(this, "配置已重置为默认值，请重新打开配置对话框。", "成功", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        }
    }

    /**
     * Import configuration from file.
     */
    private void importConfig() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导入配置文件");
        fileChooser.setFileFilter(new FileNameExtensionFilter("配置文件 (*.properties, *.json)", "properties", "json"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path path = fileChooser.getSelectedFile().toPath();
            try {
                String fileName = path.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".json")) {
                    configManager.importFromJson(path);
                } else if (fileName.endsWith(".properties")) {
                    configManager.importFromProperties(path);
                } else {
                    JOptionPane.showMessageDialog(this, "不支持的文件格式，请使用 .properties 或 .json 文件。", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                JOptionPane.showMessageDialog(this, "配置已成功导入，请重新打开配置对话框。", "成功", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "导入配置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Export configuration to file.
     */
    private void exportConfig() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出配置文件");
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON 文件 (*.json)", "json"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path path = fileChooser.getSelectedFile().toPath();

            // Ensure .json extension
            if (!path.toString().toLowerCase().endsWith(".json")) {
                path = Paths.get(path + ".json");
            }

            try {
                configManager.exportToJson(path);
                JOptionPane.showMessageDialog(this, "配置已成功导出到: " + path, "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "导出配置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
