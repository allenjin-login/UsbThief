package com.superredrock.usbthief.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CheckSumTest {

    @Test
    void equals_shouldReturnTrueForSameContent() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        CheckSum cs1 = new CheckSum(data);
        CheckSum cs2 = new CheckSum(data.clone());

        assertEquals(cs1, cs2);
    }

    @Test
    void equals_shouldReturnFalseForDifferentContent() {
        CheckSum cs1 = new CheckSum(new byte[]{1, 2, 3});
        CheckSum cs2 = new CheckSum(new byte[]{4, 5, 6});

        assertNotEquals(cs1, cs2);
    }

    @Test
    void equals_shouldReturnFalseForNull() {
        CheckSum cs = new CheckSum(new byte[]{1, 2, 3});

        assertNotEquals(null, cs);
    }

    @Test
    void equals_shouldReturnFalseForDifferentType() {
        CheckSum cs = new CheckSum(new byte[]{1, 2, 3});

        assertNotEquals("not a checksum", cs);
    }

    @Test
    void hashCode_shouldBeConsistent() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        CheckSum cs1 = new CheckSum(data);
        CheckSum cs2 = new CheckSum(data.clone());

        assertEquals(cs1.hashCode(), cs2.hashCode());
    }

    @Test
    void verify_shouldComputeSHA256(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "hello world");

        CheckSum checksum = CheckSum.verify(testFile);

        assertNotNull(checksum);
        assertEquals(32, checksum.context().length);
    }

    @Test
    void verify_shouldProduceSameResultForSameContent(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        String content = "same content";
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        CheckSum cs1 = CheckSum.verify(file1);
        CheckSum cs2 = CheckSum.verify(file2);

        assertEquals(cs1, cs2);
    }

    @Test
    void verify_shouldProduceDifferentResultForDifferentContent(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content A");
        Files.writeString(file2, "content B");

        CheckSum cs1 = CheckSum.verify(file1);
        CheckSum cs2 = CheckSum.verify(file2);

        assertNotEquals(cs1, cs2);
    }

    @Test
    void verify_shouldThrowForNonExistentFile() {
        Path nonExistent = Path.of("nonexistent_file_12345.txt");

        assertThrows(IOException.class, () -> CheckSum.verify(nonExistent));
    }

    @Test
    void compareTo_shouldReturnZero() {
        CheckSum cs1 = new CheckSum(new byte[]{1, 2, 3});
        CheckSum cs2 = new CheckSum(new byte[]{4, 5, 6});

        assertEquals(0, cs1.compareTo(cs2));
    }
}
