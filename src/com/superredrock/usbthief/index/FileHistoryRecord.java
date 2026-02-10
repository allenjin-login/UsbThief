package com.superredrock.usbthief.index;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Record representing a failed file copy entry.
 * Stores information about failed file copies including path, size, bytes copied, and timestamp.
 */
public record FileHistoryRecord(String fileName, String sourcePath, String destPath, long fileSize,
                                long bytesCopied, String timestamp) implements Serializable {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Create a new record with current timestamp.
     */
    public FileHistoryRecord(String fileName, String sourcePath, String destPath, long fileSize, long bytesCopied) {
        this(fileName, sourcePath, destPath, fileSize, bytesCopied, LocalDateTime.now().format(TIME_FORMATTER));
    }

    @Override
    public String toString() {
        return String.format("FileHistoryRecord{fileName='%s', source='%s', dest='%s', size=%d, copied=%d, time='%s'}",
                fileName, sourcePath, destPath, fileSize, bytesCopied, timestamp);
    }
}
