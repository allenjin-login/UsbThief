package com.superredrock.usbthief.core.config;

import com.superredrock.usbthief.core.config.ConfigSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for storage management configuration entries.
 */
class StorageConfigTest {

    @Test
    void testStorageReservedBytes() {
        assertEquals("storage.reservedBytes", ConfigSchema.STORAGE_RESERVED_BYTES.key());
        assertEquals(10L * 1024 * 1024 * 1024, ConfigSchema.STORAGE_RESERVED_BYTES.defaultValue());
        assertEquals("Storage Management", ConfigSchema.STORAGE_RESERVED_BYTES.category());
    }

    @Test
    void testStorageMaxBytes() {
        assertEquals("storage.maxBytes", ConfigSchema.STORAGE_MAX_BYTES.key());
        assertEquals(100L * 1024 * 1024 * 1024, ConfigSchema.STORAGE_MAX_BYTES.defaultValue());
        assertEquals("Storage Management", ConfigSchema.STORAGE_MAX_BYTES.category());
    }

    @Test
    void testSnifferWaitNormalMinutes() {
        assertEquals("sniffer.waitNormalMinutes", ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES.key());
        assertEquals(30, ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES.defaultValue());
        assertEquals("Storage Management", ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES.category());
    }

    @Test
    void testSnifferWaitErrorMinutes() {
        assertEquals("sniffer.waitErrorMinutes", ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES.key());
        assertEquals(5, ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES.defaultValue());
        assertEquals("Storage Management", ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES.category());
    }

    @Test
    void testRecyclerStrategy() {
        assertEquals("recycler.strategy", ConfigSchema.RECYCLER_STRATEGY.key());
        assertEquals("AUTO", ConfigSchema.RECYCLER_STRATEGY.defaultValue());
        assertEquals("Storage Management", ConfigSchema.RECYCLER_STRATEGY.category());
    }

    @Test
    void testRecyclerProtectedAgeHours() {
        assertEquals("recycler.protectedAgeHours", ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS.key());
        assertEquals(1, ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS.defaultValue());
        assertEquals("Storage Management", ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS.category());
    }

    @Test
    void testStorageWarningEnabled() {
        assertEquals("storage.warningEnabled", ConfigSchema.STORAGE_WARNING_ENABLED.key());
        assertEquals(true, ConfigSchema.STORAGE_WARNING_ENABLED.defaultValue());
        assertEquals("Storage Management", ConfigSchema.STORAGE_WARNING_ENABLED.category());
    }

    @Test
    void testAllStorageEntriesRegistered() {
        assertNotNull(ConfigSchema.getEntry("storage.reservedBytes"));
        assertNotNull(ConfigSchema.getEntry("storage.maxBytes"));
        assertNotNull(ConfigSchema.getEntry("sniffer.waitNormalMinutes"));
        assertNotNull(ConfigSchema.getEntry("sniffer.waitErrorMinutes"));
        assertNotNull(ConfigSchema.getEntry("recycler.strategy"));
        assertNotNull(ConfigSchema.getEntry("recycler.protectedAgeHours"));
        assertNotNull(ConfigSchema.getEntry("storage.warningEnabled"));
    }
}
