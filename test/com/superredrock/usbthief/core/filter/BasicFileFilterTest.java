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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BasicFileFilter.
 * Uses mocked ConfigManager to control configuration values.
 */
class BasicFileFilterTest {

    private ConfigManager mockConfig;
    private BasicFileFilter filter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockConfig = mock(ConfigManager.class);
        // Set default config values
        when(mockConfig.get(ConfigSchema.FILE_FILTER_MAX_SIZE)).thenReturn(100L * 1024 * 1024); // 100MB
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(false);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(24L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("HOURS");
        when(mockConfig.get(ConfigSchema.FILE_FILTER_INCLUDE_HIDDEN)).thenReturn(false);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_SKIP_SYMLINKS)).thenReturn(true);
        
        filter = new BasicFileFilter(mockConfig);
    }

    @Test
    void testMaxFileSize_allowed() throws IOException {
        Path file = tempDir.resolve("small_file.txt");
        Files.writeString(file, "test content");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "File under size limit should pass");
    }

    @Test
    void testMaxFileSize_blocked() throws IOException {
        // Set a very small max size
        when(mockConfig.get(ConfigSchema.FILE_FILTER_MAX_SIZE)).thenReturn(5L);
        
        Path file = tempDir.resolve("large_file.bin");
        Files.writeString(file, "this content is more than 5 bytes");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertFalse(filter.test(file, attrs), "File over size limit should be blocked");
    }

    @Test
    void testTimeFilter_recentFile() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(24L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("HOURS");
        
        Path file = tempDir.resolve("recent_file.txt");
        Files.writeString(file, "recent content");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "Recent file should pass");
    }

    @Test
    void testTimeFilter_oldFile() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(1L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("HOURS");
        
        // Create a file with an old timestamp
        Path file = tempDir.resolve("old_file.txt");
        Files.writeString(file, "old content");
        
        // Modify file time to 2 hours ago
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertFalse(filter.test(file, attrs), "Old file should be blocked when time filter enabled");
    }

    @Test
    void testTimeFilter_days() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(7L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("DAYS");
        
        // Create a file modified 3 days ago
        Path file = tempDir.resolve("file_3days.txt");
        Files.writeString(file, "content");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)));
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "File from 3 days ago should pass with 7-day filter");
    }

    @Test
    void testTimeFilter_weeks() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(2L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("WEEKS");
        
        // Create a file modified 10 days ago (more than 1 week, less than 2 weeks)
        Path file = tempDir.resolve("file_10days.txt");
        Files.writeString(file, "content");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(10, ChronoUnit.DAYS)));
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "File from 10 days ago should pass with 2-week filter");
    }

    @Test
    void testTimeFilter_months() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(1L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("MONTHS");
        
        // Create a file modified 2 weeks ago (within 1 month)
        Path file = tempDir.resolve("file_2weeks.txt");
        Files.writeString(file, "content");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(14, ChronoUnit.DAYS)));
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "File from 2 weeks ago should pass with 1-month filter");
    }

    @Test
    void testTimeFilter_years() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(1L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("YEARS");
        
        // Create a file modified 6 months ago (within 1 year)
        Path file = tempDir.resolve("file_6months.txt");
        Files.writeString(file, "content");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(180, ChronoUnit.DAYS)));
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "File from 6 months ago should pass with 1-year filter");
    }

    @Test
    void testTimeFilter_oldFileInDays() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_ENABLED)).thenReturn(true);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_VALUE)).thenReturn(3L);
        when(mockConfig.get(ConfigSchema.FILE_FILTER_TIME_UNIT)).thenReturn("DAYS");
        
        // Create a file modified 5 days ago
        Path file = tempDir.resolve("file_5days.txt");
        Files.writeString(file, "content");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(5, ChronoUnit.DAYS)));
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertFalse(filter.test(file, attrs), "File from 5 days ago should be blocked with 3-day filter");
    }

    @Test
    void testHiddenFiles_configuredToInclude() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_INCLUDE_HIDDEN)).thenReturn(true);
        
        // On Windows, dot-prefixed files aren't hidden by default
        // So this test verifies the config is used
        Path file = tempDir.resolve("normal_file.txt");
        Files.writeString(file, "content");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "Normal file should pass with includeHidden=true");
    }

    @Test
    void testHiddenFiles_configuredToExclude() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_INCLUDE_HIDDEN)).thenReturn(false);
        
        Path file = tempDir.resolve("normal_file.txt");
        Files.writeString(file, "content");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "Non-hidden file should pass");
    }

    @Test
    void testSymlinks_skip() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_SKIP_SYMLINKS)).thenReturn(true);
        
        Path realFile = tempDir.resolve("real_file.txt");
        Files.writeString(realFile, "real content");
        Path symlink = tempDir.resolve("link_to_real");
        Files.createSymbolicLink(symlink, realFile);
        
        BasicFileAttributes attrs = Files.readAttributes(symlink, BasicFileAttributes.class);
        
        assertFalse(filter.test(symlink, attrs), "Symlink should be blocked when skipSymlinks=true");
    }

    @Test
    void testSymlinks_follow() throws IOException {
        when(mockConfig.get(ConfigSchema.FILE_FILTER_SKIP_SYMLINKS)).thenReturn(false);
        
        Path realFile = tempDir.resolve("real_file.txt");
        Files.writeString(realFile, "real content");
        Path symlink = tempDir.resolve("link_to_real");
        Files.createSymbolicLink(symlink, realFile);
        
        BasicFileAttributes attrs = Files.readAttributes(symlink, BasicFileAttributes.class);
        
        assertTrue(filter.test(symlink, attrs), "Symlink should be allowed when skipSymlinks=false");
    }

    @Test
    void testDirectory_skipped() throws IOException {
        Path dir = tempDir.resolve("subdir");
        Files.createDirectory(dir);
        BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
        
        assertFalse(filter.test(dir, attrs), "Directory should be blocked");
    }

    @Test
    void testZeroSizeFile_allowed() throws IOException {
        Path file = tempDir.resolve("empty_file.txt");
        Files.createFile(file);
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "Zero-size file should be allowed");
    }

    @Test
    void testReadableFile_passes() throws IOException {
        Path file = tempDir.resolve("readable.txt");
        Files.writeString(file, "content");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(filter.test(file, attrs), "Readable file should pass");
    }
}
