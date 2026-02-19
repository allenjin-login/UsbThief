package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.event.storage.StorageLevel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FileSelector component.
 */
class FileSelectorTest {

    @Test
    @DisplayName("selectByTime returns oldest files first")
    void selectByTime_returnsOldestFiles() {
        // Sorted by copyTime: file2(1000), file3(2000), file1(3000)
        // bytesNeeded=200, file2 has size=200 which exactly meets requirement
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 3000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 1000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 300, 2000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 200);

        assertEquals(1, result.size());
        assertEquals(Path.of("file2.txt"), result.get(0).path());
        assertEquals(200, result.get(0).size());
    }

    @Test
    @DisplayName("selectByTime returns multiple files when needed")
    void selectByTime_returnsMultipleFiles() {
        // Sorted by copyTime: file1(1000), file2(2000), file3(3000)
        // bytesNeeded=150
        // Add file1(size=100): accumulated=100, 100 >= 150? NO
        // Add file2(size=100): accumulated=200, 200 >= 150? YES, break
        // Expected: [file1.txt, file2.txt] with total size 200
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 100, 2000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 50, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 150);

        assertEquals(2, result.size());
        assertEquals(Path.of("file1.txt"), result.get(0).path());
        assertEquals(Path.of("file2.txt"), result.get(1).path());
        assertEquals(200, result.stream().mapToLong(FileSelector.FileMetadata::size).sum());
    }

    @Test
    @DisplayName("selectByTime returns empty list for empty input")
    void selectByTime_emptyInput() {
        List<FileSelector.FileMetadata> files = List.of();

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 100);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("selectByTime returns empty list for zero bytesNeeded")
    void selectByTime_zeroBytesNeeded() {
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("selectByTime excludes protected files")
    void selectByTime_excludesProtectedFiles() {
        // Sorted by copyTime: file1(1000), file3(3000) - file2 is protected
        // bytesNeeded=200
        // Add file1(size=100): accumulated=100, 100 >= 200? NO
        // Add file3(size=300): accumulated=400, 400 >= 200? YES, break
        // Expected: [file1.txt, file3.txt] - no protected files, both selected
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 2000, true),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 300, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 200);

        assertEquals(2, result.size());
        assertFalse(result.stream().anyMatch(FileSelector.FileMetadata::isProtected));
        assertEquals(Path.of("file1.txt"), result.get(0).path());
        assertEquals(Path.of("file3.txt"), result.get(1).path());
    }

    @Test
    @DisplayName("selectByTime returns all files if all protected")
    void selectByTime_allProtected() {
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, true),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 2000, true)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 100);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("selectBySize returns largest files first")
    void selectBySize_returnsLargestFiles() {
        // Sorted by size descending: file2(300), file3(200), file1(100)
        // bytesNeeded=250
        // Add file2(size=300): accumulated=300, 300 >= 250? YES, break
        // Expected: [file2.txt] - single largest file meets requirement
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 300, 2000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 200, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectBySize(files, 250);

        // file2(size=300) meets requirement
        assertEquals(1, result.size());
        assertEquals(300, result.get(0).size());
        assertEquals(Path.of("file2.txt"), result.get(0).path());
    }

    @Test
    @DisplayName("selectBySize selects file when size matches bytesNeeded")
    void selectBySize_matchesBytesNeeded() {
        // Sorted by size descending: file2(300), file3(200), file1(100)
        // bytesNeeded=300
        // Add file2(size=300): accumulated=300, 300 >= 300? YES, break
        // Expected: [file2.txt] - single file exactly meets requirement
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 300, 2000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 200, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectBySize(files, 300);

        assertEquals(1, result.size());
        assertEquals(300, result.get(0).size());
    }

    @Test
    @DisplayName("selectBySize returns empty list for empty input")
    void selectBySize_emptyInput() {
        List<FileSelector.FileMetadata> files = List.of();

        List<FileSelector.FileMetadata> result = FileSelector.selectBySize(files, 100);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("selectBySize excludes protected files")
    void selectBySize_excludesProtectedFiles() {
        // Sorted by size descending: file2(500), file3(400), file1(100)
        // But file2 is protected, so we get: file3(400), file1(100)
        // bytesNeeded=400
        // Check file3(size=400): 0+400 > 400? NO, add, accumulated=400
        // Check file1(size=100): 400+100 > 400? YES, break
        // Expected: [file3.txt]
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 500, 2000, true),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 400, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectBySize(files, 400);

        assertEquals(1, result.size());
        assertEquals(400, result.get(0).size());
    }

    @Test
    @DisplayName("selectAuto uses time-first for OK level")
    void selectAuto_usesTimeFirstForOK() {
        // OK level → time-first strategy
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 3000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 1000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 300, 2000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectAuto(files, 200, StorageLevel.OK);

        // file2 has oldest copyTime(1000) and size=200 meets requirement
        assertEquals(1, result.size());
        assertEquals(Path.of("file2.txt"), result.get(0).path());
    }

    @Test
    @DisplayName("selectAuto uses time-first for LOW level")
    void selectAuto_usesTimeFirstForLOW() {
        // LOW level → time-first strategy
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 3000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 1000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 300, 2000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectAuto(files, 200, StorageLevel.LOW);

        assertEquals(1, result.size());
        assertEquals(Path.of("file2.txt"), result.get(0).path());
    }

    @Test
    @DisplayName("selectAuto uses size-first for CRITICAL level")
    void selectAuto_usesSizeFirstForCRITICAL() {
        // CRITICAL level → size-first strategy
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 300, 2000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 200, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectAuto(files, 300, StorageLevel.CRITICAL);

        // file2 is largest(300) and matches bytesNeeded
        assertEquals(1, result.size());
        assertEquals(300, result.get(0).size());
    }

    @Test
    @DisplayName("selectByTime handles bytesNeeded larger than total size")
    void selectByTime_bytesNeededLargerThanTotal() {
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 2000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectByTime(files, 1000);

        // Both files should be selected since they don't exceed bytesNeeded
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("selectBySize selects multiple files when needed")
    void selectBySize_multipleFiles() {
        // Sorted by size descending: file3(300), file2(200), file1(100)
        // bytesNeeded=500
        // Add file3(size=300): accumulated=300, 300 >= 500? NO
        // Add file2(size=200): accumulated=500, 500 >= 500? YES, break
        // Expected: [file3.txt, file2.txt] with total size 500
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 2000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 300, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectBySize(files, 500);

        assertEquals(2, result.size());
        assertEquals(500, result.stream().mapToLong(FileSelector.FileMetadata::size).sum());
    }

    @Test
    @DisplayName("selectBySize handles bytesNeeded larger than total size")
    void selectBySize_bytesNeededLargerThanTotal() {
        List<FileSelector.FileMetadata> files = List.of(
                new FileSelector.FileMetadata(Path.of("file1.txt"), 100, 1000, false),
                new FileSelector.FileMetadata(Path.of("file2.txt"), 200, 2000, false),
                new FileSelector.FileMetadata(Path.of("file3.txt"), 300, 3000, false)
        );

        List<FileSelector.FileMetadata> result = FileSelector.selectBySize(files, 1000);

        // All files should be selected
        assertEquals(3, result.size());
        assertEquals(600, result.stream().mapToLong(FileSelector.FileMetadata::size).sum());
    }
}
