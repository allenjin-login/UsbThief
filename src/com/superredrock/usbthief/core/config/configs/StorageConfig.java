package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class StorageConfig {
    public static final String CATEGORY = "Storage Management";

    public static final ConfigEntry<Long> STORAGE_RESERVED_BYTES =
            longEntry("storage.reservedBytes", "Minimum free space to preserve (bytes)", 10L * 1024 * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Long> STORAGE_MAX_BYTES =
            longEntry("storage.maxBytes", "Maximum space for copied files (bytes)", 100L * 1024 * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> SNIFFER_WAIT_NORMAL_MINUTES =
            intEntry("sniffer.waitNormalMinutes", "Wait time after normal completion (minutes)", 30, CATEGORY);

    public static final ConfigEntry<Integer> SNIFFER_WAIT_ERROR_MINUTES =
            intEntry("sniffer.waitErrorMinutes", "Wait time after error (minutes)", 5, CATEGORY);

    public static final ConfigEntry<String> RECYCLER_STRATEGY =
            stringEntry("recycler.strategy", "Recycler strategy: TIME_FIRST, SIZE_FIRST, or AUTO", "AUTO", CATEGORY);

    public static final ConfigEntry<Integer> RECYCLER_PROTECTED_AGE_HOURS =
            intEntry("recycler.protectedAgeHours", "Protect files newer than X hours from deletion", 1, CATEGORY);

    public static final ConfigEntry<Boolean> STORAGE_WARNING_ENABLED =
            booleanEntry("storage.warningEnabled", "Log warning when storage space is critical", true, CATEGORY);

    public static final ConfigEntry<Boolean> STORAGE_ENABLED =
            booleanEntry("storage.enabled", "Enable storage management (monitoring, recycling, space checks)", true, CATEGORY);

    private StorageConfig() {}
}
