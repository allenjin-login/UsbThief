package com.superredrock.usbthief.statistics.collector;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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

    /**
     * Returns the persisted keys as a set, fetched once.
     *
     * <p>Collectors use this instead of calling {@link #keys()} per entry; backing stores that can
     * do better than {@code Preferences.keys()} may override it.</p>
     */
    default Set<String> keySet() {
        String[] stored = keys();
        Set<String> result = new LinkedHashSet<>(Math.max(16, stored.length * 2));
        Collections.addAll(result, stored);
        return result;
    }

    /**
     * Applies a batch of typed mutations.
     *
     * <p>Stores without diff support apply everything; stores with diff support (such as
     * {@link com.superredrock.usbthief.statistics.store.PreferencesMetricStore}) skip writes whose
     * value is already persisted and removals of keys that do not exist.</p>
     *
     * @return the number of mutations actually written to the backing store
     */
    default int apply(MetricWriteBatch batch) {
        return batch.applyTo(this);
    }
}
