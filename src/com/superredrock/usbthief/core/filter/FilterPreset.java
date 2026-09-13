package com.superredrock.usbthief.core.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Predefined filter presets for common file extension categories.
 *
 * <p>Each preset contains a list of file extensions (without dot prefix) that belong
 * to a category. A preset is only a <em>shortcut</em>: picking one writes its
 * extensions into the regular whitelist, and the user may keep editing that list
 * afterwards. Filtering never consults the preset name, so an adjusted list stays the
 * single source of truth.</p>
 *
 * <p>Following the {@code OverwriteStrategy} / {@code CategoryMode} style, every
 * constant owns its data and {@link #safeValueOf(String)} degrades to a sensible
 * default ({@link #ALL}, i.e. no extension filtering) instead of throwing.</p>
 */
public enum FilterPreset {
    /**
     * Document files: Word, PDF, plain text, spreadsheets, presentations,
     * comma separated values, Markdown and OpenDocument text.
     */
    DOCUMENTS(
        List.of("doc", "docx", "pdf", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "md", "odt", "rtf"),
        "filter.suffix.preset.documents"
    ),

    /**
     * Image files: JPEG, PNG, GIF, BMP, WebP, HEIC, TIFF, camera RAW and SVG.
     */
    IMAGES(
        List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tiff", "raw", "svg"),
        "filter.suffix.preset.images"
    ),

    /**
     * Video files: MP4, MKV, AVI, MOV, WMV, FLV, WebM, M4V and MPEG.
     */
    VIDEO(
        List.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg"),
        "filter.suffix.preset.video"
    ),

    /**
     * Audio files: MP3, WAV, FLAC, AAC, OGG, WMA, M4A, Opus, AIFF and MIDI.
     */
    AUDIO(
        List.of("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "aiff", "mid"),
        "filter.suffix.preset.audio"
    ),

    /**
     * Archive files: ZIP, RAR, 7Z, TAR, GZ, BZ2, XZ, ISO, CAB and TGZ.
     */
    ARCHIVES(
        List.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab", "tgz"),
        "filter.suffix.preset.archives"
    ),

    /**
     * All files: the empty extension list means "no whitelist", i.e. extension
     * filtering is switched off entirely.
     */
    ALL(
        List.of(),
        "filter.suffix.preset.all"
    );

    private static final Logger logger = LogManager.getLogger(FilterPreset.class);

    private final List<String> extensions;
    private final String displayNameKey;

    FilterPreset(List<String> extensions, String displayNameKey) {
        this.extensions = List.copyOf(toLowercase(extensions));
        this.displayNameKey = displayNameKey;
    }

    /**
     * Get the list of file extensions for this preset.
     *
     * @return immutable list of extensions (without dot prefix, lowercase)
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Get the i18n key for the display name of this preset.
     *
     * @return the message bundle key for localized display name
     */
    public String getDisplayNameKey() {
        return displayNameKey;
    }

    /**
     * Whether this preset actually narrows the copy set.
     *
     * @return {@code false} for {@link #ALL} (empty set means no filtering)
     */
    public boolean filtersExtensions() {
        return !extensions.isEmpty();
    }

    /**
     * Suffix filter mode a one-click application of this preset must select.
     *
     * <p>{@link #ALL} maps to {@link SuffixFilter#MODE_NONE}, so its empty extension
     * list really means "copy everything" and never trips the whitelist edge case
     * where an empty list blocks every file.</p>
     *
     * @return {@link SuffixFilter#MODE_WHITELIST} or {@link SuffixFilter#MODE_NONE}
     */
    public String suffixMode() {
        return filtersExtensions() ? SuffixFilter.MODE_WHITELIST : SuffixFilter.MODE_NONE;
    }

    /**
     * Case-insensitive, order-insensitive comparison against a concrete extension
     * list; used to decide whether a hand-edited list still equals a preset.
     *
     * @param candidate extensions to compare, may be {@code null}
     * @return true when both contain exactly the same extensions
     */
    public boolean matches(Collection<String> candidate) {
        if (candidate == null) {
            return extensions.isEmpty();
        }
        Set<String> normalized = new HashSet<>();
        for (String ext : candidate) {
            if (ext != null && !ext.isBlank()) {
                normalized.add(ext.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized.equals(new HashSet<>(extensions));
    }

    /**
     * Parses a configured preset name, falling back to {@link #ALL} on invalid input.
     *
     * @param name the configured value, may be {@code null}
     * @return the matching preset, or {@link #ALL} when the name is unknown
     */
    public static FilterPreset safeValueOf(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Unknown filter preset '{}', falling back to ALL (no filtering)", name);
            return ALL;
        }
    }

    private static List<String> toLowercase(List<String> extensions) {
        return extensions.stream()
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .toList();
    }
}
