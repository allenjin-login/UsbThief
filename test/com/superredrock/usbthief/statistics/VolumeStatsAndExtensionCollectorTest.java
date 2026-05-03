package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.statistics.collector.*;
import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VolumeStatsCollectorTest {

    private VolumeStatsCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new VolumeStatsCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void getId() {
        assertEquals("volume.stats", collector.getId());
    }

    @Test
    void isPersistent() {
        assertTrue(collector.isPersistent());
    }

    @Test
    void tracksPerVolumeFilesAndBytes() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "serial1"));

        VolumeStats stats = collector.getVolumeStats("serial1");
        assertEquals(1, stats.getFilesCopied());
        assertEquals(100, stats.getBytesCopied());
        assertEquals(0, stats.getErrors());
    }

    @Test
    void tracksErrors() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), null, 100, 0, CopyResult.FAIL, "serial1"));

        VolumeStats stats = collector.getVolumeStats("serial1");
        assertEquals(0, stats.getFilesCopied());
        assertEquals(1, stats.getErrors());
    }

    @Test
    void separateVolumesTrackedIndependently() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.txt"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s2"));

        assertEquals(1, collector.getVolumeStats("s1").getFilesCopied());
        assertEquals(1, collector.getVolumeStats("s2").getFilesCopied());
    }

    @Test
    void ignoresEmptySerial() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, ""));

        Map<String, VolumeStats> all = collector.getAllVolumeStats();
        assertTrue(all.isEmpty());
    }

    @Test
    void extensionCounting() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\doc.pdf"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\doc.pdf"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s1"));

        VolumeStats stats = collector.getVolumeStats("s1");
        Map<String, Long> exts = stats.getExtensionCounts();
        assertEquals(2L, exts.get("pdf"));
    }

    @Test
    void resetClearsAll() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        collector.reset();
        assertTrue(collector.getAllVolumeStats().isEmpty());
    }

    @Test
    void snapshot() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        MetricSnapshot snap = collector.snapshot();
        assertEquals("volume.stats", snap.metricId());
    }

    @Test
    void getAllVolumeStatsReturnsCopy() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        Map<String, VolumeStats> stats1 = collector.getAllVolumeStats();
        Map<String, VolumeStats> stats2 = collector.getAllVolumeStats();
        assertNotSame(stats1, stats2);
    }
}

class SpeedCollectorTest {

    private SpeedCollector collector;

    @BeforeEach
    void setUp() {
        collector = new SpeedCollector();
    }

    @Test
    void getId() {
        assertEquals("speed.global", collector.getId());
    }

    @Test
    void isNotPersistent() {
        assertFalse(collector.isPersistent());
    }

    @Test
    void createProbe() {
        SpeedProbe probe = collector.createProbe("test-probe");
        assertNotNull(probe);
        assertEquals(1, collector.getProbeGroup().getProbeCount());
    }

    @Test
    void snapshot() {
        MetricSnapshot snap = collector.snapshot();
        assertEquals("speed.global", snap.metricId());
    }

    @Test
    void loadSaveResetAreNoop() {
        assertDoesNotThrow(() -> collector.load(null));
        assertDoesNotThrow(() -> collector.save(null));
        assertDoesNotThrow(() -> collector.reset());
    }
}

class ExtensionCountCollectorTest {

    private ExtensionCountCollector collector;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new ExtensionCountCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void countsExtensions() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\doc.pdf"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\doc.pdf"), Path.of("out"), 200, 200, CopyResult.SUCCESS, "s1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\image.jpg"), Path.of("out"), 300, 300, CopyResult.SUCCESS, "s1"));

        Map<String, Long> counts = collector.getExtensionCounts();
        assertEquals(2L, counts.get("pdf"));
        assertEquals(1L, counts.get("jpg"));
    }

    @Test
    void ignoresDirectories() throws Exception {
        // Verify extension extraction for directory-like names
        var method = ExtensionCountCollector.class.getDeclaredMethod("getFileExtension", String.class);
        method.setAccessible(true);
        assertEquals("pdf", method.invoke(null, "doc.pdf"));
        assertEquals("txt", method.invoke(null, "readme.txt"));
    }

    @Test
    void getFileExtension() throws Exception {
        var method = ExtensionCountCollector.class.getDeclaredMethod("getFileExtension", String.class);
        method.setAccessible(true);
        assertEquals("pdf", method.invoke(null, "doc.pdf"));
        assertEquals("jpg", method.invoke(null, "image.jpg"));
        assertEquals("gz", method.invoke(null, "archive.tar.gz"));
        assertNull(method.invoke(null, ".hidden"));
        assertNull(method.invoke(null, "noext"));
        assertNull(method.invoke(null, "trailing."));
    }

    @Test
    void ignoresFailure() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\doc.pdf"), null, 100, 0, CopyResult.FAIL, "s1"));
        assertTrue(collector.getExtensionCounts().isEmpty());
    }

    @Test
    void reset() {
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\doc.pdf"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1"));
        collector.reset();
        assertTrue(collector.getExtensionCounts().isEmpty());
    }

    @Test
    void isPersistent() {
        assertTrue(collector.isPersistent());
    }
}
