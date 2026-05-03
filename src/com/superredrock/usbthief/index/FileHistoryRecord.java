package com.superredrock.usbthief.index;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Record representing a failed file copy entry.
 * Stores information about failed file copies including path, size, bytes copied, and timestamp.
 */
@Deprecated
public final class FileHistoryRecord implements Serializable {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String fileName;
    private final String sourcePath;
    private final String destPath;
    private final long fileSize;
    private final long bytesCopied;
    private final String timestamp;

    public FileHistoryRecord(String fileName, String sourcePath, String destPath, long fileSize,
                            long bytesCopied, String timestamp) {
        this.fileName = fileName;
        this.sourcePath = sourcePath;
        this.destPath = destPath;
        this.fileSize = fileSize;
        this.bytesCopied = bytesCopied;
        this.timestamp = timestamp;
    }

    /**
     * Create a new record with current timestamp.
     */
    public FileHistoryRecord(String fileName, String sourcePath, String destPath, long fileSize, long bytesCopied) {
        this(fileName, sourcePath, destPath, fileSize, bytesCopied, LocalDateTime.now().format(TIME_FORMATTER));
    }

    public String fileName() { return fileName; }
    public String sourcePath() { return sourcePath; }
    public String destPath() { return destPath; }
    public long fileSize() { return fileSize; }
    public long bytesCopied() { return bytesCopied; }
    public String timestamp() { return timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileHistoryRecord)) return false;
        FileHistoryRecord that = (FileHistoryRecord) o;
        return fileSize == that.fileSize &&
               bytesCopied == that.bytesCopied &&
               Objects.equals(fileName, that.fileName) &&
               Objects.equals(sourcePath, that.sourcePath) &&
               Objects.equals(destPath, that.destPath) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, sourcePath, destPath, fileSize, bytesCopied, timestamp);
    }

    @Override
    public String toString() {
        return String.format("FileHistoryRecord{fileName='%s', source='%s', dest='%s', size=%d, copied=%d, time='%s'}",
                fileName, sourcePath, destPath, fileSize, bytesCopied, timestamp);
    }
}
