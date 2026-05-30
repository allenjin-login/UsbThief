package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class PathConfig {
    public static final String CATEGORY = "Paths";

    public static final ConfigEntry<String> WORK_PATH =
            stringEntry("workPath", "Working directory for storing copied files", "devices", CATEGORY);

    private PathConfig() {}
}
