package com.superredrock.usbthief.worker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A single completed copy operation, as kept by {@link CopyHistoryRecorder} and rendered by
 * {@link ReportExporter}.
 *
 * <p>This is the flat, report-shaped view of a {@code CopyCompletedEvent}: the event carries
 * the raw paths, the entry carries the strings an operator wants to see in a spreadsheet
 * (formatted time, volume label, file name, throughput).</p>
 *
 * @param timestampMillis completion time, epoch milliseconds
 * @param volumeLabel     label of the source volume; falls back to the drive letter or the raw
 *                        serial number when no label is available, empty when the copy was not
 *                        bound to a device
 * @param sourceFileName  file name of the copied source
 * @param destinationPath destination path as text, empty when no target was chosen (e.g. failure
 *                        before the target was resolved)
 * @param sizeBytes       size of the source file in bytes
 * @param durationMillis  wall-clock duration of the operation, {@code 0} when unknown
 * @param result          copy outcome ({@link CopyResult})
 */
public record CopyHistoryEntry(
        long timestampMillis,
        String volumeLabel,
        String sourceFileName,
        String destinationPath,
        long sizeBytes,
        long durationMillis,
        CopyResult result) {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    /**
     * Normalises the text fields and clamps the numeric ones so an exporter can never print
     * "null" or a negative size/duration into the report.
     */
    public CopyHistoryEntry {
        volumeLabel = volumeLabel == null ? "" : volumeLabel;
        sourceFileName = sourceFileName == null ? "" : sourceFileName;
        destinationPath = destinationPath == null ? "" : destinationPath;
        result = result == null ? CopyResult.FAIL : result;
        if (sizeBytes < 0) {
            sizeBytes = 0;
        }
        if (durationMillis < 0) {
            durationMillis = 0;
        }
    }

    /**
     * @return the completion time as {@code yyyy-MM-dd HH:mm:ss} in the local time zone
     */
    public String formattedTimestamp() {
        return TIME_FORMATTER.format(
                Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()));
    }

    /**
     * Average throughput of the whole operation.
     *
     * @return megabytes per second over {@link #sizeBytes()} and {@link #durationMillis()},
     *         or {@code 0.0} when the duration is unknown (which is how copies reported by
     *         paths that do not measure time arrive)
     */
    public double megabytesPerSecond() {
        if (durationMillis <= 0) {
            return 0.0;
        }
        return (sizeBytes / BYTES_PER_MB) / (durationMillis / 1000.0);
    }

    /** @return whether the copy failed */
    public boolean isFailure() {
        return result == CopyResult.FAIL;
    }
}
