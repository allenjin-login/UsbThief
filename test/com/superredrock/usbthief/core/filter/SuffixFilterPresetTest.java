package com.superredrock.usbthief.core.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.FileFilterConfig;
import com.superredrock.usbthief.core.config.configs.SuffixFilterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end behaviour of the extension filter once a preset has been written into
 * the whitelist: extension matching is case-insensitive, {@code ALL} really disables
 * filtering, and a hand-edited whitelist is never overridden by the stored preset
 * name (presets are shortcuts, not lock modes).
 */
class SuffixFilterPresetTest {

    private ConfigManager manager;
    private Preferences testPrefs;
    private SuffixFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        testPrefs = Preferences.userRoot().node("/usbthief-suffixfilter-test-" + System.nanoTime());
        testPrefs.clear();
        var ctor = ConfigManager.class.getDeclaredConstructor(Preferences.class);
        ctor.setAccessible(true);
        manager = ctor.newInstance(testPrefs);
        filter = new SuffixFilter(manager);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testPrefs.removeNode();
    }

    @Test
    void documentsPresetWrittenToWhitelistFiltersExtensions() {
        applyPreset(FilterPreset.DOCUMENTS);
        manager.set(FileFilterConfig.FILE_FILTER_ALLOW_NO_EXT, false);

        assertTrue(accepts("report.pdf"));
        assertTrue(accepts("sheet.CSV"));
        assertFalse(accepts("photo.jpeg"), "images must not pass a document whitelist");
        assertFalse(accepts("clip.mp4"));
        assertFalse(accepts("noextension"));
    }

    @Test
    void whitelistMatchingIsCaseInsensitive() {
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_MODE, SuffixFilter.MODE_WHITELIST);
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, List.of("PDF", "DocX"));

        assertTrue(accepts("REPORT.PDF"));
        assertTrue(accepts("letter.docX"));
        assertFalse(accepts("report.png"));
    }

    @Test
    void allPresetDisablesFilteringEvenThoughTheListIsEmpty() {
        applyPreset(FilterPreset.ALL);

        assertTrue(manager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST).isEmpty());
        assertTrue(accepts("anything.png"));
        assertTrue(accepts("README"));
    }

    @Test
    void storedPresetDoesNotOverrideAHandEditedWhitelist() {
        // A preset name is still stored, but the user narrowed the list afterwards.
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_MODE, SuffixFilter.MODE_WHITELIST);
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, List.of("png"));
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_PRESET, FilterPreset.DOCUMENTS.name());

        assertTrue(accepts("screenshot.PNG"));
        assertFalse(accepts("report.pdf"), "the preset must not re-expand the edited whitelist");
    }

    @Test
    void emptyWhitelistModeStillBlocksEveryFile() {
        // Documented whitelist edge case, kept intact for users who deliberately clear
        // the list; only the ALL preset maps to NONE instead.
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_MODE, SuffixFilter.MODE_WHITELIST);
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, List.of());
        manager.set(FileFilterConfig.FILE_FILTER_ALLOW_NO_EXT, false);

        assertFalse(accepts("report.pdf"));
    }

    private void applyPreset(FilterPreset preset) {
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_MODE, preset.suffixMode());
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST,
                new ArrayList<>(preset.getExtensions()));
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_BLACKLIST, List.of());
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_PRESET, preset.name());
    }

    private boolean accepts(String fileName) {
        return filter.test(Path.of(fileName), null);
    }
}
