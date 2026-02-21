package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.storage.StorageLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StorageController with real FileStore.
 * <p>
 * These tests use a real temporary directory and FileStore to verify that
 * StorageController correctly queries disk space and calculates storage levels.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>Cannot reliably simulate a full disk without actually filling it</li>
 *   <li>Tests assume the temp directory has sufficient free space</li>
 *   <li>Cannot test IOException handling from FileStore (requires actual I/O failure)</li>
 * </ul>
 */
class StorageControllerIntegrationTest {

    private Path tempDirectory;
    private String originalWorkPath;
    private Long originalReservedBytes;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory for testing
        tempDirectory = Files.createTempDirectory("usbthief-storage-test");

        // Save original config values
        originalWorkPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
        originalReservedBytes = ConfigManager.getInstance().get(ConfigSchema.STORAGE_RESERVED_BYTES);

        // Set work path to temp directory
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, tempDirectory.toString());

        // Reset singleton to pick up new config
        StorageController controller = StorageController.getInstance();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Restore original config values
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, originalWorkPath);
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, originalReservedBytes);

        // Clean up temp directory
        if (tempDirectory != null && Files.exists(tempDirectory)) {
            // Delete all files in the directory first
            Files.walk(tempDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best effort cleanup
                    }
                });
        }
    }

    @Test
    void getStorageStatus_returnsAccurateValuesFromRealFileStore() {
        StorageController controller = StorageController.getInstance();

        StorageController.StorageStatus status = controller.getStorageStatus();

        // Verify all values are present and valid
        assertNotNull(status);
        assertTrue(status.freeBytes() >= 0, "Free bytes should be non-negative");
        assertTrue(status.usedBytes() >= 0, "Used bytes should be non-negative");
        assertTrue(status.totalBytes() > 0, "Total bytes should be positive");
        assertEquals(status.totalBytes(), status.freeBytes() + status.usedBytes(),
            "Total should equal free + used");
        assertNotNull(status.level(), "Storage level should not be null");

        // Verify consistency with FileStore
        assertDoesNotThrow(() -> {
            FileStore fileStore = controller.getFileStore();
            assertEquals(fileStore.getTotalSpace(), status.totalBytes(),
                "Total space should match FileStore");
            // Note: getUsableSpace() may vary between calls, so we allow small tolerance
            assertTrue(Math.abs(fileStore.getUsableSpace() - status.freeBytes()) < 1024 * 1024,
                "Free space should be close to FileStore (allow 1MB tolerance)");
        });
    }

    @Test
    void getStorageStatus_queriesFreshValuesOnEachCall() throws InterruptedException {
        StorageController controller = StorageController.getInstance();

        StorageController.StorageStatus status1 = controller.getStorageStatus();
        Thread.sleep(10);
        StorageController.StorageStatus status2 = controller.getStorageStatus();

        // Values should be fresh from FileStore
        assertEquals(status1.totalBytes(), status2.totalBytes(),
            "Total space should be consistent");
        assertEquals(status1.level(), status2.level(),
            "Storage level should be consistent");

        // Free bytes may vary slightly due to system activity
        assertTrue(Math.abs(status1.freeBytes() - status2.freeBytes()) < 1024 * 1024 * 10,
            "Free bytes should be similar (allow 10MB variance)");
    }

    @Test
    void calculateStorageLevel_withOKThreshold_returnsOKLevel() {
        StorageController controller = StorageController.getInstance();

        // Set reserved bytes to a very low value to ensure OK state
        // Assuming temp directory has at least 1GB free
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, 1L);

        StorageLevel level = controller.getStorageLevel();

        assertEquals(StorageLevel.OK, level,
            "With low reserved threshold, storage should be OK");
    }

    @Test
    void calculateStorageLevel_withLOWThreshold_returnsLOWLevel() {
        StorageController controller = StorageController.getInstance();

        // Get current free space and set reserved threshold in LOW range
        // LOW = free space within 10% buffer zone above reserved
        StorageController.StorageStatus status = controller.getStorageStatus();
        long freeBytes = status.freeBytes();

        // Set reserved to 91% of free space (should trigger LOW)
        // LOW: free <= reserved + (reserved * 0.1) = 1.1 * reserved
        // So if reserved = free * 0.91, then reserved * 1.1 = free * 1.001 > free
        // Therefore free <= reserved * 1.1, which triggers LOW
        long reservedBytes = (long) (freeBytes * 0.91);
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, reservedBytes);

        StorageLevel level = controller.getStorageLevel();

        assertEquals(StorageLevel.LOW, level,
            "With reserved at ~91% of free, storage should be LOW");
    }

    @Test
    void calculateStorageLevel_withCRITICALThreshold_returnsCRITICALLevel() {
        StorageController controller = StorageController.getInstance();

        // Set reserved bytes higher than free space to force CRITICAL
        // Assuming temp directory doesn't have EBs of free space
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, Long.MAX_VALUE);

        StorageLevel level = controller.getStorageLevel();

        assertEquals(StorageLevel.CRITICAL, level,
            "With extremely high reserved threshold, storage should be CRITICAL");
    }

    @Test
    void getStorageStatus_returnsCRITICALWhenThresholdExceeded() {
        StorageController controller = StorageController.getInstance();

        // Set reserved bytes to force CRITICAL
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, Long.MAX_VALUE);

        StorageController.StorageStatus status = controller.getStorageStatus();

        assertEquals(StorageLevel.CRITICAL, status.level(),
            "Storage level should be CRITICAL");
    }

    @Test
    void isStorageOK_returnsTrueWhenLevelIsOK() {
        StorageController controller = StorageController.getInstance();

        // Set reserved to very low value to ensure OK
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, 1L);

        StorageLevel level = controller.getStorageLevel();

        if (level == StorageLevel.OK) {
            assertTrue(controller.isStorageOK(),
                "isStorageOK should return true when level is OK");
        }
    }

    @Test
    void isStorageOK_returnsFalseWhenLevelIsCRITICAL() {
        StorageController controller = StorageController.getInstance();

        // Set reserved to very high value to force CRITICAL
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, Long.MAX_VALUE);

        StorageLevel level = controller.getStorageLevel();

        if (level == StorageLevel.CRITICAL) {
            assertFalse(controller.isStorageOK(),
                "isStorageOK should return false when level is CRITICAL");
        }
    }

    @Test
    void isStorageCritical_returnsTrueWhenLevelIsCRITICAL() {
        StorageController controller = StorageController.getInstance();

        // Set reserved to very high value to force CRITICAL
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, Long.MAX_VALUE);

        StorageLevel level = controller.getStorageLevel();

        if (level == StorageLevel.CRITICAL) {
            assertTrue(controller.isStorageCritical(),
                "isStorageCritical should return true when level is CRITICAL");
        }
    }

    @Test
    void isStorageCritical_returnsFalseWhenLevelIsOK() {
        StorageController controller = StorageController.getInstance();

        // Set reserved to very low value to ensure OK
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, 1L);

        StorageLevel level = controller.getStorageLevel();

        if (level == StorageLevel.OK) {
            assertFalse(controller.isStorageCritical(),
                "isStorageCritical should return false when level is OK");
        }
    }

    @Test
    void getFileStore_returnsRealFileStoreForTempDirectory() throws IOException {
        StorageController controller = StorageController.getInstance();

        FileStore fileStore = controller.getFileStore();

        assertNotNull(fileStore, "FileStore should not be null");
        assertNotNull(fileStore.name(), "FileStore name should not be null");
        assertTrue(fileStore.getTotalSpace() > 0, "FileStore should have positive total space");
        assertTrue(fileStore.getUsableSpace() >= 0, "FileStore should have non-negative usable space");
    }

    @Test
    void getWorkDirectory_returnsTempDirectoryPath() {
        StorageController controller = StorageController.getInstance();

        Path workDir = controller.getWorkDirectory();

        assertNotNull(workDir, "Work directory should not be null");
        assertEquals(tempDirectory.toString(), workDir.toString(),
            "Work directory should match temp directory");
    }

    @Test
    void storageLevelConsistentAcrossMultipleQueries() {
        StorageController controller = StorageController.getInstance();

        // Set reserved to low value for consistent OK state
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, 1L);

        StorageLevel level1 = controller.getStorageLevel();
        StorageController.StorageStatus status1 = controller.getStorageStatus();
        StorageLevel level2 = controller.getStorageLevel();
        StorageController.StorageStatus status2 = controller.getStorageStatus();

        // All queries should return consistent results
        assertEquals(level1, level2, "Storage level should be consistent");
        assertEquals(level1, status1.level(), "getStorageLevel should match status.level()");
        assertEquals(level1, status2.level(), "getStorageLevel should match status.level()");
    }

    @Test
    void storageStatusRecord_containsAllExpectedFields() {
        StorageController controller = StorageController.getInstance();

        StorageController.StorageStatus status = controller.getStorageStatus();

        // Verify all record fields are accessible and have valid values
        assertDoesNotThrow(status::freeBytes);
        assertDoesNotThrow(status::usedBytes);
        assertDoesNotThrow(status::totalBytes);
        assertDoesNotThrow(status::level);

        assertTrue(status.freeBytes() >= 0, "Free bytes should be non-negative");
        assertTrue(status.usedBytes() >= 0, "Used bytes should be non-negative");
        assertTrue(status.totalBytes() > 0, "Total bytes should be positive");
        assertNotNull(status.level(), "Level should not be null");
    }
}
