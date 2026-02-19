package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.Event;

import java.nio.file.Path;

/**
 * Event fired when storage is low or critically low.
 * Provides information about the current storage status and threshold.
 */
public class StorageLowEvent implements Event {

    private final Path workDir;
    private final long freeBytes;
    private final long thresholdBytes;
    private final StorageLevel level;
    private final long timestamp;

    public StorageLowEvent(Path workDir, long freeBytes, long thresholdBytes, StorageLevel level) {
        if (workDir == null || level == null) {
            throw new IllegalArgumentException("workDir and level cannot be null");
        }
        if (freeBytes < 0 || thresholdBytes < 0) {
            throw new IllegalArgumentException("freeBytes and thresholdBytes cannot be negative");
        }
        this.workDir = workDir;
        this.freeBytes = freeBytes;
        this.thresholdBytes = thresholdBytes;
        this.level = level;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the working directory path where storage is low
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * @return the current amount of free bytes in storage
     */
    public long freeBytes() {
        return freeBytes;
    }

    /**
     * @return the threshold in bytes that triggered this event
     */
    public long thresholdBytes() {
        return thresholdBytes;
    }

    /**
     * @return the storage level (LOW or CRITICAL)
     */
    public StorageLevel level() {
        return level;
    }

    /**
     * @return whether the storage is critically low
     */
    public boolean isCritical() {
        return level == StorageLevel.CRITICAL;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("StorageLowEvent: %s (free: %d bytes, threshold: %d bytes, level: %s) at %s",
                workDir, freeBytes, thresholdBytes, level, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
