package com.superredrock.usbthief.worker;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Classification table used by {@link CategoryMode#BY_TYPE}: which category folder a
 * copied file belongs to, keyed off its extension.
 *
 * <p>Kept as its own class (instead of a {@code switch} inside the copy loop) so the
 * table can be unit-tested and extended without touching {@code CopyTask}. The
 * {@link #directoryName()} values are stable, locale-independent folder names - the
 * same reason the default backup folder is the literal {@code UsbThiefData}: renaming
 * on-disk folders when the UI language changes would split one library across
 * several trees.</p>
 *
 * <p>Classification is deliberately case-insensitive ({@code photo.JPG} is an image)
 * and extension-only: no content sniffing. Anything without a recognised extension,
 * including extension-less and dot-files such as {@code .gitignore}, falls into
 * {@link #OTHER}.</p>
 */
public enum FileCategory {

    /** 图片 - bitmaps and other still images. */
    IMAGE("Images",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tif", "tiff", "svg", "ico"),

    /** 视频 - container and stream video formats. */
    VIDEO("Videos",
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "3gp", "ts"),

    /** 音乐 - audio files. */
    AUDIO("Music",
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus", "aiff", "mid", "midi"),

    /** 文档 - text, office documents and e-books. */
    DOCUMENT("Documents",
            "doc", "docx", "pdf", "xls", "xlsx", "ppt", "pptx", "txt", "md",
            "rtf", "csv", "odt", "ods", "odp", "epub"),

    /** 压缩包 - archives and disk images. */
    ARCHIVE("Archives",
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "cab"),

    /** 程序 - executables, installers and packaged applications. */
    PROGRAM("Programs",
            "exe", "msi", "msix", "apk", "dmg", "app", "bat", "cmd", "com", "jar", "deb", "rpm"),

    /** 其他 - fallback for every file the table above does not recognise. */
    OTHER("Other");

    /** Category used for unrecognised, extension-less and dot-files. */
    public static final FileCategory FALLBACK = OTHER;

    private static final Map<String, FileCategory> BY_EXTENSION;

    static {
        Map<String, FileCategory> byExtension = new HashMap<>();
        for (FileCategory category : values()) {
            for (String extension : category.extensions) {
                FileCategory previous = byExtension.put(extension, category);
                if (previous != null) {
                    // A duplicated extension would silently make the table ambiguous.
                    throw new IllegalStateException(
                            "Extension '" + extension + "' is listed in both " + previous + " and " + category);
                }
            }
        }
        BY_EXTENSION = Collections.unmodifiableMap(byExtension);
    }

    private final String directoryName;
    private final Set<String> extensions;

    FileCategory(String directoryName, String... extensions) {
        this.directoryName = directoryName;
        this.extensions = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(extensions)));
    }

    /**
     * @return the folder name this category is copied into, e.g. {@code Images}
     */
    public String directoryName() {
        return directoryName;
    }

    /**
     * @return the extensions handled by this category, in declaration order
     */
    public Set<String> extensions() {
        return extensions;
    }

    /**
     * Classifies a file by its name.
     *
     * @param file the file about to be copied, may be {@code null}
     * @return the matching category, never {@code null}
     */
    public static FileCategory classify(Path file) {
        if (file == null || file.getFileName() == null) {
            return FALLBACK;
        }
        return classifyFileName(file.getFileName().toString());
    }

    /**
     * Classifies a file name (or path) by its extension.
     *
     * @param fileName the name to classify, may be {@code null} or blank
     * @return the matching category, or {@link #FALLBACK} when the extension is missing or unknown
     */
    public static FileCategory classifyFileName(String fileName) {
        String extension = extensionOf(fileName);
        if (extension == null) {
            return FALLBACK;
        }
        return BY_EXTENSION.getOrDefault(extension, FALLBACK);
    }

    /**
     * Extracts the lower-cased extension of a file name.
     *
     * @return the extension without the dot, or {@code null} when the name has none
     *         (extension-less files, dot-files like {@code .gitignore}, trailing dots)
     */
    static String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 1 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
