package com.superredrock.usbthief.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndexDiskStoreTest {

    private static final byte[] HASH_A = new byte[32]; // all zeros
    private static final byte[] HASH_B = new byte[32];
    static { HASH_B[0] = 1; }

    @Test
    void loadReturnsEmptyMapForNonexistentFile(@TempDir Path dir) {
        IndexDiskStore store = new IndexDiskStore(dir.resolve("no-such-file.idx"));
        Map<Path, CheckSum> entries = store.load();
        assertTrue(entries.isEmpty());
    }

    @Test
    void appendAndLoadRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("test.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\test\\file.txt");
        CheckSum checksumA = new CheckSum(HASH_A);
        store.append(pathA, checksumA);

        Map<Path, CheckSum> entries = store.load();
        assertEquals(1, entries.size());
        assertEquals(checksumA, entries.get(pathA));
    }

    @Test
    void appendMultipleEntries(@TempDir Path dir) {
        Path file = dir.resolve("test.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\docs\\a.pdf");
        Path pathB = Path.of("E:\\docs\\b.pdf");
        CheckSum csA = new CheckSum(HASH_A);
        CheckSum csB = new CheckSum(HASH_B);

        store.append(pathA, csA);
        store.append(pathB, csB);

        Map<Path, CheckSum> entries = store.load();
        assertEquals(2, entries.size());
        assertEquals(csA, entries.get(pathA));
        assertEquals(csB, entries.get(pathB));
    }

    @Test
    void compactDeduplicatesKeepingLatest(@TempDir Path dir) {
        Path file = dir.resolve("test.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\test\\file.txt");
        CheckSum csOld = new CheckSum(HASH_A);
        CheckSum csNew = new CheckSum(HASH_B);

        store.append(pathA, csOld);
        store.append(pathA, csNew); // same path, different hash

        // Compact with the latest map
        store.compact(Map.of(pathA, csNew));

        Map<Path, CheckSum> entries = store.load();
        assertEquals(1, entries.size());
        assertEquals(csNew, entries.get(pathA));
    }

    @Test
    void loadHandlesCorruptFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("corrupt.idx");
        // Write garbage
        java.nio.file.Files.writeString(file, "not a valid index file");
        IndexDiskStore store = new IndexDiskStore(file);

        Map<Path, CheckSum> entries = store.load();
        assertTrue(entries.isEmpty());
    }

    @Test
    void loadEmptyFileReturnsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("empty.idx");
        Files.createFile(file);
        IndexDiskStore store = new IndexDiskStore(file);
        Map<Path, CheckSum> entries = store.load();
        assertTrue(entries.isEmpty());
    }

    @Test
    void appendDuplicatePathKeepsLatest(@TempDir Path dir) {
        Path file = dir.resolve("dup.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\test\\file.txt");
        CheckSum csOld = new CheckSum(HASH_A);
        CheckSum csNew = new CheckSum(HASH_B);

        store.append(pathA, csOld);
        store.append(pathA, csNew);

        Map<Path, CheckSum> entries = store.load();
        assertEquals(1, entries.size());
        assertEquals(csNew, entries.get(pathA));
    }

    @Test
    void clearRemovesAllData(@TempDir Path dir) {
        Path file = dir.resolve("clear.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        store.append(Path.of("E:\\a.txt"), new CheckSum(HASH_A));
        store.append(Path.of("E:\\b.txt"), new CheckSum(HASH_B));

        store.clear();

        Map<Path, CheckSum> entries = store.load();
        assertTrue(entries.isEmpty());
    }

    @Test
    void largeDataset(@TempDir Path dir) {
        Path file = dir.resolve("large.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        int count = 100;
        for (int i = 0; i < count; i++) {
            byte[] hash = new byte[32];
            hash[0] = (byte) i;
            store.append(Path.of("E:\\file_" + i + ".txt"), new CheckSum(hash));
        }

        Map<Path, CheckSum> entries = store.load();
        assertEquals(count, entries.size());
    }
}
