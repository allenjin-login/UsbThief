package com.superredrock.usbthief.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Welcome dialog shown on first run to guide new users.
 */
public class WelcomeDialog extends JDialog {

    private static final String FIRST_RUN_KEY = "firstRunCompleted";
    private final I18NManager i18n;

    public WelcomeDialog(Frame parent) {
        super(parent, "UsbThief", true);
        this.i18n = I18NManager.getInstance();
        setTitle(i18n.getMessage("welcome.title"));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(480, 400);
        setLocationRelativeTo(parent);
        setResizable(false);
        
        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 16));
        mainPanel.setBorder(new EmptyBorder(24, 32, 24, 32));

        // Header
        JLabel titleLabel = new JLabel(i18n.getMessage("welcome.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitleLabel = new JLabel(i18n.getMessage("welcome.subtitle"));
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(14f));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        headerPanel.add(titleLabel);
        headerPanel.add(subtitleLabel);

        // Content - Feature highlights
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(16, 0, 16, 0));

        String[] features = {
            "welcome.feature.auto",
            "welcome.feature.dedupe",
            "welcome.feature.manage",
            "welcome.feature.stats"
        };

        for (String featureKey : features) {
            JPanel featurePanel = createFeaturePanel(featureKey);
            featurePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(featurePanel);
            contentPanel.add(Box.createVerticalStrut(12));
        }

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        
        JCheckBox dontShowAgain = new JCheckBox(i18n.getMessage("welcome.dontShow"));
        dontShowAgain.setSelected(false);
        
        JButton getStartedButton = new JButton(i18n.getMessage("welcome.start"));
        getStartedButton.setFont(getStartedButton.getFont().deriveFont(Font.BOLD));
        getStartedButton.setPreferredSize(new Dimension(140, 36));
        getStartedButton.addActionListener(e -> {
            if (dontShowAgain.isSelected()) {
                setFirstRunCompleted();
            }
            dispose();
        });

        buttonPanel.add(dontShowAgain);
        buttonPanel.add(Box.createHorizontalStrut(24));
        buttonPanel.add(getStartedButton);

        // Layout
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(getStartedButton);
    }

    private JPanel createFeaturePanel(String featureKey) {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);

        // Icon
        JLabel iconLabel = new JLabel(getFeatureIcon(featureKey));
        iconLabel.setFont(iconLabel.getFont().deriveFont(20f));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(40, 40));

        // Text
        JLabel textLabel = new JLabel("<html>" + i18n.getMessage(featureKey) + "</html>");
        textLabel.setFont(textLabel.getFont().deriveFont(13f));

        panel.add(iconLabel, BorderLayout.WEST);
        panel.add(textLabel, BorderLayout.CENTER);

        return panel;
    }

    private String getFeatureIcon(String featureKey) {
        return switch (featureKey) {
            case "welcome.feature.auto" -> "\uD83D\uDD0C";
            case "welcome.feature.dedupe" -> "♻️";
            case "welcome.feature.manage" -> "⚙️";
            case "welcome.feature.stats" -> "\uD83D\uDCCA";
            default -> "•";
        };
    }

    /**
     * Check if this is the first run (welcome dialog should be shown).
     */
    public static boolean isFirstRun() {
        return !java.util.prefs.Preferences.userNodeForPackage(WelcomeDialog.class)
                .getBoolean(FIRST_RUN_KEY, false);
    }

    /**
     * Mark first run as completed.
     */
    private static void setFirstRunCompleted() {
        java.util.prefs.Preferences.userNodeForPackage(WelcomeDialog.class)
                .putBoolean(FIRST_RUN_KEY, true);
    }

    /**
     * Show the welcome dialog if this is the first run.
     * @param parent Parent frame
     */
    public static void showIfFirstRun(Frame parent) {
        if (isFirstRun()) {
            SwingUtilities.invokeLater(() -> {
                WelcomeDialog dialog = new WelcomeDialog(parent);
                dialog.setVisible(true);
            });
        }
    }
}
