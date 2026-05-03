package com.superredrock.usbthief.statistics.collector;

public interface MetricCollector {
    String getId();
    MetricSnapshot snapshot();
    boolean isPersistent();
    void load(MetricStore store);
    void save(MetricStore store);
    void reset();
}
