package com.superredrock.usbthief.core.event.worker;

import com.superredrock.usbthief.core.event.Event;
import com.superredrock.usbthief.worker.CopyResult;

import java.nio.file.Path;

/**
 * Event fired when a file copy operation completes (success, failure, or cancellation).
 * Provides statistics for monitoring and tracking copy operations.
 */
public class CopyCompletedEvent implements Event {

    private final Path sourcePath;
    private final Path destinationPath;
    private final long fileSize;
    private final long bytesCopied;
    private final CopyResult result;
    private final long timestamp;

    public CopyCompletedEvent(Path sourcePath, Path destinationPath,
                           long fileSize, long bytesCopied, CopyResult result) {
        if (sourcePath == null || result == null) {
            throw new IllegalArgumentException("sourcePath and result cannot be null");
        }
        this.sourcePath = sourcePath;
        this.destinationPath = destinationPath;
        this.fileSize = fileSize;
        this.bytesCopied = bytesCopied;
        this.result = result;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the source file path that was copied
     */
    public Path sourcePath() {
        return sourcePath;
    }

    /**
     * @return the destination file path where file was copied (may be null on failure)
     */
    public Path destinationPath() {
        return destinationPath;
    }

    /**
     * @return the total size of the file in bytes
     */
    public long fileSize() {
        return fileSize;
    }

    /**
     * @return the number of bytes actually copied (may be less than fileSize on failure/cancel)
     */
    public long bytesCopied() {
        return bytesCopied;
    }

    /**
     * @return the result of the copy operation (SUCCESS, FAIL, or CANCEL)
     */
    public CopyResult result() {
        return result;
    }

    /**
     * @return whether the copy operation completed successfully
     */
    public boolean isSuccess() {
        return result == CopyResult.SUCCESS;
    }

    /**
     * @return whether the copy operation failed
     */
    public boolean isFailure() {
        return result == CopyResult.FAIL;
    }

    /**
     * @return whether the copy operation was cancelled
     */
    public boolean isCancelled() {
        return result == CopyResult.CANCEL;
    }

    /**
     * @return the percentage of file copied (0.0 to 1.0)
     */
    public double progressPercentage() {
        if (fileSize == 0) {
            return 0.0;
        }
        return (double) bytesCopied / fileSize;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("CopyCompletedEvent: %s -> %s (size: %d, copied: %d, result: %s) at %s",
                sourcePath.getFileName(),
                destinationPath != null ? destinationPath.getFileName() : "null",
                fileSize, bytesCopied, result, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
