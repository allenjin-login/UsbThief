package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.Event;

import java.nio.file.Path;

/**
 * Event fired when storage recovers from a low state back to acceptable levels.
 * Provides information about the recovered storage status.
 */
public class StorageRecoveredEvent implements Event {

    private final Path workDir;
    private final long freeBytes;
    private final long timestamp;

    public StorageRecoveredEvent(Path workDir, long freeBytes) {
        if (workDir == null) {
            throw new IllegalArgumentException("workDir cannot be null");
        }
        if (freeBytes < 0) {
            throw new IllegalArgumentException("freeBytes cannot be negative");
        }
        this.workDir = workDir;
        this.freeBytes = freeBytes;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the working directory path where storage has recovered
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * @return the current amount of free bytes in storage after recovery
     */
    public long freeBytes() {
        return freeBytes;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("StorageRecoveredEvent: %s (free: %d bytes) at %s",
                workDir, freeBytes, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
