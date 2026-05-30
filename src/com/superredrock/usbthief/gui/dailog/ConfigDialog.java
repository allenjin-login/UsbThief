package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigType;
import com.superredrock.usbthief.core.config.configs.*;
import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IntelliJ-style configuration dialog with tree navigation.
 * Features:
 * - Left panel: Search field + tree with grouped configuration categories
 * - Right panel: Breadcrumb + settings form for selected category
 * - Bottom: OK + Cancel buttons
 * - Search filters tree and auto-selects first match
 * - Breadcrumb shows "Group > Category" path
 */
public class ConfigDialog extends JDialog {

    private static final I18nManager i18n = I18nManager.getInstance();
    private final ConfigManager configManager;

    // UI Components
    private final JTextField searchField;
    private final JTree tree;
    private final JLabel breadcrumbLabel;
    private final JPanel rightPanel;
    private final JSplitPane splitPane;

    // State management - persists across panel switches
    private final Map<String, Map<String, JComponent>> allCategoryComponents = new HashMap<>();
    private final Map<String, JPanel> categoryPanelCache = new HashMap<>();
    private String currentCategoryKey = null;
    private DefaultMutableTreeNode currentRootNode;

    public ConfigDialog(JFrame parent) {
        super(parent, i18n.getMessage("config.title"), true);
        setSize(900, 650);
        setLocationRelativeTo(parent);

        this.configManager = ConfigManager.getInstance();

        // Initialize components
        searchField = createSearchField();
        tree = createTree();
        breadcrumbLabel = new JLabel(" ");
        breadcrumbLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        rightPanel = new JPanel(new BorderLayout());

        // Create split pane
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftPanel(), rightPanel);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.0);

        // Action buttons
        JButton okButton = new JButton(i18n.getMessage("config.button.ok"));
        okButton.addActionListener(e -> saveAndClose());

        JButton cancelButton = new JButton(i18n.getMessage("config.button.cancel"));
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Layout
        setLayout(new BorderLayout(5, 5));
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Select first category by default
        selectFirstLeaf();
    }

    /**
     * Create search field with placeholder text support.
     */
    private JTextField createSearchField() {
        JTextField field = new JTextField();
        field.putClientProperty("JTextField.placeholderText", i18n.getMessage("config.search.placeholder"));

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearchTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearchTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearchTextChanged();
            }
        });

        return field;
    }

    /**
     * Create the configuration tree structure.
     */
    private JTree createTree() {
        currentRootNode = new DefaultMutableTreeNode();

        // Build tree structure: 9 parent groups with leaf children
        addGroupNode(i18n.getMessage("config.group.general"),
            i18n.getMessage("config.category.threadPool"), "config.category.threadPool",
            i18n.getMessage("config.category.scanner"), "config.category.scanner"
        );

        addGroupNode(i18n.getMessage("config.group.file"),
            i18n.getMessage("config.category.fileCopy"), "config.category.fileCopy",
            i18n.getMessage("config.category.fileWatch"), "config.category.fileWatch",
            i18n.getMessage("config.category.fileFilter"), "config.category.fileFilter",
            i18n.getMessage("config.category.suffixFilter"), "config.category.suffixFilter"
        );

        addGroupNode(i18n.getMessage("config.group.index"),
            i18n.getMessage("config.category.index"), "config.category.index"
        );

        addGroupNode(i18n.getMessage("config.group.rateLimit"),
            i18n.getMessage("config.category.rateLimit"), "config.category.rateLimit"
        );

        addGroupNode(i18n.getMessage("config.group.paths"),
            i18n.getMessage("config.category.paths"), "config.category.paths"
        );

        addGroupNode(i18n.getMessage("config.group.ui"),
            i18n.getMessage("config.category.ui"), "config.category.ui",
            i18n.getMessage("config.category.window"), "config.category.window"
        );

        addGroupNode(i18n.getMessage("config.group.security"),
            i18n.getMessage("config.category.blacklist"), "config.category.blacklist"
        );

        addGroupNode(i18n.getMessage("config.group.storage"),
            i18n.getMessage("config.category.storage"), "config.category.storage"
        );

        addGroupNode(i18n.getMessage("config.group.advanced"),
            i18n.getMessage("config.category.statisticsApi"), "config.category.statisticsApi"
        );

        JTree newTree = new JTree(currentRootNode);
        newTree.setRootVisible(false);
        newTree.setShowsRootHandles(true);
        newTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        newTree.setToggleClickCount(1); // Single click to expand/collapse

        // Tree selection listener
        newTree.addTreeSelectionListener(e -> {
            TreePath path = e.getPath();
            if (path != null) {
                Object lastComponent = path.getLastPathComponent();
                if (lastComponent instanceof DefaultMutableTreeNode node) {
                    Object userObject = node.getUserObject();
                    if (userObject instanceof CategoryNode catNode && catNode.isLeaf()) {
                        onCategorySelected(catNode);
                    }
                }
            }
        });

        return newTree;
    }

    /**
     * Add a group node with its leaf children to the tree.
     */
    private void addGroupNode(String groupName, Object... nameKeyPairs) {
        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupName);

        for (int i = 0; i < nameKeyPairs.length; i += 2) {
            String displayName = (String) nameKeyPairs[i];
            String i18nKey = (String) nameKeyPairs[i + 1];
            groupNode.add(new DefaultMutableTreeNode(new CategoryNode(displayName, i18nKey, groupName)));
        }

        currentRootNode.add(groupNode);
    }

    /**
     * Create the left panel with search and tree.
     */
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(treeScroll, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Handle search text changes - filter tree and select first match.
     */
    private void onSearchTextChanged() {
        String searchText = searchField.getText().trim().toLowerCase();

        if (searchText.isEmpty()) {
            // Restore full tree
            rebuildTree(currentRootNode);
            expandAllNodes();
            return;
        }

        // Filter tree based on search text
        filterTree(searchText);
    }

    /**
     * Filter tree to show only nodes matching search text.
     */
    private void filterTree(String searchText) {
        // Save current selection
        TreePath selectedPath = tree.getSelectionPath();

        // Rebuild full tree first
        rebuildTree(currentRootNode);

        // Remove non-matching nodes
        filterNode(currentRootNode, searchText);

        // Reload tree
        ((DefaultMutableTreeNode) tree.getModel().getRoot()).removeAllChildren();
        copyNodeChildren(currentRootNode, (DefaultMutableTreeNode) tree.getModel().getRoot());
        ((javax.swing.tree.DefaultTreeModel) tree.getModel()).reload();

        // Expand all and select first match
        expandAllNodes();
        selectFirstMatchingLeaf(searchText);
    }

    /**
     * Recursively filter nodes based on search text.
     */
    private boolean filterNode(DefaultMutableTreeNode node, String searchText) {
        boolean hasMatchingChild = false;

        // Process children first (bottom-up)
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            boolean childMatches = filterNode(child, searchText);

            if (!childMatches) {
                node.remove(i);
            } else {
                hasMatchingChild = true;
            }
        }

        // Check if this node matches (has user object matching search)
        Object userObject = node.getUserObject();
        if (userObject instanceof CategoryNode catNode) {
            String nodeName = catNode.getDisplayName().toLowerCase();
            if (nodeName.contains(searchText)) {
                return true;
            }
        } else if (userObject instanceof String groupName) {
            // Group nodes match if they have matching children
            return hasMatchingChild;
        }

        return hasMatchingChild;
    }

    /**
     * Rebuild the tree from scratch.
     */
    private void rebuildTree(DefaultMutableTreeNode originalRoot) {
        // Create a new tree to restore from
        currentRootNode = new DefaultMutableTreeNode();

        // Re-add all groups
        addGroupNode(i18n.getMessage("config.group.general"),
            i18n.getMessage("config.category.threadPool"), "config.category.threadPool",
            i18n.getMessage("config.category.scanner"), "config.category.scanner"
        );

        addGroupNode(i18n.getMessage("config.group.file"),
            i18n.getMessage("config.category.fileCopy"), "config.category.fileCopy",
            i18n.getMessage("config.category.fileWatch"), "config.category.fileWatch",
            i18n.getMessage("config.category.fileFilter"), "config.category.fileFilter",
            i18n.getMessage("config.category.suffixFilter"), "config.category.suffixFilter"
        );

        addGroupNode(i18n.getMessage("config.group.index"),
            i18n.getMessage("config.category.index"), "config.category.index"
        );

        addGroupNode(i18n.getMessage("config.group.rateLimit"),
            i18n.getMessage("config.category.rateLimit"), "config.category.rateLimit"
        );

        addGroupNode(i18n.getMessage("config.group.paths"),
            i18n.getMessage("config.category.paths"), "config.category.paths"
        );

        addGroupNode(i18n.getMessage("config.group.ui"),
            i18n.getMessage("config.category.ui"), "config.category.ui",
            i18n.getMessage("config.category.window"), "config.category.window"
        );

        addGroupNode(i18n.getMessage("config.group.security"),
            i18n.getMessage("config.category.blacklist"), "config.category.blacklist"
        );

        addGroupNode(i18n.getMessage("config.group.storage"),
            i18n.getMessage("config.category.storage"), "config.category.storage"
        );

        addGroupNode(i18n.getMessage("config.group.advanced"),
            i18n.getMessage("config.category.statisticsApi"), "config.category.statisticsApi"
        );
    }

    /**
     * Copy node children from source to target.
     */
    private void copyNodeChildren(DefaultMutableTreeNode source, DefaultMutableTreeNode target) {
        for (int i = 0; i < source.getChildCount(); i++) {
            DefaultMutableTreeNode sourceChild = (DefaultMutableTreeNode) source.getChildAt(i);
            DefaultMutableTreeNode targetChild = new DefaultMutableTreeNode(sourceChild.getUserObject());
            target.add(targetChild);
            copyNodeChildren(sourceChild, targetChild);
        }
    }

    /**
     * Expand all nodes in the tree.
     */
    private void expandAllNodes() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    /**
     * Select the first matching leaf node based on search text.
     */
    private void selectFirstMatchingLeaf(String searchText) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        TreePath firstMatch = findFirstMatchingLeaf(root, searchText);
        if (firstMatch != null) {
            tree.setSelectionPath(firstMatch);
            tree.scrollPathToVisible(firstMatch);
        }
    }

    /**
     * Recursively find first matching leaf.
     */
    private TreePath findFirstMatchingLeaf(DefaultMutableTreeNode node, String searchText) {
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object userObject = child.getUserObject();

            if (userObject instanceof CategoryNode catNode) {
                String nodeName = catNode.getDisplayName().toLowerCase();
                if (nodeName.contains(searchText)) {
                    return new TreePath(((DefaultMutableTreeNode) tree.getModel().getRoot()).getPath()[0].equals(node)
                        ? new Object[]{node, child}
                        : getPathToNode(child));
                }
            }

            // Check children
            TreePath childPath = findFirstMatchingLeaf(child, searchText);
            if (childPath != null) {
                return childPath;
            }
        }
        return null;
    }

    /**
     * Get path to a node.
     */
    private Object[] getPathToNode(DefaultMutableTreeNode node) {
        List<Object> path = new ArrayList<>();
        Object current = node;
        while (current != null) {
            path.add(0, current);
            current = (current instanceof DefaultMutableTreeNode)
                ? ((DefaultMutableTreeNode) current).getParent()
                : null;
        }
        return path.toArray();
    }

    /**
     * Select the first leaf node in the tree.
     */
    private void selectFirstLeaf() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        if (root.getChildCount() > 0) {
            DefaultMutableTreeNode firstGroup = (DefaultMutableTreeNode) root.getChildAt(0);
            if (firstGroup.getChildCount() > 0) {
                DefaultMutableTreeNode firstLeaf = (DefaultMutableTreeNode) firstGroup.getChildAt(0);
                TreePath path = new TreePath(new Object[]{root, firstGroup, firstLeaf});
                tree.setSelectionPath(path);
            }
        }
    }

    /**
     * Handle category selection in the tree.
     */
    private void onCategorySelected(CategoryNode categoryNode) {
        // Capture current panel values before switching
        if (currentCategoryKey != null) {
            captureCurrentPanelValues();
        }

        // Update breadcrumb
        String breadcrumb = i18n.getMessage("config.breadcrumb.format",
            categoryNode.getGroupName(), categoryNode.getDisplayName());
        breadcrumbLabel.setText(breadcrumb);

        // Resolve category name to actual category constant
        String categoryName = resolveCategoryName(categoryNode.getI18nKey());
        currentCategoryKey = categoryNode.getI18nKey();

        // Build or retrieve panel for this category
        JPanel categoryPanel = categoryPanelCache.get(currentCategoryKey);
        if (categoryPanel == null) {
            categoryPanel = buildCategoryPanel(categoryName);
            categoryPanelCache.put(currentCategoryKey, categoryPanel);
            // Initialize components map for this category
            if (!allCategoryComponents.containsKey(currentCategoryKey)) {
                allCategoryComponents.put(currentCategoryKey, new HashMap<>());
            }
        }

        // Update right panel
        rightPanel.removeAll();
        rightPanel.add(breadcrumbLabel, BorderLayout.NORTH);
        rightPanel.add(categoryPanel, BorderLayout.CENTER);
        rightPanel.revalidate();
        rightPanel.repaint();
    }

    /**
     * Resolve i18n category key to actual category name constant.
     */
    private String resolveCategoryName(String i18nKey) {
        return switch (i18nKey) {
            case "config.category.threadPool" -> ThreadPoolConfig.CATEGORY;
            case "config.category.scanner" -> DeviceScannerConfig.CATEGORY;
            case "config.category.index" -> IndexConfig.CATEGORY;
            case "config.category.fileCopy" -> FileCopyConfig.CATEGORY;
            case "config.category.fileWatch" -> FileWatchConfig.CATEGORY;
            case "config.category.rateLimit" -> RateLimitConfig.CATEGORY;
            case "config.category.paths" -> PathConfig.CATEGORY;
            case "config.category.ui" -> UIConfig.CATEGORY;
            case "config.category.window" -> WindowConfig.CATEGORY;
            case "config.category.blacklist" -> BlacklistConfig.CATEGORY;
            case "config.category.fileFilter" -> FileFilterConfig.CATEGORY;
            case "config.category.suffixFilter" -> SuffixFilterConfig.CATEGORY;
            case "config.category.storage" -> StorageConfig.CATEGORY;
            case "config.category.statisticsApi" -> StatisticsApiConfig.CATEGORY;
            default -> throw new IllegalArgumentException("Unknown category key: " + i18nKey);
        };
    }

    /**
     * Build panel for a specific category.
     */
    private JPanel buildCategoryPanel(String categoryName) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        List<ConfigEntry<?>> entries = ConfigSchema.getEntriesByCategory().get(categoryName);
        if (entries != null) {
            int row = 0;
            for (ConfigEntry<?> entry : entries) {
                JComponent component = createValueComponent(entry);
                // Store component in map
                allCategoryComponents.get(currentCategoryKey).put(entry.key(), component);

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
        }

        return panel;
    }

    /**
     * Capture current panel values before switching.
     */
    private void captureCurrentPanelValues() {
        // Values are already stored in the components themselves
        // This method is a placeholder for any pre-switch logic
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
        return new JLabel("?");
    }

    /**
     * Create spinner for integer/long values.
     */
    private JSpinner createSpinner(Number value, String description) {
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
        textArea.setToolTipText(description + " (" + i18n.getMessage("config.tooltip.separator") + ")");
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return textArea;
    }

    /**
     * Save configuration and close dialog.
     */
    private void saveAndClose() {
        try {
            // Capture current panel values first
            if (currentCategoryKey != null) {
                captureCurrentPanelValues();
            }

            // Iterate ALL categories (not just current one)
            for (Map.Entry<String, Map<String, JComponent>> categoryEntry : allCategoryComponents.entrySet()) {
                String i18nKey = categoryEntry.getKey();
                Map<String, JComponent> components = categoryEntry.getValue();

                // Resolve category name
                String categoryName = resolveCategoryName(i18nKey);

                // Get entries for this category
                List<ConfigEntry<?>> entries = ConfigSchema.getEntriesByCategory().get(categoryName);
                if (entries != null) {
                    for (ConfigEntry<?> entry : entries) {
                        JComponent component = components.get(entry.key());
                        if (component == null) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        ConfigEntry<Object> typedEntry = (ConfigEntry<Object>) entry;

                        Object newValue;
                        if (entry.type() == ConfigType.INT) {
                            Object spinnerValue = ((JSpinner) component).getValue();
                            newValue = ((Number) spinnerValue).intValue();
                        } else if (entry.type() == ConfigType.LONG) {
                            Object spinnerValue = ((JSpinner) component).getValue();
                            newValue = ((Number) spinnerValue).longValue();
                        } else if (entry.type() == ConfigType.BOOLEAN) {
                            newValue = ((JCheckBox) component).isSelected();
                        } else if (entry.type() == ConfigType.STRING) {
                            newValue = ((JTextField) component).getText();
                        } else if (entry.type() == ConfigType.STRING_LIST) {
                            String text = ((JTextArea) component).getText();
                            List<String> list = new ArrayList<>();
                            for (String part : text.split(";")) {
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
     * Inner class to store category node information.
     */
    private static class CategoryNode {
        private final String displayName;
        private final String i18nKey;
        private final String groupName;

        public CategoryNode(String displayName, String i18nKey, String groupName) {
            this.displayName = displayName;
            this.i18nKey = i18nKey;
            this.groupName = groupName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getI18nKey() {
            return i18nKey;
        }

        public String getGroupName() {
            return groupName;
        }

        public boolean isLeaf() {
            return true;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
