package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileWatchConfig {
    public static final String CATEGORY = "File Watch";

    public static final ConfigEntry<Boolean> WATCH_ENABLED =
            booleanEntry("watchEnabled", "Enable/disable real-time file monitoring", false, CATEGORY);

    public static final ConfigEntry<Integer> WATCH_THRESHOLD =
            intEntry("watchThreshold", "Number of file changes before triggering copy", 10, CATEGORY);

    public static final ConfigEntry<Integer> WATCH_RESET_INTERVAL_SECONDS =
            intEntry("watchResetIntervalSeconds", "Interval to reset change counter (seconds)", 60, CATEGORY);

    private FileWatchConfig() {}
}
