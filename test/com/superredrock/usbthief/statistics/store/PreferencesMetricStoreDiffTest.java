package com.superredrock.usbthief.statistics.store;

import com.superredrock.usbthief.statistics.collector.MetricWriteBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PF-16 regression: the preferences-backed store must only write entries that actually changed.
 *
 * <p>The store used to be rewritten key by key on every save; with the diff write path an
 * unchanged batch costs a single {@code keys()} fetch and zero {@code Preferences} writes.</p>
 */
class PreferencesMetricStoreDiffTest {

    private Preferences node;
    private PreferencesMetricStore store;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("/usbthief-test-metric-diff-" + System.nanoTime());
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
    void identicalBatchIsNotRewritten() {
        MetricWriteBatch batch = new MetricWriteBatch()
                .putLong("volumeStats.count", 1)
                .putString("vs.0.serial", "serial-a")
                .putLong("vs.0.filesCopied", 5)
                .putLong("vs.0.ext.pdf", 3);

        assertEquals(4, store.apply(batch), "first write persists every entry");
        assertEquals(0, store.apply(batch), "unchanged entries must not be rewritten");
    }

    @Test
    void onlyChangedEntriesAreWritten() {
        store.apply(new MetricWriteBatch()
                .putLong("vs.0.filesCopied", 1)
                .putLong("vs.0.errors", 0)
                .putString("vs.0.serial", "serial-a")
                .putDouble("speed.global", 1.5));

        int applied = store.apply(new MetricWriteBatch()
                .putLong("vs.0.filesCopied", 1)      // unchanged
                .putLong("vs.0.errors", 2)           // changed
                .putString("vs.0.serial", "serial-a") // unchanged
                .putDouble("speed.global", 1.5)      // unchanged
                .putLong("vs.0.bytesCopied", 99));   // new

        assertEquals(2, applied, "only the changed and the new entry may be written");
        assertEquals(2L, store.getLong("vs.0.errors").orElse(-1));
        assertEquals(99L, store.getLong("vs.0.bytesCopied").orElse(-1));
        assertEquals(1L, store.getLong("vs.0.filesCopied").orElse(-1));
        assertEquals("serial-a", store.getString("vs.0.serial").orElse(null));
        assertEquals(1.5, store.getDouble("speed.global").orElse(-1.0));
    }

    @Test
    void removalsOnlyTouchExistingKeys() {
        store.put("keep", 1L);

        int applied = store.apply(new MetricWriteBatch()
                .remove("missing")
                .remove("keep"));

        assertEquals(1, applied, "removing a missing key must not count as a mutation");
        assertFalse(store.keySet().contains("keep"));
        assertTrue(store.keySet().isEmpty());
    }

    @Test
    void keySetReflectsPersistedKeys() {
        store.apply(new MetricWriteBatch()
                .putLong("volumeStats.count", 2)
                .putString("vs.0.serial", "a")
                .putString("vs.1.serial", "b"));

        assertEquals(3, store.keySet().size());
        assertTrue(store.keySet().contains("vs.1.serial"));
    }
}
