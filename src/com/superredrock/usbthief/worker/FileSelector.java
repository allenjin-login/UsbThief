package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.event.storage.StorageLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * File selection algorithms for storage recycling.
 *
 * <p>Provides static methods for selecting files to recycle based on different strategies:
 * time-first (oldest files), size-first (largest files), and auto-switching based on
 * storage level.
 */
public final class FileSelector {

    /**
     * Metadata record for files available for recycling.
     *
     * @param path      file path
     * @param size      file size in bytes
     * @param copyTime  when file was copied (epoch milliseconds)
     * @param isProtected true if file cannot be recycled
     */
    public record FileMetadata(Path path, long size, long copyTime, boolean isProtected) {
    }

    private FileSelector() {
        // Utility class - prevent instantiation
    }

    /**
     * Select files using time-first strategy.
     *
     * <p>Sorts files by copyTime ascending (oldest first) and selects until accumulated
     * size meets or exceeds bytesNeeded. Protected files are excluded from selection.
     *
     * @param files      list of file metadata to select from
     * @param bytesNeeded target byte count to accumulate
     * @return list of selected files, or empty list if no suitable files found
     */
    public static List<FileMetadata> selectByTime(List<FileMetadata> files, long bytesNeeded) {
        if (files == null || bytesNeeded <= 0) {
            return List.of();
        }

        long accumulated = 0;
        List<FileMetadata> result = new ArrayList<>();

        List<FileMetadata> filtered = files.stream()
                .filter(f -> !f.isProtected())
                .sorted(Comparator.comparingLong(FileMetadata::copyTime))
                .toList();

        for (FileMetadata file : filtered) {
            result.add(file);
            accumulated += file.size();

            if (accumulated >= bytesNeeded) {
                break;
            }
        }

        return result;
    }

    /**
     * Select files using size-first strategy.
     *
     * <p>Sorts files by size descending (largest first) and selects until accumulated
     * size meets or exceeds bytesNeeded. Protected files are excluded from selection.
     *
     * @param files      list of file metadata to select from
     * @param bytesNeeded target byte count to accumulate
     * @return list of selected files, or empty list if no suitable files found
     */
    public static List<FileMetadata> selectBySize(List<FileMetadata> files, long bytesNeeded) {
        if (files == null || bytesNeeded <= 0) {
            return List.of();
        }

        long accumulated = 0;
        List<FileMetadata> result = new ArrayList<>();

        List<FileMetadata> filtered = files.stream()
                .filter(f -> !f.isProtected())
                .sorted(Comparator.comparingLong(FileMetadata::size).reversed())
                .toList();

        for (FileMetadata file : filtered) {
            result.add(file);
            accumulated += file.size();

            if (accumulated >= bytesNeeded) {
                break;
            }
        }

        return result;
    }

    /**
     * Auto-select files based on storage level.
     *
     * <p>Strategy selection:
     * <ul>
     *   <li>OK/LOW → time-first strategy</li>
     *   <li>CRITICAL → size-first strategy</li>
     * </ul>
     *
     * @param files      list of file metadata to select from
     * @param bytesNeeded target byte count to accumulate
     * @param level      current storage level
     * @return list of selected files, or empty list if no suitable files found
     */
    public static List<FileMetadata> selectAuto(List<FileMetadata> files, long bytesNeeded, StorageLevel level) {
        return switch (level) {
            case CRITICAL -> selectBySize(files, bytesNeeded);
            case OK, LOW -> selectByTime(files, bytesNeeded);
        };
    }
}
