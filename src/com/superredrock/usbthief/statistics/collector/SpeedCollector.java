package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.statistics.SpeedProbe;
import com.superredrock.usbthief.statistics.SpeedProbeGroup;

import java.util.Map;

public final class SpeedCollector implements MetricCollector {
    public static final String ID = "speed.global";
    private final SpeedProbeGroup probeGroup = new SpeedProbeGroup("copy-tasks");

    @Override public String getId() { return ID; }
    @Override public boolean isPersistent() { return false; }

    @Override
    public MetricSnapshot snapshot() {
        return new MetricSnapshot(ID, 0L, probeGroup.getTotalSpeed(),
                Map.of(
                    "currentSpeed", probeGroup.getTotalSpeed(),
                    "totalBytes", probeGroup.getTotalBytes(),
                    "probeCount", probeGroup.getProbeCount()
                ));
    }

    @Override public void load(MetricStore store) {}
    @Override public void save(MetricStore store) {}
    @Override public void reset() {}

    public SpeedProbe createProbe(String name) {
        SpeedProbe probe = new SpeedProbe(name);
        probeGroup.addProbe(probe);
        return probe;
    }

    public SpeedProbeGroup getProbeGroup() {
        return probeGroup;
    }
}
