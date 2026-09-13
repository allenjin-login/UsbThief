package com.superredrock.usbthief.gui.dailog.config;

import com.superredrock.usbthief.core.config.configs.CategoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The category-folder settings must be translated in every shipped bundle; a missing
 * key silently degrades to the raw key or the English description in the dialog, which
 * is exactly the kind of regression the entry/label/hint convention hides.
 */
class CategoryConfigI18nTest {

    private static final String BUNDLE = "com.superredrock.usbthief.gui.messages";

    @Test
    void categoryModeEntryIsTranslatedInEveryBundle() {
        List<Locale> locales = List.of(
                Locale.ROOT, Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.JAPANESE, Locale.GERMAN);

        for (Locale locale : locales) {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale);
            assertTranslated(bundle, locale, "config.category.categoryFolders");
            assertTranslated(bundle, locale, "config.entry.categoryMode.label");
            assertTranslated(bundle, locale, "config.entry.categoryMode.hint");
            for (String option : CategoryConfig.CATEGORY_MODE.options()) {
                assertTranslated(bundle, locale, "config.option.categoryMode." + option);
            }
        }
    }

    private static void assertTranslated(ResourceBundle bundle, Locale locale, String key) {
        assertTrue(bundle.containsKey(key), "missing key " + key + " in " + locale);
        String value = bundle.getString(key);
        assertFalse(value.isBlank(), "blank value for " + key + " in " + locale);
        assertFalse(value.equals(key), "untranslated key " + key + " in " + locale);
    }
}
