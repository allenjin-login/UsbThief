package com.superredrock.usbthief.core.filter;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;

/**
 * Filters out known system/junk directories and their contents.
 * Prunes entire directory subtrees during file tree traversal.
 */
public class SystemDirectoryFilter implements FileFilter {

    private static final Set<String> BLOCKED_NAMES = Set.of(
            // Windows
            "$RECYCLE.BIN",
            "RECYCLER",
            "SYSTEM VOLUME INFORMATION",
            // macOS
            ".SPOTLIGHT-V100",
            ".FSEVENTSD",
            ".TRASHES",
            ".TEMPORARYITEMS",
            ".DOCUMENTREVISIONS-V100"
    );

    @Override
    public boolean test(Path path, BasicFileAttributes attrs) {
        if (!attrs.isDirectory()) {
            return true;
        }

        Path fileName = path.getFileName();
        if (fileName == null) {
            return true;
        }

        String name = fileName.toString().toUpperCase(Locale.ROOT);
        return !BLOCKED_NAMES.contains(name) && !ishkdfRecoveryDir(name);
    }

    private static boolean ishkdfRecoveryDir(String name) {
        return name.startsWith("FOUND.") && name.length() == 9
                && Character.isDigit(name.charAt(6))
                && Character.isDigit(name.charAt(7))
                && Character.isDigit(name.charAt(8));
    }
}
