package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.dailog.filter.BasicFilterPanel;
import com.superredrock.usbthief.gui.dailog.filter.SuffixFilterPanel;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dialog for configuring file filter settings.
 * Contains two tabs: Basic Filter and Suffix Filter.
 *
 * <p>Humanization notes:</p>
 * <ul>
 *   <li>UI-04 - labels explain what each rule actually does (which time stamp,
 *       which direction of the size comparison, what "hidden" means).</li>
 *   <li>UI-05 - slider labels are evenly spaced from the maximum upwards and
 *       carry a unit suffix, so two labels can no longer collide ("144168");
 *       the unit combo boxes size themselves from their content instead of a
 *       hard coded width that clipped the text to "…".</li>
 *   <li>UI-06 - a Cancel button, a primary Save button, a secondary Reset
 *       button with confirmation, and a visible warning when a filter would
 *       silently skip files.</li>
 * </ul>
 *
 * <p>Structure (architecture-audit [19]): the two tab pages live in
 * {@link BasicFilterPanel} and {@link SuffixFilterPanel}; this class only owns
 * the dialog shell, the unsaved-changes tracking and the button actions.</p>
 */
public class FilterConfigDialog extends JDialog implements I18nManager.LocaleChangeListener {

    private static final I18nManager i18n = I18nManager.getInstance();
    private final ConfigManager configManager;

    private final BasicFilterPanel basicFilterPanel;
    private final SuffixFilterPanel suffixFilterPanel;

    /** Snapshot of the settings as last loaded/saved; used to detect unsaved edits. */
    private Map<String, Object> baseline;

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

        basicFilterPanel = new BasicFilterPanel();
        suffixFilterPanel = new SuffixFilterPanel();

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(i18n.getMessage("filter.basic.title"), basicFilterPanel);
        tabbedPane.addTab(i18n.getMessage("filter.suffix.title"), suffixFilterPanel);
        tabbedPane.setToolTipTextAt(0, i18n.getMessage("filter.dialog.hint"));

        // Create button panel
        JPanel buttonPanel = createButtonPanel();

        // Add components
        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Closing via the window button asks before dropping unsaved edits (UI-06)
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmDiscard()) {
                    dispose();
                }
            }
        });

        // Load current settings
        loadSettings();
        baseline = currentValues();
    }

    /**
     * Ask before closing when the dialog holds unsaved edits.
     *
     * @return {@code true} when closing may proceed
     */
    private boolean confirmDiscard() {
        if (!hasUnsavedChanges()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(
            this,
            i18n.getMessage("filter.discard.message"),
            i18n.getMessage("filter.discard.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * @return {@code true} when the widgets differ from the last loaded/saved state
     */
    private boolean hasUnsavedChanges() {
        return baseline != null && !baseline.equals(currentValues());
    }

    /**
     * Read every control into a map so it can be compared against the baseline.
     *
     * @return a snapshot of the values currently shown in the dialog
     */
    private Map<String, Object> currentValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        basicFilterPanel.snapshot(values);
        suffixFilterPanel.snapshot(values);
        return values;
    }

    /**
     * Creates the button panel.
     *
     * <p>UI-06: the primary action (Save) is the default button so the look and
     * feel paints it with the accent colour, while the destructive "reset"
     * action is a secondary (borderless) button placed away from Save and behind
     * a confirmation.</p>
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton resetButton = new JButton(i18n.getMessage("filter.button.reset"));
        resetButton.setToolTipText(i18n.getMessage("filter.button.reset.tooltip"));
        resetButton.putClientProperty("JButton.buttonType", "borderless");
        resetButton.addActionListener(e -> resetToDefaults());

        JButton cancelButton = new JButton(i18n.getMessage("filter.button.cancel"));
        cancelButton.addActionListener(e -> dispose());

        JButton saveButton = new JButton(i18n.getMessage("filter.button.save"));
        saveButton.addActionListener(e -> saveSettings());

        panel.add(resetButton);
        panel.add(Box.createHorizontalStrut(24));
        panel.add(cancelButton);
        panel.add(saveButton);

        // The default button gets the accent colour from the look and feel
        if (getRootPane() != null) {
            getRootPane().setDefaultButton(saveButton);
        }

        return panel;
    }

    /**
     * Loads current settings from ConfigManager.
     */
    private void loadSettings() {
        basicFilterPanel.load(configManager);
        suffixFilterPanel.load(configManager);
    }

    /**
     * Saves current settings to ConfigManager.
     */
    private void saveSettings() {
        basicFilterPanel.commitEdits();

        basicFilterPanel.save(configManager);
        suffixFilterPanel.save(configManager);

        JOptionPane.showMessageDialog(
            this,
            i18n.getMessage("config.success"),
            i18n.getMessage("common.success"),
            JOptionPane.INFORMATION_MESSAGE
        );

        baseline = currentValues();
        dispose();
    }

    /**
     * Resets all settings to default values.
     */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            i18n.getMessage("filter.reset.confirm"),
            i18n.getMessage("filter.reset.confirm.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            // Reset to default values
            basicFilterPanel.resetToDefaults();
            suffixFilterPanel.resetToDefaults();
            baseline = currentValues();

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
            // Only the title is refreshed here. The tab and field texts keep the
            // locale they were built with: the pages read their labels from the
            // bundle in their constructors and a new dialog is created every time
            // the menu entry is used, so they pick up a new locale on reopen.
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
