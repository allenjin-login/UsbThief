package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.EventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests EventBus integration for all storage event types.
 * Verifies that storage events can be dispatched and received correctly.
 */
class EventBusIntegrationTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = EventBus.getInstance();
        eventBus.clearAll();
    }

    @AfterEach
    void tearDown() {
        eventBus.clearAll();
    }

    @Test
    void dispatch_shouldNotifyStorageLowEventListener() {
        AtomicReference<StorageLowEvent> receivedEvent = new AtomicReference<>();

        eventBus.register(StorageLowEvent.class, receivedEvent::set);

        StorageLowEvent event = new StorageLowEvent(
                Paths.get("/test/work"),
                1024L,
                512L,
                StorageLevel.LOW
        );

        eventBus.dispatch(event);

        assertNotNull(receivedEvent.get());
        assertEquals(Paths.get("/test/work"), receivedEvent.get().workDir());
        assertEquals(1024L, receivedEvent.get().freeBytes());
        assertEquals(512L, receivedEvent.get().thresholdBytes());
        assertEquals(StorageLevel.LOW, receivedEvent.get().level());
        assertFalse(receivedEvent.get().isCritical());
    }

    @Test
    void dispatch_shouldNotifyStorageLowEventForCriticalLevel() {
        AtomicReference<StorageLowEvent> receivedEvent = new AtomicReference<>();

        eventBus.register(StorageLowEvent.class, receivedEvent::set);

        StorageLowEvent event = new StorageLowEvent(
                Paths.get("/test/work"),
                256L,
                512L,
                StorageLevel.CRITICAL
        );

        eventBus.dispatch(event);

        assertNotNull(receivedEvent.get());
        assertEquals(StorageLevel.CRITICAL, receivedEvent.get().level());
        assertTrue(receivedEvent.get().isCritical());
    }

    @Test
    void dispatch_shouldNotifyStorageRecoveredEventListener() {
        AtomicReference<StorageRecoveredEvent> receivedEvent = new AtomicReference<>();

        eventBus.register(StorageRecoveredEvent.class, receivedEvent::set);

        StorageRecoveredEvent event = new StorageRecoveredEvent(
                Paths.get("/test/work"),
                2048L
        );

        eventBus.dispatch(event);

        assertNotNull(receivedEvent.get());
        assertEquals(Paths.get("/test/work"), receivedEvent.get().workDir());
        assertEquals(2048L, receivedEvent.get().freeBytes());
    }

    @Test
    void dispatch_shouldNotifyFilesRecycledEventListener() {
        AtomicReference<FilesRecycledEvent> receivedEvent = new AtomicReference<>();

        eventBus.register(FilesRecycledEvent.class, receivedEvent::set);

        List<String> filePaths = List.of("/test/file1.txt", "/test/file2.pdf");
        List<String> fileNames = new ArrayList<>();
        filePaths.forEach(path -> fileNames.add(Paths.get(path).toString()));

        FilesRecycledEvent event = new FilesRecycledEvent(
                fileNames.stream().map(Paths::get).toList(),
                4096L,
                RecycleStrategy.TIME_FIRST
        );

        eventBus.dispatch(event);

        assertNotNull(receivedEvent.get());
        assertEquals(2, receivedEvent.get().fileCount());
        assertEquals(4096L, receivedEvent.get().bytesFreed());
        assertEquals(RecycleStrategy.TIME_FIRST, receivedEvent.get().strategy());
    }

    @Test
    void dispatch_shouldNotifyEmptyFoldersDeletedEventListener() {
        AtomicReference<EmptyFoldersDeletedEvent> receivedEvent = new AtomicReference<>();

        eventBus.register(EmptyFoldersDeletedEvent.class, receivedEvent::set);

        List<String> folderPaths = List.of("/test/folder1", "/test/folder2");
        EmptyFoldersDeletedEvent event = new EmptyFoldersDeletedEvent(
                folderPaths.stream().map(Paths::get).toList(),
                2
        );

        eventBus.dispatch(event);

        assertNotNull(receivedEvent.get());
        assertEquals(2, receivedEvent.get().count());
    }

    @Test
    void dispatch_shouldNotifyMultipleListenersForSameEvent() {
        AtomicInteger callCount = new AtomicInteger(0);

        eventBus.register(StorageLowEvent.class, event -> callCount.incrementAndGet());
        eventBus.register(StorageLowEvent.class, event -> callCount.incrementAndGet());

        StorageLowEvent event = new StorageLowEvent(
                Paths.get("/test/work"),
                1024L,
                512L,
                StorageLevel.LOW
        );

        eventBus.dispatch(event);

        assertEquals(2, callCount.get());
    }

    @Test
    void dispatch_shouldContinueAfterException() {
        AtomicInteger callCount = new AtomicInteger(0);

        eventBus.register(StorageLowEvent.class, event -> {
            throw new RuntimeException("Test exception");
        });
        eventBus.register(StorageLowEvent.class, event -> callCount.incrementAndGet());

        StorageLowEvent event = new StorageLowEvent(
                Paths.get("/test/work"),
                1024L,
                512L,
                StorageLevel.LOW
        );

        eventBus.dispatch(event);

        assertEquals(1, callCount.get());
    }

    @Test
    void register_shouldRejectNullStorageLowEventListener() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.register(StorageLowEvent.class, null));
    }

    @Test
    void register_shouldRejectNullEventClass() {
        AtomicReference<StorageLowEvent> listener = new AtomicReference<>();
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.register(null, listener::set));
    }

    @Test
    void unregister_shouldRemoveStorageEventListener() {
        AtomicReference<StorageLowEvent> receivedEvent = new AtomicReference<>();
        EventListener<StorageLowEvent> listener = receivedEvent::set;
        eventBus.register(StorageLowEvent.class, listener);

        assertEquals(1, eventBus.listenerCount());

        eventBus.unregister(StorageLowEvent.class, listener);

        assertEquals(0, eventBus.listenerCount());
    }

    @Test
    void StorageEventListenerInterface_shouldSupportAllEventTypes() {
        AtomicInteger lowCount = new AtomicInteger(0);
        AtomicInteger recoveredCount = new AtomicInteger(0);
        AtomicInteger recycledCount = new AtomicInteger(0);
        AtomicInteger foldersDeletedCount = new AtomicInteger(0);

        StorageEventListener listener = new StorageEventListener() {
            @Override
            public void onStorageLow(StorageLowEvent event) {
                lowCount.incrementAndGet();
            }

            @Override
            public void onStorageRecovered(StorageRecoveredEvent event) {
                recoveredCount.incrementAndGet();
            }

            @Override
            public void onFilesRecycled(FilesRecycledEvent event) {
                recycledCount.incrementAndGet();
            }

            @Override
            public void onEmptyFoldersDeleted(EmptyFoldersDeletedEvent event) {
                foldersDeletedCount.incrementAndGet();
            }
        };

        // Register individual listeners
        eventBus.register(StorageLowEvent.class, listener::onStorageLow);
        eventBus.register(StorageRecoveredEvent.class, listener::onStorageRecovered);
        eventBus.register(FilesRecycledEvent.class, listener::onFilesRecycled);
        eventBus.register(EmptyFoldersDeletedEvent.class, listener::onEmptyFoldersDeleted);

        // Dispatch all events
        eventBus.dispatch(new StorageLowEvent(
                Paths.get("/test"), 100L, 200L, StorageLevel.LOW));
        eventBus.dispatch(new StorageRecoveredEvent(Paths.get("/test"), 300L));
        eventBus.dispatch(new FilesRecycledEvent(
                List.of(Paths.get("/test/file.txt")), 100L, RecycleStrategy.TIME_FIRST));
        eventBus.dispatch(new EmptyFoldersDeletedEvent(
                List.of(Paths.get("/test/folder")), 1));

        // Verify all listeners were called
        assertEquals(1, lowCount.get());
        assertEquals(1, recoveredCount.get());
        assertEquals(1, recycledCount.get());
        assertEquals(1, foldersDeletedCount.get());
    }

    @Test
    void StorageEventListenerInterface_shouldSupportSelectiveImplementation() {
        AtomicInteger lowCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        StorageEventListener listener = new StorageEventListener() {
            @Override
            public void onStorageLow(StorageLowEvent event) {
                lowCount.incrementAndGet();
            }

            // Other methods use default no-op implementations
        };

        eventBus.register(StorageLowEvent.class, listener::onStorageLow);
        eventBus.register(StorageRecoveredEvent.class, listener::onStorageRecovered);

        // Dispatch events
        eventBus.dispatch(new StorageLowEvent(
                Paths.get("/test"), 100L, 200L, StorageLevel.LOW));
        eventBus.dispatch(new StorageRecoveredEvent(Paths.get("/test"), 300L));

        // Verify only onStorageLow was called
        assertEquals(1, lowCount.get());
        assertEquals(0, otherCount.get());
    }

    @Test
    void allStorageEvents_shouldImplementEventInterface() {
        // Verify all storage events are valid Event implementations
        StorageLowEvent lowEvent = new StorageLowEvent(
                Paths.get("/test"), 100L, 200L, StorageLevel.LOW);
        StorageRecoveredEvent recoveredEvent = new StorageRecoveredEvent(
                Paths.get("/test"), 300L);
        FilesRecycledEvent recycledEvent = new FilesRecycledEvent(
                List.of(Paths.get("/test/file.txt")), 100L, RecycleStrategy.TIME_FIRST);
        EmptyFoldersDeletedEvent foldersEvent = new EmptyFoldersDeletedEvent(
                List.of(Paths.get("/test/folder")), 1);

        // Verify timestamps are set
        assertNotEquals(0, lowEvent.timestamp());
        assertNotEquals(0, recoveredEvent.timestamp());
        assertNotEquals(0, recycledEvent.timestamp());
        assertNotEquals(0, foldersEvent.timestamp());

        // Verify descriptions are generated
        assertNotNull(lowEvent.description());
        assertNotNull(recoveredEvent.description());
        assertNotNull(recycledEvent.description());
        assertNotNull(foldersEvent.description());
    }
}
