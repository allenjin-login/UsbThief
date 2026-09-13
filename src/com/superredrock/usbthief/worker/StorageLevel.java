package com.superredrock.usbthief.worker;

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
