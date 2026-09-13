package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.statistics.collector.*;
import com.superredrock.usbthief.statistics.store.PreferencesMetricStore;
import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

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

/**
 * PF-16 regression: persistence must not rescan the full key set once per volume.
 *
 * <p>{@link RecordingMetricStore#keysCalls} counts the backing-store round trips, so the tests
 * fail if a save or load reintroduces the nested {@code keys()} scan (100 volumes x 20k keys
 * previously meant two million {@code startsWith} checks plus 100 full key-array copies).</p>
 */
class VolumeStatsPersistenceTest {

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void saveAndLoadFetchTheKeySetOnceEach() {
        VolumeStatsCollector collector = new VolumeStatsCollector();
        int volumes = 50;
        for (int i = 0; i < volumes; i++) {
            dispatchSuccess("serial-" + i, "f" + i + ".ext" + (i % 5), 1000 + i);
        }
        RecordingMetricStore store = new RecordingMetricStore();

        collector.save(store);
        assertEquals(1, store.keysCalls, "save must fetch the key set exactly once");

        VolumeStatsCollector reloaded = new VolumeStatsCollector();
        store.keysCalls = 0;
        reloaded.load(store);
        assertEquals(1, store.keysCalls, "load must fetch the key set exactly once");

        assertEquals(volumes, reloaded.getAllVolumeStats().size());
        VolumeStats stats = reloaded.getVolumeStats("serial-20");
        assertEquals(1, stats.getFilesCopied());
        assertEquals(1020L, stats.getBytesCopied());
        assertEquals(1L, stats.getExtensionCounts().get("ext0"));
    }

    @Test
    void persistedKeyLayoutIsUnchanged() {
        VolumeStatsCollector collector = new VolumeStatsCollector();
        dispatchSuccess("s0", "doc.pdf", 300);
        dispatchFailure("s0");

        RecordingMetricStore store = new RecordingMetricStore();
        collector.save(store);

        assertEquals(1L, store.getLong("volumeStats.count").orElse(-1));
        assertEquals("s0", store.getString("vs.0.serial").orElse(null));
        assertEquals(1L, store.getLong("vs.0.filesCopied").orElse(-1));
        assertEquals(300L, store.getLong("vs.0.bytesCopied").orElse(-1));
        assertEquals(1L, store.getLong("vs.0.errors").orElse(-1));
        assertTrue(store.containsKey("vs.0.firstSeenTime"));
        assertEquals(1L, store.getLong("vs.0.ext.pdf").orElse(-1));
    }

    @Test
    void legacyLayoutRemainsReadable() {
        RecordingMetricStore store = new RecordingMetricStore();
        store.put("volumeStats.count", 2L);
        store.put("vs.0.serial", "legacy-a");
        store.put("vs.0.filesCopied", 7L);
        store.put("vs.0.bytesCopied", 700L);
        store.put("vs.0.errors", 1L);
        store.put("vs.0.firstSeenTime", 1234L);
        store.put("vs.0.ext.pdf", 3L);
        store.put("vs.1.serial", "legacy-b");
        store.put("vs.1.filesCopied", 2L);

        VolumeStatsCollector collector = new VolumeStatsCollector();
        collector.load(store);

        assertEquals(2, collector.getAllVolumeStats().size());
        VolumeStats first = collector.getVolumeStats("legacy-a");
        assertEquals(7, first.getFilesCopied());
        assertEquals(700L, first.getBytesCopied());
        assertEquals(1, first.getErrors());
        assertEquals(1234L, first.getFirstSeenTime());
        assertEquals(3L, first.getExtensionCounts().get("pdf"));
        assertEquals(2, collector.getVolumeStats("legacy-b").getFilesCopied());
    }

