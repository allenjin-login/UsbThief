package com.superredrock.usbthief.statistics.collector;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class MetricSnapshot {
    private final String metricId;
    private final long longValue;
    private final double doubleValue;
    private final Map<String, Object> details;

    public MetricSnapshot(String metricId, long longValue, double doubleValue, Map<String, Object> details) {
        this.metricId = metricId;
        this.longValue = longValue;
        this.doubleValue = doubleValue;
        this.details = details != null ? Map.copyOf(details) : Map.of();
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

    public String metricId() { return metricId; }
    public long longValue() { return longValue; }
    public double doubleValue() { return doubleValue; }
    public Map<String, Object> details() { return Collections.unmodifiableMap(details); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricSnapshot)) return false;
        MetricSnapshot that = (MetricSnapshot) o;
        return longValue == that.longValue &&
               Double.compare(that.doubleValue, doubleValue) == 0 &&
               Objects.equals(metricId, that.metricId) &&
               Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricId, longValue, doubleValue, details);
    }

    @Override
    public String toString() {
        return "MetricSnapshot[metricId=" + metricId + ", longValue=" + longValue +
               ", doubleValue=" + doubleValue + ", details=" + details + "]";
    }
}
