package com.superredrock.usbthief.worker;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for checking protected file status.
 *
 * <p>Protected files are files that should not be copied or deleted:
 * <ul>
 *   <li>New files (modified within N hours)</li>
 *   <li>Locked files (in use by another process)</li>
 *   <li>System files (hidden or in Windows system directories)</li>
 * </ul>
 *
 * <p>Error handling strategy: If any check cannot be determined due to IOException,
 * the method returns {@code true} (treats the file as protected) for safety.
 */
public final class ProtectedFileChecker {

    // Default protection age threshold: 1 hour
    private static final int DEFAULT_MAX_AGE_HOURS = 1;

    private ProtectedFileChecker() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if a file is protected by ANY protection rule.
     *
     * <p>A file is considered protected if it is:
     * <ul>
     *   <li>New (modified within default age threshold)</li>
     *   <li>Locked by another process</li>
     *   <li>A system file (hidden or in system directory)</li>
     * </ul>
     *
     * @param file the file to check
     * @return {@code true} if the file is protected, {@code false} otherwise
     */
    public static boolean isProtected(Path file) {
        return isNewFile(file, DEFAULT_MAX_AGE_HOURS)
                || isLocked(file)
                || isSystemFile(file);
    }

    /**
     * Checks if a file is protected by ANY protection rule with custom age threshold.
     *
     * @param file the file to check
     * @param maxAgeHours maximum age in hours for a file to be considered "new"
     * @return {@code true} if the file is protected, {@code false} otherwise
     */
    public static boolean isProtected(Path file, int maxAgeHours) {
        return isNewFile(file, maxAgeHours)
                || isLocked(file)
                || isSystemFile(file);
    }

    /**
     * Checks if a file is "new" based on modification time.
     *
     * <p>A file is considered new if its last modified time is less than
     * the specified maximum age.
     *
     * @param file the file to check
     * @param maxAgeHours maximum age in hours for a file to be considered new
     * @return {@code true} if the file is newer than maxAgeHours, {@code false} otherwise
     */
    public static boolean isNewFile(Path file, int maxAgeHours) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(file);
            long ageMillis = System.currentTimeMillis() - lastModified.toMillis();
            long maxAgeMillis = TimeUnit.HOURS.toMillis(maxAgeHours);
            return ageMillis < maxAgeMillis;
        } catch (IOException e) {
            // Cannot determine status - treat as protected (safe default)
            return true;
        }
    }

    /**
     * Checks if a file is locked by another process.
     *
     * <p>Attempts to acquire an exclusive lock on the file.
     * If the lock cannot be acquired, the file is considered locked.
     *
     * @param file the file to check
     * @return {@code true} if the file is locked, {@code false} otherwise
     */
    public static boolean isLocked(Path file) {
        try (FileChannel channel = FileChannel.open(file,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.READ)) {

            FileLock lock = channel.tryLock();

            if (lock != null) {
                lock.release();
                return false;  // Not locked - we successfully acquired and released the lock
            }

            // Cannot acquire lock - file is locked by another process
            return true;
        } catch (java.nio.channels.OverlappingFileLockException e) {
            // File is already locked (possibly by our own test)
            return true;
        } catch (IOException e) {
            // Cannot determine status or file doesn't exist - treat as protected (safe default)
            return true;
        }
    }

    /**
     * Checks if a file is a system file.
     *
     * <p>A file is considered a system file if:
     * <ul>
     *   <li>It is marked as hidden</li>
     *   <li>It is located in a Windows system directory</li>
     * </ul>
     *
     * @param file the file to check
     * @return {@code true} if the file is a system file, {@code false} otherwise
     */
    public static boolean isSystemFile(Path file) {
        try {
            if (Files.isHidden(file)) {
                return true;
            }

            // Check for Windows system paths (case-insensitive)
            String pathLower = file.toString().toLowerCase();

            // Windows system directories
            return pathLower.contains("\\windows\\")
                    || pathLower.contains("\\program files")
                    || pathLower.contains("\\program files (x86)")
                    || pathLower.contains("\\programdata")
                    || pathLower.endsWith("\\windows")
                    || pathLower.endsWith("\\program files")
                    || pathLower.endsWith("\\program files (x86)")
                    || pathLower.endsWith("\\programdata");
        } catch (IOException e) {
            // Cannot determine status - treat as protected (safe default)
            return true;
        }
    }
}
