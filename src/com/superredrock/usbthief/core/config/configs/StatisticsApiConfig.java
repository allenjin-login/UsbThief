package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class StatisticsApiConfig {
    public static final String CATEGORY = "Statistics API";

    public static final ConfigEntry<Boolean> STATS_API_ENABLED =
            booleanEntry("stats.api.enabled", "Enable/disable HTTP API for statistics", false, CATEGORY);

    public static final ConfigEntry<Integer> STATS_API_PORT =
            intEntry("stats.api.port", "HTTP API port number", 8421, CATEGORY);

    private StatisticsApiConfig() {}
}
