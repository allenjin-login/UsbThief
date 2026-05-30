package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class DeviceScannerConfig {
    public static final String CATEGORY = "Device Scanner";

    public static final ConfigEntry<Integer> INITIAL_DELAY_SECONDS =
            intEntry("initialDelaySeconds", "Initial delay before first device scan (seconds)", 10, CATEGORY);

    public static final ConfigEntry<Integer> DELAY_SECONDS =
            intEntry("delaySeconds", "Interval between device scans (seconds)", 500, CATEGORY);

    private DeviceScannerConfig() {}
}
