package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for managing device blacklist.
 * Allows users to add, remove, and clear blacklisted devices.
 */
public class BlacklistDialog extends JDialog {

    private final JList<String> blacklistList;
    private final DefaultListModel<String> listModel;

    /**
     * Creates a new blacklist dialog.
     *
     * @param parent parent frame
     */
    public BlacklistDialog(JFrame parent) {
        super(parent, "设备黑名单", true);
        setSize(500, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        // Initialize list model
        listModel = new DefaultListModel<>();
        loadBlacklist();

        // Create list with scroll pane
        blacklistList = new JList<>(listModel);
        blacklistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blacklistList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        blacklistList.setToolTipText("设备序列号 - 设备的唯一标识");

        JScrollPane scrollPane = new JScrollPane(blacklistList);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "已屏蔽的设备 (按序列号)",
                TitledBorder.LEFT,
                TitledBorder.TOP));

        // Create info panel
        JPanel infoPanel = createInfoPanel();

        // Create button panel
        JPanel buttonPanel = createButtonPanel();

        // Add components
        add(infoPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Add window listener to reload when dialog closes
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                // Optional: refresh device list when dialog closes
            }
        });
    }

    /**
     * Creates info panel with helpful instructions.
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 248, 255));

        JLabel infoLabel = new JLabel("<html>" +
                "<div style='padding: 10px;'>" +
                "<b>提示：</b><br>" +
                "• 黑名单使用设备序列号作为唯一标识<br>" +
                "• 序列号不会因盘符变化而改变<br>" +
                "• 可以在设备列表中直接点击'加入黑名单'按钮" +
                "</div>" +
                "</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        infoLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        panel.add(infoLabel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates button panel with all action buttons.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Add button
        JButton addButton = new JButton("添加");
        addButton.addActionListener(e -> addDevice());

        // Remove button
        JButton removeButton = new JButton("移除");
        removeButton.addActionListener(e -> removeDevice());

        // Clear all button
        JButton clearButton = new JButton("清空");
        clearButton.addActionListener(e -> clearBlacklist());

        // Close button
        JButton closeButton = new JButton("关闭");
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
        List<String> blacklist = ConfigManager.getInstance().get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL);
        for (String serialNumber : blacklist) {
            listModel.addElement(serialNumber);
        }
    }

    /**
     * Adds a new device serial number to the blacklist.
     */
    private void addDevice() {
        String serialNumber = JOptionPane.showInputDialog(
                this,
                "<html>输入设备序列号：<br>" +
                "<small>提示：可以从设备列表中点击'加入黑名单'按钮直接添加</small></html>",
                "添加设备到黑名单",
                JOptionPane.QUESTION_MESSAGE);

        if (serialNumber != null && !serialNumber.trim().isEmpty()) {
            serialNumber = serialNumber.trim();

            // Check for duplicates
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.getElementAt(i).equals(serialNumber)) {
                    JOptionPane.showMessageDialog(
                            this,
                            "该设备已在黑名单中: " + serialNumber,
                            "重复添加",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            // Add to list model and config
            listModel.addElement(serialNumber);
            saveBlacklist();
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
                    "确定要移除以下设备吗?\n" + serialNumber,
                    "确认移除",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                listModel.remove(selectedIndex);
                saveBlacklist();
            }
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "请先选择要移除的设备",
                    "未选择设备",
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
                    "黑名单为空",
                    "无设备",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "确定要清空所有黑名单设备吗?\n共 " + listModel.size() + " 个设备",
                "确认清空",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            listModel.clear();
            saveBlacklist();
            JOptionPane.showMessageDialog(
                    this,
                    "黑名单已清空",
                    "操作完成",
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
     * @return list of blacklisted device serial numbers
     */
    public static List<String> showBlacklistDialog(JFrame parent) {
        BlacklistDialog dialog = new BlacklistDialog(parent);
        dialog.setVisible(true);
        return dialog.getBlacklist();
    }
}
