package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the overwrite strategies, including the batch-2 additions:
 * SKIP semantics and the data-safe RENAME default.
 */
@Timeout(10)
class OverwriteStrategyTest {

    // ─── RENAME ────────────────────────────────────────────────────────

    @Test
    void renameNeverOverwrites(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "new");
        Path target = Files.writeString(dir.resolve("a.txt.bak"), "old");
        assertFalse(OverwriteStrategy.RENAME.shouldOverwrite(source, target));
    }

    @Test
    void renameProducesTimestampedSibling(@TempDir Path dir) throws IOException {
        Path target = Files.writeString(dir.resolve("photo.jpg"), "old");
        Path resolved = OverwriteStrategy.RENAME.resolveTarget(target);

        assertNotEquals(target, resolved, "RENAME must resolve to a different path");
        assertEquals(target.getParent(), resolved.getParent(), "stays in the same folder");
        String name = resolved.getFileName().toString();
        assertTrue(name.startsWith("photo_"), "keeps the base name: " + name);
        assertTrue(name.endsWith(".jpg"), "keeps the extension: " + name);
    }

    @Test
    void renameHandlesExtensionlessFiles(@TempDir Path dir) throws IOException {
        Path target = Files.writeString(dir.resolve("README"), "old");
        Path resolved = OverwriteStrategy.RENAME.resolveTarget(target);
        assertTrue(resolved.getFileName().toString().startsWith("README_"),
                "no dot: suffix appended directly");
    }

    // ─── SKIP (batch-2) ────────────────────────────────────────────────

    @Test
    void skipNeverOverwritesAndKeepsTargetPath(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("src.txt"), "new");
        Path target = Files.writeString(dir.resolve("dst.txt"), "old");

        assertFalse(OverwriteStrategy.SKIP.shouldOverwrite(source, target));
        // Default resolveTarget returns the target unchanged → CopyTask treats this as "skip"
        assertEquals(target, OverwriteStrategy.SKIP.resolveTarget(target),
                "SKIP must leave the target path untouched so CopyTask skips the copy");
    }

    // ─── TIME_COMPARE ──────────────────────────────────────────────────

    @Test
    void timeCompareOverwritesWhenSourceNewer(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("s.txt"), "new");
        Path target = Files.writeString(dir.resolve("t.txt"), "old");
        // make source strictly newer
        Files.setLastModifiedTime(source, FileTime.from(Instant.now().plusSeconds(60)));

        assertTrue(OverwriteStrategy.TIME_COMPARE.shouldOverwrite(source, target));
    }

    @Test
    void timeCompareKeepsTargetWhenSourceOlder(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("s.txt"), "old");
        Path target = Files.writeString(dir.resolve("t.txt"), "new");
        Files.setLastModifiedTime(target, FileTime.from(Instant.now().plusSeconds(120)));

        assertFalse(OverwriteStrategy.TIME_COMPARE.shouldOverwrite(source, target));
        // resolveTarget unchanged → CopyTask skips
        assertEquals(target, OverwriteStrategy.TIME_COMPARE.resolveTarget(target));
    }

    // ─── ALWAYS_OVERWRITE ──────────────────────────────────────────────

    @Test
    void alwaysOverwriteOverwrites(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("s.txt"), "new");
        Path target = Files.writeString(dir.resolve("t.txt"), "old");
        assertTrue(OverwriteStrategy.ALWAYS_OVERWRITE.shouldOverwrite(source, target));
    }

    // ─── safeValueOf / default policy (batch-2) ────────────────────────

    @Test
    void safeValueOfParsesAllValues() {
        for (OverwriteStrategy s : OverwriteStrategy.values()) {
            assertEquals(s, OverwriteStrategy.safeValueOf(s.name()));
        }
        assertTrue(java.util.Arrays.asList(OverwriteStrategy.values()).contains(OverwriteStrategy.SKIP),
                "SKIP must exist as a first-class strategy");
    }

    @Test
    void safeValueOfFallsBackToDataSafeRename() {
        assertEquals(OverwriteStrategy.RENAME, OverwriteStrategy.safeValueOf("NOT_A_STRATEGY"),
                "invalid input must fall back to RENAME, never to an overwriting strategy");
        assertEquals(OverwriteStrategy.RENAME, OverwriteStrategy.safeValueOf(null));
    }
}
