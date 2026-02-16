package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PriorityRuleTest {

    @TempDir
    Path tempDir;

    private final PriorityRule rule = new PriorityRule();

    @Test
    void calculatePriority_pathOnly_shouldReturnValidPriority() throws IOException {
        Path pdfFile = tempDir.resolve("test.pdf");
        Files.writeString(pdfFile, "content");

        int priority = rule.calculatePriority(pdfFile);

        assertTrue(priority > 0 && priority <= 100);
    }

    @Test
    void calculatePriority_directory_shouldReturnHigherPriority() throws IOException {
        Path dir = tempDir.resolve("subdir");
        Files.createDirectory(dir);

        int priority = rule.calculatePriority(dir);

        assertEquals(11, priority);
    }

    @Test
    void calculatePriority_smallFile_shouldHaveBonus() throws IOException {
        Path smallFile = tempDir.resolve("small.txt");
        Files.writeString(smallFile, "x");

        int priority = rule.calculatePriority(smallFile);

        assertTrue(priority >= 5);
    }

    @Test
    void calculatePriority_tmpFile_shouldHaveLowPriority() throws IOException {
        Path tmpFile = tempDir.resolve("temp.tmp");
        Files.writeString(tmpFile, "content");

        int priority = rule.calculatePriority(tmpFile);

        assertTrue(priority < 10);
    }
}
