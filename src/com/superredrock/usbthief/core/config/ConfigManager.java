package com.superredrock.usbthief.core.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

/**
 * Configuration manager with import/export functionality.
 * Uses Preferences API for persistence with exportSubtree/importPreferences.
 */
public class ConfigManager {
    private final Preferences prefs;

    private ConfigManager(Preferences prefs) {
        this.prefs = prefs;
    }

    private static ConfigManager create() {
        return new ConfigManager(Preferences.userNodeForPackage(ConfigManager.class));
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ConfigManager INSTANCE = create();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ConfigEntry<T> entry) {
        return (T) entry.type().get(prefs, entry.key(), entry.defaultValue());
    }

    public <T> void set(ConfigEntry<T> entry, T value) {
        entry.type().put(prefs, entry.key(), value);
    }

    public boolean isDeviceBlacklistedBySerial(String serialNumber) {
        if (serialNumber == null || serialNumber.isEmpty()) {
            return false;
        }
        return get(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL).contains(serialNumber);
    }

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

    public void setDeviceBlacklistBySerial(List<String> blacklist) {
        set(ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL, blacklist != null ? blacklist : new java.util.ArrayList<>());
    }

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
     * Export configuration to an XML file using Preferences.exportSubtree().
     *
     * @param path the file path to export to
     * @throws IOException if writing fails
     */
    public void exportToXml(Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            prefs.exportSubtree(out);
        } catch (BackingStoreException e) {
            throw new IOException("Failed to export configuration", e);
        }
    }

    /**
     * Import configuration from an XML file using Preferences.importPreferences().
     *
     * @param path the file path to import from
     * @throws IOException if reading fails
     */
    public void importFromXml(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Preferences.importPreferences(in);
        } catch (InvalidPreferencesFormatException e) {
            throw new IOException("Failed to import configuration: invalid format", e);
        }
    }

    /**
     * Reset all configuration to default values.
     */
    public void resetToDefaults() {
        try {
            prefs.clear();
            for (String childName : prefs.childrenNames()) {
                prefs.node(childName).removeNode();
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
}
