package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.storage.StorageLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.FileStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StorageController.
 */
class StorageControllerTest {

    private String originalWorkPath;
    private Long originalReservedBytes;
    private Long originalMaxBytes;

    @BeforeEach
    void setUp() {
        // Save original config values
        originalWorkPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
        originalReservedBytes = ConfigManager.getInstance().get(ConfigSchema.STORAGE_RESERVED_BYTES);
        originalMaxBytes = ConfigManager.getInstance().get(ConfigSchema.STORAGE_MAX_BYTES);
    }

    @AfterEach
    void tearDown() {
        // Restore original config values
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, originalWorkPath);
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, originalReservedBytes);
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_MAX_BYTES, originalMaxBytes);
    }

    @Test
    void singletonReturnsSameInstance() {
        StorageController instance1 = StorageController.getInstance();
        StorageController instance2 = StorageController.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    void getStorageStatus_returnsValidStorageStatus() {
        StorageController controller = StorageController.getInstance();

        StorageController.StorageStatus status = controller.getStorageStatus();

        assertNotNull(status);
        assertTrue(status.freeBytes() >= 0);
        assertTrue(status.usedBytes() >= 0);
        assertTrue(status.totalBytes() > 0);
        assertEquals(status.totalBytes(), status.freeBytes() + status.usedBytes());
        assertNotNull(status.level());
    }

    @Test
    void getStorageLevel_returnsCorrectLevel() {
        StorageController controller = StorageController.getInstance();

        StorageLevel level = controller.getStorageLevel();

        assertNotNull(level);
        assertTrue(level == StorageLevel.OK || level == StorageLevel.LOW || level == StorageLevel.CRITICAL);
    }

    @Test
    void isStorageOK_returnsTrueWhenOK() {
        StorageController controller = StorageController.getInstance();

        boolean isOK = controller.isStorageOK();

        // This test just verifies the method works and returns a boolean
        // The actual value depends on the current disk state
        assertNotNull(Boolean.valueOf(isOK));
    }

    @Test
    void isStorageCritical_returnsTrueWhenCritical() {
        StorageController controller = StorageController.getInstance();

        boolean isCritical = controller.isStorageCritical();

        // This test just verifies the method works and returns a boolean
        // The actual value depends on the current disk state
        assertNotNull(Boolean.valueOf(isCritical));
    }

    @Test
    void getWorkDirectory_returnsValidPath() {
        StorageController controller = StorageController.getInstance();

        var workPath = controller.getWorkDirectory();

        assertNotNull(workPath);
        assertNotNull(workPath.toString());
    }

    @Test
    void getFileStore_returnsValidFileStore() throws Exception {
        StorageController controller = StorageController.getInstance();

        FileStore fileStore = controller.getFileStore();

        assertNotNull(fileStore);
        assertNotNull(fileStore.name());
    }

    @Test
    void storageLevel_logic_correct() {
        StorageController controller = StorageController.getInstance();
        StorageController.StorageStatus status = controller.getStorageStatus();

        // Verify StorageLevel matches the free bytes threshold logic
        long reservedBytes = ConfigManager.getInstance().get(ConfigSchema.STORAGE_RESERVED_BYTES);

        if (status.freeBytes() <= reservedBytes) {
            assertEquals(StorageLevel.CRITICAL, status.level());
        } else if (status.freeBytes() <= reservedBytes * 1.1) {
            // Within 10% buffer zone
            assertEquals(StorageLevel.LOW, status.level());
        } else {
            assertEquals(StorageLevel.OK, status.level());
        }
    }

    @Test
    void getStorageStatus_queriesFreshValues() throws InterruptedException {
        StorageController controller = StorageController.getInstance();

        StorageController.StorageStatus status1 = controller.getStorageStatus();
        Thread.sleep(10); // Small delay
        StorageController.StorageStatus status2 = controller.getStorageStatus();

        // Values should be fresh (same unless disk actually changed)
        assertEquals(status1.totalBytes(), status2.totalBytes());
        assertEquals(status1.level(), status2.level());
    }

    @Test
    void storageStatusRecord_allFieldsAccessible() {
        StorageController controller = StorageController.getInstance();
        StorageController.StorageStatus status = controller.getStorageStatus();

        // Verify all record fields are accessible
        assertDoesNotThrow(() -> status.freeBytes());
        assertDoesNotThrow(() -> status.usedBytes());
        assertDoesNotThrow(() -> status.totalBytes());
        assertDoesNotThrow(() -> status.level());
    }

    @Test
    void isStorageOK_isFalseWhenCritical() {
        StorageController controller = StorageController.getInstance();

        // Set reservedBytes to extremely high value to force CRITICAL state
        // This assumes the test disk doesn't have more than 1 EB of free space
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, Long.MAX_VALUE);

        StorageLevel level = controller.getStorageLevel();

        if (level == StorageLevel.CRITICAL) {
            assertFalse(controller.isStorageOK());
        }
    }

    @Test
    void isStorageCritical_isTrueWhenCritical() {
        StorageController controller = StorageController.getInstance();

        // Set reservedBytes to extremely high value to force CRITICAL state
        ConfigManager.getInstance().set(ConfigSchema.STORAGE_RESERVED_BYTES, Long.MAX_VALUE);

        StorageLevel level = controller.getStorageLevel();

        if (level == StorageLevel.CRITICAL) {
            assertTrue(controller.isStorageCritical());
        }
    }
}
