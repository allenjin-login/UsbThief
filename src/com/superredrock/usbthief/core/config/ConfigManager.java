package com.superredrock.usbthief.core.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.prefs.BackingStoreException;

/**
 * Configuration manager with import/export functionality.
 * Provides methods to load, save, import, and export configuration.
 */
public class ConfigManager {
    private final java.util.prefs.Preferences prefs;

    private ConfigManager(java.util.prefs.Preferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Create a ConfigManager for the default package.
     */
    private static ConfigManager create() {
        return new ConfigManager(java.util.prefs.Preferences.userNodeForPackage(ConfigManager.class));
    }

    /**
     * Get the singleton instance of ConfigManager.
     */
    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ConfigManager INSTANCE = create();
    }

    /**
     * Get configuration value by entry.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ConfigEntry<T> entry) {
        return (T) entry.type().get(prefs, entry.key(), entry.defaultValue());
    }

    /**
     * Set configuration value by entry.
     */
    public <T> void set(ConfigEntry<T> entry, T value) {
        entry.type().put(prefs, entry.key(), value);
    }

    /**
     * Check if a device is blacklisted by serial number.
     *
     * @param serialNumber the device serial number to check
     * @return true if blacklisted, false otherwise
     */
    public boolean isDeviceBlacklistedBySerial(String serialNumber) {
        if (serialNumber == null || serialNumber.isEmpty()) {
            return false;
        }
        return get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL).contains(serialNumber);
    }

    /**
     * Add a device serial number to the blacklist.
     *
     * @param serialNumber the device serial number to add
     */
    public void addToDeviceBlacklistBySerial(String serialNumber) {
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            return;
        }
        List<String> blacklist = new java.util.ArrayList<>(get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL));
        if (!blacklist.contains(serialNumber.trim())) {
            blacklist.add(serialNumber.trim());
            set(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL, blacklist);
        }
    }

    /**
     * Set the device blacklist by serial number.
     *
     * @param blacklist the list of serial numbers to set as blacklist
     */
    public void setDeviceBlacklistBySerial(List<String> blacklist) {
        set(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL, blacklist != null ? blacklist : new java.util.ArrayList<>());
    }

    /**
     * Remove a device serial number from the blacklist.
     *
     * @param serialNumber the device serial number to remove
     */
    public void removeFromDeviceBlacklistBySerial(String serialNumber) {
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            return;
        }
        List<String> blacklist = new java.util.ArrayList<>(get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL));
        if (blacklist.remove(serialNumber.trim())) {
            set(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL, blacklist);
        }
    }

    /**
     * Export configuration to a properties file.
     *
     * @param path the file path to export to
     * @throws IOException if writing fails
     */
    public void exportToProperties(Path path) throws IOException {
        Properties props = new Properties();

        for (ConfigEntry<?> entry : ConfigSchema.getAllEntries().values()) {
            Object value = entry.type().get(prefs, entry.key(), entry.defaultValue());
            String strValue = toStringValue(value);

            // Store with category prefix for better organization
            String key = entry.category() + "." + entry.key();
            props.setProperty(key, strValue);

            // Also store description as a comment
            props.setProperty(key + ".description", entry.description());
        }

        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "UsbThief Configuration Export");
        }
    }

    /**
     * Export configuration to a JSON file.
     *
     * @param path the file path to export to
     * @throws IOException if writing fails
     */
    public void exportToJson(Path path) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": \"1.0\",\n");
        json.append("  \"exportDate\": \"").append(java.time.Instant.now()).append("\",\n");

        // Group by category
        var byCategory = ConfigSchema.getEntriesByCategory();

        json.append("  \"categories\": {\n");
        boolean firstCategory = true;
        for (var categoryEntry : byCategory.entrySet()) {
            if (!firstCategory) {
                json.append(",\n");
            }
            firstCategory = false;

            json.append("    \"").append(categoryEntry.getKey()).append("\": {\n");

            boolean firstEntry = true;
            for (ConfigEntry<?> entry : categoryEntry.getValue()) {
                if (!firstEntry) {
                    json.append(",\n");
                }
                firstEntry = false;

                Object value = entry.type().get(prefs, entry.key(), entry.defaultValue());
                String strValue = toStringValue(value);

                json.append("      \"").append(entry.key()).append("\": {\n");
                json.append("        \"description\": \"").append(escapeJson(entry.description())).append("\",\n");
                json.append("        \"value\": ").append(formatJsonValue(strValue, entry.type())).append(",\n");
                json.append("        \"default\": ").append(formatJsonValue(toStringValue(entry.defaultValue()), entry.type())).append("\n");
                json.append("      }");
            }

            json.append("\n    }");
        }
        json.append("\n  }\n");
        json.append("}");

        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Import configuration from a properties file.
     * Only overwrites keys that exist in the file.
     *
     * @param path the file path to import from
     * @throws IOException if reading fails
     */
    public void importFromProperties(Path path) throws IOException {
        Properties props = new Properties();

        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }

        for (String key : props.stringPropertyNames()) {
            // Skip description entries
            if (key.endsWith(".description")) {
                continue;
            }

            // Parse category.key format
            int dotIndex = key.indexOf('.');
            if (dotIndex == -1) {
                continue; // Invalid format
            }

            String configKey = key.substring(dotIndex + 1);
            String value = props.getProperty(key);

            ConfigEntry<?> entry = ConfigSchema.getEntry(configKey);
            if (entry != null) {
                try {
                    Object parsedValue = parseValue(value, entry.type(), entry.defaultValue());
                    entry.type().put(prefs, entry.key(), parsedValue);
                } catch (Exception e) {
                    // Log error but continue with other entries
                    System.err.println("Failed to import " + configKey + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Import configuration from a JSON file.
     *
     * @param path the file path to import from
     * @throws IOException if reading fails
     */
    public void importFromJson(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);

        // Simple JSON parsing (without external libraries)
        // This is a basic implementation that extracts key-value pairs

        for (var entry : ConfigSchema.getAllEntries().values()) {
            // Look for "key": { "value": ... } pattern
            String pattern = "\"" + entry.key() + "\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*([^,}]+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(content);

            if (matcher.find()) {
                String valueStr = matcher.group(1).trim();
                try {
                    Object parsedValue = parseValue(valueStr, entry.type(), entry.defaultValue());
                    entry.type().put(prefs, entry.key(), parsedValue);
                } catch (Exception e) {
                    System.err.println("Failed to import " + entry.key() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Reset all configuration to default values.
     */
    public void resetToDefaults() {
        try {
            for (String key : prefs.keys()) {
                prefs.remove(key);
            }
        } catch (BackingStoreException e) {
            throw new RuntimeException("Failed to reset configuration", e);
        }
    }

    /**
     * Clear a specific configuration entry.
     */
    public <T> void clear(ConfigEntry<T> entry) {
        prefs.remove(entry.key());
    }

    // Private helper methods

    private String toStringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List) {
            return String.join(";", (List<String>) value);
        }
        return value.toString();
    }

    private String formatJsonValue(String strValue, ConfigType type) {
        return switch (type) {
            case STRING, STRING_LIST -> "\"" + escapeJson(strValue) + "\"";
            case BOOLEAN -> strValue;
            default -> strValue;
        };
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private Object parseValue(String value, ConfigType type, Object defaultValue) {
        try {
            return switch (type) {
                case INT -> Integer.parseInt(value);
                case LONG -> Long.parseLong(value);
                case BOOLEAN -> Boolean.parseBoolean(value);
                case STRING, STRING_LIST -> value;
                default -> throw new IllegalArgumentException("Unknown type: " + type);
            };
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
