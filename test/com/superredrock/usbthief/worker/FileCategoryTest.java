package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@link FileCategory} classification table: every documented extension
 * maps to its category, unknown input falls through to {@code Other}, and the folder
 * names stay distinct so two categories can never write into the same directory.
 */
class FileCategoryTest {

    private static void assertCategory(FileCategory expected, String... fileNames) {
        for (String fileName : fileNames) {
            assertEquals(expected, FileCategory.classifyFileName(fileName),
                    "expected '" + fileName + "' to be classified as " + expected);
        }
    }

    @Test
    void images() {
        assertCategory(FileCategory.IMAGE,
                "photo.jpg", "photo.jpeg", "shot.png", "anim.gif",
                "tile.bmp", "page.webp", "iphone.heic");
    }

    @Test
    void videos() {
        assertCategory(FileCategory.VIDEO,
                "clip.mp4", "clip.mov", "clip.avi", "clip.mkv", "movie.wmv");
    }

    @Test
    void music() {
        assertCategory(FileCategory.AUDIO,
                "song.mp3", "song.flac", "song.wav", "song.m4a", "song.aac");
    }

    @Test
    void documents() {
        assertCategory(FileCategory.DOCUMENT,
                "report.doc", "report.docx", "report.pdf", "sheet.xls", "sheet.xlsx",
                "slides.ppt", "slides.pptx", "notes.txt", "readme.md", "data.csv");
    }

    @Test
    void archives() {
        assertCategory(FileCategory.ARCHIVE,
                "backup.zip", "backup.rar", "backup.7z", "backup.tar", "backup.gz");
    }

    @Test
    void programs() {
        assertCategory(FileCategory.PROGRAM,
                "setup.exe", "setup.msi", "app.apk", "app.dmg", "run.bat");
    }

    @Test
    void fallbackForUnknownExtension() {
        assertCategory(FileCategory.OTHER, "data.bin", "file.unknownext", "weird.zzz");
    }

    @Test
    void fallbackForMissingExtension() {
        assertCategory(FileCategory.OTHER, "README", "Makefile", "archive.", ".gitignore");
    }

    @Test
    void fallbackForNullOrEmptyName() {
        assertEquals(FileCategory.OTHER, FileCategory.classifyFileName(null));
        assertEquals(FileCategory.OTHER, FileCategory.classifyFileName(""));
    }

    @Test
    void extensionMatchIsCaseInsensitive() {
        assertCategory(FileCategory.IMAGE, "PHOTO.JPG", "Photo.JpG");
        assertCategory(FileCategory.DOCUMENT, "README.MD");
        assertCategory(FileCategory.PROGRAM, "SETUP.EXE");
    }

    @Test
    void classifyUsesTheFileNameOfAPath() {
        assertEquals(FileCategory.IMAGE, FileCategory.classify(Path.of("/tmp/usb/DCIM/photo.PNG")));
        assertEquals(FileCategory.OTHER, FileCategory.classify(Path.of("no-extension")));
        assertEquals(FileCategory.OTHER, FileCategory.classify(null));
    }

    @Test
    void everyDeclaredExtensionResolvesToItsOwnCategory() {
        for (FileCategory category : FileCategory.values()) {
            for (String extension : category.extensions()) {
                assertEquals(extension, extension.toLowerCase(java.util.Locale.ROOT),
                        "extensions must be declared lower-case: " + extension);
                assertEquals(category, FileCategory.classifyFileName("sample." + extension),
                        "extension '" + extension + "' should belong to " + category);
            }
        }
    }

    @Test
    void categoryFoldersAreDistinct() {
        Set<String> folderNames = new HashSet<>();
        for (FileCategory category : FileCategory.values()) {
            assertTrue(folderNames.add(category.directoryName()),
                    "duplicate category folder name: " + category.directoryName());
            assertFalse(category.directoryName().isBlank());
        }
    }

    @Test
    void fallbackHasNoExtensionsOfItsOwn() {
        assertTrue(FileCategory.FALLBACK.extensions().isEmpty(),
                "the fallback category must be reached only by unknown extensions");
    }
}
