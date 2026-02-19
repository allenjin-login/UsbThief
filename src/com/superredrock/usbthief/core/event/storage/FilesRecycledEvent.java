package com.superredrock.usbthief.core.event.storage;

import com.superredrock.usbthief.core.event.Event;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Event fired when files are recycled from storage.
 * Provides information about which files were deleted and how much space was freed.
 */
public class FilesRecycledEvent implements Event {

    private final List<Path> files;
    private final long bytesFreed;
    private final RecycleStrategy strategy;
    private final long timestamp;

    public FilesRecycledEvent(List<Path> files, long bytesFreed, RecycleStrategy strategy) {
        if (files == null || strategy == null) {
            throw new IllegalArgumentException("files and strategy cannot be null");
        }
        if (bytesFreed < 0) {
            throw new IllegalArgumentException("bytesFreed cannot be negative");
        }
        // Create immutable copy to prevent external modification
        this.files = Collections.unmodifiableList(files);
        this.bytesFreed = bytesFreed;
        this.strategy = strategy;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the list of file paths that were recycled (immutable)
     */
    public List<Path> files() {
        return files;
    }

    /**
     * @return the number of files that were recycled
     */
    public int fileCount() {
        return files.size();
    }

    /**
     * @return the total amount of bytes freed by recycling
     */
    public long bytesFreed() {
        return bytesFreed;
    }

    /**
     * @return the strategy used to select files for recycling
     */
    public RecycleStrategy strategy() {
        return strategy;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("FilesRecycledEvent: %d files (strategy: %s, freed: %d bytes) at %s",
                files.size(), strategy, bytesFreed, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
