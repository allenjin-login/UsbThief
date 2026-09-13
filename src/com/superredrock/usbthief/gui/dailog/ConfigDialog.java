package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.dailog.config.CategoryNode;
import com.superredrock.usbthief.gui.dailog.config.ConfigCategoryPanel;
import com.superredrock.usbthief.gui.dailog.config.ConfigCategoryTree;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * IntelliJ-style configuration dialog with tree navigation.
 * Features:
 * - Left panel: Search field + tree with grouped configuration categories
 * - Right panel: Breadcrumb + settings form for selected category
 * - Bottom: Reset-all + OK + Cancel buttons
 * - Search filters tree and auto-selects first match
 * - Breadcrumb shows "Group > Category" path
 *
 * <p>Every configuration entry is rendered with a human readable label, a
 * unit/default/range note and a tooltip (UI-01/UI-13). Labels, notes and
 * tooltips are resolved from i18n keys derived from the configuration key:
 * {@code config.entry.<key>.label}, {@code config.entry.<key>.default} and
 * {@code config.entry.<key>.hint}. When a key is absent the raw configuration
 * key / description is used as a fallback.</p>
 *
 * <p>Structure (architecture-audit [19]): the tree, its search filtering and the
 * category pages live in {@link ConfigCategoryTree} and {@link ConfigCategoryPanel};
 * this class only wires them together and owns the dialog-level actions
 * (save, reset, cross-field validation).</p>
 */
public class ConfigDialog extends JDialog {

    private static final I18nManager i18n = I18nManager.getInstance();

    /** i18n key of the page whose settings have a cross-field constraint (UI-13). */
    private static final String THREAD_POOL_PAGE_KEY = "config.category.threadPool";

    private final ConfigManager configManager;

    // UI Components
    private final ConfigCategoryTree categoryTree;
    private final JLabel breadcrumbLabel;
    private final JPanel rightPanel;
    private final JSplitPane splitPane;

    /** Pages built so far, keyed by the category i18n key; survives panel switches. */
    private final Map<String, ConfigCategoryPanel> categoryPanelCache = new HashMap<>();
    private String currentCategoryKey = null;

    public ConfigDialog(JFrame parent) {
        super(parent, i18n.getMessage("config.title"), true);
        setSize(900, 650);
        setLocationRelativeTo(parent);

        this.configManager = ConfigManager.getInstance();

        // Initialize components
        categoryTree = new ConfigCategoryTree();
        categoryTree.setOnCategorySelected(this::onCategorySelected);
        breadcrumbLabel = new JLabel(" ");
        breadcrumbLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        rightPanel = new JPanel(new BorderLayout());

        // Create split pane. The left panel's preferred width is aligned with the
        // divider location so that packing the dialog does not steal width from the
        // settings panel (which would clip the value notes).
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categoryTree, rightPanel);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.0);

        // Action buttons
        JButton okButton = new JButton(i18n.getMessage("config.button.ok"));
        okButton.addActionListener(e -> saveAndClose());

        JButton cancelButton = new JButton(i18n.getMessage("config.button.cancel"));
        cancelButton.addActionListener(e -> dispose());

        // UI-14: make the scope of "reset" explicit (it resets EVERY page, not just the current one)
        JButton resetButton = new JButton(i18n.getMessage("config.button.resetAll"));
        resetButton.setToolTipText(i18n.getMessage("config.button.resetAll.tooltip"));
        resetButton.addActionListener(e -> resetAllToDefaults());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(resetButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Layout
        setLayout(new BorderLayout(5, 5));
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Select first category by default
        categoryTree.selectFirstLeaf();
    }

    /**
     * Handle category selection in the tree.
     */
    private void onCategorySelected(CategoryNode categoryNode) {
        // Update breadcrumb
        String breadcrumb = i18n.getMessage("config.breadcrumb.format",
            categoryNode.getGroupName(), categoryNode.getDisplayName());
        breadcrumbLabel.setText(breadcrumb);

        // Build or retrieve panel for this category
        currentCategoryKey = categoryNode.getI18nKey();
        ConfigCategoryPanel categoryPanel = categoryPanelCache.get(currentCategoryKey);
        if (categoryPanel == null) {
            categoryPanel = new ConfigCategoryPanel(currentCategoryKey, configManager);
            categoryPanelCache.put(currentCategoryKey, categoryPanel);
        }

        // Update right panel
        rightPanel.removeAll();
        rightPanel.add(breadcrumbLabel, BorderLayout.NORTH);
        rightPanel.add(categoryPanel.getComponent(), BorderLayout.CENTER);
        rightPanel.revalidate();
        rightPanel.repaint();
    }

    /**
     * Save configuration and close dialog.
     */
    private void saveAndClose() {
        try {
            // UI-13: a value typed into a spinner is only committed on Enter or focus
            // loss. Commit every editor explicitly so typed input is never silently lost.
            commitSpinnerEdits();

            // UI-13: "max threads" must never be smaller than "core threads"
            if (!validateThreadPoolSettings()) {
                return;
            }

            // Iterate ALL categories (not just current one)
            for (ConfigCategoryPanel categoryPanel : categoryPanelCache.values()) {
                categoryPanel.saveTo(configManager);
            }

            JOptionPane.showMessageDialog(this,
                i18n.getMessage("config.success"),
                i18n.getMessage("common.success"),
                JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                i18n.getMessage("config.error.save") + ": " + e.getMessage(),
                i18n.getMessage("common.error"),
                JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Commit any text typed into a spinner editor that has not been confirmed with
     * Enter yet. Without this, typing {@code 1024} and pressing OK would silently
     * keep the previous value (UI-13).
     */
    private void commitSpinnerEdits() {
        for (ConfigCategoryPanel categoryPanel : categoryPanelCache.values()) {
            categoryPanel.commitEdits();
        }
    }

    /**
     * Validate cross-field constraints of the thread pool page (UI-13).
     *
     * @return {@code true} when the settings may be saved
     */
    private boolean validateThreadPoolSettings() {
        ConfigCategoryPanel threadPoolPage = categoryPanelCache.get(THREAD_POOL_PAGE_KEY);
        if (threadPoolPage == null) {
            return true;
        }
        Number core = threadPoolPage.readNumber(ThreadPoolConfig.CORE_POOL_SIZE.key());
        Number max = threadPoolPage.readNumber(ThreadPoolConfig.MAX_POOL_SIZE.key());
        if (core == null || max == null) {
            return true;
        }
        if (max.longValue() < core.longValue()) {
            JOptionPane.showMessageDialog(this,
                i18n.getMessage("config.validation.maxPoolSize"),
                i18n.getMessage("config.validation.title"),
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Reset all configuration to default values and refresh the UI.
     */
    private void resetAllToDefaults() {
        int confirmed = JOptionPane.showConfirmDialog(this,
            i18n.getMessage("config.reset.confirm"),
            i18n.getMessage("config.button.reset"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }

        configManager.resetToDefaults();

        // Clear the cache so pages rebuild with default values
        categoryPanelCache.clear();

        // Re-show current category (or first leaf)
        if (currentCategoryKey != null) {
            // Force rebuild by selecting a different node then back
            currentCategoryKey = null;
        }
        categoryTree.selectFirstLeaf();

        JOptionPane.showMessageDialog(this,
            i18n.getMessage("config.reset.success"),
            i18n.getMessage("common.success"),
            JOptionPane.INFORMATION_MESSAGE);
    }
}
