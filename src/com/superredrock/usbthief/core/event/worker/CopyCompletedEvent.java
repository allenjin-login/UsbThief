package com.superredrock.usbthief.core.event.worker;

import com.superredrock.usbthief.core.event.AbstractEvent;
import com.superredrock.usbthief.worker.CopyResult;

import java.nio.file.Path;

/**
 * Event fired when a file copy operation completes (success, failure, or cancellation).
 * Provides statistics for monitoring and tracking copy operations.
 */
public class CopyCompletedEvent extends AbstractEvent {

    private final Path sourcePath;
    private final Path destinationPath;
    private final long fileSize;
    private final long bytesCopied;
    private final CopyResult result;
    private final String deviceSerial;
    private final long durationMillis;

    public CopyCompletedEvent(Path sourcePath, Path destinationPath,
                           long fileSize, long bytesCopied, CopyResult result, String deviceSerial) {
        this(sourcePath, destinationPath, fileSize, bytesCopied, result, deviceSerial, 0L);
    }

    /**
     * @param durationMillis wall-clock duration of the operation in milliseconds; {@code 0} when
     *                       the reporting path does not measure time. Used by the copy report to
     *                       compute an average throughput.
     */
    public CopyCompletedEvent(Path sourcePath, Path destinationPath,
                           long fileSize, long bytesCopied, CopyResult result, String deviceSerial,
                           long durationMillis) {
        if (sourcePath == null || result == null) {
            throw new IllegalArgumentException("sourcePath and result cannot be null");
        }
        this.sourcePath = sourcePath;
        this.destinationPath = destinationPath;
        this.fileSize = fileSize;
        this.bytesCopied = bytesCopied;
        this.result = result;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.durationMillis = Math.max(0L, durationMillis);
    }

    public String deviceSerial() {
        return deviceSerial;
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
     * @return the wall-clock duration of the operation in milliseconds, or {@code 0} when the
     *         event was raised by a path that does not measure it
     */
    public long durationMillis() {
        return durationMillis;
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
    public String description() {
        return String.format("CopyCompletedEvent: %s -> %s (size: %d, copied: %d, result: %s) at %s",
                sourcePath.getFileName(),
                destinationPath != null ? destinationPath.getFileName() : "null",
                fileSize, bytesCopied, result, timestamp());
    }

}
