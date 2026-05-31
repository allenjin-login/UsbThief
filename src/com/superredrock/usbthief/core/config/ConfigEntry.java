package com.superredrock.usbthief.core.config;

import java.util.List;

/**
 * A single configuration entry with type-safe access.
 *
 * @param <T> the type of the configuration value
 */
public class ConfigEntry<T> {
    private final String key;
    private final String description;
    private final T defaultValue;
    private final ConfigType type;
    private final String category;
    private final List<String> options;

    private ConfigEntry(String key, String description, T defaultValue, ConfigType type, String category, List<String> options) {
        this.key = key;
        this.description = description;
        this.defaultValue = defaultValue;
        this.type = type;
        this.category = category;
        this.options = options;
    }

    private ConfigEntry(String key, String description, T defaultValue, ConfigType type, String category) {
        this(key, description, defaultValue, type, category, null);
    }

    /**
     * Create a new integer configuration entry.
     */
    public static ConfigEntry<Integer> intEntry(String key, String description, int defaultValue, String category) {
        return new ConfigEntry<>(key, description, defaultValue, ConfigType.INT, category);
    }

    /**
     * Create a new long configuration entry.
     */
    public static ConfigEntry<Long> longEntry(String key, String description, long defaultValue, String category) {
        return new ConfigEntry<>(key, description, defaultValue, ConfigType.LONG, category);
    }

    /**
     * Create a new boolean configuration entry.
     */
    public static ConfigEntry<Boolean> booleanEntry(String key, String description, boolean defaultValue, String category) {
        return new ConfigEntry<>(key, description, defaultValue, ConfigType.BOOLEAN, category);
    }

    /**
     * Create a new string configuration entry.
     */
    public static ConfigEntry<String> stringEntry(String key, String description, String defaultValue, String category) {
        return new ConfigEntry<>(key, description, defaultValue, ConfigType.STRING, category);
    }

    /**
     * Create a new string list configuration entry.
     */
    public static ConfigEntry<java.util.List<String>> listEntry(String key, String description, java.util.List<String> defaultValue, String category) {
        return new ConfigEntry<>(key, description, defaultValue, ConfigType.STRING_LIST, category);
    }

    /**
     * Create a new enum configuration entry with a fixed set of options.
     */
    public static ConfigEntry<String> enumEntry(String key, String description, String defaultValue, String category, List<String> options) {
        return new ConfigEntry<>(key, description, defaultValue, ConfigType.ENUM, category, List.copyOf(options));
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public ConfigType type() {
        return type;
    }

    public String category() {
        return category;
    }

    /**
     * Returns the list of valid options for ENUM-type entries, or null for other types.
     */
    public List<String> options() {
        return options;
    }
}
