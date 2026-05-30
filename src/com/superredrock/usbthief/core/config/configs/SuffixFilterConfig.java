package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

import java.util.List;

public final class SuffixFilterConfig {
    public static final String CATEGORY = "Suffix Filter";

    public static final ConfigEntry<String> SUFFIX_FILTER_MODE =
            stringEntry("suffixFilter.mode", "Suffix filter mode: NONE, WHITELIST, or BLACKLIST", "NONE", CATEGORY);

    public static final ConfigEntry<List<String>> SUFFIX_FILTER_WHITELIST =
            listEntry("suffixFilter.whitelist", "Whitelist of file extensions (without dot)", List.of(), CATEGORY);

    public static final ConfigEntry<List<String>> SUFFIX_FILTER_BLACKLIST =
            listEntry("suffixFilter.blacklist", "Blacklist of file extensions (without dot)", List.of(), CATEGORY);

    public static final ConfigEntry<String> SUFFIX_FILTER_PRESET =
            stringEntry("suffixFilter.preset", "Selected preset name (empty string for none)", "", CATEGORY);

    private SuffixFilterConfig() {}
}
