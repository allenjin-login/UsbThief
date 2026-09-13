package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.BlacklistConfig;
import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for managing device blacklist.
 * Allows users to add, remove, and clear blacklisted devices.
 *
 * <p>Humanization notes:</p>
 * <ul>
 *   <li>UI-08 - the explanation panel uses colours derived from the active
 *       theme instead of a hard coded light blue, so it stays readable in the
 *       dark theme; an empty state explains what to do; Remove/Clear are
 *       disabled while there is nothing to act on.</li>
 *   <li>UI-16 - the list has a column header and a device count.</li>
 * </ul>
 */
public class BlacklistDialog extends JDialog {

    private static final I18nManager i18n = I18nManager.getInstance();

    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";

    private final JList<String> blacklistList;
    private final DefaultListModel<String> listModel;

    private JPanel listCards;
    private JPanel headerPanel;
    private JLabel countLabel;

    private JButton addButton;
    private JButton removeButton;
    private JButton clearButton;

    /**
     * Creates a new blacklist dialog.
     *
     * @param parent parent frame
     */
    public BlacklistDialog(JFrame parent) {
        super(parent, i18n.getMessage("blacklist.title"), true);
        setSize(500, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        // Initialize list model
        listModel = new DefaultListModel<>();

        // Create list with scroll pane
        blacklistList = new JList<>(listModel);
        blacklistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blacklistList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        blacklistList.setToolTipText(i18n.getMessage("blacklist.tooltip"));
        blacklistList.addListSelectionListener(e -> refreshState());

        JScrollPane scrollPane = new JScrollPane(blacklistList);

        // Column header + count (UI-16)
        headerPanel = createHeaderPanel();

        // Card layout: the list, or an empty state that says what to do (UI-08)
        listCards = new JPanel(new CardLayout());
        listCards.add(scrollPane, CARD_LIST);
        listCards.add(createEmptyStatePanel(), CARD_EMPTY);

        JPanel listArea = new JPanel(new BorderLayout());
        listArea.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                i18n.getMessage("blacklist.border.title"),
                TitledBorder.LEFT,
                TitledBorder.TOP));
        listArea.add(headerPanel, BorderLayout.NORTH);
        listArea.add(listCards, BorderLayout.CENTER);

        // Create info panel
        JPanel infoPanel = createInfoPanel();

        // Create button panel
        JPanel buttonPanel = createButtonPanel();

        // Add components
        add(infoPanel, BorderLayout.NORTH);
        add(listArea, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Load entries and sync the enabled state of the actions
        loadBlacklist();
    }

