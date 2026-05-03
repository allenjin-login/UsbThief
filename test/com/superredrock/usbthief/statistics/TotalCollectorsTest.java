package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.statistics.collector.*;
import com.superredrock.usbthief.statistics.store.InMemoryMetricStore;
import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TotalBytesCopiedCollectorTest {

    private TotalBytesCopiedCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new TotalBytesCopiedCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void getId() {
        assertEquals("bytes.copied", collector.getId());
    }

    @Test
    void isPersistent() {
        assertTrue(collector.isPersistent());
    }

    @Test
    void countsBytesOnSuccess() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        assertEquals(100, collector.snapshot().longValue());
    }

    @Test
    void doesNotCountOnFailure() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 50, CopyResult.FAIL, "s1"));
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void accumulates() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s1"));
        assertEquals(300, collector.snapshot().longValue());
    }

    @Test
    void reset() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        collector.reset();
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void saveAndLoad() {
        InMemoryMetricStore store = new InMemoryMetricStore();
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 50, 50, CopyResult.SUCCESS, "s1"));
        collector.save(store);
        // InMemoryMetricStore is a no-op, so we just verify no exception
        assertDoesNotThrow(() -> collector.load(store));
    }
}

class TotalFilesCopiedCollectorTest {

    private TotalFilesCopiedCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new TotalFilesCopiedCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void countsFilesOnSuccess() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        assertEquals(1, collector.snapshot().longValue());
    }

    @Test
    void doesNotCountOnFailure() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 50, CopyResult.FAIL, "s1"));
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void accumulates() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s1"));
        assertEquals(2, collector.snapshot().longValue());
    }

    @Test
    void reset() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        collector.reset();
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void isPersistent() {
        assertTrue(collector.isPersistent());
    }
}

class TotalErrorsCollectorTest {

    private TotalErrorsCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new TotalErrorsCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void countsOnFailure() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 0, CopyResult.FAIL, "s1"));
        assertEquals(1, collector.snapshot().longValue());
    }

    @Test
    void doesNotCountOnSuccess() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void doesNotCountOnCancel() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 0, CopyResult.CANCEL, "s1"));
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void reset() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 0, CopyResult.FAIL, "s1"));
        collector.reset();
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void isPersistent() {
        assertTrue(collector.isPersistent());
    }
}

class TotalDevicesCopiedCollectorTest {

    private TotalDevicesCopiedCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new TotalDevicesCopiedCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void countsUniqueSerials() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "serial1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "serial1"));
        assertEquals(1, collector.snapshot().longValue());
    }

    @Test
    void countsDifferentSerials() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "serial1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "serial2"));
        assertEquals(2, collector.snapshot().longValue());
    }

    @Test
    void doesNotCountFailure() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 0, CopyResult.FAIL, "serial1"));
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void ignoresEmptySerial() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, ""));
        assertEquals(0, collector.snapshot().longValue());
    }

    @Test
    void reset() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "serial1"));
        collector.reset();
        assertEquals(0, collector.snapshot().longValue());
        assertEquals(0, collector.getCopiedDeviceCount());
    }

    @Test
    void isPersistent() {
        assertTrue(collector.isPersistent());
    }
}
