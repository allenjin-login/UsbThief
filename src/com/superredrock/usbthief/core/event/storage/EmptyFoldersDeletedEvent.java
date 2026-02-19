package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.Event;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Event fired when empty folders are deleted during storage cleanup.
 * Provides information about which folders were removed.
 */
public class EmptyFoldersDeletedEvent implements Event {

    private final List<Path> folders;
    private final int count;
    private final long timestamp;

    public EmptyFoldersDeletedEvent(List<Path> folders, int count) {
        if (folders == null) {
            throw new IllegalArgumentException("folders cannot be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        if (count != folders.size()) {
            throw new IllegalArgumentException("count must match the size of the folders list");
        }
        // Create immutable copy to prevent external modification
        this.folders = Collections.unmodifiableList(folders);
        this.count = count;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the list of folder paths that were deleted (immutable)
     */
    public List<Path> folders() {
        return folders;
    }

    /**
     * @return the number of folders that were deleted
     */
    public int count() {
        return count;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("EmptyFoldersDeletedEvent: %d folders deleted at %s", count, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
