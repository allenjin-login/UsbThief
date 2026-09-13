package com.superredrock.usbthief.gui.dailog.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.SuffixFilterConfig;
import com.superredrock.usbthief.core.filter.FilterPreset;
import com.superredrock.usbthief.core.filter.SuffixFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preset combo is a one-click shortcut: it must write the category extensions
 * into the ordinary whitelist, and any later manual edit must detach the preset so
 * that the edited list is what gets saved.
 */
class SuffixFilterPanelTest {

    private ConfigManager manager;
    private Preferences testPrefs;

    @BeforeEach
    void setUp() throws Exception {
        testPrefs = Preferences.userRoot().node("/usbthief-suffixpanel-test-" + System.nanoTime());
        testPrefs.clear();
        var ctor = ConfigManager.class.getDeclaredConstructor(Preferences.class);
        ctor.setAccessible(true);
        manager = ctor.newInstance(testPrefs);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testPrefs.removeNode();
    }

    @Test
    void selectingDocumentsPresetWritesTheWhitelistInOneStep() {
        SuffixFilterPanel panel = new SuffixFilterPanel();

        panel.selectPreset(FilterPreset.DOCUMENTS);
        panel.save(manager);

        assertEquals(SuffixFilter.MODE_WHITELIST, manager.get(SuffixFilterConfig.SUFFIX_FILTER_MODE));
        assertEquals(FilterPreset.DOCUMENTS.getExtensions(),
                manager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST));
        assertEquals(FilterPreset.DOCUMENTS.name(),
                manager.get(SuffixFilterConfig.SUFFIX_FILTER_PRESET));
    }

    @Test
    void eachCategoryPresetWritesItsOwnWhitelist() {
        for (FilterPreset preset : List.of(FilterPreset.IMAGES, FilterPreset.VIDEO)) {
            SuffixFilterPanel panel = new SuffixFilterPanel();
            panel.selectPreset(preset);
            panel.save(manager);

            assertEquals(SuffixFilter.MODE_WHITELIST, manager.get(SuffixFilterConfig.SUFFIX_FILTER_MODE));
            assertEquals(new ArrayList<>(preset.getExtensions()),
                    new ArrayList<>(manager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST)));
        }
    }

    @Test
    void allPresetClearsTheWhitelistAndTurnsFilteringOff() {
        SuffixFilterPanel panel = new SuffixFilterPanel();
        panel.selectPreset(FilterPreset.DOCUMENTS);
        panel.save(manager);

        panel.selectPreset(FilterPreset.ALL);
        panel.save(manager);

        assertEquals(SuffixFilter.MODE_NONE, manager.get(SuffixFilterConfig.SUFFIX_FILTER_MODE));
        assertTrue(manager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST).isEmpty());
        assertTrue(manager.get(SuffixFilterConfig.SUFFIX_FILTER_BLACKLIST).isEmpty());
        assertEquals(FilterPreset.ALL.name(), manager.get(SuffixFilterConfig.SUFFIX_FILTER_PRESET));
    }

    @Test
    void manualEditAfterAPresetIsKeptAndDropsThePresetLabel() {
        SuffixFilterPanel panel = new SuffixFilterPanel();
        panel.selectPreset(FilterPreset.DOCUMENTS);

        assertTrue(panel.addExtensionEntry(".XYZ"));
        panel.save(manager);

        List<String> saved = manager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST);
        assertTrue(saved.contains("xyz"), "the manual extension must survive");
        assertTrue(saved.containsAll(FilterPreset.DOCUMENTS.getExtensions()));
        assertEquals("", manager.get(SuffixFilterConfig.SUFFIX_FILTER_PRESET),
                "an edited list is no longer the untouched preset");
    }

    @Test
    void reloadingAHandEditedWhitelistKeepsIt() {
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_MODE, SuffixFilter.MODE_WHITELIST);
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST, List.of("pdf", "txt"));
        manager.set(SuffixFilterConfig.SUFFIX_FILTER_PRESET, "");

        SuffixFilterPanel panel = new SuffixFilterPanel();
        panel.load(manager);
        panel.save(manager);

        assertEquals(List.of("pdf", "txt"),
                manager.get(SuffixFilterConfig.SUFFIX_FILTER_WHITELIST));
    }
}
