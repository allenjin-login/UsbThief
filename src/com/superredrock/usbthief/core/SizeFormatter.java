package com.superredrock.usbthief.core;

/**
 * Utility class for formatting byte sizes to human-readable strings.
 */
public final class SizeFormatter {

    private static final long KB = 1024;
    private static final long MB = 1024 * 1024;
    private static final long GB = 1024L * 1024 * 1024;
    private static final long TB = 1024L * 1024 * 1024 * 1024;

    private SizeFormatter() {}

    /**
     * Formats a byte count to a human-readable string.
     *
     * @param bytes the byte count
     * @return formatted string (e.g., "1.5 MB", "256.0 KB", "2.35 GB", "1.2 TB")
     */
    public static String format(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < KB) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return String.format("%.1f KB", bytes / (double) KB);
        }
        if (bytes < GB) {
            return String.format("%.1f MB", bytes / (double) MB);
        }
        if (bytes < TB) {
            return String.format("%.2f GB", bytes / (double) GB);
        }
        return String.format("%.1f TB", bytes / (double) TB);
    }
}