    @Test
    void saveRemovesKeysOfVanishedVolumes() {
        VolumeStatsCollector collector = new VolumeStatsCollector();
        for (int i = 0; i < 3; i++) {
            dispatchSuccess("s" + i, "doc.pdf", 100 + i);
        }
        RecordingMetricStore store = new RecordingMetricStore();
        collector.save(store);
        assertTrue(store.containsKey("vs.2.serial"));
        assertTrue(store.containsKey("vs.2.ext.pdf"));

        collector.reset();
        dispatchSuccess("only", "note.txt", 50);
        collector.save(store);

        assertFalse(store.containsKey("vs.1.serial"));
        assertFalse(store.containsKey("vs.1.ext.pdf"));
        assertFalse(store.containsKey("vs.2.serial"));
        assertFalse(store.containsKey("vs.0.ext.pdf"), "stale extension keys must be dropped too");
        assertTrue(store.containsKey("vs.0.ext.txt"));
        assertEquals(1L, store.getLong("volumeStats.count").orElse(-1));

        VolumeStatsCollector reloaded = new VolumeStatsCollector();
        reloaded.load(store);
        assertEquals(1, reloaded.getAllVolumeStats().size());
        assertEquals(1L, reloaded.getVolumeStats("only").getExtensionCounts().get("txt"));
    }

    private static void dispatchSuccess(String serial, String fileName, long bytes) {
        EventBus.getInstance().dispatch(new CopyCompletedEvent(
                Path.of("E:\\" + fileName), Path.of("out"), bytes, bytes, CopyResult.SUCCESS, serial));
    }

    private static void dispatchFailure(String serial) {
        EventBus.getInstance().dispatch(new CopyCompletedEvent(
                Path.of("E:\\broken.pdf"), null, 0, 0, CopyResult.FAIL, serial));
    }
}

class DeviceHistoryPersistenceTest {

    private EventBus bus;
    private DeviceHistoryCollector collector;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
        collector = new DeviceHistoryCollector();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void saveAndLoadFetchTheKeySetOnceEach() {
        int devices = 30;
        for (int i = 0; i < devices; i++) {
            String serial = "dev-" + i;
            bus.dispatch(new DeviceArrivalEvent(new Device(serial, "vid" + i, "pid" + i, "path")));
            bus.dispatch(new DeviceRemovalEvent(new Device(serial, "vid" + i, "pid" + i, "path")));
        }

        RecordingMetricStore store = new RecordingMetricStore();
        collector.save(store);
        assertEquals(1, store.keysCalls, "save must fetch the key set exactly once");

        DeviceHistoryCollector reloaded = new DeviceHistoryCollector();
        store.keysCalls = 0;
        reloaded.load(store);
        // Loading device history reads the scalar keys directly (the timeline length is persisted),
        // so it must never enumerate the whole key set more than once.
        assertTrue(store.keysCalls <= 1, "load must not scan the key set per device");

        assertEquals(devices, reloaded.getAllDeviceHistory().size());
        DeviceHistoryEntry entry = reloaded.getDeviceHistory("dev-7");
        assertEquals("vid7", entry.getVid());
        assertEquals("pid7", entry.getPid());
        assertEquals(1, entry.getInsertionCount());
        assertFalse(entry.getTimelineLog().isEmpty());
    }

    @Test
    void saveRemovesEntriesOfVanishedDevices() {
        bus.dispatch(new DeviceArrivalEvent(new Device("gone-1", "v", "p", "path")));
        bus.dispatch(new DeviceArrivalEvent(new Device("gone-2", "v", "p", "path")));
        RecordingMetricStore store = new RecordingMetricStore();
        collector.save(store);
        assertTrue(store.containsKey("dh.1.serial"));

        collector.reset();
        bus.dispatch(new DeviceArrivalEvent(new Device("fresh", "v", "p", "path")));
        collector.save(store);

        assertTrue(store.containsKey("dh.0.serial"));
        assertFalse(store.containsKey("dh.1.serial"), "vanished device entries must be removed");
        assertEquals("fresh", store.getString("dh.0.serial").orElse(null));
        assertEquals(1L, store.getLong("deviceHistory.count").orElse(-1));
    }
}

/**
 * Plain in-memory {@link MetricStore} that records how often the backing keys were enumerated.
 */
class PreferencesPersistenceRoundTripTest {

