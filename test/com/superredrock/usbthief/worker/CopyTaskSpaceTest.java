package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.EventListener;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.event.storage.StorageLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CopyTask space checking functionality.
 * <p>
 * Tests verify that CopyTask properly checks available storage space before
 * copying files and returns SKIPPED result when storage is insufficient.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CopyTaskSpaceTest {

    @TempDir
    Path tempDir;

    private Path testWorkPath;
    private Path testSourcePath;
    private Path testFile;

    private String originalWorkPath;

    @BeforeEach
    void setUp() throws Exception {
        // Save original config values
        originalWorkPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);

        // Create test directories
        testWorkPath = tempDir.resolve("work");
        testSourcePath = tempDir.resolve("source");
        Files.createDirectories(testWorkPath);
        Files.createDirectories(testSourcePath);

        // Set work path for testing
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, testWorkPath.toString());

        // Create a test file
        testFile = testSourcePath.resolve("test.txt");
        Files.writeString(testFile, "test content for space checking");
    }

    @AfterEach
    void tearDown() {
        // Restore original config values
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, originalWorkPath);
    }

    /**
     * Test: Copy skipped when storage is CRITICAL.
     */
    @Test
    void copySkipped_whenStorageCritical() throws Exception {
        // Use AtomicReference to capture the dispatched event
        AtomicReference<CopyCompletedEvent> capturedEvent = new AtomicReference<>();

        // Register event listener to capture CopyCompletedEvent
        EventBus.getInstance().register(CopyCompletedEvent.class,
                new EventListener<CopyCompletedEvent>() {
                    @Override
                    public void onEvent(CopyCompletedEvent event) {
                        capturedEvent.set(event);
                    }
                });

        try (MockedStatic<StorageController> mockedStorage = mockStatic(StorageController.class);
             MockedStatic<QueueManager> mockedQueue = mockStatic(QueueManager.class)) {

            // Arrange: Setup StorageController to return CRITICAL status
            StorageController mockController = mock(StorageController.class);
            mockedStorage.when(StorageController::getInstance).thenReturn(mockController);
            when(mockController.isStorageCritical()).thenReturn(true);

            // Mock Index
            com.superredrock.usbthief.index.Index mockIndex = mock(com.superredrock.usbthief.index.Index.class);
            when(mockIndex.checkDuplicate(any(), any())).thenReturn(false);
            mockedQueue.when(QueueManager::getIndex).thenReturn(mockIndex);

            // Mock DeviceManager
            Device mockDevice = mock(Device.class);
            when(mockDevice.getSerialNumber()).thenReturn("test-device");
            DeviceManager mockDeviceManager = mock(DeviceManager.class);
            when(mockDeviceManager.getDevice(eq(testFile))).thenReturn(mockDevice);
            mockedQueue.when(QueueManager::getDeviceManager).thenReturn(mockDeviceManager);

            // Act: Create and run CopyTask
            CopyTask task = new CopyTask(testFile, "test-device");
            CopyResult result = task.call();

            // Assert: Verify copy was skipped
            assertEquals(CopyResult.SKIPPED, result,
                "Copy should be SKIPPED when storage is CRITICAL");

            // Verify storage critical check was called
            verify(mockController).isStorageCritical();

            // Verify isStorageCritical() prevented further checks
            verify(mockController, never()).getStorageStatus();

            // Verify event was dispatched
            assertNotNull(capturedEvent.get(), "CopyCompletedEvent should be dispatched");
            assertEquals(CopyResult.SKIPPED, capturedEvent.get().result(),
                    "Event result should be SKIPPED");
            assertEquals(testFile, capturedEvent.get().sourcePath(),
                    "Event should contain source path");
            assertEquals(0L, capturedEvent.get().bytesCopied(),
                    "Bytes copied should be 0 for skipped copy");
        }
    }

    /**
     * Test: Copy skipped when file size exceeds available space with buffer.
     */
    @Test
    void copySkipped_whenFileTooLargeForAvailableSpace() throws Exception {
        // Use AtomicReference to capture the dispatched event
        AtomicReference<CopyCompletedEvent> capturedEvent = new AtomicReference<>();

        // Register event listener to capture CopyCompletedEvent
        EventBus.getInstance().register(CopyCompletedEvent.class,
                new EventListener<CopyCompletedEvent>() {
                    @Override
                    public void onEvent(CopyCompletedEvent event) {
                        capturedEvent.set(event);
                    }
                });

        try (MockedStatic<StorageController> mockedStorage = mockStatic(StorageController.class);
             MockedStatic<QueueManager> mockedQueue = mockStatic(QueueManager.class)) {

            // Arrange: Setup StorageController to return OK status but limited space
            StorageController mockController = mock(StorageController.class);
            mockedStorage.when(StorageController::getInstance).thenReturn(mockController);
            when(mockController.isStorageCritical()).thenReturn(false);

            // Get actual file size
            long fileSize = Files.size(testFile);

            // Set available space to less than file size + buffer
            long freeBytes = fileSize;  // Available space = file size (without buffer)
            StorageController.StorageStatus mockStatus = mock(StorageController.StorageStatus.class);
            when(mockStatus.freeBytes()).thenReturn(freeBytes);
            when(mockStatus.level()).thenReturn(StorageLevel.OK);
            when(mockController.getStorageStatus()).thenReturn(mockStatus);

            // Mock Index
            com.superredrock.usbthief.index.Index mockIndex = mock(com.superredrock.usbthief.index.Index.class);
            when(mockIndex.checkDuplicate(any(), any())).thenReturn(false);
            mockedQueue.when(QueueManager::getIndex).thenReturn(mockIndex);

            // Mock DeviceManager
            Device mockDevice = mock(Device.class);
            when(mockDevice.getSerialNumber()).thenReturn("test-device");
            DeviceManager mockDeviceManager = mock(DeviceManager.class);
            when(mockDeviceManager.getDevice(eq(testFile))).thenReturn(mockDevice);
            mockedQueue.when(QueueManager::getDeviceManager).thenReturn(mockDeviceManager);

            // Act: Create and run CopyTask
            CopyTask task = new CopyTask(testFile, "test-device");
            CopyResult result = task.call();

            // Assert: Verify copy was skipped
            assertEquals(CopyResult.SKIPPED, result,
                "Copy should be SKIPPED when file size > available space with 10% buffer");

            // Verify both storage checks were called
            verify(mockController).isStorageCritical();
            verify(mockController).getStorageStatus();

            // Verify event was dispatched
            assertNotNull(capturedEvent.get(), "CopyCompletedEvent should be dispatched");
            assertEquals(CopyResult.SKIPPED, capturedEvent.get().result(),
                    "Event result should be SKIPPED");
            assertEquals(testFile, capturedEvent.get().sourcePath(),
                    "Event should contain source path");
            assertEquals(0L, capturedEvent.get().bytesCopied(),
                    "Bytes copied should be 0 for skipped copy");
        }
    }

    /**
     * Test: Copy proceeds when space is sufficient.
     */
    @Test
    void copyProceeds_whenSpaceSufficient() throws Exception {
        // Use AtomicReference to capture the dispatched event
        AtomicReference<CopyCompletedEvent> capturedEvent = new AtomicReference<>();

        // Register event listener to capture CopyCompletedEvent
        EventBus.getInstance().register(CopyCompletedEvent.class,
                new EventListener<CopyCompletedEvent>() {
                    @Override
                    public void onEvent(CopyCompletedEvent event) {
                        capturedEvent.set(event);
                    }
                });

        try (MockedStatic<StorageController> mockedStorage = mockStatic(StorageController.class);
             MockedStatic<QueueManager> mockedQueue = mockStatic(QueueManager.class)) {

            // Arrange: Setup StorageController to return OK status with sufficient space
            StorageController mockController = mock(StorageController.class);
            mockedStorage.when(StorageController::getInstance).thenReturn(mockController);
            when(mockController.isStorageCritical()).thenReturn(false);

            // Get actual file size
            long fileSize = Files.size(testFile);

            // Set available space to more than file size + buffer
            long freeBytes = fileSize * 10;  // Plenty of space
            StorageController.StorageStatus mockStatus = mock(StorageController.StorageStatus.class);
            when(mockStatus.freeBytes()).thenReturn(freeBytes);
            when(mockStatus.level()).thenReturn(StorageLevel.OK);
            when(mockController.getStorageStatus()).thenReturn(mockStatus);

            // Mock Index
            com.superredrock.usbthief.index.Index mockIndex = mock(com.superredrock.usbthief.index.Index.class);
            when(mockIndex.checkDuplicate(any(), any())).thenReturn(false);
            mockedQueue.when(QueueManager::getIndex).thenReturn(mockIndex);

            // Mock DeviceManager
            Device mockDevice = mock(Device.class);
            when(mockDevice.getSerialNumber()).thenReturn("test-device");
            DeviceManager mockDeviceManager = mock(DeviceManager.class);
            when(mockDeviceManager.getDevice(eq(testFile))).thenReturn(mockDevice);
            mockedQueue.when(QueueManager::getDeviceManager).thenReturn(mockDeviceManager);

            // Act: Create and run CopyTask
            CopyTask task = new CopyTask(testFile, "test-device");
            CopyResult result = task.call();

            // Assert: Verify copy succeeded
            assertEquals(CopyResult.SUCCESS, result,
                "Copy should succeed when sufficient space is available");

            // Verify both storage checks were called
            verify(mockController).isStorageCritical();
            verify(mockController).getStorageStatus();

            // Verify file was actually copied to work directory
            boolean fileExistsInWorkDir = Files.walk(testWorkPath)
                .anyMatch(path -> path.getFileName().toString().equals(testFile.getFileName().toString()));
            assertTrue(fileExistsInWorkDir, "File should be copied to work directory");

            // Verify event was dispatched
            assertNotNull(capturedEvent.get(), "CopyCompletedEvent should be dispatched");
            assertEquals(CopyResult.SUCCESS, capturedEvent.get().result(),
                    "Event result should be SUCCESS");
            assertEquals(testFile, capturedEvent.get().sourcePath(),
                    "Event should contain source path");
            assertEquals(fileSize, capturedEvent.get().bytesCopied(),
                    "Bytes copied should match file size");
        }
    }

    /**
     * Test: Copy skipped when file size exactly equals available space with buffer.
     * This tests the boundary condition where file size > freeBytes * 0.9.
     */
    @Test
    void copySkipped_whenFileSizeExceedsAvailableSpaceWithBuffer() throws Exception {
        // Use AtomicReference to capture the dispatched event
        AtomicReference<CopyCompletedEvent> capturedEvent = new AtomicReference<>();

        // Register event listener to capture CopyCompletedEvent
        EventBus.getInstance().register(CopyCompletedEvent.class,
                new EventListener<CopyCompletedEvent>() {
                    @Override
                    public void onEvent(CopyCompletedEvent event) {
                        capturedEvent.set(event);
                    }
                });

        try (MockedStatic<StorageController> mockedStorage = mockStatic(StorageController.class);
             MockedStatic<QueueManager> mockedQueue = mockStatic(QueueManager.class)) {

            // Arrange: Setup StorageController to return OK status
            StorageController mockController = mock(StorageController.class);
            mockedStorage.when(StorageController::getInstance).thenReturn(mockController);
            when(mockController.isStorageCritical()).thenReturn(false);

            // Get actual file size
            long fileSize = Files.size(testFile);

            // Set available space such that file size > freeBytes * 0.9
            // fileSize = 31, need: 31 > freeBytes * 0.9
            // So: freeBytes * 0.9 < 31
            // freeBytes < 31 / 0.9 = 34.44
            long freeBytes = 34;  // This gives availableWithBuffer = 30.6 which is < fileSize (31)
            StorageController.StorageStatus mockStatus = mock(StorageController.StorageStatus.class);
            when(mockStatus.freeBytes()).thenReturn(freeBytes);
            when(mockStatus.level()).thenReturn(StorageLevel.OK);
            when(mockController.getStorageStatus()).thenReturn(mockStatus);

            // Mock Index
            com.superredrock.usbthief.index.Index mockIndex = mock(com.superredrock.usbthief.index.Index.class);
            when(mockIndex.checkDuplicate(any(), any())).thenReturn(false);
            mockedQueue.when(QueueManager::getIndex).thenReturn(mockIndex);

            // Mock DeviceManager
            Device mockDevice = mock(Device.class);
            when(mockDevice.getSerialNumber()).thenReturn("test-device");
            DeviceManager mockDeviceManager = mock(DeviceManager.class);
            when(mockDeviceManager.getDevice(eq(testFile))).thenReturn(mockDevice);
            mockedQueue.when(QueueManager::getDeviceManager).thenReturn(mockDeviceManager);

            // Act: Create and run CopyTask
            CopyTask task = new CopyTask(testFile, "test-device");
            CopyResult result = task.call();

            // Assert: Verify copy was skipped
            assertEquals(CopyResult.SKIPPED, result,
                "Copy should be SKIPPED when file size > available space with buffer");

            // Verify event was dispatched
            assertNotNull(capturedEvent.get(), "CopyCompletedEvent should be dispatched");
            assertEquals(CopyResult.SKIPPED, capturedEvent.get().result(),
                    "Event result should be SKIPPED");
            assertEquals(testFile, capturedEvent.get().sourcePath(),
                    "Event should contain source path");
            assertEquals(0L, capturedEvent.get().bytesCopied(),
                    "Bytes copied should be 0 for skipped copy");
        }
    }
}
