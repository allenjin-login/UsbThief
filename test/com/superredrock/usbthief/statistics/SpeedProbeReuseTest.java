package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.statistics.collector.MetricSnapshot;
import com.superredrock.usbthief.statistics.collector.SpeedCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PF-15: probes are reused per thread and the group keeps an O(1) byte total.
 */
@Timeout(10)
class SpeedProbeReuseTest {

    @Test
    void threadProbesAreReusedPerThread() {
        SpeedCollector collector = new SpeedCollector();

        SpeedProbe firstRead = collector.getThreadReadProbe();
        SpeedProbe secondRead = collector.getThreadReadProbe();
        SpeedProbe firstWrite = collector.getThreadWriteProbe();
        SpeedProbe secondWrite = collector.getThreadWriteProbe();

        assertSame(firstRead, secondRead, "read probe must be reused by the same thread");
        assertSame(firstWrite, secondWrite, "write probe must be reused by the same thread");
        assertEquals(1, collector.getReadProbeGroup().getProbeCount());
        assertEquals(1, collector.getWriteProbeGroup().getProbeCount());
    }

    @Test
    void groupTotalBytesAggregatesWithoutScanningProbes() {
        SpeedProbeGroup group = new SpeedProbeGroup("test");
        SpeedProbe first = new SpeedProbe("first");
        SpeedProbe second = new SpeedProbe("second");
        group.addProbe(first);
        group.addProbe(second);

        first.record(100);
        second.record(250);
        assertEquals(350, group.getTotalBytes());

        first.record(50);
        assertEquals(400, group.getTotalBytes());
    }

    @Test
    void closingProbeRemovesItsBytesFromGroupTotal() {
        SpeedProbeGroup group = new SpeedProbeGroup("test");
        SpeedProbe probe = new SpeedProbe("probe");
        group.addProbe(probe);
        probe.record(1_000);
        assertEquals(1_000, group.getTotalBytes());

        probe.close();
        assertEquals(0, group.getTotalBytes());
        assertEquals(0, group.getProbeCount());
    }

    @Test
    void speedCollectorSnapshotReportsReusedProbeBytes() {
        SpeedCollector collector = new SpeedCollector();
        collector.getThreadWriteProbe().record(4_096);

        MetricSnapshot snapshot = collector.snapshot();
        assertEquals(4_096L, snapshot.details().get("totalBytes"));
        assertTrue((Integer) snapshot.details().get("probeCount") >= 1);
    }

    @Test
    void repeatedRecordCallsStayCheap() {
        SpeedProbeGroup group = new SpeedProbeGroup("test");
        SpeedProbe probe = new SpeedProbe("probe");
        group.addProbe(probe);

        long start = System.nanoTime();
        for (int i = 0; i < 200_000; i++) {
            probe.record(1);
            group.getTotalBytes();
        }
        long elapsed = System.nanoTime() - start;
        assertEquals(200_000, group.getTotalBytes());
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(5),
                "recording plus O(1) aggregation should be fast, took "
                        + TimeUnit.NANOSECONDS.toMillis(elapsed) + "ms");
    }
}
