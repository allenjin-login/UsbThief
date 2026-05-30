package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.statistics.SpeedProbe;
import com.superredrock.usbthief.statistics.SpeedProbeGroup;

import java.util.Map;

public final class SpeedCollector implements MetricCollector {
    public static final String ID = "speed.global";
    private final SpeedProbeGroup readProbeGroup = new SpeedProbeGroup("copy-read");
    private final SpeedProbeGroup writeProbeGroup = new SpeedProbeGroup("copy-write");

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

    public SpeedProbeGroup getReadProbeGroup() { return readProbeGroup; }
    public SpeedProbeGroup getWriteProbeGroup() { return writeProbeGroup; }

    /** @deprecated Use {@link #createWriteProbe(String)} */
    @Deprecated
    public SpeedProbe createProbe(String name) { return createWriteProbe(name); }

    /** @deprecated Use {@link #getWriteProbeGroup()} */
    @Deprecated
    public SpeedProbeGroup getProbeGroup() { return writeProbeGroup; }
}
