package com.superredrock.usbthief.core.event.index;

import com.superredrock.usbthief.core.event.Event;
import com.superredrock.usbthief.index.CheckSum;

import java.nio.file.Path;

/**
 * Base class for all index-related events.
 * Provides access to checksum and file path information.
 */
public abstract class IndexEvent implements Event {

    private final CheckSum checksum;
    private final Path filePath;
    private final long timestamp;

    protected IndexEvent(CheckSum checksum, Path filePath) {
        this.checksum = checksum;
        this.filePath = filePath;
        this.timestamp = System.currentTimeMillis();
    }

    protected IndexEvent(Path filePath) {
        this(null, filePath);
    }

    /**
     * @return the checksum associated with this event (maybe null)
     */
    public CheckSum checksum() {
        return checksum;
    }

    /**
     * @return the file path associated with this event
     */
    public Path filePath() {
        return filePath;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("%s: %s at %s",
            getClass().getSimpleName(),
            filePath != null ? filePath : "(no file)",
            timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
