package com.superredrock.usbthief.core.filter;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Memoized "blocked, or under a blocked directory" verdicts, keyed by directory. */
    private final ConcurrentHashMap<Path, Boolean> blockedDirectoryCache = new ConcurrentHashMap<>();

    @Override
    public boolean test(Path path, BasicFileAttributes attrs) {
        // Files.find() does not prune subtrees when filter rejects a directory,
        // so we must check ALL path components, not just the immediate name.
        // Directory verdicts are memoized: during a scan, sibling files re-check
        // the same directories, turning O(depth) per file into O(1) amortized.
        Path parent = path.getParent();
        if (parent != null && isBlockedOrUnder(parent)) {
            return false;
        }
        return !isBlockedName(path);
    }

    private boolean isBlockedOrUnder(Path dir) {
        if (dir == null) {
            return false;
        }
        Boolean cached = blockedDirectoryCache.get(dir);
        if (cached != null) {
            return cached;
        }
        boolean blocked = isBlockedName(dir) || isBlockedOrUnder(dir.getParent());
        blockedDirectoryCache.put(dir, blocked);
        return blocked;
    }

    private static boolean isBlockedName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) return false;
        String name = fileName.toString().toUpperCase(Locale.ROOT);
        return BLOCKED_NAMES.contains(name) || isChkdskRecoveryDir(name);
    }

    public static boolean isSystemDirName(Path path) {
        return isBlockedName(path);
    }

    private static boolean isChkdskRecoveryDir(String name) {
        return name.startsWith("FOUND.") && name.length() == 9
                && Character.isDigit(name.charAt(6))
                && Character.isDigit(name.charAt(7))
                && Character.isDigit(name.charAt(8));
    }
}