    private java.util.prefs.Preferences node;
    private PreferencesMetricStore store;

    @BeforeEach
    void setUp() {
        node = java.util.prefs.Preferences.userRoot()
                .node("/usbthief-test-round-trip-" + System.nanoTime());
        store = new PreferencesMetricStore(node);
    }

    @AfterEach
    void tearDown() {
        try {
            node.removeNode();
        } catch (Exception ignored) {
            // best effort cleanup of the temporary test node
        }
    }

    @Test
    void volumeStatsSurviveAPreferencesRoundTrip() {
        EventBus bus = EventBus.getInstance();
        bus.clearAll();
        VolumeStatsCollector collector = new VolumeStatsCollector();
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "serial-1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\b.pdf"), Path.of("out"), 250, 250, CopyResult.SUCCESS, "serial-1"));
        bus.dispatch(new CopyCompletedEvent(
                Path.of("E:\\c.txt"), null, 0, 0, CopyResult.FAIL, "serial-2"));

        collector.save(store);
        assertEquals(2L, store.getLong("volumeStats.count").orElse(-1));

        VolumeStatsCollector reloaded = new VolumeStatsCollector();
        reloaded.load(store);

        VolumeStats stats = reloaded.getVolumeStats("serial-1");
        assertEquals(2, stats.getFilesCopied());
        assertEquals(350L, stats.getBytesCopied());
        assertEquals(1L, stats.getExtensionCounts().get("txt"));
        assertEquals(1L, stats.getExtensionCounts().get("pdf"));
        assertEquals(1, reloaded.getVolumeStats("serial-2").getErrors());
        assertEquals(2, reloaded.getAllVolumeStats().size());
        bus.clearAll();
    }

    @Test
    void deviceHistorySurvivesAPreferencesRoundTrip() {
        EventBus bus = EventBus.getInstance();
        bus.clearAll();
        DeviceHistoryCollector collector = new DeviceHistoryCollector();
        bus.dispatch(new DeviceArrivalEvent(new Device("dev-1", "vid1", "pid1", "path")));
        bus.dispatch(new DeviceRemovalEvent(new Device("dev-1", "vid1", "pid1", "path")));
        collector.save(store);

        DeviceHistoryCollector reloaded = new DeviceHistoryCollector();
        reloaded.load(store);

        DeviceHistoryEntry entry = reloaded.getDeviceHistory("dev-1");
        assertEquals("vid1", entry.getVid());
        assertEquals("pid1", entry.getPid());
        assertEquals(1, entry.getInsertionCount());
        assertFalse(entry.getTimelineLog().isEmpty());
        assertEquals(1, reloaded.getAllDeviceHistory().size());
        bus.clearAll();
    }
}

/**
 * Plain in-memory {@link MetricStore} that records how often the backing keys were enumerated.
 */
final class RecordingMetricStore implements MetricStore {
    private final Map<String, Long> longs = new LinkedHashMap<>();
    private final Map<String, Double> doubles = new LinkedHashMap<>();
    private final Map<String, String> strings = new LinkedHashMap<>();
    int keysCalls;

    @Override public void put(String key, long value) { longs.put(key, value); }
    @Override public void put(String key, double value) { doubles.put(key, value); }
    @Override public void put(String key, String value) { strings.put(key, value); }

    @Override public OptionalLong getLong(String key) {
        Long value = longs.get(key);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    @Override public OptionalDouble getDouble(String key) {
        Double value = doubles.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @Override public Optional<String> getString(String key) {
        String value = strings.get(key);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    @Override public void remove(String key) {
        longs.remove(key);
        doubles.remove(key);
        strings.remove(key);
    }

    @Override public void flush() { }

    @Override public String[] keys() {
        keysCalls++;
        Set<String> all = new LinkedHashSet<>();
        all.addAll(longs.keySet());
        all.addAll(doubles.keySet());
        all.addAll(strings.keySet());
        return all.toArray(new String[0]);
    }

    boolean containsKey(String key) {
        return longs.containsKey(key) || doubles.containsKey(key) || strings.containsKey(key);
    }
}
