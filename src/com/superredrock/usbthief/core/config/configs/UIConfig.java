package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class UIConfig {
    public static final String CATEGORY = "UI";

    public static final ConfigEntry<Integer> FILE_HISTORY_MAX_ENTRIES =
            intEntry("fileHistoryMaxEntries", "Maximum number of file history entries to keep in memory", 10000, CATEGORY);

    private UIConfig() {}
}
