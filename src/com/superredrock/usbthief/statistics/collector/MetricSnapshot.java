package com.superredrock.usbthief.statistics.collector;

import java.util.Collections;
import java.util.Map;

public record MetricSnapshot(
    String metricId,
    long longValue,
    double doubleValue,
    Map<String, Object> details
) {
    public MetricSnapshot {
        details = details != null ? Map.copyOf(details) : Map.of();
    }

    public MetricSnapshot(String metricId, long longValue) {
        this(metricId, longValue, 0.0, Map.of());
    }

    public MetricSnapshot(String metricId, double doubleValue) {
        this(metricId, 0L, doubleValue, Map.of());
    }

    public MetricSnapshot(String metricId, long longValue, double doubleValue) {
        this(metricId, longValue, doubleValue, Map.of());
    }

    public Map<String, Object> details() {
        return Collections.unmodifiableMap(details);
    }
}
