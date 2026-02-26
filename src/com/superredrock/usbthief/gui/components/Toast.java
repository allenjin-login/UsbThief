package com.superredrock.usbthief.gui.components;

import com.superredrock.usbthief.gui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;

/**
 * A toast notification component that displays non-intrusive messages.
 * Supports success, error, warning, and info types with auto-dismiss.
 */
public class Toast extends JPanel {

    private static final Logger logger = Logger.getLogger(Toast.class.getName());

    /**
     * Toast type enumeration with corresponding colors and icons.
     */
    public enum Type {
        SUCCESS(ThemeManager.TOAST_SUCCESS_BG, ThemeManager.TOAST_SUCCESS_BORDER, ThemeManager.ACCENT_SUCCESS, "✓"),
        ERROR(ThemeManager.TOAST_ERROR_BG, ThemeManager.TOAST_ERROR_BORDER, ThemeManager.ACCENT_ERROR, "✕"),
        WARNING(ThemeManager.TOAST_WARNING_BG, ThemeManager.TOAST_WARNING_BORDER, ThemeManager.ACCENT_WARNING, "!"),
        INFO(ThemeManager.TOAST_INFO_BG, ThemeManager.TOAST_INFO_BORDER, ThemeManager.ACCENT_INFO, "i");

        final Color backgroundColor;
        final Color borderColor;
        final Color iconColor;
        final String iconText;

        Type(Color bg, Color border, Color icon, String iconText) {
            this.backgroundColor = bg;
            this.borderColor = border;
            this.iconColor = icon;
            this.iconText = iconText;
        }
    }

    private static final int DEFAULT_DURATION = 3000;
    private static final int TOAST_WIDTH = 320;
    private static final int TOAST_HEIGHT = 56;
    private static final int ANIMATION_STEPS = 10;
    private static final int ANIMATION_DELAY = 15;

    private final Type type;
    private final String message;
    private final int duration;
    private final JWindow window;
    private final Window parentWindow;

    private Timer dismissTimer;
    private final float opacity = 0f;
    private int slideOffset;

    /**
     * Create a new toast notification.
     *
     * @param parentWindow the parent window (for positioning)
     * @param type         the toast type
     * @param message      the message to display
     * @param duration     display duration in milliseconds
     */
    public Toast(Window parentWindow, Type type, String message, int duration) {
        this.parentWindow = parentWindow;
        this.type = type;
        this.message = message;
        this.duration = duration;
        this.slideOffset = TOAST_HEIGHT + 20;

        // Create undecorated window
        window = new JWindow(parentWindow);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);

        // Setup panel
        setLayout(new BorderLayout(12, 0));
        setBackground(type.backgroundColor);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(type.borderColor, 1),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        setPreferredSize(new Dimension(TOAST_WIDTH, TOAST_HEIGHT));

        // Icon label
        JLabel iconLabel = new JLabel(type.iconText);
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        iconLabel.setForeground(type.iconColor);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(24, 24));

        // Message label
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        messageLabel.setForeground(ThemeManager.TEXT_PRIMARY);

        // Layout
        add(iconLabel, BorderLayout.WEST);
        add(messageLabel, BorderLayout.CENTER);

        window.setContentPane(this);
        window.pack();

        // Click to dismiss
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                dismiss();
            }
        });

        // Position window
        updatePosition();
    }

    /**
     * Update toast position relative to parent window.
     */
    private void updatePosition() {
        if (parentWindow != null) {
            int x = parentWindow.getX() + parentWindow.getWidth() - TOAST_WIDTH - 20;
            int y = parentWindow.getY() + parentWindow.getHeight() - slideOffset - 20;
            window.setLocation(x, y);
        }
    }

    /**
     * Show the toast with slide-in animation.
     */
    public void show() {
        window.setVisible(true);

        // Slide-in animation
        final int targetOffset = 0;
        final int startOffset = slideOffset;
        final float[] step = {0};

        Timer slideTimer = new Timer(ANIMATION_DELAY, null);
        slideTimer.addActionListener(e -> {
            step[0]++;
            float progress = step[0] / (float) ANIMATION_STEPS;
            // Ease-out cubic
            float eased = 1 - (1 - progress) * (1 - progress) * (1 - progress);
            slideOffset = (int) (startOffset * (1 - eased));
            updatePosition();

            if (step[0] >= ANIMATION_STEPS) {
                slideTimer.stop();
                startDismissTimer();
            }
        });
        slideTimer.start();
    }

    /**
     * Start the auto-dismiss timer.
     */
    private void startDismissTimer() {
        dismissTimer = new Timer(duration, e -> dismiss());
        dismissTimer.setRepeats(false);
        dismissTimer.start();
    }

    /**
     * Dismiss the toast with fade-out animation.
     */
    public void dismiss() {
        if (dismissTimer != null) {
            dismissTimer.stop();
        }

        // Fade-out animation
        final float[] currentOpacity = {1f};
        Timer fadeTimer = new Timer(ANIMATION_DELAY, null);
        fadeTimer.addActionListener(e -> {
            currentOpacity[0] -= 0.1f;
            if (currentOpacity[0] <= 0) {
                window.dispose();
                fadeTimer.stop();
            } else {
                window.setOpacity(currentOpacity[0]);
            }
        });
        fadeTimer.start();
    }

    /**
     * Get the window height for stacking calculations.
     *
     * @return the toast height
     */
    public int getToastHeight() {
        return TOAST_HEIGHT + 10; // Include margin
    }

    /**
     * Set the vertical offset for stacking multiple toasts.
     *
     * @param offset the offset from bottom of parent window
     */
    public void setVerticalOffset(int offset) {
        this.slideOffset = offset;
        updatePosition();
    }
}
