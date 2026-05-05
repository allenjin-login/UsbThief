package com.superredrock.usbthief.index;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HashAlgorithmTest {

    private static final long SEED = 42;
    private static final Random RNG = new Random(SEED);

    @TempDir
    static Path tempDir;

    private static Path emptyFile;
    private static Path smallFile;
    private static Path largeFile;
    private static Path binaryFile;

    @BeforeAll
    static void createTestFiles() throws IOException {
        emptyFile = tempDir.resolve("empty.dat");
        Files.write(emptyFile, new byte[0]);

        smallFile = tempDir.resolve("small.dat");
        byte[] small = new byte[64];
        new Random(SEED).nextBytes(small);
        Files.write(smallFile, small);

        largeFile = tempDir.resolve("large.dat");
        byte[] large = new byte[64 * 1024 + 7]; // > one buffer read
        new Random(SEED).nextBytes(large);
        Files.write(largeFile, large);

        binaryFile = tempDir.resolve("binary.dat");
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) binary[i] = (byte) i;
        Files.write(binaryFile, binary);
    }

    @Test
    void outputLengthMatchesEnumDeclaration() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        for (HashAlgorithm algo : HashAlgorithm.values()) {
            CheckSum cs = algo.compute(smallFile, buf);
            assertEquals(algo.outputLength(), cs.context().length,
                    algo.name() + " output length mismatch");
            buf.clear();
        }
    }

    @Test
    void sameFileProducesSameHash() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        for (HashAlgorithm algo : HashAlgorithm.values()) {
            CheckSum a = algo.compute(smallFile, buf);
            buf.clear();
            CheckSum b = algo.compute(smallFile, buf);
            buf.clear();
            assertEquals(a, b, algo.name() + " should be deterministic");
        }
    }

    @Test
    void differentFilesProduceDifferentHashes() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        for (HashAlgorithm algo : HashAlgorithm.values()) {
            CheckSum hashSmall = algo.compute(smallFile, buf);
            buf.clear();
            CheckSum hashLarge = algo.compute(largeFile, buf);
            buf.clear();
            assertNotEquals(hashSmall, hashLarge,
                    algo.name() + " should differ for different content");
        }
    }

    @Test
    void emptyFileHashIsValid() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        for (HashAlgorithm algo : HashAlgorithm.values()) {
            CheckSum cs = algo.compute(emptyFile, buf);
            assertNotNull(cs);
            assertEquals(algo.outputLength(), cs.context().length);
            buf.clear();
        }
    }

    @Test
    void emptyFileHashIsConsistent() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        for (HashAlgorithm algo : HashAlgorithm.values()) {
            CheckSum a = algo.compute(emptyFile, buf);
            buf.clear();
            CheckSum b = algo.compute(emptyFile, buf);
            buf.clear();
            assertEquals(a, b, algo.name() + " empty file hash should be consistent");
        }
    }

    @Test
    void binaryFileProducesHash() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        for (HashAlgorithm algo : HashAlgorithm.values()) {
            CheckSum cs = algo.compute(binaryFile, buf);
            assertNotNull(cs);
            assertEquals(algo.outputLength(), cs.context().length);
            buf.clear();
        }
    }

    @Test
    void fromIdResolvesCorrectly() {
        assertEquals(HashAlgorithm.SHA_256, HashAlgorithm.fromId("SHA-256"));
        assertEquals(HashAlgorithm.MD5, HashAlgorithm.fromId("MD5"));
        assertEquals(HashAlgorithm.CRC_8, HashAlgorithm.fromId("CRC-8"));
        assertEquals(HashAlgorithm.CRC_16, HashAlgorithm.fromId("CRC-16"));
        assertEquals(HashAlgorithm.CRC_32, HashAlgorithm.fromId("CRC-32"));
        assertEquals(HashAlgorithm.CRC_64, HashAlgorithm.fromId("CRC-64"));
    }

    @Test
    void fromIdCaseInsensitive() {
        assertEquals(HashAlgorithm.SHA_256, HashAlgorithm.fromId("sha-256"));
        assertEquals(HashAlgorithm.MD5, HashAlgorithm.fromId("md5"));
        assertEquals(HashAlgorithm.CRC_32, HashAlgorithm.fromId("crc-32"));
    }

    @Test
    void fromIdFallsBackToSha256() {
        assertEquals(HashAlgorithm.SHA_256, HashAlgorithm.fromId("unknown"));
        assertEquals(HashAlgorithm.SHA_256, HashAlgorithm.fromId(""));
        assertEquals(HashAlgorithm.SHA_256, HashAlgorithm.fromId(null));
    }

    @Test
    void idReturnsCorrectString() {
        assertEquals("SHA-256", HashAlgorithm.SHA_256.id());
        assertEquals("MD5", HashAlgorithm.MD5.id());
        assertEquals("CRC-8", HashAlgorithm.CRC_8.id());
        assertEquals("CRC-16", HashAlgorithm.CRC_16.id());
        assertEquals("CRC-32", HashAlgorithm.CRC_32.id());
        assertEquals("CRC-64", HashAlgorithm.CRC_64.id());
    }

    @Test
    void crc32MatchesJavaBuiltin() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        byte[] fileBytes = Files.readAllBytes(smallFile);
        java.util.zip.CRC32 builtin = new java.util.zip.CRC32();
        builtin.update(fileBytes);
        long expected = builtin.getValue();

        CheckSum cs = HashAlgorithm.CRC_32.compute(smallFile, buf);
        long actual = bytesToLong(cs.context());
        assertEquals(expected, actual, "CRC-32 should match java.util.zip.CRC32");
    }

    @Test
    void sha256MatchesMessageDigest() throws Exception {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        byte[] fileBytes = Files.readAllBytes(smallFile);
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] expected = md.digest(fileBytes);

        CheckSum cs = HashAlgorithm.SHA_256.compute(smallFile, buf);
        assertArrayEquals(expected, cs.context());
    }

    @Test
    void md5MatchesMessageDigest() throws Exception {
        ByteBuffer buf = ByteBuffer.allocateDirect(8192);
        byte[] fileBytes = Files.readAllBytes(smallFile);
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] expected = md.digest(fileBytes);

        CheckSum cs = HashAlgorithm.MD5.compute(smallFile, buf);
        assertArrayEquals(expected, cs.context());
    }

    private static long bytesToLong(byte[] bytes) {
        long value = 0;
        for (byte b : bytes) value = (value << 8) | (b & 0xFF);
        return value;
    }
}
