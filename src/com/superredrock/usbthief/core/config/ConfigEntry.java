package com.superredrock.usbthief.core.config;

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

    private ConfigEntry(String key, String description, T defaultValue, ConfigType type, String category) {
        this.key = key;
        this.description = description;
        this.defaultValue = defaultValue;
        this.type = type;
        this.category = category;
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
}
