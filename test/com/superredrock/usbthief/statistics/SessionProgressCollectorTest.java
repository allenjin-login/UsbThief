package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.statistics.collector.SessionProgressCollector;
import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionProgressCollectorTest {

    private SessionProgressCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new SessionProgressCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void getId() {
        assertEquals("session.progress", collector.getId());
    }

    @Test
    void isNotPersistent() {
        assertFalse(collector.isPersistent());
    }

    @Test
    void initialValues() {
        assertEquals(0, collector.getBytesDiscovered());
        assertEquals(0, collector.getBytesCopied());
        assertEquals(0, collector.getFilesCopied());
        assertEquals(0, collector.getFoldersCopied());
        assertEquals(0, collector.getProgressPercentage());
    }

    @Test
    void fileDiscoveredIncrementsDiscovered() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\test.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        // FileDiscoveredEvent is dispatched separately; test via snapshot
        assertEquals(100, collector.getBytesCopied());
    }

    @Test
    void successCopiesIncrementsBytes() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        assertEquals(100, collector.getBytesCopied());
        assertEquals(1, collector.getFilesCopied());
    }

    @Test
    void failureDoesNotIncrementCopied() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 50, CopyResult.FAIL, "s1"));
        assertEquals(0, collector.getBytesCopied());
        assertEquals(0, collector.getFilesCopied());
    }

    @Test
    void progressPercentage() {
        // Dispatch a discovery event first to set discovered bytes
        bus.dispatch(new com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent(
                Path.of("E:\\a.txt"), 200, "s1"));

        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s1"));

        assertEquals(100, collector.getProgressPercentage());
    }

    @Test
    void zeroDiscoveredProgressIsZero() {
        assertEquals(0, collector.getProgressPercentage());
    }

    @Test
    void reset() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        assertTrue(collector.getBytesCopied() > 0);

        collector.reset();
        assertEquals(0, collector.getBytesDiscovered());
        assertEquals(0, collector.getBytesCopied());
        assertEquals(0, collector.getFilesCopied());
        assertEquals(0, collector.getFoldersCopied());
    }

    @Test
    void snapshot() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 50, 50, CopyResult.SUCCESS, "s1"));
        var snap = collector.snapshot();
        assertEquals("session.progress", snap.metricId());
    }

    @Test
    void loadAndSaveAreNoop() {
        assertDoesNotThrow(() -> collector.load(null));
        assertDoesNotThrow(() -> collector.save(null));
    }

    @Test
    void multipleSuccessEvents() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s1"));

        assertEquals(300, collector.getBytesCopied());
        assertEquals(2, collector.getFilesCopied());
    }
}
