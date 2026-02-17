package com.superredrock.usbthief.gui.theme;

/**
 * Enumeration of available application themes.
 */
public enum AppTheme {
    LIGHT("Light", "theme.light"),
    DARK("Dark", "theme.dark");

    private final String displayName;
    private final String i18nKey;

    AppTheme(String displayName, String i18nKey) {
        this.displayName = displayName;
        this.i18nKey = i18nKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getI18nKey() {
        return i18nKey;
    }
}
