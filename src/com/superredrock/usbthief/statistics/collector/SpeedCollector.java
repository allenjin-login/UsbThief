package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.statistics.SpeedProbe;
import com.superredrock.usbthief.statistics.SpeedProbeGroup;

import java.util.Map;

public final class SpeedCollector implements MetricCollector {
    public static final String ID = "speed.global";
    private final SpeedProbeGroup readProbeGroup = new SpeedProbeGroup("copy-read");
    private final SpeedProbeGroup writeProbeGroup = new SpeedProbeGroup("copy-write");

    /**
     * Per-thread probes reused by every copy performed on that worker thread.
     *
     * <p>Copy tasks used to build two probes each and never close them, so a session copying
     * 200k files leaked 400k probes (about 1.1 KB each) and made every group aggregation
     * O(probe count). Probes are now created once per worker thread, stay attached to the group
     * for the lifetime of the thread and are reused across files.</p>
     */
    private final ThreadLocal<SpeedProbe> threadReadProbe =
            ThreadLocal.withInitial(() -> attach(new SpeedProbe("CopyTask-read"), readProbeGroup));
    private final ThreadLocal<SpeedProbe> threadWriteProbe =
            ThreadLocal.withInitial(() -> attach(new SpeedProbe("CopyTask-write"), writeProbeGroup));

    @Override public String getId() { return ID; }
    @Override public boolean isPersistent() { return false; }

    @Override
    public MetricSnapshot snapshot() {
        return new MetricSnapshot(ID, 0L, writeProbeGroup.getTotalSpeed(),
                Map.of(
                    "readSpeed", readProbeGroup.getTotalSpeed(),
                    "writeSpeed", writeProbeGroup.getTotalSpeed(),
                    "totalBytes", writeProbeGroup.getTotalBytes(),
                    "probeCount", writeProbeGroup.getProbeCount()
                ));
    }

    @Override public void load(MetricStore store) {}
    @Override public void save(MetricStore store) {}
    @Override public void reset() {}

    public SpeedProbe createReadProbe(String name) {
        SpeedProbe probe = new SpeedProbe(name);
        readProbeGroup.addProbe(probe);
        return probe;
    }

    public SpeedProbe createWriteProbe(String name) {
        SpeedProbe probe = new SpeedProbe(name);
        writeProbeGroup.addProbe(probe);
        return probe;
    }

    /**
     * Returns the copy-read probe owned by the calling thread, creating and registering it on
     * first use.
     *
     * @return a thread-confined, reusable read probe
     */
    public SpeedProbe getThreadReadProbe() {
        return threadReadProbe.get();
    }

    /**
     * Returns the copy-write probe owned by the calling thread, creating and registering it on
     * first use.
     *
     * @return a thread-confined, reusable write probe
     */
    public SpeedProbe getThreadWriteProbe() {
        return threadWriteProbe.get();
    }

    private static SpeedProbe attach(SpeedProbe probe, SpeedProbeGroup group) {
        group.addProbe(probe);
        return probe;
    }

    public SpeedProbeGroup getReadProbeGroup() { return readProbeGroup; }
    public SpeedProbeGroup getWriteProbeGroup() { return writeProbeGroup; }

    /** @deprecated Use {@link #createWriteProbe(String)} */
    @Deprecated
    public SpeedProbe createProbe(String name) { return createWriteProbe(name); }

    /** @deprecated Use {@link #getWriteProbeGroup()} */
    @Deprecated
    public SpeedProbeGroup getProbeGroup() { return writeProbeGroup; }
}
