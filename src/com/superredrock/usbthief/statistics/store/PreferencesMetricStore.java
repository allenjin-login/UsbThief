package com.superredrock.usbthief.statistics.store;

import com.superredrock.usbthief.statistics.collector.MetricStore;
import com.superredrock.usbthief.statistics.collector.MetricWriteBatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

public final class PreferencesMetricStore implements MetricStore {
    private static final Logger logger = LogManager.getLogger(PreferencesMetricStore.class);
    private final Preferences prefs;

    public PreferencesMetricStore(Preferences prefs) {
        this.prefs = prefs;
    }

    public PreferencesMetricStore(Class<?> nodeClass) {
        this.prefs = Preferences.userNodeForPackage(nodeClass);
    }

    @Override
    public void put(String key, long value) {
        prefs.putLong(key, value);
    }

    @Override
    public void put(String key, double value) {
        prefs.putDouble(key, value);
    }

    @Override
    public void put(String key, String value) {
        prefs.put(key, value);
    }

    @Override
    public OptionalLong getLong(String key) {
        return OptionalLong.of(prefs.getLong(key, 0));
    }

    @Override
    public OptionalDouble getDouble(String key) {
        return OptionalDouble.of(prefs.getDouble(key, 0.0));
    }

    @Override
    public Optional<String> getString(String key) {
        String val = prefs.get(key, null);
        return val != null ? Optional.of(val) : Optional.empty();
    }

    @Override
    public void remove(String key) {
        prefs.remove(key);
    }

    @Override
    public void flush() {
        try {
            prefs.flush();
        } catch (Exception e) {
            logger.warn("Failed to flush preferences: {}", e.getMessage());
        }
    }

    public String[] keys() {
        try {
            return prefs.keys();
        } catch (Exception e) {
            logger.warn("Failed to get preference keys: {}", e.getMessage());
            return new String[0];
        }
    }

    @Override
    public Set<String> keySet() {
        try {
            String[] stored = prefs.keys();
            Set<String> result = new LinkedHashSet<>(Math.max(16, stored.length * 2));
            result.addAll(Arrays.asList(stored));
            return result;
        } catch (Exception e) {
            logger.warn("Failed to read preference key set: {}", e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    /**
     * Diff write: only entries whose value actually changed are written and only keys that exist
     * are removed.
     *
     * <p>The key set is fetched exactly once per batch, so a save with many volumes no longer
     * triggers one {@code Preferences.keys()} call (and one full key array copy) per volume. Every
     * skipped entry saves the JNI round trip that {@code Preferences.put*} would cost.</p>
     *
     * @return the number of mutations actually handed to the preferences node
     */
    @Override
    public int apply(MetricWriteBatch batch) {
        Set<String> existing = keySet();
        int applied = 0;

        for (String key : batch.removalKeys()) {
            if (existing.contains(key)) {
                prefs.remove(key);
                applied++;
            }
        }

        for (Map.Entry<String, Long> entry : batch.longEntries().entrySet()) {
            String key = entry.getKey();
            long value = entry.getValue().longValue();
            if (existing.contains(key) && prefs.getLong(key, 0L) == value) {
                continue;
            }
            prefs.putLong(key, value);
            applied++;
        }

        for (Map.Entry<String, Double> entry : batch.doubleEntries().entrySet()) {
            String key = entry.getKey();
            double value = entry.getValue().doubleValue();
            if (existing.contains(key) && prefs.getDouble(key, 0.0) == value) {
                continue;
            }
            prefs.putDouble(key, value);
            applied++;
        }

        for (Map.Entry<String, String> entry : batch.stringEntries().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String current = prefs.get(key, null);
            if (existing.contains(key) && current != null && current.equals(value)) {
                continue;
            }
            prefs.put(key, value);
            applied++;
        }

        logger.debug("Applied {} of {} metric mutations ({} keys persisted)",
                applied, batch.size(), existing.size());
        return applied;
    }
}
