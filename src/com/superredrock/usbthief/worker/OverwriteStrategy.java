package com.superredrock.usbthief.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Strategy for handling existing target files during copy operations.
 * Each enum value encapsulates its own decision logic.
 */
public enum OverwriteStrategy {

    RENAME {
        @Override
        public boolean shouldOverwrite(Path source, Path target) {
            return false; // Never overwrite — resolveTarget renames instead
        }

        @Override
        public Path resolveTarget(Path target) {
            String fileName = target.getFileName().toString();
            String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
            int dotIndex = fileName.lastIndexOf('.');
            String newName;
            if (dotIndex > 0) {
                newName = fileName.substring(0, dotIndex) + "_" + timestamp + fileName.substring(dotIndex);
            } else {
                newName = fileName + "_" + timestamp;
            }
            return target.resolveSibling(newName);
        }
    },

    TIME_COMPARE {
        @Override
        public boolean shouldOverwrite(Path source, Path target) {
            try {
                FileTime sourceCreated = Files.readAttributes(source, BasicFileAttributes.class)
                        .lastModifiedTime();
                FileTime targetModified = Files.readAttributes(target, BasicFileAttributes.class)
                        .creationTime();
                return sourceCreated.compareTo(targetModified) > 0;
            } catch (IOException e) {
                logger.warn("Failed to compare file times for {} vs {}, falling back to overwrite", source, target, e);
                return true;
            }
        }
    },

    ALWAYS_OVERWRITE {
        @Override
        public boolean shouldOverwrite(Path source, Path target) {
            return true;
        }
    };

    private static final Logger logger = LogManager.getLogger(OverwriteStrategy.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Decide whether the target should be overwritten.
     *
     * @param source the source file being copied
     * @param target the existing target file
     * @return true to overwrite, false to use {@link #resolveTarget(Path)} instead
     */
    public abstract boolean shouldOverwrite(Path source, Path target);

    /**
     * Return the actual target path. Only RENAME produces a different path;
     * other strategies return the original target unchanged.
     */
    public Path resolveTarget(Path target) {
        return target;
    }

    /**
     * Parse a strategy name, falling back to ALWAYS_OVERWRITE on invalid input.
     */
    public static OverwriteStrategy safeValueOf(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Invalid overwrite strategy '{}', falling back to ALWAYS_OVERWRITE", name);
            return ALWAYS_OVERWRITE;
        }
    }
}
