package com.superredrock.usbthief.core.filter;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SuffixFilter.
 * Uses mocked ConfigManager to control configuration values.
 */
class SuffixFilterTest {

    private ConfigManager mockConfig;
    private SuffixFilter filter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockConfig = mock(ConfigManager.class);
        // Set default config values (NONE mode)
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("NONE");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of());
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_BLACKLIST)).thenReturn(List.of());
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_PRESET)).thenReturn("");
        when(mockConfig.get(ConfigSchema.FILE_FILTER_ALLOW_NO_EXT)).thenReturn(true);
        
        filter = new SuffixFilter(mockConfig);
    }

    // ==================== Mode NONE Tests ====================

    @Test
    void testModeNone_allowsAll() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("NONE");
        
        Path pdfPath = createFile("doc.pdf");
        Path tmpPath = createFile("temp.tmp");
        Path noExtPath = createFile("README");
        
        assertTrue(filter.test(pdfPath, getAttrs(pdfPath)));
        assertTrue(filter.test(tmpPath, getAttrs(tmpPath)));
        assertTrue(filter.test(noExtPath, getAttrs(noExtPath)));
    }

    // ==================== Whitelist Tests ====================

    @Test
    void testWhitelist_matches() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("pdf", "doc"));
        
        Path pdfPath = createFile("document.pdf");
        
        assertTrue(filter.test(pdfPath, getAttrs(pdfPath)), "PDF should pass when in whitelist");
    }

    @Test
    void testWhitelist_noMatch() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("pdf", "doc"));
        
        Path mp4Path = createFile("video.mp4");
        
        assertFalse(filter.test(mp4Path, getAttrs(mp4Path)), "MP4 should be blocked when not in whitelist");
    }

    @Test
    void testEmptyWhitelist() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of());
        
        Path pdfPath = createFile("doc.pdf");
        
        assertFalse(filter.test(pdfPath, getAttrs(pdfPath)), "All files should be blocked with empty whitelist");
    }

    // ==================== Blacklist Tests ====================

    @Test
    void testBlacklist_matches() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("BLACKLIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_BLACKLIST)).thenReturn(List.of("tmp", "temp"));
        
        Path tmpPath = createFile("temporary.tmp");
        
        assertFalse(filter.test(tmpPath, getAttrs(tmpPath)), "TMP should be blocked when in blacklist");
    }

    @Test
    void testBlacklist_noMatch() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("BLACKLIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_BLACKLIST)).thenReturn(List.of("tmp"));
        
        Path pdfPath = createFile("document.pdf");
        
        assertTrue(filter.test(pdfPath, getAttrs(pdfPath)), "PDF should pass when not in blacklist");
    }

    // ==================== Case Insensitivity Tests ====================

    @Test
    void testCaseInsensitive_whitelist() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("jpg"));
        
        Path upperPath = createFile("IMAGE.JPG");
        Path mixedPath = createFile("photo.JpG");
        
        assertTrue(filter.test(upperPath, getAttrs(upperPath)), "JPG should match jpg in whitelist");
        assertTrue(filter.test(mixedPath, getAttrs(mixedPath)), "JpG should match jpg in whitelist");
    }

    @Test
    void testCaseInsensitive_blacklist() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("BLACKLIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_BLACKLIST)).thenReturn(List.of("pdf"));
        
        Path upperPath = createFile("DOCUMENT.PDF");
        Path mixedPath = createFile("file.PdF");
        
        assertFalse(filter.test(upperPath, getAttrs(upperPath)), "PDF should match pdf in blacklist");
        assertFalse(filter.test(mixedPath, getAttrs(mixedPath)), "PdF should match pdf in blacklist");
    }

    // ==================== No Extension Tests ====================

    @Test
    void testNoExtension_allow() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("pdf"));
        when(mockConfig.get(ConfigSchema.FILE_FILTER_ALLOW_NO_EXT)).thenReturn(true);
        
        Path noExtPath = createFile("README");
        
        assertTrue(filter.test(noExtPath, getAttrs(noExtPath)), "No-ext file should pass when allowNoExtension=true");
    }

    @Test
    void testNoExtension_block() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("pdf"));
        when(mockConfig.get(ConfigSchema.FILE_FILTER_ALLOW_NO_EXT)).thenReturn(false);
        
        Path noExtPath = createFile("README");
        
        assertFalse(filter.test(noExtPath, getAttrs(noExtPath)), "No-ext file should be blocked when allowNoExtension=false");
    }

    // ==================== Preset Tests ====================

    @Test
    void testPresetDocuments() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_PRESET)).thenReturn("DOCUMENTS");
        
        Path pdfPath = createFile("document.pdf");
        Path jpgPath = createFile("image.jpg");
        
        assertTrue(filter.test(pdfPath, getAttrs(pdfPath)), "PDF should pass with DOCUMENTS preset");
        assertFalse(filter.test(jpgPath, getAttrs(jpgPath)), "JPG should not pass with DOCUMENTS preset");
    }

    @Test
    void testPresetImages() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_PRESET)).thenReturn("IMAGES");
        
        Path jpgPath = createFile("photo.jpg");
        Path pdfPath = createFile("document.pdf");
        
        assertTrue(filter.test(jpgPath, getAttrs(jpgPath)), "JPG should pass with IMAGES preset");
        assertFalse(filter.test(pdfPath, getAttrs(pdfPath)), "PDF should not pass with IMAGES preset");
    }

    // ==================== Multiple Extension Tests ====================

    @Test
    void testMultipleExtensions_whitelist() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("pdf", "doc", "xls"));
        
        Path pdfPath = createFile("doc.pdf");
        Path xlsPath = createFile("data.xls");
        Path mp4Path = createFile("video.mp4");
        
        assertTrue(filter.test(pdfPath, getAttrs(pdfPath)), "PDF should pass");
        assertTrue(filter.test(xlsPath, getAttrs(xlsPath)), "XLS should pass");
        assertFalse(filter.test(mp4Path, getAttrs(mp4Path)), "MP4 should be blocked");
    }

    @Test
    void testExtensionWithDots() throws IOException {
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_MODE)).thenReturn("WHITELIST");
        when(mockConfig.get(ConfigSchema.SUFFIX_FILTER_WHITELIST)).thenReturn(List.of("pdf"));
        
        Path multiDotPath = createFile("my.document.final.pdf");
        
        assertTrue(filter.test(multiDotPath, getAttrs(multiDotPath)), "Should use last extension");
    }

    // ==================== Helper Methods ====================

    private Path createFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        return file;
    }

    private BasicFileAttributes getAttrs(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class);
    }
}
