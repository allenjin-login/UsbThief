package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.event.storage.StorageLevel;
import com.superredrock.usbthief.worker.FileSelector.FileMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileSelectorTest {

    private static FileMetadata fm(String name, long size, long copyTime, boolean isProtected) {
        return new FileMetadata(Path.of(name), size, copyTime, isProtected);
    }

    @Test
    void selectByTimeBasic() {
        List<FileMetadata> files = List.of(
                fm("old.txt", 60, 1000, false),
                fm("mid.txt", 60, 2000, false),
                fm("new.txt", 60, 3000, false)
        );

        List<FileMetadata> selected = FileSelector.selectByTime(files, 100);
        assertEquals(2, selected.size());
        assertEquals("old.txt", selected.get(0).path().toString());
        assertEquals("mid.txt", selected.get(1).path().toString());
    }

    @Test
    void selectByTimeProtected() {
        List<FileMetadata> files = List.of(
                fm("protected.txt", 100, 1000, true),
                fm("normal.txt", 50, 2000, false)
        );

        List<FileMetadata> selected = FileSelector.selectByTime(files, 100);
        assertEquals(1, selected.size());
        assertEquals("normal.txt", selected.get(0).path().toString());
    }

    @Test
    void selectByTimeNullFiles() {
        assertEquals(List.of(), FileSelector.selectByTime(null, 100));
    }

    @Test
    void selectByTimeBytesNeededZero() {
        List<FileMetadata> files = List.of(fm("a.txt", 100, 1000, false));
        assertEquals(List.of(), FileSelector.selectByTime(files, 0));
        assertEquals(List.of(), FileSelector.selectByTime(files, -1));
    }

    @Test
    void selectByTimeInsufficientFiles() {
        List<FileMetadata> files = List.of(
                fm("a.txt", 30, 1000, false),
                fm("b.txt", 30, 2000, false)
        );

        List<FileMetadata> selected = FileSelector.selectByTime(files, 100);
        assertEquals(2, selected.size()); // Returns all non-protected
    }

    @Test
    void selectBySizeBasic() {
        List<FileMetadata> files = List.of(
                fm("small.txt", 30, 1000, false),
                fm("large.txt", 100, 2000, false),
                fm("medium.txt", 60, 3000, false)
        );

        List<FileMetadata> selected = FileSelector.selectBySize(files, 80);
        assertEquals(1, selected.size());
        assertEquals("large.txt", selected.get(0).path().toString());
    }

    @Test
    void selectBySizeProtected() {
        List<FileMetadata> files = List.of(
                fm("big-protected.txt", 200, 1000, true),
                fm("normal.txt", 50, 2000, false)
        );

        List<FileMetadata> selected = FileSelector.selectBySize(files, 100);
        assertEquals(1, selected.size());
        assertEquals("normal.txt", selected.get(0).path().toString());
    }

    @Test
    void selectBySizeNullFiles() {
        assertEquals(List.of(), FileSelector.selectBySize(null, 100));
    }

    @Test
    void selectBySizeBytesNeededZero() {
        List<FileMetadata> files = List.of(fm("a.txt", 100, 1000, false));
        assertEquals(List.of(), FileSelector.selectBySize(files, 0));
    }

    @Test
    void selectAutoCritical() {
        List<FileMetadata> files = List.of(
                fm("small.txt", 30, 1000, false),
                fm("large.txt", 100, 2000, false)
        );

        List<FileMetadata> autoResult = FileSelector.selectAuto(files, 80, StorageLevel.CRITICAL);
        List<FileMetadata> sizeResult = FileSelector.selectBySize(files, 80);
        assertEquals(sizeResult, autoResult);
    }

    @Test
    void selectAutoOK() {
        List<FileMetadata> files = List.of(
                fm("old.txt", 60, 1000, false),
                fm("new.txt", 60, 2000, false)
        );

        List<FileMetadata> autoResult = FileSelector.selectAuto(files, 60, StorageLevel.OK);
        List<FileMetadata> timeResult = FileSelector.selectByTime(files, 60);
        assertEquals(timeResult, autoResult);
    }

    @Test
    void selectAutoLOW() {
        List<FileMetadata> files = List.of(
                fm("a.txt", 50, 1000, false),
                fm("b.txt", 50, 2000, false)
        );

        List<FileMetadata> autoResult = FileSelector.selectAuto(files, 60, StorageLevel.LOW);
        List<FileMetadata> timeResult = FileSelector.selectByTime(files, 60);
        assertEquals(timeResult, autoResult);
    }

    @Test
    void emptyListReturnsEmpty() {
        assertEquals(List.of(), FileSelector.selectByTime(List.of(), 100));
        assertEquals(List.of(), FileSelector.selectBySize(List.of(), 100));
    }

    @Test
    void singleFileExceedsTarget() {
        List<FileMetadata> files = List.of(
                fm("big.txt", 200, 1000, false)
        );

        List<FileMetadata> selected = FileSelector.selectByTime(files, 50);
        assertEquals(1, selected.size());
        assertEquals("big.txt", selected.get(0).path().toString());
    }

    @Test
    void allProtectedReturnsEmpty() {
        List<FileMetadata> files = List.of(
                fm("a.txt", 100, 1000, true),
                fm("b.txt", 100, 2000, true)
        );

        assertEquals(List.of(), FileSelector.selectByTime(files, 50));
        assertEquals(List.of(), FileSelector.selectBySize(files, 50));
    }
}
