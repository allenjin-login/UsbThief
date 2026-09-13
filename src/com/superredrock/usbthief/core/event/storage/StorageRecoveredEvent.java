package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.AbstractEvent;

import java.nio.file.Path;

/**
 * Event fired when storage recovers from a low state back to acceptable levels.
 * Provides information about the recovered storage status.
 */
public class StorageRecoveredEvent extends AbstractEvent {

    private final Path workDir;
    private final long freeBytes;

    public StorageRecoveredEvent(Path workDir, long freeBytes) {
        if (workDir == null) {
            throw new IllegalArgumentException("workDir cannot be null");
        }
        if (freeBytes < 0) {
            throw new IllegalArgumentException("freeBytes cannot be negative");
        }
        this.workDir = workDir;
        this.freeBytes = freeBytes;
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
    public String description() {
        return String.format("StorageRecoveredEvent: %s (free: %d bytes) at %s",
                workDir, freeBytes, timestamp());
    }

}
