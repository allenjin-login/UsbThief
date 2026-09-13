package com.superredrock.usbthief.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Strategy for the optional "category folder" layer that {@link CopyTask} inserts
 * between the volume folder ({@code workPath/(storeName_serial)}) and the copied file.
 *
 * <p>Follows the {@link OverwriteStrategy} pattern: every mode is an enum constant
 * that owns its decision logic, and {@link #safeValueOf(String)} degrades to a
 * sensible default instead of throwing. The default is {@link #OFF}, so a copy to a
 * target that existed before this feature keeps its previous layout exactly.</p>
 *
 * <ul>
 *   <li>{@link #OFF} - no category layer, destination unchanged (default)</li>
 *   <li>{@link #BY_TYPE} - one folder per {@link FileCategory}
 *       ({@code (store)/Images/photo.jpg})</li>
 *   <li>{@link #BY_DATE} - one folder per copy date
 *       ({@code (store)/2026-09-13/photo.jpg})</li>
 * </ul>
 */
public enum CategoryMode {

    /** Keep the current layout: {@code (store)/relative/path/file.ext}. */
    OFF {
        @Override
        public String categoryDirectory(Path sourceFile) {
            return null;
        }
    },

    /** Sort files into {@code (store)/<FileCategory.directoryName()>/file.ext}. */
    BY_TYPE {
        @Override
        public String categoryDirectory(Path sourceFile) {
            return FileCategory.classify(sourceFile).directoryName();
        }
    },

    /** Sort files into {@code (store)/yyyy-MM-dd/file.ext} by copy date. */
    BY_DATE {
        @Override
        public String categoryDirectory(Path sourceFile) {
            return DATE_FORMAT.format(LocalDate.now());
        }
    };

    private static final Logger logger = LogManager.getLogger(CategoryMode.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Name of the single folder level to insert below the volume folder.
     *
     * @param sourceFile the file being copied, used by {@link #BY_TYPE}
     * @return the folder name, or {@code null} when no category layer should be created
     */
    public abstract String categoryDirectory(Path sourceFile);

    /**
     * Parses a configured mode name, falling back to {@link #OFF} on invalid input.
     *
     * @param name the configured value, may be {@code null}
     * @return the matching mode, or {@code OFF} when the name is unknown
     */
    public static CategoryMode safeValueOf(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Invalid category mode '{}', falling back to OFF", name);
            return OFF;
        }
    }
}
