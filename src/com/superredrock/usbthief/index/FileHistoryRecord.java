package com.superredrock.usbthief.index;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Record representing a file copy history entry.
 * Stores information about copied files including path, size, and timestamp.
 */
public record FileHistoryRecord(String fileName, String filePath, long fileSize, String timestamp) implements Serializable {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Create a new record with current timestamp.
     */
    public FileHistoryRecord(String fileName, String filePath, long fileSize) {
        this(fileName, filePath, fileSize, LocalDateTime.now().format(TIME_FORMATTER));
    }

    @Override
    public String toString() {
        return String.format("FileHistoryRecord{fileName='%s', filePath='%s', size=%d, time='%s'}",
                fileName, filePath, fileSize, timestamp);
    }
}
