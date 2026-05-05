package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.index.HashAlgorithm;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class HashAlgorithmDialog extends JDialog implements I18nManager.LocaleChangeListener {

    private static final I18nManager i18n = I18nManager.getInstance();

    private JComboBox<HashAlgorithm> algorithmCombo;
    private JTextArea descriptionArea;
    private JLabel warningLabel;
    private JButton saveButton;
    private JButton cancelButton;
    private TitledBorder border;

    public HashAlgorithmDialog(JFrame parent) {
        super(parent, i18n.getMessage("hash.dialog.title"), true);
        setSize(480, 300);
        setMinimumSize(new Dimension(400, 250));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        i18n.addLocaleChangeListener(this);

        add(createContentPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        loadSettings();
        updateDescription();
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR),
                i18n.getMessage("hash.dialog.border"),
                TitledBorder.LEFT, TitledBorder.TOP);
        panel.setBorder(BorderFactory.createCompoundBorder(border, new EmptyBorder(10, 10, 10, 10)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Label + combo
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel label = new JLabel(i18n.getMessage("hash.dialog.label"));
        panel.add(label, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        algorithmCombo = new JComboBox<>(HashAlgorithm.values());
        algorithmCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                HashAlgorithm algo = (HashAlgorithm) value;
                return super.getListCellRendererComponent(list,
                        algo.id() + " (" + algo.outputLength() + " bytes)", index, sel, focus);
            }
        });
        algorithmCombo.addActionListener(e -> updateDescription());
        panel.add(algorithmCombo, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        descriptionArea = new JTextArea();
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBackground(panel.getBackground());
        descriptionArea.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        panel.add(descriptionArea, gbc);

        // Warning
        gbc.gridy = 2; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        warningLabel = new JLabel(i18n.getMessage("hash.dialog.warning"));
        warningLabel.setForeground(ThemeManager.ACCENT_WARNING);
        panel.add(warningLabel, gbc);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        saveButton = new JButton(i18n.getMessage("hash.button.save"));
        saveButton.addActionListener(e -> saveAndClose());
        cancelButton = new JButton(i18n.getMessage("hash.button.cancel"));
        cancelButton.addActionListener(e -> dispose());
        panel.add(saveButton);
        panel.add(cancelButton);
        return panel;
    }

    private void loadSettings() {
        String current = ConfigManager.getInstance().get(ConfigSchema.HASH_ALGORITHM);
        HashAlgorithm algo = HashAlgorithm.fromId(current);
        algorithmCombo.setSelectedItem(algo);
    }

    private void updateDescription() {
        HashAlgorithm selected = (HashAlgorithm) algorithmCombo.getSelectedItem();
        if (selected != null) {
            descriptionArea.setText(i18n.getMessage("hash.algorithm." + selected.name().toLowerCase() + ".desc"));
        }
    }

    private void saveAndClose() {
        HashAlgorithm selected = (HashAlgorithm) algorithmCombo.getSelectedItem();
        String newAlgo = null;
        if (selected != null) {
            newAlgo = selected.id();
        }
        String currentAlgo = ConfigManager.getInstance().get(ConfigSchema.HASH_ALGORITHM);

        if (newAlgo != null && !newAlgo.equals(currentAlgo)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    i18n.getMessage("hash.confirm.change"),
                    i18n.getMessage("hash.confirm.title"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            QueueManager.getIndex().clear();
            ConfigManager.getInstance().set(ConfigSchema.HASH_ALGORITHM, newAlgo);
            ConfigManager.getInstance().set(ConfigSchema.HASH_ALGORITHM_LAST, newAlgo);

            JOptionPane.showMessageDialog(this,
                    i18n.getMessage("hash.success.cleared"),
                    i18n.getMessage("common.success"), JOptionPane.INFORMATION_MESSAGE);
        }
        dispose();
    }

    @Override
    public void onLocaleChanged(java.util.Locale newLocale) {
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("hash.dialog.title"));
            border.setTitle(i18n.getMessage("hash.dialog.border"));
            warningLabel.setText(i18n.getMessage("hash.dialog.warning"));
            saveButton.setText(i18n.getMessage("hash.button.save"));
            cancelButton.setText(i18n.getMessage("hash.button.cancel"));
            updateDescription();
            revalidate();
            repaint();
        });
    }

    @Override
    public void dispose() {
        i18n.removeLocaleChangeListener(this);
        super.dispose();
    }

    public static void showDialog(JFrame parent) {
        HashAlgorithmDialog dialog = new HashAlgorithmDialog(parent);
        dialog.setVisible(true);
    }
}
