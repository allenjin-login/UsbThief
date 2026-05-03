package com.superredrock.usbthief.gui;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a language with its locale and display information.
 */
public final class LanguageInfo {
    private final Locale locale;
    private final String displayName;
    private final String nativeName;
    private final int priority;

    public LanguageInfo(Locale locale, String displayName, String nativeName, int priority) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        this.locale = locale;
        this.displayName = displayName != null && !displayName.isBlank()
            ? displayName
            : locale.getDisplayName(Locale.ENGLISH);
        this.nativeName = nativeName != null && !nativeName.isBlank()
            ? nativeName
            : locale.getDisplayName(locale);
        this.priority = priority;
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

    public Locale locale() { return locale; }
    public String displayName() { return displayName; }
    public String nativeName() { return nativeName; }
    public int priority() { return priority; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LanguageInfo)) return false;
        LanguageInfo that = (LanguageInfo) o;
        return priority == that.priority &&
               Objects.equals(locale, that.locale) &&
               Objects.equals(displayName, that.displayName) &&
               Objects.equals(nativeName, that.nativeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locale, displayName, nativeName, priority);
    }

    @Override
    public String toString() {
        return "LanguageInfo[locale=" + locale + ", displayName=" + displayName +
               ", nativeName=" + nativeName + ", priority=" + priority + "]";
    }
}
