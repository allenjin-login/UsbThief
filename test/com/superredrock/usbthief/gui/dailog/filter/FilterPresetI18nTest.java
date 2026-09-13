package com.superredrock.usbthief.gui.dailog.filter;

import com.superredrock.usbthief.core.filter.FilterPreset;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preset dropdown must be translated in every shipped bundle.
 *
 * <p>Checked against the five property files directly rather than through
 * {@code ResourceBundle}: the bundle lookup falls back to the base (English) file,
 * so a missing translation would silently look present.</p>
 */
class FilterPresetI18nTest {

    private static final String BUNDLE_DIR = "src/com/superredrock/usbthief/gui/";

    private static final List<String> BUNDLES = List.of(
            "messages.properties",
            "messages_en.properties",
            "messages_zh.properties",
            "messages_ja.properties",
            "messages_de.properties");

    @Test
    void everyPresetLabelExistsInEveryBundle() throws IOException {
        for (String bundle : BUNDLES) {
            Properties props = load(bundle);
            assertTranslated(props, bundle, "filter.suffix.preset");
            assertTranslated(props, bundle, "filter.suffix.preset.custom");
            for (FilterPreset preset : FilterPreset.values()) {
                assertTranslated(props, bundle, preset.getDisplayNameKey());
            }
        }
    }

    private static Properties load(String bundle) throws IOException {
        String raw = Files.readString(Path.of(BUNDLE_DIR + bundle), StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }
        Properties props = new Properties();
        props.load(new StringReader(raw));
        return props;
    }

    private static void assertTranslated(Properties props, String bundle, String key) {
        String value = props.getProperty(key);
        assertTrue(value != null, "missing key " + key + " in " + bundle);
        assertFalse(value.isBlank(), "blank value for " + key + " in " + bundle);
        assertFalse(value.equals(key), "untranslated key " + key + " in " + bundle);
    }
}
