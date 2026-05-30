package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileFilterConfig {
    public static final String CATEGORY = "File Filter";

    public static final ConfigEntry<Long> FILE_FILTER_MAX_SIZE =
            longEntry("fileFilter.maxSize", "Maximum file size to copy (bytes)", 100L * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_MAX_SIZE_ENABLED =
            booleanEntry("fileFilter.maxSizeEnabled", "Enable maximum file size filter", true, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_TIME_ENABLED =
            booleanEntry("fileFilter.timeEnabled", "Enable time-based file filtering", false, CATEGORY);

    public static final ConfigEntry<Long> FILE_FILTER_TIME_VALUE =
            longEntry("fileFilter.timeValue", "Time filter value (combined with timeUnit)", 24L, CATEGORY);

    public static final ConfigEntry<String> FILE_FILTER_TIME_UNIT =
            stringEntry("fileFilter.timeUnit", "Time filter unit: HOURS, DAYS, WEEKS, MONTHS, YEARS", "HOURS", CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_INCLUDE_HIDDEN =
            booleanEntry("fileFilter.includeHidden", "Include hidden files in copy", false, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_SKIP_SYMLINKS =
            booleanEntry("fileFilter.skipSymlinks", "Skip symbolic links during copy", true, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_ALLOW_NO_EXT =
            booleanEntry("fileFilter.allowNoExtension", "Allow files without extension", true, CATEGORY);

    private FileFilterConfig() {}
}
