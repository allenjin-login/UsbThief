package com.superredrock.usbthief.core.filter;

import java.util.List;

/**
 * Predefined filter presets for common file extension categories.
 *
 * <p>Each preset contains a list of file extensions (without dot prefix)
 * that belong to a specific category.
 */
public enum FilterPreset {
    /**
     * Document files: PDF, Word, Excel, PowerPoint, text, and RTF.
     */
    DOCUMENTS(
        List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf"),
        "filter.suffix.preset.documents"
    ),

    /**
     * Image files: JPEG, PNG, GIF, BMP, TIFF, WebP, and SVG.
     */
    IMAGES(
        List.of("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "svg"),
        "filter.suffix.preset.images"
    ),

    /**
     * Video files: MP4, AVI, MKV, MOV, WMV, and FLV.
     */
    VIDEO(
        List.of("mp4", "avi", "mkv", "mov", "wmv", "flv"),
        "filter.suffix.preset.video"
    ),

    /**
     * Audio files: MP3, WAV, FLAC, AAC, OGG, and WMA.
     */
    AUDIO(
        List.of("mp3", "wav", "flac", "aac", "ogg", "wma"),
        "filter.suffix.preset.audio"
    ),

    /**
     * Archive files: ZIP, RAR, 7Z, TAR, and GZ.
     */
    ARCHIVES(
        List.of("zip", "rar", "7z", "tar", "gz"),
        "filter.suffix.preset.archives"
    ),

    /**
     * All files: empty list meaning no extension filter.
     */
    ALL(
        List.of(),
        "filter.suffix.preset.all"
    );

    private final List<String> extensions;
    private final String displayNameKey;

    FilterPreset(List<String> extensions, String displayNameKey) {
        this.extensions = List.copyOf(extensions);
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
}
