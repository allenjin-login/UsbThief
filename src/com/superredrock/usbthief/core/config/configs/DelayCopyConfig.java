package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

/**
 * Settings of the delayed copy ("wait until the file stops changing") feature.
 *
 * <p>Both entries live on the existing {@code File Copy} page: the preferences tree in
 * {@code ConfigCategories} enumerates categories explicitly, so a category of its own would
 * have to be added to the GUI tree as well. Sharing the page keeps the escape hatch reachable
 * without touching the dialog.</p>
 */
public final class DelayCopyConfig {
    public static final String CATEGORY = FileCopyConfig.CATEGORY;

    /**
     * Master switch for delayed copying.
     *
     * <p>Default {@code true}: copying a file that is still being written produces a truncated
     * destination - a data-corruption level defect - so the safe behaviour has to be the
     * default. Turning it off restores the "copy immediately" behaviour of earlier versions.</p>
     */
    public static final ConfigEntry<Boolean> DELAY_COPY_ENABLED =
            booleanEntry("delayCopy.enabled",
                    "Wait until a file stops changing before copying it",
                    true,
                    CATEGORY);

    /**
     * Quiet period a file has to be unchanged for before it is copied.
     *
     * <p>Clamped to {@code FileStabilityGate.MIN_QUIET_MILLIS}..{@code MAX_QUIET_MILLIS} when
     * read, so a mistyped value cannot stall the copy pipeline for minutes.</p>
     */
    public static final ConfigEntry<Integer> DELAY_COPY_STABLE_MILLIS =
            intEntry("delayCopy.stableMillis",
                    "Time a file must stay unchanged before it is copied (milliseconds)",
                    400,
                    CATEGORY);

    private DelayCopyConfig() {}
}
