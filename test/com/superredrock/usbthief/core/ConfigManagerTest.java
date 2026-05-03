package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    private ConfigManager manager;
    private Preferences testPrefs;

    @BeforeEach
    void setUp() throws Exception {
        testPrefs = Preferences.userNodeForPackage(ConfigManager.class).node("test_" + System.nanoTime());
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
    void getDefault() {
        assertEquals(2, manager.get(ConfigSchema.CORE_POOL_SIZE));
        assertEquals(60, manager.get(ConfigSchema.SAVE_DELAY_SECONDS));
        assertEquals("index.obj", manager.get(ConfigSchema.INDEX_PATH));
    }

    @Test
    void getAfterSet() {
        manager.set(ConfigSchema.CORE_POOL_SIZE, 8);
        assertEquals(8, manager.get(ConfigSchema.CORE_POOL_SIZE));
    }

    @Test
    void getLongType() {
        assertEquals(0L, manager.get(ConfigSchema.COPY_RATE_LIMIT));
        manager.set(ConfigSchema.COPY_RATE_LIMIT, 1024L);
        assertEquals(1024L, manager.get(ConfigSchema.COPY_RATE_LIMIT));
    }

    @Test
    void getBooleanType() {
        assertTrue(manager.get(ConfigSchema.WATCH_ENABLED));
        manager.set(ConfigSchema.WATCH_ENABLED, false);
        assertFalse(manager.get(ConfigSchema.WATCH_ENABLED));
    }

    @Test
    void getStringType() {
        assertEquals("index.obj", manager.get(ConfigSchema.INDEX_PATH));
        manager.set(ConfigSchema.INDEX_PATH, "/custom/path");
        assertEquals("/custom/path", manager.get(ConfigSchema.INDEX_PATH));
    }

    @Test
    void setThenClear() {
        manager.set(ConfigSchema.CORE_POOL_SIZE, 16);
        assertEquals(16, manager.get(ConfigSchema.CORE_POOL_SIZE));
        manager.clear(ConfigSchema.CORE_POOL_SIZE);
        assertEquals(2, manager.get(ConfigSchema.CORE_POOL_SIZE)); // back to default
    }

    @Test
    void resetToDefaults() {
        manager.set(ConfigSchema.CORE_POOL_SIZE, 99);
        manager.set(ConfigSchema.WATCH_ENABLED, false);
        manager.resetToDefaults();
        assertEquals(2, manager.get(ConfigSchema.CORE_POOL_SIZE));
        assertTrue(manager.get(ConfigSchema.WATCH_ENABLED));
    }

    @Test
    void isDeviceBlacklistedBySerialFalse() {
        assertFalse(manager.isDeviceBlacklistedBySerial("abc123"));
    }

    @Test
    void isDeviceBlacklistedBySerialTrue() {
        manager.addToDeviceBlacklistBySerial("abc123");
        assertTrue(manager.isDeviceBlacklistedBySerial("abc123"));
    }

    @Test
    void isDeviceBlacklistedBySerialNull() {
        assertFalse(manager.isDeviceBlacklistedBySerial(null));
    }

    @Test
    void isDeviceBlacklistedBySerialEmpty() {
        assertFalse(manager.isDeviceBlacklistedBySerial(""));
    }

    @Test
    void addToDeviceBlacklistBySerialDuplicate() {
        manager.addToDeviceBlacklistBySerial("abc");
        manager.addToDeviceBlacklistBySerial("abc");
        List<String> list = manager.get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL);
        assertEquals(1, list.size());
    }

    @Test
    void addToDeviceBlacklistBySerialNull() {
        manager.addToDeviceBlacklistBySerial(null);
        List<String> list = manager.get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL);
        assertTrue(list.isEmpty());
    }

    @Test
    void setDeviceBlacklistBySerial() {
        manager.setDeviceBlacklistBySerial(List.of("a", "b"));
        assertTrue(manager.isDeviceBlacklistedBySerial("a"));
        assertTrue(manager.isDeviceBlacklistedBySerial("b"));
    }

    @Test
    void setDeviceBlacklistBySerialNull() {
        manager.setDeviceBlacklistBySerial(null);
        List<String> list = manager.get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL);
        assertTrue(list.isEmpty());
    }

    @Test
    void removeFromDeviceBlacklistBySerial() {
        manager.addToDeviceBlacklistBySerial("abc");
        manager.removeFromDeviceBlacklistBySerial("abc");
        assertFalse(manager.isDeviceBlacklistedBySerial("abc"));
    }

    @Test
    void removeFromDeviceBlacklistBySerialNotFound() {
        assertDoesNotThrow(() -> manager.removeFromDeviceBlacklistBySerial("notfound"));
    }

    @Test
    void exportToXmlAndImportFromXml() throws IOException {
        manager.set(ConfigSchema.CORE_POOL_SIZE, 16);
        Path xmlFile = Files.createTempFile("config-test", ".xml");
        try {
            manager.exportToXml(xmlFile);
            assertTrue(Files.size(xmlFile) > 0);

            manager.set(ConfigSchema.CORE_POOL_SIZE, 4);
            assertEquals(4, manager.get(ConfigSchema.CORE_POOL_SIZE));

            manager.importFromXml(xmlFile);
            assertEquals(16, manager.get(ConfigSchema.CORE_POOL_SIZE));
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }

    @Test
    void exportToXmlInvalidPathThrows() {
        assertThrows(IOException.class,
                () -> manager.exportToXml(Path.of("Z:\\nonexistent\\dir\\config.xml")));
    }

    @Test
    void importFromXmlInvalidFormatThrows() throws IOException {
        Path badFile = Files.createTempFile("bad-config", ".xml");
        Files.writeString(badFile, "this is not xml");
        try {
            assertThrows(IOException.class, () -> manager.importFromXml(badFile));
        } finally {
            Files.deleteIfExists(badFile);
        }
    }
}
