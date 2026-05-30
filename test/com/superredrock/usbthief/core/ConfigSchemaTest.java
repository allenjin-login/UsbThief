package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.config.ConfigType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSchemaTest {

    @Test
    void getAllEntriesIsNotEmpty() {
        Map<String, ConfigEntry<?>> entries = ConfigSchema.getAllEntries();
        assertFalse(entries.isEmpty());
        // We have 51 entries registered
        assertTrue(entries.size() >= 40);
    }

    @Test
    void getAllEntriesIsImmutable() {
        Map<String, ConfigEntry<?>> entries = ConfigSchema.getAllEntries();
        assertThrows(UnsupportedOperationException.class,
                () -> entries.put("newKey", ConfigEntry.intEntry("k", "d", 0, "c")));
    }

    @Test
    void getEntriesByCategoryHasCorrectCategories() {
        Map<String, List<ConfigEntry<?>>> byCategory = ConfigSchema.getEntriesByCategory();
        assertFalse(byCategory.isEmpty());

        assertTrue(byCategory.containsKey("Thread Pool"));
        assertTrue(byCategory.containsKey("File Copy"));
        assertTrue(byCategory.containsKey("Rate Limiting"));
        assertTrue(byCategory.containsKey("Storage Management"));
    }

    @Test
    void getEntriesByCategoryEachCategoryNonEmpty() {
        Map<String, List<ConfigEntry<?>>> byCategory = ConfigSchema.getEntriesByCategory();
        for (var entry : byCategory.entrySet()) {
            assertFalse(entry.getValue().isEmpty(),
                    "Category '" + entry.getKey() + "' should not be empty");
        }
    }

    @Test
    void getEntryKnownKey() {
        ConfigEntry<Integer> entry = ConfigSchema.getEntry("corePoolSize");
        assertNotNull(entry);
        assertEquals("corePoolSize", entry.key());
        assertEquals(2, entry.defaultValue());
        assertEquals(ConfigType.INT, entry.type());
    }

    @Test
    void getEntryUnknownKeyReturnsNull() {
        ConfigEntry<?> entry = ConfigSchema.getEntry("nonExistentKey123");
        assertNull(entry);
    }

    @Test
    void threadPoolEntries() {
        assertEquals(2, ConfigSchema.CORE_POOL_SIZE.defaultValue());
        assertEquals(60, ConfigSchema.KEEP_ALIVE_TIME_SECONDS.defaultValue());
        assertEquals(1024, ConfigSchema.TASK_QUEUE_CAPACITY.defaultValue());
        assertEquals(ConfigType.INT, ConfigSchema.CORE_POOL_SIZE.type());
    }

    @Test
    void indexManagementEntries() {
        assertEquals(2000, ConfigSchema.INDEX_CACHE_SIZE.defaultValue());
        assertEquals(ConfigType.INT, ConfigSchema.INDEX_CACHE_SIZE.type());
    }

    @Test
    void rateLimitingEntries() {
        assertEquals(0L, ConfigSchema.COPY_READ_RATE_LIMIT.defaultValue());
        assertEquals(0L, ConfigSchema.COPY_WRITE_RATE_LIMIT.defaultValue());
        assertEquals(16L * 1024 * 1024, ConfigSchema.COPY_RATE_BURST_SIZE.defaultValue());
        assertEquals(ConfigType.LONG, ConfigSchema.COPY_READ_RATE_LIMIT.type());
        assertEquals(ConfigType.LONG, ConfigSchema.COPY_WRITE_RATE_LIMIT.type());
    }

    @Test
    void booleanEntries() {
        assertEquals(false, ConfigSchema.WATCH_ENABLED.defaultValue());
        assertEquals(true, ConfigSchema.STORAGE_ENABLED.defaultValue());
        assertEquals(ConfigType.BOOLEAN, ConfigSchema.WATCH_ENABLED.type());
    }

    @Test
    void listEntries() {
        assertEquals(List.of(), ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL.defaultValue());
        assertEquals(ConfigType.STRING_LIST, ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL.type());
    }

    @Test
    void allEntriesHaveNonNullKeyAndDescription() {
        Map<String, ConfigEntry<?>> entries = ConfigSchema.getAllEntries();
        for (var e : entries.entrySet()) {
            assertNotNull(e.getKey(), "Key should not be null");
            assertNotNull(e.getValue().key(), "ConfigEntry key should not be null for " + e.getKey());
            assertNotNull(e.getValue().description(), "Description should not be null for " + e.getKey());
            assertNotNull(e.getValue().type(), "Type should not be null for " + e.getKey());
            assertNotNull(e.getValue().category(), "Category should not be null for " + e.getKey());
        }
    }
}
