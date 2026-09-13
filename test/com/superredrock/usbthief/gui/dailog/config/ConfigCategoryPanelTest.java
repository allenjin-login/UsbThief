package com.superredrock.usbthief.gui.dailog.config;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@code ConfigDialog} class split (architecture-audit [19]): every
 * category page must render every one of its entries and must write back exactly
 * the values it was shown, so that the dialog stays behaviour-neutral. The
 * configuration store is an isolated Preferences node, as in
 * {@code ConfigManagerTest}.
 */
class ConfigCategoryPanelTest {

    private ConfigManager manager;
    private Preferences testPrefs;

    @BeforeEach
    void setUp() throws Exception {
        testPrefs = Preferences.userNodeForPackage(ConfigManager.class).node("panel_test_" + System.nanoTime());
        testPrefs.clear();
        var ctor = ConfigManager.class.getDeclaredConstructor(Preferences.class);
        ctor.setAccessible(true);
        manager = ctor.newInstance(testPrefs);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testPrefs.clear();
        testPrefs.sync();
    }

    @Test
    void everyCategoryPageRendersAndRoundTripsEveryEntry() {
        Map<String, Object> before = snapshotAllEntries();

        for (String categoryKey : ConfigCategories.categoryKeys()) {
            ConfigCategoryPanel page = new ConfigCategoryPanel(categoryKey, manager);
            assertNotNull(page.getComponent(), "no component for " + categoryKey);
            page.commitEdits();
            page.saveTo(manager);
        }

        assertEquals(before, snapshotAllEntries(),
                "saving an untouched page must not change any configured value");
    }

    @Test
    void everySchemaCategoryIsReachableFromTheTree() {
        // A category missing from the tree would silently disappear from the dialog
        for (String categoryName : ConfigSchema.getEntriesByCategory().keySet()) {
            boolean reachable = false;
            for (String categoryKey : ConfigCategories.categoryKeys()) {
                if (categoryName.equals(ConfigCategories.resolveName(categoryKey))) {
                    reachable = true;
                    break;
                }
            }
            assertTrue(reachable, "category has no tree entry: " + categoryName);
        }
    }

    @Test
    void numericEntryIsReadableForCrossFieldValidation() {
        ConfigCategoryPanel page = new ConfigCategoryPanel("config.category.threadPool", manager);
        assertEquals(manager.get(ThreadPoolConfig.CORE_POOL_SIZE),
                page.readNumber(ThreadPoolConfig.CORE_POOL_SIZE.key()));
        assertNull(page.readNumber("not.an.entry"));
    }

    @Test
    void unknownCategoryKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigCategoryPanel("config.category.doesNotExist", manager));
    }

    private Map<String, Object> snapshotAllEntries() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.List<ConfigEntry<?>>> category
                : ConfigSchema.getEntriesByCategory().entrySet()) {
            for (ConfigEntry<?> entry : category.getValue()) {
                values.put(category.getKey() + "/" + entry.key(), manager.get(entry));
            }
        }
        return values;
    }
}
