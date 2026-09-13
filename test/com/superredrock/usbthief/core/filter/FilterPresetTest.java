package com.superredrock.usbthief.core.filter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preset - to - whitelist mapping is the contract behind the one-click filter
 * buttons: every category must carry a useful 8-12 extension set, and {@code ALL}
 * must stay an empty set that means "no filtering" rather than "whitelist nothing".
 */
class FilterPresetTest {

    @Test
    void everyFilteringPresetHasEightToTwelveExtensions() {
        for (FilterPreset preset : FilterPreset.values()) {
            if (preset == FilterPreset.ALL) {
                continue;
            }
            int count = preset.getExtensions().size();
            assertTrue(count >= 8 && count <= 12,
                    preset + " should list 8-12 extensions but lists " + count);
        }
    }

    @Test
    void allPresetIsEmptyAndMeansNoFiltering() {
        assertTrue(FilterPreset.ALL.getExtensions().isEmpty(),
                "ALL must be an empty set so it clears the whitelist");
        assertFalse(FilterPreset.ALL.filtersExtensions());
        assertEquals(SuffixFilter.MODE_NONE, FilterPreset.ALL.suffixMode(),
                "ALL must switch filtering off, not on with an empty whitelist");
    }

    @Test
    void everyFilteringPresetMapsToWhitelistMode() {
        for (FilterPreset preset : FilterPreset.values()) {
            if (preset == FilterPreset.ALL) {
                continue;
            }
            assertTrue(preset.filtersExtensions(), preset + " must have extensions");
            assertEquals(SuffixFilter.MODE_WHITELIST, preset.suffixMode(), preset.name());
        }
    }

    @Test
    void extensionsAreLowercaseAndDotless() {
        for (FilterPreset preset : FilterPreset.values()) {
            for (String ext : preset.getExtensions()) {
                assertEquals(ext.toLowerCase(Locale.ROOT), ext,
                        preset + " extension '" + ext + "' must be lowercase");
                assertFalse(ext.startsWith("."),
                        preset + " extension '" + ext + "' must not start with a dot");
                assertFalse(ext.isBlank(), preset + " contains a blank extension");
            }
        }
    }

    @Test
    void documentsPresetCoversTheRepresentativeExtensions() {
        assertContainsAll(FilterPreset.DOCUMENTS,
                "doc", "docx", "pdf", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "md", "odt");
    }

    @Test
    void imagesPresetCoversTheRepresentativeExtensions() {
        assertContainsAll(FilterPreset.IMAGES,
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tiff", "raw");
    }

    @Test
    void videoPresetCoversTheRepresentativeExtensions() {
        assertContainsAll(FilterPreset.VIDEO,
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v");
    }

    @Test
    void displayNameKeysAreNonBlankAndUnique() {
        Set<String> keys = new HashSet<>();
        for (FilterPreset preset : FilterPreset.values()) {
            assertFalse(preset.getDisplayNameKey().isBlank(), preset + " has no i18n key");
            assertTrue(keys.add(preset.getDisplayNameKey()),
                    "duplicate i18n key " + preset.getDisplayNameKey());
        }
    }

    @Test
    void safeValueOfIsCaseInsensitiveAndFallsBackToAll() {
        assertEquals(FilterPreset.DOCUMENTS, FilterPreset.safeValueOf("Documents"));
        assertEquals(FilterPreset.DOCUMENTS, FilterPreset.safeValueOf("DOCUMENTS"));
        assertEquals(FilterPreset.VIDEO, FilterPreset.safeValueOf(" video "));
        assertEquals(FilterPreset.ALL, FilterPreset.safeValueOf("does-not-exist"));
        assertEquals(FilterPreset.ALL, FilterPreset.safeValueOf(null));
    }

    @Test
    void matchesIgnoresCaseAndOrder() {
        List<String> shuffled = new ArrayList<>(FilterPreset.DOCUMENTS.getExtensions());
        java.util.Collections.reverse(shuffled);
        List<String> uppercase = shuffled.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();

        assertTrue(FilterPreset.DOCUMENTS.matches(uppercase));
        assertFalse(FilterPreset.DOCUMENTS.matches(List.of("pdf")));
        assertTrue(FilterPreset.ALL.matches(List.of()));
        assertTrue(FilterPreset.ALL.matches(null));
        assertFalse(FilterPreset.ALL.matches(List.of("pdf")));
    }

    private static void assertContainsAll(FilterPreset preset, String... required) {
        Set<String> actual = new HashSet<>(preset.getExtensions());
        List<String> missing = Arrays.stream(required).filter(ext -> !actual.contains(ext)).toList();
        assertTrue(missing.isEmpty(), preset + " is missing " + missing);
    }
}
