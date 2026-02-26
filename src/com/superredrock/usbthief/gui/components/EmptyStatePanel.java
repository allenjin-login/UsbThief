package com.superredrock.usbthief.gui.components;

import com.superredrock.usbthief.gui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A panel displayed when a list or container has no content.
 * Provides visual guidance to the user with an icon, title, description,
 * and optional action button.
 */
public class EmptyStatePanel extends JPanel {

    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel descriptionLabel;
    private final JButton actionButton;

    /**
     * Create an empty state panel with icon, title, and description.
     *
     * @param icon        the icon (emoji or Unicode character)
     * @param title       the title text
     * @param description the description text
     */
    public EmptyStatePanel(String icon, String title, String description) {
        this(icon, title, description, null, null);
    }

    /**
     * Create an empty state panel with icon, title, description, and action button.
     *
     * @param icon        the icon (emoji or Unicode character)
     * @param title       the title text
     * @param description the description text
     * @param actionText  the action button text (null for no button)
     * @param action      the action to perform when button is clicked (null for no button)
     */
    public EmptyStatePanel(String icon, String title, String description, 
                           String actionText, Runnable action) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(60, 40, 60, 40));
        setOpaque(false);

        // Icon
        iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title
        titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Description
        descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        descriptionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Action button (optional)
        if (actionText != null && action != null) {
            actionButton = createActionButton(actionText, action);
        } else {
            actionButton = null;
        }

        // Layout components
        add(Box.createVerticalGlue());
        add(iconLabel);
        add(Box.createVerticalStrut(16));
        add(titleLabel);
        add(Box.createVerticalStrut(8));
        add(descriptionLabel);
        if (actionButton != null) {
            add(Box.createVerticalStrut(20));
            add(actionButton);
        }
        add(Box.createVerticalGlue());

        // Center alignment
        setAlignmentX(Component.CENTER_ALIGNMENT);
        setAlignmentY(Component.CENTER_ALIGNMENT);
    }

    /**
     * Create the action button with modern styling.
     */
    private JButton createActionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(ThemeManager.ACCENT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBorder(new EmptyBorder(10, 24, 10, 24));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(ThemeManager.darker(ThemeManager.ACCENT_PRIMARY, 0.9f));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(ThemeManager.ACCENT_PRIMARY);
            }
        });

        button.addActionListener(e -> action.run());

        return button;
    }

    /**
     * Update the icon.
     *
     * @param icon the new icon
     */
    public void setIcon(String icon) {
        iconLabel.setText(icon);
    }

    /**
     * Update the title.
     *
     * @param title the new title
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Update the description.
     *
     * @param description the new description
     */
    public void setDescription(String description) {
        descriptionLabel.setText(description);
    }

    /**
     * Create a builder for EmptyStatePanel.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for EmptyStatePanel.
     */
    public static class Builder {
        private String icon;
        private String title;
        private String description;
        private String actionText;
        private Runnable action;

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder action(String actionText, Runnable action) {
            this.actionText = actionText;
            this.action = action;
            return this;
        }

        public EmptyStatePanel build() {
            return new EmptyStatePanel(icon, title, description, actionText, action);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Optional: Draw subtle background
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}
