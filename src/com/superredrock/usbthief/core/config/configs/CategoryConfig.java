package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.worker.CategoryMode;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.superredrock.usbthief.core.config.ConfigEntry.enumEntry;

/**
 * Configuration of the optional category folders that {@code CopyTask} creates
 * below the volume folder. Mirrors {@link OverwriteConfig}: a single enum entry
 * whose options are the names of {@link CategoryMode}.
 */
public final class CategoryConfig {
    public static final String CATEGORY = "Category Folders";

    public static final ConfigEntry<String> CATEGORY_MODE =
            enumEntry("categoryMode",
                    "Sort copied files into per-type or per-date folders",
                    CategoryMode.OFF.name(),
                    CATEGORY,
                    Arrays.stream(CategoryMode.values())
                            .map(CategoryMode::name)
                            .collect(Collectors.toList()));

    private CategoryConfig() {}
}
