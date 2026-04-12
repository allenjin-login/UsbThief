package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;
import com.superredrock.usbthief.gui.components.EmptyStatePanel;
import com.superredrock.usbthief.gui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Collection;
import java.util.Locale;

public class DeviceInfoDialog extends JDialog implements I18NManager.LocaleChangeListener {

    private final I18NManager i18n = I18NManager.getInstance();
    private final DeviceManager deviceManager;
    private final JPanel cardsPanel;
    private EmptyStatePanel emptyStatePanel;
    private JScrollPane scrollPane;

    public DeviceInfoDialog(JFrame owner) {
        super(owner, I18NManager.getInstance().getMessage("deviceinfo.title"), false);
        this.deviceManager = DeviceManager.getInstance();

        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(owner);

        cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));

        refreshDevices();
        registerListeners();
        i18n.addLocaleChangeListener(this);
    }

    private void registerListeners() {
        EventBus bus = EventBus.getInstance();
        bus.register(DeviceArrivalEvent.class, e -> SwingUtilities.invokeLater(this::refreshDevices));
        bus.register(DeviceRemovalEvent.class, e -> SwingUtilities.invokeLater(this::refreshDevices));
        bus.register(NewDeviceJoinedEvent.class, e -> SwingUtilities.invokeLater(this::refreshDevices));
    }

    private void refreshDevices() {
        cardsPanel.removeAll();
        Collection<Device> devices = deviceManager.getAllDevices();

        if (devices.isEmpty()) {
            emptyStatePanel = new EmptyStatePanel(
                    "\uD83D\uDD0C",
                    i18n.getMessage("deviceinfo.empty.title"),
                    i18n.getMessage("deviceinfo.empty.description")
            );
            cardsPanel.add(emptyStatePanel);
        } else {
            for (Device device : devices) {
                cardsPanel.add(createDeviceCard(device));
                cardsPanel.add(Box.createVerticalStrut(8));
            }
        }

        cardsPanel.revalidate();
        cardsPanel.repaint();

        // Wrap in scroll pane if not already
        if (scrollPane == null) {
            scrollPane = new JScrollPane(cardsPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(10, 10, 10, 10),
                    new TitledBorder(i18n.getMessage("deviceinfo.border"))
            ));
            setLayout(new BorderLayout());
            add(scrollPane, BorderLayout.CENTER);
        }
    }

    private JPanel createDeviceCard(Device device) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1, true),
                new EmptyBorder(12, 16, 12, 16)
        ));
        card.setBackground(ThemeManager.CARD_BACKGROUND);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Serial
        gbc.gridx = 0; gbc.gridy = 0;
        card.add(createLabel(i18n.getMessage("deviceinfo.card.serial") + ": ", true), gbc);
        gbc.gridx = 1;
        card.add(createLabel(device.getSerialNumber(), false), gbc);

        // VID
        gbc.gridx = 0; gbc.gridy = 1;
        card.add(createLabel(i18n.getMessage("deviceinfo.card.vid") + ": ", true), gbc);
        gbc.gridx = 1;
        card.add(createLabel(device.getVid() != null ? device.getVid() : "-", false), gbc);

        // PID
        gbc.gridx = 0; gbc.gridy = 2;
        card.add(createLabel(i18n.getMessage("deviceinfo.card.pid") + ": ", true), gbc);
        gbc.gridx = 1;
        card.add(createLabel(device.getPid() != null ? device.getPid() : "-", false), gbc);

        // Device Path
        gbc.gridx = 0; gbc.gridy = 3;
        card.add(createLabel(i18n.getMessage("deviceinfo.card.path") + ": ", true), gbc);
        gbc.gridx = 1;
        JLabel pathLabel = new JLabel(device.getDevicePath() != null ? device.getDevicePath() : "-");
        pathLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        pathLabel.setForeground(ThemeManager.TEXT_SECONDARY);
        card.add(pathLabel, gbc);

        return card;
    }

    private JLabel createLabel(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 13));
        if (bold) {
            label.setForeground(ThemeManager.TEXT_PRIMARY);
        }
        return label;
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        SwingUtilities.invokeLater(this::refreshLanguage);
    }

    public void refreshLanguage() {
        setTitle(i18n.getMessage("deviceinfo.title"));
        refreshDevices();
    }
}
