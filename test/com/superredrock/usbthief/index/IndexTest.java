package com.superredrock.usbthief.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private static final byte[] HASH_A = new byte[32];
    private static final byte[] HASH_B = new byte[32];
    static { HASH_B[0] = 1; }

    private Index index;

    @BeforeEach
    void setUp() {
        try {
            var field = Index.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        index = Index.getInstance();
    }

    @Test
    void newFileIsNotDuplicate() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\newfile.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        assertFalse(index.checkDuplicate(key, cs));
    }

    @Test
    void addedFileIsDuplicate() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, key, 100);
        assertTrue(index.checkDuplicate(key, cs));
    }

    @Test
    void samePathDifferentContentIsNotDuplicate() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum csA = new CheckSum(HASH_A);
        CheckSum csB = new CheckSum(HASH_B);
        index.addFile(csA, key, 100);
        assertFalse(index.checkDuplicate(key, csB));
    }

    @Test
    void differentPathSameContentIsNotDuplicate() {
        IndexKey keyA = new IndexKey("SERIAL1", Path.of("E:\\fileA.txt"));
        IndexKey keyB = new IndexKey("SERIAL1", Path.of("E:\\fileB.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, keyA, 100);
        assertFalse(index.checkDuplicate(keyB, cs));
    }

    @Test
    void differentSerialSamePathIsNotDuplicate() {
        IndexKey keyA = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        IndexKey keyB = new IndexKey("SERIAL2", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, keyA, 100);
        assertFalse(index.checkDuplicate(keyB, cs));
    }

    @Test
    void addFileReturnsTrueForNewEntry() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        assertTrue(index.addFile(cs, key, 100));
        assertFalse(index.addFile(cs, key, 100));
    }

    @Test
    void clearRemovesAllEntries() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, key, 100);
        index.clear();
        assertFalse(index.checkDuplicate(key, cs));
        assertEquals(0, index.getDigestSize());
    }

    @Test
    void getDigestSizeReturnsCorrectCount() {
        assertEquals(0, index.getDigestSize());
        index.addFile(new CheckSum(HASH_A), new IndexKey("SERIAL1", Path.of("E:\\a.txt")), 10);
        assertEquals(1, index.getDigestSize());
        index.addFile(new CheckSum(HASH_B), new IndexKey("SERIAL1", Path.of("E:\\b.txt")), 20);
        assertEquals(2, index.getDigestSize());
    }
}
