package com.superredrock.usbthief.core.event.index;

import com.superredrock.usbthief.index.CheckSum;

import java.nio.file.Path;

/**
 * Event fired when a duplicate file is detected.
 * The file was not added to the index because its checksum already exists.
 */
public class DuplicateDetectedEvent extends IndexEvent {

    private final long duplicateCount;

    public DuplicateDetectedEvent(CheckSum checksum, Path filePath, int duplicateCount) {
        super(checksum, filePath);
        this.duplicateCount = duplicateCount;
    }

    /**
     * @return the number of times this checksum has been encountered as a duplicate
     */
    public long duplicateCount() {
        return duplicateCount;
    }

    @Override
    public String description() {
        return String.format("DuplicateDetectedEvent: %s (duplicate #%d) at %s",
            filePath(), duplicateCount, timestamp());
    }
}
