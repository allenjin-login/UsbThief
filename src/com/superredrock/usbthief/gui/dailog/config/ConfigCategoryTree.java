package com.superredrock.usbthief.gui.dailog.config;

import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Left hand side of the preferences dialog: a search field above the tree of
 * configuration groups. Typing into the search field prunes the tree down to the
 * matching categories and selects the first match; selecting a category leaf
 * notifies the listener registered with {@link #setOnCategorySelected}.
 *
 * <p>Extracted verbatim from {@code ConfigDialog} (architecture-audit [19]).
 * The only structural change is that the tree shape now comes from
 * {@link ConfigCategories#buildRootNode()} instead of being written out twice.</p>
 */
public class ConfigCategoryTree extends JPanel {

    private static final I18nManager i18n = I18nManager.getInstance();

    private final JTextField searchField;
    private final JTree tree;
    private DefaultMutableTreeNode currentRootNode;
    private Consumer<CategoryNode> categorySelectionListener;

    public ConfigCategoryTree() {
        super(new BorderLayout());

        searchField = createSearchField();
        tree = createTree();

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

        add(searchPanel, BorderLayout.NORTH);
        add(treeScroll, BorderLayout.CENTER);

        // The preferred width is aligned with the split pane divider so that
        // packing the dialog does not steal width from the settings panel (which
        // would clip the value notes).
        setPreferredSize(new Dimension(200, getPreferredSize().height));
    }

    /**
     * Register the callback invoked when a category leaf is selected.
     */
    public void setOnCategorySelected(Consumer<CategoryNode> listener) {
        this.categorySelectionListener = listener;
    }

    /**
     * Select the first leaf node in the tree.
     */
    public void selectFirstLeaf() {
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
        currentRootNode = ConfigCategories.buildRootNode();

        JTree newTree = new JTree(currentRootNode);
        newTree.setRootVisible(false);
        newTree.setShowsRootHandles(true);
        newTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        newTree.setToggleClickCount(1); // Single click to expand/collapse

        newTree.addTreeSelectionListener(e -> {
            TreePath path = e.getPath();
            if (path != null) {
                Object lastComponent = path.getLastPathComponent();
                if (lastComponent instanceof DefaultMutableTreeNode) {
                    Object userObject = ((DefaultMutableTreeNode) lastComponent).getUserObject();
                    if (userObject instanceof CategoryNode) {
                        CategoryNode categoryNode = (CategoryNode) userObject;
                        if (categoryNode.isLeaf() && categorySelectionListener != null) {
                            categorySelectionListener.accept(categoryNode);
                        }
                    }
                }
            }
        });

        return newTree;
    }

    /**
     * Handle search text changes - filter tree and select first match.
     */
    private void onSearchTextChanged() {
        String searchText = searchField.getText().trim().toLowerCase();

        if (searchText.isEmpty()) {
            // Restore full tree
            rebuildTree();
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
        // Rebuild full tree first
        rebuildTree();

        // Remove non-matching nodes
        filterNode(currentRootNode, searchText);

        // Reload tree
        ((DefaultMutableTreeNode) tree.getModel().getRoot()).removeAllChildren();
        copyNodeChildren(currentRootNode, (DefaultMutableTreeNode) tree.getModel().getRoot());
        ((DefaultTreeModel) tree.getModel()).reload();

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
        if (userObject instanceof CategoryNode) {
            String nodeName = ((CategoryNode) userObject).getDisplayName().toLowerCase();
            if (nodeName.contains(searchText)) {
                return true;
            }
        } else if (userObject instanceof String) {
            // Group nodes match if they have matching children
            return hasMatchingChild;
        }

        return hasMatchingChild;
    }

    /**
     * Rebuild the tree from scratch.
     */
    private void rebuildTree() {
        currentRootNode = ConfigCategories.buildRootNode();
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

            if (userObject instanceof CategoryNode) {
                String nodeName = ((CategoryNode) userObject).getDisplayName().toLowerCase();
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
}
