package com.superredrock.usbthief.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.superredrock.usbthief.core.Device;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Centralized theme management for UsbThief.
 * Provides consistent color palette and theme switching support.
 */
public class ThemeManager {

    private static final Logger logger = Logger.getLogger(ThemeManager.class.getName());
    private static volatile ThemeManager INSTANCE;

    // Color palette - Modern UI colors
    public static final Color ACCENT_PRIMARY = new Color(0x6366F1);    // Indigo
    public static final Color ACCENT_SUCCESS = new Color(0x10B981);    // Green (Emerald)
    public static final Color ACCENT_WARNING = new Color(0xF59E0B);    // Amber
    public static final Color ACCENT_ERROR = new Color(0xEF4444);      // Red
    public static final Color ACCENT_INFO = new Color(0x3B82F6);       // Blue

    // Device state colors
    public static final Color DEVICE_ONLINE = new Color(0x22C55E);     // Bright green
    public static final Color DEVICE_OFFLINE = new Color(0x94A3B8);    // Slate gray
    public static final Color DEVICE_SCANNING = new Color(0x3B82F6);   // Blue
    public static final Color DEVICE_DISABLED = new Color(0x6B7280);   // Gray
    public static final Color DEVICE_UNAVAILABLE = new Color(0xF97316); // Orange

    // UI colors - Light theme defaults
    public static final Color CARD_BACKGROUND = new Color(0xFFFFFF);
    public static final Color CARD_BACKGROUND_ALT = new Color(0xF8FAFC);
    public static final Color BORDER_COLOR = new Color(0xE2E8F0);
    public static final Color TEXT_PRIMARY = new Color(0x1E293B);
    public static final Color TEXT_SECONDARY = new Color(0x64748B);
    public static final Color TEXT_MUTED = new Color(0x94A3B8);
    public static final Color BACKGROUND_PRIMARY = new Color(0xF1F5F9);
    public static final Color BACKGROUND_SECONDARY = new Color(0xFFFFFF);

    // Shadows and effects
    public static final Color SHADOW_COLOR = new Color(0x00000016, true);
    public static final Color HOVER_COLOR = new Color(0x00000008, true);

    // Toast colors
    public static final Color TOAST_SUCCESS_BG = new Color(0xECFDF5);
    public static final Color TOAST_SUCCESS_BORDER = new Color(0x10B981);
    public static final Color TOAST_ERROR_BG = new Color(0xFEF2F2);
    public static final Color TOAST_ERROR_BORDER = new Color(0xEF4444);
    public static final Color TOAST_WARNING_BG = new Color(0xFFFBEB);
    public static final Color TOAST_WARNING_BORDER = new Color(0xF59E0B);
    public static final Color TOAST_INFO_BG = new Color(0xEEF2FF);
    public static final Color TOAST_INFO_BORDER = new Color(0x3B82F6);

    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    private AppTheme currentTheme;

    private ThemeManager() {
        loadSavedTheme();
    }

    public static synchronized ThemeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ThemeManager();
        }
        return INSTANCE;
    }

    /**
     * Load the saved theme preference from storage.
     */
    public void loadSavedTheme() {
        String themeName = prefs.get("theme", AppTheme.LIGHT.name());
        try {
            currentTheme = AppTheme.valueOf(themeName);
        } catch (IllegalArgumentException e) {
            currentTheme = AppTheme.LIGHT;
        }
        applyTheme(currentTheme);
    }

    /**
     * Apply the specified theme to the application.
     *
     * @param theme the theme to apply
     */
    public void applyTheme(AppTheme theme) {
        this.currentTheme = theme;

        try {
            switch (theme) {
                case DARK -> {
                    FlatDarkLaf.setup();
                    updateDarkThemeColors();
                }
                case LIGHT -> {
                    FlatLightLaf.setup();
                    updateLightThemeColors();
                }
            }

            // Save preference
            prefs.put("theme", theme.name());

            // Notify listeners
            notifyThemeChanged(theme);

            logger.info("Applied theme: " + theme.getDisplayName());
        } catch (Exception e) {
            logger.severe("Failed to apply theme: " + e.getMessage());
        }
    }

    private void updateLightThemeColors() {
        // Light theme uses default colors defined above
    }

    private void updateDarkThemeColors() {
        // Override colors for dark theme
        // Note: In a full implementation, we would use mutable colors or
        // a different approach to handle dark theme colors
    }

    /**
     * Toggle between light and dark themes.
     */
    public void toggleTheme() {
        AppTheme newTheme = (currentTheme == AppTheme.LIGHT) ? AppTheme.DARK : AppTheme.LIGHT;
        applyTheme(newTheme);

        // Update all windows
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }

    /**
     * Get the current theme.
     *
     * @return the current theme
     */
    public AppTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Check if dark theme is active.
     *
     * @return true if dark theme is active
     */
    public boolean isDarkTheme() {
        return currentTheme == AppTheme.DARK;
    }

    /**
     * Get the appropriate color for a device state.
     *
     * @param state the device state
     * @return the corresponding color
     */
    public static Color getStateColor(Device.DeviceState state) {
        return switch (state) {
            case IDLE -> DEVICE_ONLINE;
            case SCANNING -> DEVICE_SCANNING;
            case OFFLINE -> DEVICE_OFFLINE;
            case UNAVAILABLE -> DEVICE_UNAVAILABLE;
            case PAUSED, DISABLED -> DEVICE_DISABLED;
        };
    }

    /**
     * Add a theme change listener.
     *
     * @param listener the listener to add
     */
    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a theme change listener.
     *
     * @param listener the listener to remove
     */
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyThemeChanged(AppTheme newTheme) {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(newTheme);
            } catch (Exception e) {
                logger.warning("Error notifying theme change listener: " + e.getMessage());
            }
        }
    }

    /**
     * Get a slightly darker version of a color for hover effects.
     *
     * @param color the base color
     * @return a darker version of the color
     */
    public static Color darker(Color color) {
        return darker(color, 0.9f);
    }

    /**
     * Get a darker version of a color by a specified factor.
     *
     * @param color  the base color
     * @param factor the factor (0.0-1.0)
     * @return a darker version of the color
     */
    public static Color darker(Color color, float factor) {
        return new Color(
            Math.max((int) (color.getRed() * factor), 0),
            Math.max((int) (color.getGreen() * factor), 0),
            Math.max((int) (color.getBlue() * factor), 0),
            color.getAlpha()
        );
    }

    /**
     * Get a lighter version of a color.
     *
     * @param color  the base color
     * @param factor the factor (1.0+)
     * @return a lighter version of the color
     */
    public static Color lighter(Color color, float factor) {
        int r = (int) Math.min(255, color.getRed() * factor);
        int g = (int) Math.min(255, color.getGreen() * factor);
        int b = (int) Math.min(255, color.getBlue() * factor);
        return new Color(r, g, b, color.getAlpha());
    }

    /**
     * Create a rounded border with the specified radius and color.
     *
     * @param radius the corner radius
     * @param color  the border color
     * @return a rounded border
     */
    public static javax.swing.border.Border createRoundedBorder(int radius, Color color) {
        return new RoundedBorder(radius, color);
    }

    /**
     * Simple rounded border implementation.
     */
    public static class RoundedBorder implements javax.swing.border.Border {
        private final int radius;
        private final Color color;

        public RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
