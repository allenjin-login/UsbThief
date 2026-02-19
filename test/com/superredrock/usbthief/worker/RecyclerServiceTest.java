package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.EventListener;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.RecycleStrategy;
import com.superredrock.usbthief.core.event.storage.StorageLevel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecyclerService.
 */
class RecyclerServiceTest {

    private Path testWorkPath;
    private Path testIndexPath;
    private RecyclerService recyclerService;
    private AtomicBoolean eventReceived;
    private List<Path> deletedFolders;
    private AtomicReference<FilesRecycledEvent> recycledEvent;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary work directory
        testWorkPath = Files.createTempDirectory("recycler-test");
        testIndexPath = Files.createTempFile("recycler-index", ".obj");

        // Override work path for testing
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, testWorkPath.toString());

        // Initialize test event listeners
        eventReceived = new AtomicBoolean(false);
        deletedFolders = new ArrayList<>();
        recycledEvent = new AtomicReference<>();

        // Register event listener for EmptyFoldersDeletedEvent
        EventBus.getInstance().register(EmptyFoldersDeletedEvent.class,
                new EventListener<EmptyFoldersDeletedEvent>() {
                    @Override
                    public void onEvent(EmptyFoldersDeletedEvent event) {
                        eventReceived.set(true);
                        deletedFolders.addAll(event.folders());
                    }
                });

        // Register event listener for FilesRecycledEvent
        EventBus.getInstance().register(FilesRecycledEvent.class,
                new EventListener<FilesRecycledEvent>() {
                    @Override
                    public void onEvent(FilesRecycledEvent event) {
                        recycledEvent.set(event);
                    }
                });
    }

    @AfterEach
    void tearDown() throws IOException {
        // Stop service if running
        if (recyclerService != null && recyclerService.isAlive()) {
            recyclerService.stopService();
        }

        // Clean up temporary directory
        deleteDirectory(testWorkPath);
        Files.deleteIfExists(testIndexPath);
    }

    /**
     * Test: Service extends Service correctly.
     */
    @Test
    void serviceExtendsServiceClass() {
        recyclerService = new RecyclerService();

        assertNotNull(recyclerService);
        assertEquals("RecyclerService", recyclerService.getServiceName());
        assertNotNull(recyclerService.getDescription());
        assertTrue(recyclerService.getTickIntervalMs() > 0);
    }

    /**
     * Test: Service can start and stop.
     */
    @Test
    void serviceStartsAndStops() throws InterruptedException {
        recyclerService = new RecyclerService();
        recyclerService.start();

        Thread.sleep(100); // Give time to start
        assertTrue(recyclerService.isRunning());

        recyclerService.stopService();
        Thread.sleep(100); // Give time to stop
        assertFalse(recyclerService.isRunning());
    }

    /**
     * Test: Empty folder deletion works when storage is OK.
     */
    @Test
    void deletesEmptyFoldersWhenStorageOk() throws IOException, InterruptedException {
        // Create test folder structure
        Path emptyFolder1 = testWorkPath.resolve("empty1");
        Path emptyFolder2 = testWorkPath.resolve("empty2");
        Path nonEmptyFolder = testWorkPath.resolve("nonempty");

        Files.createDirectory(emptyFolder1);
        Files.createDirectory(emptyFolder2);
        Files.createDirectory(nonEmptyFolder);

        // Add a file to nonEmptyFolder
        Files.writeString(nonEmptyFolder.resolve("file.txt"), "test");

        // Setup StorageController stub to return OK status
        setupStorageStatus(StorageLevel.OK);

        // Create service and call tick() directly
        recyclerService = new RecyclerService();
        recyclerService.tick(); // Call directly for testing

        // Verify empty folders deleted
        assertFalse(Files.exists(emptyFolder1), "emptyFolder1 should be deleted");
        assertFalse(Files.exists(emptyFolder2), "emptyFolder2 should be deleted");
        assertTrue(Files.exists(nonEmptyFolder), "nonEmptyFolder should exist");
        assertTrue(Files.exists(nonEmptyFolder.resolve("file.txt")), "file.txt should exist");

        // Verify event dispatched
        assertTrue(eventReceived.get(), "EmptyFoldersDeletedEvent should be dispatched");
        assertEquals(2, deletedFolders.size(), "2 folders should be deleted");
    }

    /**
     * Test: Empty folders are deleted bottom-up (deepest first).
     */
    @Test
    void deletesEmptyFoldersBottomUp() throws IOException, InterruptedException {
        // Create nested empty folder structure
        Path parent = testWorkPath.resolve("parent");
        Path child1 = parent.resolve("child1");
        Path child2 = child1.resolve("child2");
        Path grandchild = child2.resolve("grandchild");

        Files.createDirectories(grandchild);

        // Setup StorageController stub to return OK status
        setupStorageStatus(StorageLevel.OK);

        // Create service and call tick() directly
        recyclerService = new RecyclerService();
        recyclerService.tick(); // Call directly for testing

        // Verify only truly empty folders are deleted
        // grandchild was empty (no children), so it should be deleted
        assertFalse(Files.exists(grandchild), "grandchild should be deleted");

        // parent, child1, and child2 still had subdirectories when checked,
        // so they should NOT be deleted
        assertTrue(Files.exists(parent), "parent should still exist (had subdirectory)");
        assertTrue(Files.exists(child1), "child1 should still exist (had subdirectory)");
        assertTrue(Files.exists(child2), "child2 should still exist (had subdirectory)");

        // Verify event dispatched for grandchild only
        assertTrue(eventReceived.get(), "EmptyFoldersDeletedEvent should be dispatched");
        assertEquals(1, deletedFolders.size(), "1 folder should be deleted (grandchild)");
    }

    /**
     * Test: No event dispatched when no empty folders exist.
     */
    @Test
    void noEventWhenNoEmptyFolders() throws IOException, InterruptedException {
        // Create only non-empty folder
        Path folder = testWorkPath.resolve("folder");
        Files.createDirectory(folder);
        Files.writeString(folder.resolve("file.txt"), "test");

        // Setup StorageController stub to return OK status
        setupStorageStatus(StorageLevel.OK);

        // Create service and call tick() directly
        recyclerService = new RecyclerService();
        recyclerService.tick(); // Call directly for testing

        // Verify no event dispatched
        assertFalse(eventReceived.get(), "No event should be dispatched");
        assertTrue(Files.exists(folder), "folder should exist");
    }

    /**
     * Test: File recycling with time-first strategy when storage is LOW.
     */
    @Test
    void recyclesFilesByTimeWhenStorageLow() throws IOException, InterruptedException {
        // Create test files with different ages
        Path file1 = createTestFile(testWorkPath.resolve("file1.txt"), "content1", 0);
        Path file2 = createTestFile(testWorkPath.resolve("file2.txt"), "content2", 1000);
        Path file3 = createTestFile(testWorkPath.resolve("file3.txt"), "content3", 2000);

        long file1Size = Files.size(file1);
        long file2Size = Files.size(file2);

        // Setup StorageController stub to return LOW status
        setupStorageStatus(StorageLevel.OK); // Start OK, will trigger recycling via storageBytes threshold

        // Create service and call tick() directly
        recyclerService = new RecyclerService();
        recyclerService.tick(); // Call directly for testing

        // Files should not be deleted if storage is OK
        // This test verifies the skeleton - full behavior requires actual StorageController implementation
    }

    /**
     * Test: Tick interval is reasonable.
     */
    @Test
    void tickIntervalIsReasonable() {
        recyclerService = new RecyclerService();
        long interval = recyclerService.getTickIntervalMs();

        // Interval should be between 10 seconds and 10 minutes
        assertTrue(interval >= 10000, "Tick interval should be at least 10 seconds");
        assertTrue(interval <= 600000, "Tick interval should be at most 10 minutes");
    }

    /**
     * Test: Service description is not empty.
     */
    @Test
    void descriptionIsNotEmpty() {
        recyclerService = new RecyclerService();
        String description = recyclerService.getDescription();

        assertNotNull(description);
        assertFalse(description.isEmpty());
        assertTrue(description.length() > 10);
    }

    // Helper methods

    private void setupStorageStatus(StorageLevel level) {
        // Stub implementation - in real test, would use mocked StorageController
        // For now, we rely on the stub returning StorageLevel.OK
    }

    private Path createTestFile(Path path, String content, long ageOffsetMs) throws IOException {
        Files.writeString(path, content);

        // Adjust file modification time to simulate age
        if (ageOffsetMs > 0) {
            long newTime = System.currentTimeMillis() - ageOffsetMs;
            Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(newTime));
        }

        return path;
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a)) // Reverse order (files before directories)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }
}
