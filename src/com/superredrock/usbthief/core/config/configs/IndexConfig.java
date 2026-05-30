package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class IndexConfig {
    public static final String CATEGORY = "Index Management";

    public static final ConfigEntry<Integer> INDEX_CACHE_SIZE =
            intEntry("indexCacheSize", "Maximum number of entries in the in-memory index cache", 2000, CATEGORY);

    private IndexConfig() {}
}
