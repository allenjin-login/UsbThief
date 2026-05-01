package com.superredrock.usbthief.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private static final byte[] HASH_A = new byte[32];
    private static final byte[] HASH_B = new byte[32];
    static { HASH_B[0] = 1; }

    private Index index;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        try {
            var field = Index.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        index = Index.createForTest(dir.resolve("test.idx"));
    }

    @Test
    void newFileIsNotDuplicate() {
        Path file = Path.of("E:\\newfile.txt");
        CheckSum cs = new CheckSum(HASH_A);
        assertFalse(index.checkDuplicate(file, cs));
    }

    @Test
    void addedFileIsDuplicate() {
        Path file = Path.of("E:\\file.txt");
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, file, 100);
        assertTrue(index.checkDuplicate(file, cs));
    }

    @Test
    void samePathDifferentContentIsNotDuplicate() {
        Path file = Path.of("E:\\file.txt");
        CheckSum csA = new CheckSum(HASH_A);
        CheckSum csB = new CheckSum(HASH_B);
        index.addFile(csA, file, 100);
        assertFalse(index.checkDuplicate(file, csB));
    }

    @Test
    void differentPathSameContentIsNotDuplicate() {
        Path fileA = Path.of("E:\\fileA.txt");
        Path fileB = Path.of("E:\\fileB.txt");
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, fileA, 100);
        assertFalse(index.checkDuplicate(fileB, cs));
    }

    @Test
    void addFileReturnsTrueForNewEntry() {
        Path file = Path.of("E:\\file.txt");
        CheckSum cs = new CheckSum(HASH_A);
        assertTrue(index.addFile(cs, file, 100));
        assertFalse(index.addFile(cs, file, 100));
    }

    @Test
    void clearRemovesAllEntries() {
        Path file = Path.of("E:\\file.txt");
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, file, 100);
        index.clear();
        assertFalse(index.checkDuplicate(file, cs));
        assertEquals(0, index.getDigestSize());
    }

    @Test
    void getDigestSizeReturnsCorrectCount() {
        assertEquals(0, index.getDigestSize());
        index.addFile(new CheckSum(HASH_A), Path.of("E:\\a.txt"), 10);
        assertEquals(1, index.getDigestSize());
        index.addFile(new CheckSum(HASH_B), Path.of("E:\\b.txt"), 20);
        assertEquals(2, index.getDigestSize());
    }
}