    /**
     * Creates the small header row above the list: the column name and the count.
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JLabel columnLabel = new JLabel(i18n.getMessage("blacklist.list.header"));
        columnLabel.setFont(columnLabel.getFont().deriveFont(Font.BOLD));
        Color secondary = UIManager.getColor("Label.disabledForeground");
        if (secondary != null) {
            columnLabel.setForeground(secondary);
        }

        countLabel = new JLabel();
        if (secondary != null) {
            countLabel.setForeground(secondary);
        }

        panel.add(columnLabel, BorderLayout.WEST);
        panel.add(countLabel, BorderLayout.EAST);
        return panel;
    }

    /**
     * Creates the placeholder shown while the blacklist is empty (UI-08).
     */
    private JPanel createEmptyStatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 8, 4, 8);

        JLabel title = new JLabel(i18n.getMessage("blacklist.empty.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));

        JLabel text = new JLabel(i18n.getMessage("blacklist.empty.text"));
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            text.setForeground(muted);
        }

        gbc.gridy = 0;
        panel.add(title, gbc);
        gbc.gridy = 1;
        panel.add(text, gbc);
        return panel;
    }

    /**
     * Creates info panel with helpful instructions.
     *
     * <p>Colours are derived from the active theme and the accent palette; the
     * previous hard coded light blue produced light text on a light background
     * in the dark theme (UI-08).</p>
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        boolean dark = ThemeManager.getInstance().isDarkTheme();

        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            base = dark ? new Color(0x2B2B2B) : new Color(0xF1F5F9);
        }
        panel.setBackground(blend(base, ThemeManager.ACCENT_INFO, dark ? 0.18 : 0.10));

        JLabel infoLabel = new JLabel("<html>" +
                "<div style='padding: 10px;'>" +
                "<b>" + i18n.getMessage("blacklist.info.title") + "</b><br>" +
                i18n.getMessage("blacklist.info.text").replace("\n", "<br>") +
                "</div>" +
                "</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        Color foreground = UIManager.getColor("Label.foreground");
        if (foreground != null) {
            infoLabel.setForeground(foreground);
        }
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 12f));

        panel.add(infoLabel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Mixes {@code tint} into {@code base}.
     *
     * @param base  the background colour of the current theme
     * @param tint  the accent colour to blend in
     * @param ratio how much of the accent to apply (0..1)
     * @return the blended colour
     */
    private static Color blend(Color base, Color tint, double ratio) {
        int red = (int) Math.round(base.getRed() * (1 - ratio) + tint.getRed() * ratio);
        int green = (int) Math.round(base.getGreen() * (1 - ratio) + tint.getGreen() * ratio);
        int blue = (int) Math.round(base.getBlue() * (1 - ratio) + tint.getBlue() * ratio);
        return new Color(
                Math.max(0, Math.min(255, red)),
                Math.max(0, Math.min(255, green)),
                Math.max(0, Math.min(255, blue)));
    }

    /**
     * Creates button panel with all action buttons.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Add button
        addButton = new JButton(i18n.getMessage("blacklist.button.add"));
        addButton.addActionListener(e -> addDevice());

        // Remove button
        removeButton = new JButton(i18n.getMessage("blacklist.button.remove"));
        removeButton.setToolTipText(i18n.getMessage("blacklist.button.remove.tooltip"));
        removeButton.addActionListener(e -> removeDevice());

        // Clear all button (destructive - rendered as a secondary button)
        clearButton = new JButton(i18n.getMessage("blacklist.button.clear"));
        clearButton.setToolTipText(i18n.getMessage("blacklist.button.clear.tooltip"));
        clearButton.putClientProperty("JButton.buttonType", "borderless");
        clearButton.addActionListener(e -> clearBlacklist());

        // Close button
        JButton closeButton = new JButton(i18n.getMessage("blacklist.button.close"));
        closeButton.addActionListener(e -> dispose());

        // Add to panel
        panel.add(addButton);
        panel.add(removeButton);
        panel.add(clearButton);
        panel.add(closeButton);

        return panel;
    }

    /**
     * Loads blacklist from Config into list model.
     */
    private void loadBlacklist() {
        listModel.clear();
        List<String> blacklist = ConfigManager.getInstance().get(BlacklistConfig.DEVICE_BLACKLIST_BY_SERIAL);
        for (String serialNumber : blacklist) {
            listModel.addElement(serialNumber);
        }
        refreshState();
    }

    /**
     * Syncs the empty state, the counter and the enabled state of the actions.
     */
    private void refreshState() {
        boolean empty = listModel.isEmpty();
        if (listCards != null) {
            ((CardLayout) listCards.getLayout()).show(listCards, empty ? CARD_EMPTY : CARD_LIST);
        }
        if (countLabel != null) {
            countLabel.setText(i18n.getMessage("blacklist.count", listModel.size()));
        }
        if (headerPanel != null) {
            headerPanel.setVisible(!empty);
        }
        if (removeButton != null) {
            removeButton.setEnabled(!empty && blacklistList.getSelectedIndex() >= 0);
        }
        if (clearButton != null) {
            clearButton.setEnabled(!empty);
        }
    }

    /**
     * Adds a new device serial number to the blacklist.
     */
    private void addDevice() {
        String serialNumber = JOptionPane.showInputDialog(
                this,
                "<html>" + i18n.getMessage("blacklist.add.prompt").replace("\n", "<br>") + "</html>",
                i18n.getMessage("blacklist.add.title"),
                JOptionPane.QUESTION_MESSAGE);

        if (serialNumber != null && !serialNumber.trim().isEmpty()) {
            serialNumber = serialNumber.trim();

            // Check for duplicates
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.getElementAt(i).equals(serialNumber)) {
                    JOptionPane.showMessageDialog(
                            this,
                            i18n.getMessage("blacklist.add.duplicate", serialNumber),
                            i18n.getMessage("blacklist.add.duplicate.title"),
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            // Add to list model and config
            listModel.addElement(serialNumber);
            saveBlacklist();
            refreshState();
        }
    }

    /**
     * Removes the selected device from the blacklist.
     */
    private void removeDevice() {
        int selectedIndex = blacklistList.getSelectedIndex();
        if (selectedIndex != -1) {
            String serialNumber = listModel.getElementAt(selectedIndex);

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    i18n.getMessage("blacklist.remove.confirm", serialNumber),
                    i18n.getMessage("blacklist.remove.confirm.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                listModel.remove(selectedIndex);
                saveBlacklist();
                refreshState();
            }
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    i18n.getMessage("blacklist.remove.noselection"),
                    i18n.getMessage("blacklist.remove.noselection.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Clears all devices from the blacklist.
     */
    private void clearBlacklist() {
        if (listModel.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    i18n.getMessage("blacklist.clear.empty"),
                    i18n.getMessage("blacklist.clear.empty.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                i18n.getMessage("blacklist.clear.confirm", listModel.size()),
                i18n.getMessage("blacklist.clear.confirm.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            listModel.clear();
            saveBlacklist();
            refreshState();
            JOptionPane.showMessageDialog(
                    this,
                    i18n.getMessage("blacklist.clear.success"),
                    i18n.getMessage("blacklist.clear.success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Saves the current list model content to Config.
     */
    private void saveBlacklist() {
        List<String> blacklist = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            blacklist.add(listModel.getElementAt(i));
        }
        ConfigManager.getInstance().setDeviceBlacklistBySerial(blacklist);
    }

    /**
     * Returns the current blacklist as a list.
     *
     * @return list of blacklisted device serial numbers
     */
    public List<String> getBlacklist() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            result.add(listModel.getElementAt(i));
        }
        return result;
    }

    /**
     * Shows the dialog and returns the final blacklist.
     *
     * @param parent parent frame
     */
    public static void showBlacklistDialog(JFrame parent) {
        BlacklistDialog dialog = new BlacklistDialog(parent);
        dialog.setVisible(true);
        dialog.getBlacklist();
    }
}
