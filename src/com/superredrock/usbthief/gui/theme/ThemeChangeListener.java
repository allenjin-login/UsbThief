package com.superredrock.usbthief.gui.theme;

/**
 * Listener interface for theme change events.
 */
@FunctionalInterface
public interface ThemeChangeListener {
    /**
     * Called when the application theme changes.
     *
     * @param newTheme the new theme that was applied
     */
    void onThemeChanged(AppTheme newTheme);
}
