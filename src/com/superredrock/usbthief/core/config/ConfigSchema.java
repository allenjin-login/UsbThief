package com.superredrock.usbthief.core.config;

import com.superredrock.usbthief.core.config.configs.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Schema containing all configuration entries for the application.
 * This class acts as a registry for all configuration keys and their metadata.
 */
public class ConfigSchema {
    private static final Map<String, ConfigEntry<?>> ALL_ENTRIES = new ConcurrentHashMap<>();

    static {
        registerClass(ThreadPoolConfig.class);
        registerClass(DeviceScannerConfig.class);
        registerClass(IndexConfig.class);
        registerClass(FileCopyConfig.class);
        registerClass(FileWatchConfig.class);
        registerClass(RateLimitConfig.class);
        registerClass(PathConfig.class);
        registerClass(UIConfig.class);
        registerClass(WindowConfig.class);
        registerClass(BlacklistConfig.class);
        registerClass(FileFilterConfig.class);
        registerClass(SuffixFilterConfig.class);
        registerClass(StorageConfig.class);
        registerClass(StatisticsApiConfig.class);
        registerClass(OverwriteConfig.class);
    }

    private ConfigSchema() {
        // Static utility class
    }

    private static void registerClass(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isFinal(field.getModifiers())
                    && ConfigEntry.class.isAssignableFrom(field.getType())) {
                try {
                    @SuppressWarnings("unchecked")
                    ConfigEntry<?> entry = (ConfigEntry<?>) field.get(null);
                    if (entry != null) {
                        ALL_ENTRIES.put(entry.key(), entry);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to access config entry: " + field.getName(), e);
                }
            }
        }
    }

    /**
     * Get all registered configuration entries.
     */
    public static Map<String, ConfigEntry<?>> getAllEntries() {
        return Map.copyOf(ALL_ENTRIES);
    }

    /**
     * Get all entries grouped by category.
     */
    public static Map<String, List<ConfigEntry<?>>> getEntriesByCategory() {
        return ALL_ENTRIES.values().stream()
                .collect(Collectors.groupingBy(ConfigEntry::category));
    }

    /**
     * Get entry by key.
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigEntry<T> getEntry(String key) {
        return (ConfigEntry<T>) ALL_ENTRIES.get(key);
    }
}
