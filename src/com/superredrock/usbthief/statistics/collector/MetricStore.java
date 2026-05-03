package com.superredrock.usbthief.statistics.collector;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Optional;

public interface MetricStore {
    void put(String key, long value);
    void put(String key, double value);
    void put(String key, String value);
    OptionalLong getLong(String key);
    OptionalDouble getDouble(String key);
    Optional<String> getString(String key);
    void remove(String key);
    void flush();
    String[] keys();
}
