package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.Event;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.EventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for storage-related events.
 */
class StorageEventTest {

    private static final Path TEST_WORK_DIR = Paths.get("E:\\test\\work");
    private static final long TEST_FREE_BYTES = 1024 * 1024 * 100; // 100 MB
    private static final long TEST_THRESHOLD_BYTES = 1024 * 1024 * 200; // 200 MB

    @BeforeEach
    void setUp() {
        // Reset event bus before each test
        // Note: EventBus is a singleton, so we clear listeners if needed
    }

    @Test
    void testStorageLevelEnum() {
        // Test enum values exist
        assertNotNull(StorageLevel.OK);
        assertNotNull(StorageLevel.LOW);
        assertNotNull(StorageLevel.CRITICAL);

        // Test all enum values
        StorageLevel[] levels = StorageLevel.values();
        assertEquals(3, levels.length);
    }

    @Test
    void testRecycleStrategyEnum() {
        // Test enum values exist
        assertNotNull(RecycleStrategy.TIME_FIRST);
        assertNotNull(RecycleStrategy.SIZE_FIRST);
        assertNotNull(RecycleStrategy.AUTO);

        // Test all enum values
        RecycleStrategy[] strategies = RecycleStrategy.values();
        assertEquals(3, strategies.length);
    }

    @Test
    void testStorageLowEventCreation() {
        StorageLowEvent event = new StorageLowEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES,
                TEST_THRESHOLD_BYTES,
                StorageLevel.LOW
        );

