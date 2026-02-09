package com.superredrock.usbthief.core.event.index;

import com.superredrock.usbthief.index.CheckSum;

import java.nio.file.Path;

/**
 * Event fired when a file is successfully added to the index.
 */
public class FileIndexedEvent extends IndexEvent {

    private final long fileSize;
    private final int totalIndexed;

    public FileIndexedEvent(CheckSum checksum, Path filePath, long fileSize, int totalIndexed) {
        super(checksum, filePath);
        this.fileSize = fileSize;
        this.totalIndexed = totalIndexed;
    }

    /**
     * @return the size of the indexed file in bytes
     */
    public long fileSize() {
        return fileSize;
    }

    /**
     * @return the total number of files in the index after this addition
     */
    public int totalIndexed() {
        return totalIndexed;
    }

    @Override
    public String description() {
        return String.format("FileIndexedEvent: %s (size: %d bytes, total: %d files) at %s",
            filePath(), fileSize, totalIndexed, timestamp());
    }
}
