package com.superredrock.usbthief.gui;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a language with its locale and display information.
 */
public record LanguageInfo(
        Locale locale,
        String displayName,
        String nativeName,
        int priority
) {
    public LanguageInfo {
        Objects.requireNonNull(locale, "Locale cannot be null");
        if (displayName == null || displayName.isBlank()) {
            displayName = locale.getDisplayName(Locale.ENGLISH);
        }
        if (nativeName == null || nativeName.isBlank()) {
            nativeName = locale.getDisplayName(locale);
        }
    }

    /**
     * Create a LanguageInfo with default priority (0).
     */
    public LanguageInfo(Locale locale, String displayName, String nativeName) {
        this(locale, displayName, nativeName, 0);
    }

    /**
     * Create a LanguageInfo with locale only.
     */
    public LanguageInfo(Locale locale) {
        this(locale, null, null, 0);
    }

    /**
     * Create a LanguageInfo from locale string (e.g., "zh_CN", "en").
     */
    public static LanguageInfo fromString(String localeString) {
        String[] parts = localeString.split("_");
        Locale locale = switch (parts.length) {
            case 1 -> new Locale(parts[0]);
            case 2 -> new Locale(parts[0], parts[1]);
            case 3 -> new Locale(parts[0], parts[1], parts[2]);
            default -> throw new IllegalArgumentException("Invalid locale string: " + localeString);
        };
        return new LanguageInfo(locale);
    }

    /**
     * Get locale string for this language.
     */
    public String localeString() {
        return locale.toString();
    }

    /**
     * Check if this is the default (English) language.
     */
    public boolean isDefault() {
        return locale.getLanguage().equals(Locale.ENGLISH.getLanguage()) &&
               locale.getCountry().isEmpty();
    }
}
