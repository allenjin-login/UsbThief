package com.superredrock.usbthief.statistics.collector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A typed, in-memory set of metric mutations that can be handed to a {@link MetricStore} in one
 * pass.
 *
 * <p>Collectors build the complete desired state of their keys in a batch and let the store decide
 * what actually has to hit the backing store. {@link com.superredrock.usbthief.statistics.store.PreferencesMetricStore}
 * uses this to write only changed entries instead of deleting and rewriting every key.</p>
 *
 * <p>Keys are only ever present in one of the maps; a later {@code put} of the same key overrides
 * an earlier {@code remove} and vice versa.</p>
 */
public final class MetricWriteBatch {

    private final Map<String, String> stringValues = new LinkedHashMap<>();
    private final Map<String, Long> longValues = new LinkedHashMap<>();
    private final Map<String, Double> doubleValues = new LinkedHashMap<>();
    private final Set<String> removals = new LinkedHashSet<>();

    public MetricWriteBatch putString(String key, String value) {
        removals.remove(key);
        stringValues.put(key, value);
        return this;
    }

    public MetricWriteBatch putLong(String key, long value) {
        removals.remove(key);
        longValues.put(key, value);
        return this;
    }

    public MetricWriteBatch putDouble(String key, double value) {
        removals.remove(key);
        doubleValues.put(key, value);
        return this;
    }

    public MetricWriteBatch remove(String key) {
        stringValues.remove(key);
        longValues.remove(key);
        doubleValues.remove(key);
        removals.add(key);
        return this;
    }

    /**
     * @return the number of mutation instructions in this batch
     */
    public int size() {
        return removals.size() + stringValues.size() + longValues.size() + doubleValues.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Set<String> removalKeys() {
        return Collections.unmodifiableSet(removals);
    }

    public Map<String, String> stringEntries() {
        return Collections.unmodifiableMap(stringValues);
    }

    public Map<String, Long> longEntries() {
        return Collections.unmodifiableMap(longValues);
    }

    public Map<String, Double> doubleEntries() {
        return Collections.unmodifiableMap(doubleValues);
    }

    /**
     * Applies every mutation to a store that has no batching support of its own.
     *
     * @return the number of mutations applied
     */
    public int applyTo(MetricStore store) {
        int applied = 0;
        for (String key : removals) {
            store.remove(key);
            applied++;
        }
        for (Map.Entry<String, String> entry : stringValues.entrySet()) {
            store.put(entry.getKey(), entry.getValue());
            applied++;
        }
        for (Map.Entry<String, Long> entry : longValues.entrySet()) {
            store.put(entry.getKey(), entry.getValue().longValue());
            applied++;
        }
        for (Map.Entry<String, Double> entry : doubleValues.entrySet()) {
            store.put(entry.getKey(), entry.getValue().doubleValue());
            applied++;
        }
        return applied;
    }
}
