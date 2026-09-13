package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.BlacklistConfig;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import com.superredrock.usbthief.core.config.configs.IndexConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

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
        // Isolate every test in its own root-level node so nothing is written into the real
        // application preferences tree (and no sibling test can observe the values).
        testPrefs = Preferences.userRoot().node("/usbthief-config-test-" + System.nanoTime());
        testPrefs.clear();
        var ctor = ConfigManager.class.getDeclaredConstructor(Preferences.class);
        ctor.setAccessible(true);
        manager = ctor.newInstance(testPrefs);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        // clear() alone leaves the node behind forever; removeNode() actually reclaims it.
        testPrefs.removeNode();
    }

    @Test
    void getDefault() {
        assertEquals(2, manager.get(ThreadPoolConfig.CORE_POOL_SIZE));
        assertEquals(2000, manager.get(IndexConfig.INDEX_CACHE_SIZE));
    }

    @Test
    void getAfterSet() {
        manager.set(ThreadPoolConfig.CORE_POOL_SIZE, 8);
        assertEquals(8, manager.get(ThreadPoolConfig.CORE_POOL_SIZE));
    }

    @Test
    void getLongType() {
        assertEquals(0L, manager.get(RateLimitConfig.COPY_READ_RATE_LIMIT));
        manager.set(RateLimitConfig.COPY_READ_RATE_LIMIT, 1024L);
        assertEquals(1024L, manager.get(RateLimitConfig.COPY_READ_RATE_LIMIT));
    }

    @Test
    void getBooleanType() {
        assertFalse(manager.get(FileWatchConfig.WATCH_ENABLED));
        manager.set(FileWatchConfig.WATCH_ENABLED, true);
        assertTrue(manager.get(FileWatchConfig.WATCH_ENABLED));
    }

    @Test
    void getStringType() {
        assertEquals("SHA-256", manager.get(FileCopyConfig.HASH_ALGORITHM));
        manager.set(FileCopyConfig.HASH_ALGORITHM, "MD5");
        assertEquals("MD5", manager.get(FileCopyConfig.HASH_ALGORITHM));
    }

    @Test
    void setThenClear() {
        manager.set(ThreadPoolConfig.CORE_POOL_SIZE, 16);
        assertEquals(16, manager.get(ThreadPoolConfig.CORE_POOL_SIZE));
        manager.clear(ThreadPoolConfig.CORE_POOL_SIZE);
        assertEquals(2, manager.get(ThreadPoolConfig.CORE_POOL_SIZE)); // back to default
    }

    @Test
    void resetToDefaults() {
        manager.set(ThreadPoolConfig.CORE_POOL_SIZE, 99);
        manager.set(FileWatchConfig.WATCH_ENABLED, true);
        manager.resetToDefaults();
        assertEquals(2, manager.get(ThreadPoolConfig.CORE_POOL_SIZE));
        assertFalse(manager.get(FileWatchConfig.WATCH_ENABLED));
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
        List<String> list = manager.get(BlacklistConfig.DEVICE_BLACKLIST_BY_SERIAL);
        assertEquals(1, list.size());
    }

    @Test
    void addToDeviceBlacklistBySerialNull() {
        manager.addToDeviceBlacklistBySerial(null);
        List<String> list = manager.get(BlacklistConfig.DEVICE_BLACKLIST_BY_SERIAL);
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
        List<String> list = manager.get(BlacklistConfig.DEVICE_BLACKLIST_BY_SERIAL);
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
        manager.set(ThreadPoolConfig.CORE_POOL_SIZE, 16);
        Path xmlFile = Files.createTempFile("config-test", ".xml");
        try {
            manager.exportToXml(xmlFile);
            assertTrue(Files.size(xmlFile) > 0);

            manager.set(ThreadPoolConfig.CORE_POOL_SIZE, 4);
            assertEquals(4, manager.get(ThreadPoolConfig.CORE_POOL_SIZE));

            manager.importFromXml(xmlFile);
            assertEquals(16, manager.get(ThreadPoolConfig.CORE_POOL_SIZE));
        } finally {
            Files.deleteIfExists(xmlFile);
        }
    }

    @Test
    void exportToXmlInvalidPathThrows(@TempDir Path tempDir) throws IOException {
        // A platform-independent "genuinely unwritable" path: a regular file can never act as a
        // directory, so resolving a child below it makes the export fail on every OS. (The old
        // "Z:\nonexistent\dir\config.xml" literal is a *valid* file name on Linux, where the
        // export silently succeeded and created junk in the working directory.)
        Path regularFile = Files.createFile(tempDir.resolve("not-a-directory"));
        Path invalidPath = regularFile.resolve("child").resolve("config.xml");

        assertThrows(IOException.class, () -> manager.exportToXml(invalidPath));
        assertFalse(Files.exists(invalidPath), "no file may be created under a regular file");
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
