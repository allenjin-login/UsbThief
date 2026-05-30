package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class RateLimitConfig {
    public static final String CATEGORY = "Rate Limiting";

    public static final ConfigEntry<Long> COPY_READ_RATE_LIMIT =
            longEntry("copyReadRateLimit", "Read rate limit in bytes per second (0 = no limit)", 0L, CATEGORY);

    public static final ConfigEntry<Long> COPY_WRITE_RATE_LIMIT =
            longEntry("copyWriteRateLimit", "Write rate limit in bytes per second (0 = no limit)", 0L, CATEGORY);

    public static final ConfigEntry<Long> COPY_RATE_BURST_SIZE =
            longEntry("copyRateBurstSize", "Copy rate burst size in bytes", 16L * 1024 * 1024, CATEGORY);

    private RateLimitConfig() {}
}