        assertNotNull(event);
        assertEquals(TEST_WORK_DIR, event.workDir());
        assertEquals(TEST_FREE_BYTES, event.freeBytes());
        assertEquals(TEST_THRESHOLD_BYTES, event.thresholdBytes());
        assertEquals(StorageLevel.LOW, event.level());
        assertFalse(event.isCritical());
    }

    @Test
    void testStorageLowEventCritical() {
        StorageLowEvent event = new StorageLowEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES,
                TEST_THRESHOLD_BYTES,
                StorageLevel.CRITICAL
        );

        assertEquals(StorageLevel.CRITICAL, event.level());
        assertTrue(event.isCritical());
    }

    @Test
    void testStorageLowEventImplementsEvent() {
        StorageLowEvent event = new StorageLowEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES,
                TEST_THRESHOLD_BYTES,
                StorageLevel.LOW
        );

        assertInstanceOf(Event.class, event);
        assertNotNull(event.description());
        assertTrue(event.timestamp() > 0);
        assertTrue(event.description().contains("StorageLowEvent"));
    }

    @Test
    void testStorageLowEventNullValidation() {
        // Test null workDir
        assertThrows(IllegalArgumentException.class, () -> {
            new StorageLowEvent(null, TEST_FREE_BYTES, TEST_THRESHOLD_BYTES, StorageLevel.LOW);
        });

        // Test null level
        assertThrows(IllegalArgumentException.class, () -> {
            new StorageLowEvent(TEST_WORK_DIR, TEST_FREE_BYTES, TEST_THRESHOLD_BYTES, null);
        });

        // Test negative freeBytes
        assertThrows(IllegalArgumentException.class, () -> {
            new StorageLowEvent(TEST_WORK_DIR, -1, TEST_THRESHOLD_BYTES, StorageLevel.LOW);
        });

        // Test negative thresholdBytes
        assertThrows(IllegalArgumentException.class, () -> {
            new StorageLowEvent(TEST_WORK_DIR, TEST_FREE_BYTES, -1, StorageLevel.LOW);
        });
    }

    @Test
    void testStorageLowEventCanBeDispatched() {
        StorageLowEvent event = new StorageLowEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES,
                TEST_THRESHOLD_BYTES,
                StorageLevel.LOW
        );

        // Should not throw exception
        assertDoesNotThrow(() -> EventBus.getInstance().dispatch(event));
    }

    @Test
    void testStorageRecoveredEventCreation() {
        StorageRecoveredEvent event = new StorageRecoveredEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES
        );

        assertNotNull(event);
        assertEquals(TEST_WORK_DIR, event.workDir());
        assertEquals(TEST_FREE_BYTES, event.freeBytes());
    }

    @Test
    void testStorageRecoveredEventImplementsEvent() {
        StorageRecoveredEvent event = new StorageRecoveredEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES
        );

        assertInstanceOf(Event.class, event);
        assertNotNull(event.timestamp());
        assertNotNull(event.description());
        assertTrue(event.timestamp() > 0);
        assertTrue(event.description().contains("StorageRecoveredEvent"));
    }

    @Test
    void testStorageRecoveredEventNullValidation() {
        // Test null workDir
        assertThrows(IllegalArgumentException.class, () -> {
            new StorageRecoveredEvent(null, TEST_FREE_BYTES);
        });

        // Test negative freeBytes
        assertThrows(IllegalArgumentException.class, () -> {
            new StorageRecoveredEvent(TEST_WORK_DIR, -1);
        });
    }

    @Test
    void testStorageRecoveredEventCanBeDispatched() {
        StorageRecoveredEvent event = new StorageRecoveredEvent(
                TEST_WORK_DIR,
                TEST_FREE_BYTES
        );

        // Should not throw exception
        assertDoesNotThrow(() -> EventBus.getInstance().dispatch(event));
    }

    @Test
    void testFilesRecycledEventCreation() {
        List<Path> files = new ArrayList<>();
        files.add(Paths.get("E:\\test\\file1.txt"));
        files.add(Paths.get("E:\\test\\file2.txt"));
        long bytesFreed = 1024 * 50; // 50 KB

        FilesRecycledEvent event = new FilesRecycledEvent(files, bytesFreed, RecycleStrategy.TIME_FIRST);

        assertNotNull(event);
        assertEquals(2, event.fileCount());
        assertEquals(bytesFreed, event.bytesFreed());
        assertEquals(RecycleStrategy.TIME_FIRST, event.strategy());
        assertEquals(2, event.files().size());
    }

    @Test
    void testFilesRecycledEventImplementsEvent() {
        List<Path> files = new ArrayList<>();
        files.add(Paths.get("E:\\test\\file1.txt"));

        FilesRecycledEvent event = new FilesRecycledEvent(files, 1024, RecycleStrategy.SIZE_FIRST);

        assertInstanceOf(Event.class, event);
        assertNotNull(event.timestamp());
        assertNotNull(event.description());
        assertTrue(event.timestamp() > 0);
        assertTrue(event.description().contains("FilesRecycledEvent"));
    }

    @Test
    void testFilesRecycledEventNullValidation() {
        List<Path> files = new ArrayList<>();
        files.add(Paths.get("E:\\test\\file1.txt"));

        // Test null files
        assertThrows(IllegalArgumentException.class, () -> {
            new FilesRecycledEvent(null, 1024, RecycleStrategy.TIME_FIRST);
        });

        // Test null strategy
        assertThrows(IllegalArgumentException.class, () -> {
            new FilesRecycledEvent(files, 1024, null);
        });

        // Test negative bytesFreed
        assertThrows(IllegalArgumentException.class, () -> {
            new FilesRecycledEvent(files, -1, RecycleStrategy.TIME_FIRST);
        });
    }

    @Test
    void testFilesRecycledEventImmutableList() {
        List<Path> files = new ArrayList<>();
        files.add(Paths.get("E:\\test\\file1.txt"));

        FilesRecycledEvent event = new FilesRecycledEvent(files, 1024, RecycleStrategy.AUTO);

        // Try to modify the returned list - should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            event.files().add(Paths.get("E:\\test\\file2.txt"));
        });
    }

    @Test
    void testFilesRecycledEventCanBeDispatched() {
        List<Path> files = new ArrayList<>();
        files.add(Paths.get("E:\\test\\file1.txt"));

        FilesRecycledEvent event = new FilesRecycledEvent(files, 1024, RecycleStrategy.AUTO);

        // Should not throw exception
        assertDoesNotThrow(() -> EventBus.getInstance().dispatch(event));
    }

    @Test
    void testEmptyFoldersDeletedEventCreation() {
        List<Path> folders = new ArrayList<>();
        folders.add(Paths.get("E:\\test\\folder1"));
        folders.add(Paths.get("E:\\test\\folder2"));
        int count = 2;

        EmptyFoldersDeletedEvent event = new EmptyFoldersDeletedEvent(folders, count);

        assertNotNull(event);
        assertEquals(2, event.count());
        assertEquals(2, event.folders().size());
    }

    @Test
    void testEmptyFoldersDeletedEventImplementsEvent() {
        List<Path> folders = new ArrayList<>();
        folders.add(Paths.get("E:\\test\\folder1"));

        EmptyFoldersDeletedEvent event = new EmptyFoldersDeletedEvent(folders, 1);

        assertInstanceOf(Event.class, event);
        assertNotNull(event.timestamp());
        assertNotNull(event.description());
        assertTrue(event.timestamp() > 0);
        assertTrue(event.description().contains("EmptyFoldersDeletedEvent"));
    }

    @Test
    void testEmptyFoldersDeletedEventNullValidation() {
        // Test null folders
        assertThrows(IllegalArgumentException.class, () -> {
            new EmptyFoldersDeletedEvent(null, 1);
        });

        // Test negative count
        assertThrows(IllegalArgumentException.class, () -> {
            new EmptyFoldersDeletedEvent(new ArrayList<>(), -1);
        });
    }

    @Test
    void testEmptyFoldersDeletedEventCountMismatch() {
        List<Path> folders = new ArrayList<>();
        folders.add(Paths.get("E:\\test\\folder1"));

        // Count should match list size
        assertThrows(IllegalArgumentException.class, () -> {
            new EmptyFoldersDeletedEvent(folders, 2);
        });
    }

    @Test
    void testEmptyFoldersDeletedEventImmutableList() {
        List<Path> folders = new ArrayList<>();
        folders.add(Paths.get("E:\\test\\folder1"));

        EmptyFoldersDeletedEvent event = new EmptyFoldersDeletedEvent(folders, 1);

        // Try to modify the returned list - should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            event.folders().add(Paths.get("E:\\test\\folder2"));
        });
    }

    @Test
    void testEmptyFoldersDeletedEventCanBeDispatched() {
        List<Path> folders = new ArrayList<>();
        folders.add(Paths.get("E:\\test\\folder1"));

        EmptyFoldersDeletedEvent event = new EmptyFoldersDeletedEvent(folders, 1);

        // Should not throw exception
        assertDoesNotThrow(() -> EventBus.getInstance().dispatch(event));
    }

    @Test
    void testEventDispatchWithListener() {
        AtomicInteger listenerCount = new AtomicInteger(0);

        // Register a listener for storage events
        EventListener<Event> listener = event -> {
            listenerCount.incrementAndGet();
        };
        EventBus.getInstance().register(Event.class, listener);

        // Dispatch all four event types
        StorageLowEvent lowEvent = new StorageLowEvent(TEST_WORK_DIR, TEST_FREE_BYTES, TEST_THRESHOLD_BYTES, StorageLevel.LOW);
        StorageRecoveredEvent recoveredEvent = new StorageRecoveredEvent(TEST_WORK_DIR, TEST_FREE_BYTES);
        FilesRecycledEvent recycledEvent = new FilesRecycledEvent(List.of(Paths.get("file.txt")), 1024, RecycleStrategy.AUTO);
        EmptyFoldersDeletedEvent deletedEvent = new EmptyFoldersDeletedEvent(List.of(Paths.get("folder")), 1);

        EventBus.getInstance().dispatch(lowEvent);
        EventBus.getInstance().dispatch(recoveredEvent);
        EventBus.getInstance().dispatch(recycledEvent);
        EventBus.getInstance().dispatch(deletedEvent);

        // All events should have been dispatched synchronously
        assertEquals(4, listenerCount.get());

        // Cleanup: unregister listener
        EventBus.getInstance().unregister(Event.class, listener);
    }

    @Test
    void testAllEventDescriptionsContainEventName() {
        StorageLowEvent lowEvent = new StorageLowEvent(TEST_WORK_DIR, TEST_FREE_BYTES, TEST_THRESHOLD_BYTES, StorageLevel.LOW);
        StorageRecoveredEvent recoveredEvent = new StorageRecoveredEvent(TEST_WORK_DIR, TEST_FREE_BYTES);
        FilesRecycledEvent recycledEvent = new FilesRecycledEvent(List.of(Paths.get("file.txt")), 1024, RecycleStrategy.AUTO);
        EmptyFoldersDeletedEvent deletedEvent = new EmptyFoldersDeletedEvent(List.of(Paths.get("folder")), 1);

        assertTrue(lowEvent.description().contains("StorageLowEvent"));
        assertTrue(recoveredEvent.description().contains("StorageRecoveredEvent"));
        assertTrue(recycledEvent.description().contains("FilesRecycledEvent"));
        assertTrue(deletedEvent.description().contains("EmptyFoldersDeletedEvent"));
    }

    @Test
    void testEventTimestampsAreReasonable() {
        long beforeCreate = System.currentTimeMillis();

        StorageLowEvent lowEvent = new StorageLowEvent(TEST_WORK_DIR, TEST_FREE_BYTES, TEST_THRESHOLD_BYTES, StorageLevel.LOW);
        StorageRecoveredEvent recoveredEvent = new StorageRecoveredEvent(TEST_WORK_DIR, TEST_FREE_BYTES);
        FilesRecycledEvent recycledEvent = new FilesRecycledEvent(List.of(Paths.get("file.txt")), 1024, RecycleStrategy.AUTO);
        EmptyFoldersDeletedEvent deletedEvent = new EmptyFoldersDeletedEvent(List.of(Paths.get("folder")), 1);

        long afterCreate = System.currentTimeMillis();

        // Timestamps should be between beforeCreate and afterCreate
        assertTrue(lowEvent.timestamp() >= beforeCreate && lowEvent.timestamp() <= afterCreate);
        assertTrue(recoveredEvent.timestamp() >= beforeCreate && recoveredEvent.timestamp() <= afterCreate);
        assertTrue(recycledEvent.timestamp() >= beforeCreate && recycledEvent.timestamp() <= afterCreate);
        assertTrue(deletedEvent.timestamp() >= beforeCreate && deletedEvent.timestamp() <= afterCreate);
    }
}
