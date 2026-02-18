package com.superredrock.usbthief.core.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Basic file filter that applies size, time, hidden, and symlink filters.
 *
 * <p>This filter checks files against multiple criteria:
 * <ul>
 *   <li>Maximum file size (configurable via FILE_FILTER_MAX_SIZE)</li>
 *   <li>Time filter - only files modified within last N time units (configurable)</li>
 *   <li>Hidden files - can be included or excluded (configurable)</li>
 *   <li>Symbolic links - can be skipped or followed (configurable)</li>
 * </ul>
 *
 * <p>Time units supported: HOURS, DAYS, WEEKS, MONTHS, YEARS
 *
 * <p>All configuration is read from ConfigManager, allowing runtime changes
 * without restarting the application.
 */
public class BasicFileFilter implements FileFilter {

    protected static final Logger logger = Logger.getLogger(BasicFileFilter.class.getName());

    /** Supported time unit constants */
    public static final String UNIT_HOURS = "HOURS";
    public static final String UNIT_DAYS = "DAYS";
    public static final String UNIT_WEEKS = "WEEKS";
    public static final String UNIT_MONTHS = "MONTHS";
    public static final String UNIT_YEARS = "YEARS";

    private final ConfigManager configManager;

    /**
     * Creates a new BasicFileFilter with the default ConfigManager.
     */
    public BasicFileFilter() {
        this.configManager = ConfigManager.getInstance();
    }

    /**
     * Creates a new BasicFileFilter with the specified ConfigManager.
     * Useful for testing with mock configurations.
     *
     * @param configManager the configuration manager to use
     */
    public BasicFileFilter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean test(Path path, BasicFileAttributes attrs) {
        // Check if it's a regular file (not a directory)
        if (!attrs.isRegularFile()) {
            return false;
        }

        try {
            // Check symbolic links
            if (shouldSkipSymlinks() && Files.isSymbolicLink(path)) {
                logger.fine("Skipping symbolic link: " + path);
                return false;
            }

            // Check hidden files
            if (!shouldIncludeHidden() && Files.isHidden(path)) {
                logger.fine("Skipping hidden file: " + path);
                return false;
            }

            // Check if file is readable
            if (!Files.isReadable(path)) {
                logger.fine("Skipping unreadable file: " + path);
                return false;
            }
        } catch (IOException e) {
            logger.fine("IO error checking file attributes: " + path + " - " + e.getMessage());
            return false;
        }

        // Check file size
        long maxSize = getMaxFileSize();
        long fileSize = attrs.size();
        if (fileSize > maxSize) {
            logger.fine("File exceeds max size (" + fileSize + " > " + maxSize + "): " + path);
            return false;
        }

        // Check time filter
        if (isTimeFilterEnabled()) {
            Instant cutoffTime = calculateCutoffTime();
            Instant lastModified = attrs.lastModifiedTime().toInstant();

            if (lastModified.isBefore(cutoffTime)) {
                logger.fine("File is too old (modified " + lastModified + "): " + path);
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate the cutoff time based on configuration.
     *
     * @return the cutoff instant (files modified before this are filtered out)
     */
    protected Instant calculateCutoffTime() {
        long value = getTimeFilterValue();
        String unit = getTimeFilterUnit().toUpperCase(Locale.ROOT);

        // Use ZonedDateTime for variable-length units (weeks, months, years)
        ZonedDateTime now = ZonedDateTime.now();

        return switch (unit) {
            case UNIT_DAYS -> Instant.now().minus(value, ChronoUnit.DAYS);
            case UNIT_WEEKS -> now.minusWeeks(value).toInstant();
            case UNIT_MONTHS -> now.minusMonths(value).toInstant();
            case UNIT_YEARS -> now.minusYears(value).toInstant();
            default -> Instant.now().minus(value, ChronoUnit.HOURS);
        };
    }

    /**
     * Get the maximum file size from configuration.
     *
     * @return the maximum file size in bytes
     */
    protected long getMaxFileSize() {
        return configManager.get(ConfigSchema.FILE_FILTER_MAX_SIZE);
    }

    /**
     * Check if time-based filtering is enabled.
     *
     * @return true if time filter should be applied
     */
    protected boolean isTimeFilterEnabled() {
        return configManager.get(ConfigSchema.FILE_FILTER_TIME_ENABLED);
    }

    /**
     * Get the time filter value from configuration.
     *
     * @return the numeric value for the time filter
     */
    protected long getTimeFilterValue() {
        return configManager.get(ConfigSchema.FILE_FILTER_TIME_VALUE);
    }

    /**
     * Get the time filter unit from configuration.
     *
     * @return the time unit (HOURS, DAYS, WEEKS, MONTHS, or YEARS)
     */
    protected String getTimeFilterUnit() {
        return configManager.get(ConfigSchema.FILE_FILTER_TIME_UNIT);
    }

    /**
     * Check if hidden files should be included.
     *
     * @return true if hidden files should be processed
     */
    protected boolean shouldIncludeHidden() {
        return configManager.get(ConfigSchema.FILE_FILTER_INCLUDE_HIDDEN);
    }

    /**
     * Check if symbolic links should be skipped.
     *
     * @return true if symlinks should be skipped
     */
    protected boolean shouldSkipSymlinks() {
        return configManager.get(ConfigSchema.FILE_FILTER_SKIP_SYMLINKS);
    }
}
