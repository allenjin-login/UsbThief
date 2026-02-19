package com.superredrock.usbthief.core.event.storage;

/**
 * Represents the current storage level status.
 */
public enum StorageLevel {
    /**
     * Storage is at acceptable levels.
     */
    OK,

    /**
     * Storage is running low but not critical yet.
     */
    LOW,

    /**
     * Storage is critically low and requires immediate action.
     */
    CRITICAL
}
