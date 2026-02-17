package com.superredrock.usbthief.core.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * File filter based on file extension (suffix).
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>NONE</b> - No extension filtering, all files pass</li>
 *   <li><b>WHITELIST</b> - Only files with extensions in the whitelist pass</li>
 *   <li><b>BLACKLIST</b> - Files with extensions in the blacklist are blocked</li>
 * </ul>
 *
 * <p>Extensions are case-insensitive (PDF, PDF, PDF all match).
 * Extensions should be specified without the leading dot.
 *
 * <p>Edge cases:
 * <ul>
 *   <li>Empty whitelist: blocks all files</li>
 *   <li>Files without extension: controlled by FILE_FILTER_ALLOW_NO_EXT config</li>
 * </ul>
 */
public class SuffixFilter implements FileFilter {

    protected static final Logger logger = Logger.getLogger(SuffixFilter.class.getName());

    /** Filter mode constants */
    public static final String MODE_NONE = "NONE";
    public static final String MODE_WHITELIST = "WHITELIST";
    public static final String MODE_BLACKLIST = "BLACKLIST";

    private final ConfigManager configManager;

    /**
     * Creates a new SuffixFilter with the default ConfigManager.
     */
    public SuffixFilter() {
        this.configManager = ConfigManager.getInstance();
    }

    /**
     * Creates a new SuffixFilter with the specified ConfigManager.
     * Useful for testing with mock configurations.
     *
     * @param configManager the configuration manager to use
     */
    public SuffixFilter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean test(Path path, BasicFileAttributes attrs) {
        String mode = getFilterMode();

        // NONE mode: all files pass
        if (MODE_NONE.equalsIgnoreCase(mode)) {
            return true;
        }

        // Get file extension
        String extension = getFileExtension(path);
        boolean hasExtension = extension != null && !extension.isEmpty();

        // Handle files without extension
        if (!hasExtension) {
            boolean allow = shouldAllowNoExtension();
            logger.fine("File has no extension, " + (allow ? "allowing" : "blocking") + ": " + path);
            return allow;
        }

        // Normalize extension to lowercase for comparison
        extension = extension.toLowerCase(Locale.ROOT);

        // Get the effective extension list (from preset or direct config)
        Set<String> extensions = getEffectiveExtensions();

        if (MODE_WHITELIST.equalsIgnoreCase(mode)) {
            // Whitelist mode: extension must be in list
            boolean inList = extensions.contains(extension);
            if (!inList) {
                logger.fine("Extension '" + extension + "' not in whitelist: " + path);
            }
            return inList;
        } else if (MODE_BLACKLIST.equalsIgnoreCase(mode)) {
            // Blacklist mode: extension must NOT be in list
            boolean inList = extensions.contains(extension);
            if (inList) {
                logger.fine("Extension '" + extension + "' is blacklisted: " + path);
                return false;
            }
            return true;
        }

        // Unknown mode: default to pass
        return true;
    }

    /**
     * Extract the file extension from a path.
     *
     * @param path the file path
     * @return the extension without the dot, or empty string if no extension
     */
    protected String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    /**
     * Get the effective set of extensions based on preset or direct configuration.
     *
     * @return a set of lowercase extensions
     */
    protected Set<String> getEffectiveExtensions() {
        Set<String> extensions = new HashSet<>();

        // Check if a preset is selected
        String presetName = getPreset();
        if (presetName != null && !presetName.isEmpty()) {
            try {
                FilterPreset preset = FilterPreset.valueOf(presetName.toUpperCase(Locale.ROOT));
                for (String ext : preset.getExtensions()) {
                    extensions.add(ext.toLowerCase(Locale.ROOT));
                }
                return extensions;
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown preset: " + presetName + ", falling back to direct config");
            }
        }

        // Use direct whitelist or blacklist
        String mode = getFilterMode();
        List<String> configList;
        
        if (MODE_WHITELIST.equalsIgnoreCase(mode)) {
            configList = getWhitelist();
        } else if (MODE_BLACKLIST.equalsIgnoreCase(mode)) {
            configList = getBlacklist();
        } else {
            configList = List.of();
        }

        for (String ext : configList) {
            extensions.add(ext.toLowerCase(Locale.ROOT));
        }

        return extensions;
    }

    /**
     * Get the filter mode from configuration.
     *
     * @return the mode (NONE, WHITELIST, or BLACKLIST)
     */
    protected String getFilterMode() {
        return configManager.get(ConfigSchema.SUFFIX_FILTER_MODE);
    }

    /**
     * Get the whitelist from configuration.
     *
     * @return list of extensions in the whitelist
     */
    protected List<String> getWhitelist() {
        return configManager.get(ConfigSchema.SUFFIX_FILTER_WHITELIST);
    }

    /**
     * Get the blacklist from configuration.
     *
     * @return list of extensions in the blacklist
     */
    protected List<String> getBlacklist() {
        return configManager.get(ConfigSchema.SUFFIX_FILTER_BLACKLIST);
    }

    /**
     * Get the preset name from configuration.
     *
     * @return the preset name, or empty string if none selected
     */
    protected String getPreset() {
        return configManager.get(ConfigSchema.SUFFIX_FILTER_PRESET);
    }

    /**
     * Check if files without extension should be allowed.
     *
     * @return true if files without extension should pass the filter
     */
    protected boolean shouldAllowNoExtension() {
        return configManager.get(ConfigSchema.FILE_FILTER_ALLOW_NO_EXT);
    }
}
