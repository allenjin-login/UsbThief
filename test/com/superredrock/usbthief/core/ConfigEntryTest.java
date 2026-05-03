package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigEntryTest {

    @Test
    void intEntry() {
        ConfigEntry<Integer> entry = ConfigEntry.intEntry("test.int", "desc", 42, "cat");
        assertEquals("test.int", entry.key());
        assertEquals("desc", entry.description());
        assertEquals(42, entry.defaultValue());
        assertEquals(ConfigType.INT, entry.type());
        assertEquals("cat", entry.category());
    }

    @Test
    void longEntry() {
        ConfigEntry<Long> entry = ConfigEntry.longEntry("test.long", "desc", 100L, "cat");
        assertEquals(100L, entry.defaultValue());
        assertEquals(ConfigType.LONG, entry.type());
    }

    @Test
    void booleanEntry() {
        ConfigEntry<Boolean> entry = ConfigEntry.booleanEntry("test.bool", "desc", true, "cat");
        assertEquals(true, entry.defaultValue());
        assertEquals(ConfigType.BOOLEAN, entry.type());
    }

    @Test
    void stringEntry() {
        ConfigEntry<String> entry = ConfigEntry.stringEntry("test.str", "desc", "hello", "cat");
        assertEquals("hello", entry.defaultValue());
        assertEquals(ConfigType.STRING, entry.type());
    }

    @Test
    void listEntry() {
        ConfigEntry<List<String>> entry = ConfigEntry.listEntry("test.list", "desc", List.of("a", "b"), "cat");
        assertEquals(List.of("a", "b"), entry.defaultValue());
        assertEquals(ConfigType.STRING_LIST, entry.type());
    }

    @Test
    void listEntryEmptyDefault() {
        ConfigEntry<List<String>> entry = ConfigEntry.listEntry("test.list", "desc", List.of(), "cat");
        assertTrue(entry.defaultValue().isEmpty());
    }

    @Test
    void stringEntryNullDefault() {
        ConfigEntry<String> entry = ConfigEntry.stringEntry("test.null", "desc", null, "cat");
        assertNull(entry.defaultValue());
    }

    @Test
    void differentTypesAreDistinct() {
        ConfigEntry<Integer> intEntry = ConfigEntry.intEntry("k", "d", 1, "c");
        ConfigEntry<Long> longEntry = ConfigEntry.longEntry("k", "d", 1L, "c");
        ConfigEntry<Boolean> boolEntry = ConfigEntry.booleanEntry("k", "d", true, "c");
        ConfigEntry<String> strEntry = ConfigEntry.stringEntry("k", "d", "", "c");

        assertNotEquals(intEntry.type(), longEntry.type());
        assertNotEquals(intEntry.type(), boolEntry.type());
        assertNotEquals(intEntry.type(), strEntry.type());
    }
}
