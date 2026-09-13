package com.superredrock.usbthief.gui.dailog.config;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One page of the preferences dialog: all configuration entries of a single
 * category, laid out as a label / editor / note grid, plus the logic that reads
 * every entry back out of its editor and writes it to {@link ConfigManager}.
 *
 * <p>Extracted from {@code ConfigDialog} (architecture-audit [19]). The page owns
 * its widgets, so the dialog no longer has to keep a parallel
 * {@code Map<category, Map<entryKey, JComponent>>} in sync with the UI.</p>
 */
public class ConfigCategoryPanel {

    private final String categoryName;
    private final JPanel panel;
    private final Map<String, ConfigEntryPanel> entryPanels = new HashMap<>();

    /**
     * @param categoryI18nKey the i18n key of the category, e.g.
     *                        {@code config.category.threadPool}
     * @param configManager   source of the current values shown by the page
     */
    public ConfigCategoryPanel(String categoryI18nKey, ConfigManager configManager) {
        this.categoryName = ConfigCategories.resolveName(categoryI18nKey);

        panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        List<ConfigEntry<?>> entries = ConfigSchema.getEntriesByCategory().get(categoryName);
        if (entries != null) {
            int row = 0;
            for (ConfigEntry<?> entry : entries) {
                ConfigEntryPanel entryPanel = new ConfigEntryPanel(entry, configManager.get(entry));
                entryPanel.addTo(panel, gbc, row);
                entryPanels.put(entry.key(), entryPanel);
                row++;
            }

            // Add empty space at bottom
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 4;
            gbc.weightx = 0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            panel.add(Box.createVerticalGlue(), gbc);
        }
    }

    /**
     * The Swing component to place in the dialog's content area.
     */
    public JPanel getComponent() {
        return panel;
    }

    /**
     * Commit pending spinner edits so typed input is not silently dropped (UI-13).
     */
    public void commitEdits() {
        for (ConfigEntryPanel entryPanel : entryPanels.values()) {
            entryPanel.commitEdit();
        }
    }

    /**
     * Write every entry of this page back to the configuration.
     */
    public void saveTo(ConfigManager configManager) {
        List<ConfigEntry<?>> entries = ConfigSchema.getEntriesByCategory().get(categoryName);
        if (entries == null) {
            return;
        }
        for (ConfigEntry<?> entry : entries) {
            ConfigEntryPanel entryPanel = entryPanels.get(entry.key());
            if (entryPanel == null) {
                continue;
            }
            Object newValue = entryPanel.readValue();
            if (newValue == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            ConfigEntry<Object> typedEntry = (ConfigEntry<Object>) entry;
            configManager.set(typedEntry, newValue);
        }
    }

    /**
     * Read the current value of a numeric entry of this page.
     *
     * @return the value, or {@code null} when this page has no such spinner
     */
    public Number readNumber(String entryKey) {
        ConfigEntryPanel entryPanel = entryPanels.get(entryKey);
        return entryPanel == null ? null : entryPanel.readNumber();
    }
}
