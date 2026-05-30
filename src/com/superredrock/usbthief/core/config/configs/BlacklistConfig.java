package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

import java.util.List;

public final class BlacklistConfig {
    public static final String CATEGORY = "Blacklist";

    public static final ConfigEntry<List<String>> DEVICE_BLACKLIST =
            listEntry("deviceBlacklist", "Device blacklist by path (deprecated, use deviceBlacklistBySerial)", List.of(), CATEGORY);

    public static final ConfigEntry<List<String>> DEVICE_BLACKLIST_BY_SERIAL =
            listEntry("deviceBlacklistBySerial", "Device blacklist by serial number", List.of(), CATEGORY);

    private BlacklistConfig() {}
}
