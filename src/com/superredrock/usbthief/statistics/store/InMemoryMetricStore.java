package com.superredrock.usbthief.statistics.store;

import com.superredrock.usbthief.statistics.collector.MetricStore;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public final class InMemoryMetricStore implements MetricStore {
    @Override public void put(String key, long value) {}
    @Override public void put(String key, double value) {}
    @Override public void put(String key, String value) {}
    @Override public OptionalLong getLong(String key) { return OptionalLong.empty(); }
    @Override public OptionalDouble getDouble(String key) { return OptionalDouble.empty(); }
    @Override public Optional<String> getString(String key) { return Optional.empty(); }
    @Override public void remove(String key) {}
    @Override public void flush() {}
    @Override public String[] keys() { return new String[0]; }
}
