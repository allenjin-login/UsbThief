package com.superredrock.usbthief.core.event.storage;

/**
 * Represents the strategy used for selecting files to recycle.
 */
public enum RecycleStrategy {
    /**
     * Prioritize files by oldest access time first.
     */
    TIME_FIRST,

    /**
     * Prioritize files by largest size first.
     */
    SIZE_FIRST,

    /**
     * Automatically determine the best strategy based on current conditions.
     */
    AUTO
}
