package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.worker.OverwriteStrategy;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class OverwriteConfig {
    public static final String CATEGORY = "Overwrite Strategy";

    public static final ConfigEntry<String> OVERWRITE_STRATEGY =
            enumEntry("overwriteStrategy",
                    "Strategy when target file exists",
                    OverwriteStrategy.ALWAYS_OVERWRITE.name(),
                    CATEGORY,
                    Arrays.stream(OverwriteStrategy.values())
                            .map(OverwriteStrategy::name)
                            .collect(Collectors.toList()));

    private OverwriteConfig() {}
}
