package com.superredrock.usbthief.statistics.store;

import com.superredrock.usbthief.statistics.collector.MetricStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
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
}
