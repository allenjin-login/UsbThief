package com.superredrock.usbthief.gui;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Configuration for language settings.
 * Stores user preferences, custom display names, and priorities.
 */
public class LanguageConfig {

    private static final Logger logger = Logger.getLogger(LanguageConfig.class.getName());
    private static final String CONFIG_FILE = "languages.properties";
    private static final String PRIORITY_SUFFIX = ".priority";
    private static final String DISPLAY_NAME_SUFFIX = ".displayName";
    private static final String NATIVE_NAME_SUFFIX = ".nativeName";
    private static final String HIDDEN_SUFFIX = ".hidden";

    private final Properties config = new Properties();
    private Path configPath;

    public LanguageConfig() {
        this.configPath = determineConfigPath();
        load();
    }

    private Path determineConfigPath() {
        Path dataDir = Paths.get(System.getProperty("user.home"), ".usbthief");
        if (!Files.exists(dataDir)) {
            try {
                Files.createDirectories(dataDir);
            } catch (IOException e) {
                logger.warning("Failed to create data directory: " + e.getMessage());
            }
        }
        return dataDir.resolve(CONFIG_FILE);
    }

    public void load() {
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                config.load(in);
                logger.info("Loaded language config from: " + configPath);
            } catch (IOException e) {
                logger.warning("Failed to load language config: " + e.getMessage());
            }
        }
    }

    public void save() {
        try (OutputStream out = Files.newOutputStream(configPath)) {
            config.store(out, "UsbThief Language Configuration");
            logger.info("Saved language config to: " + configPath);
        } catch (IOException e) {
            logger.warning("Failed to save language config: " + e.getMessage());
        }
    }

    public int getPriority(String localeString) {
        String key = localeString + PRIORITY_SUFFIX;
        return Integer.parseInt(config.getProperty(key, "0"));
    }

    public void setPriority(String localeString, int priority) {
        String key = localeString + PRIORITY_SUFFIX;
        config.setProperty(key, String.valueOf(priority));
    }

    public String getDisplayName(String localeString) {
        String key = localeString + DISPLAY_NAME_SUFFIX;
        return config.getProperty(key);
    }

    public void setDisplayName(String localeString, String displayName) {
        String key = localeString + DISPLAY_NAME_SUFFIX;
        config.setProperty(key, displayName);
    }

    public String getNativeName(String localeString) {
        String key = localeString + NATIVE_NAME_SUFFIX;
        return config.getProperty(key);
    }

    public void setNativeName(String localeString, String nativeName) {
        String key = localeString + NATIVE_NAME_SUFFIX;
        config.setProperty(key, nativeName);
    }

    public boolean isHidden(String localeString) {
        String key = localeString + HIDDEN_SUFFIX;
        return Boolean.parseBoolean(config.getProperty(key, "false"));
    }

    public void setHidden(String localeString, boolean hidden) {
        String key = localeString + HIDDEN_SUFFIX;
        config.setProperty(key, String.valueOf(hidden));
    }

    public String getDefaultLanguage() {
        return config.getProperty("default.language", "");
    }

    public void setDefaultLanguage(String localeString) {
        config.setProperty("default.language", localeString);
    }

    /**
     * Apply configuration to a list of language infos.
     */
    public List<LanguageInfo> applyConfig(List<LanguageInfo> languages) {
        List<LanguageInfo> result = new ArrayList<>();
        for (LanguageInfo lang : languages) {
            String localeStr = lang.localeString();
            if (isHidden(localeStr)) {
                continue;
            }

            int priority = getPriority(localeStr);
            String displayName = getDisplayName(localeStr);
            String nativeName = getNativeName(localeStr);

            LanguageInfo configured = new LanguageInfo(
                    lang.locale(),
                    displayName != null ? displayName : lang.displayName(),
                    nativeName != null ? nativeName : lang.nativeName(),
                    priority > 0 ? priority : lang.priority()
            );
            result.add(configured);
        }

        result.sort(Comparator
                .comparingInt(LanguageInfo::priority).reversed()
                .thenComparing(LanguageInfo::nativeName)
                .thenComparing(LanguageInfo::localeString));

        return result;
    }
}
