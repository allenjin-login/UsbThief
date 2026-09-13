package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The report row derives the values the CSV prints (formatted time, throughput). These are the
 * calculations that used to live nowhere, so they are pinned here.
 */
class CopyHistoryEntryTest {

    @Test
    void nullTextFieldsBecomeEmptyStrings() {
        CopyHistoryEntry entry = new CopyHistoryEntry(0L, null, null, null, 1L, 1L, CopyResult.SUCCESS);
        assertEquals("", entry.volumeLabel());
        assertEquals("", entry.sourceFileName());
        assertEquals("", entry.destinationPath());
    }

    @Test
    void negativeSizeAndDurationAreClampedToZero() {
        CopyHistoryEntry entry = new CopyHistoryEntry(0L, "A", "a.txt", "C:\\a.txt", -5L, -7L, CopyResult.FAIL);
        assertEquals(0L, entry.sizeBytes());
        assertEquals(0L, entry.durationMillis());
    }

    @Test
    void aNullResultIsReportedAsFailure() {
        CopyHistoryEntry entry = new CopyHistoryEntry(0L, "A", "a.txt", "C:\\a.txt", 1L, 1L, null);
        assertEquals(CopyResult.FAIL, entry.result());
        assertTrue(entry.isFailure());
    }

    @Test
    void throughputIsZeroWhenTheDurationIsUnknown() {
        CopyHistoryEntry entry = new CopyHistoryEntry(0L, "A", "a.txt", "C:\\a.txt", 1048576L, 0L, CopyResult.SUCCESS);
        assertEquals(0.0, entry.megabytesPerSecond());
    }

    @Test
    void throughputIsComputedFromSizeAndDuration() {
        // 10 MiB in 2 s -> 5 MB/s
        CopyHistoryEntry entry = new CopyHistoryEntry(
                0L, "A", "a.txt", "C:\\a.txt", 10L * 1024L * 1024L, 2000L, CopyResult.SUCCESS);
        assertEquals(5.0, entry.megabytesPerSecond(), 1e-9);
        assertFalse(entry.isFailure());
    }

    @Test
    void timestampIsFormattedAsASortableLocalDateTime() {
        CopyHistoryEntry entry = new CopyHistoryEntry(
                1_700_000_000_000L, "A", "a.txt", "C:\\a.txt", 1L, 1L, CopyResult.SUCCESS);
        assertTrue(entry.formattedTimestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "unexpected format: " + entry.formattedTimestamp());
    }

    @Test
    void everyOutcomeIsClassified() {
        assertFalse(new CopyHistoryEntry(0L, "A", "a.txt", "", 1L, 1L, CopyResult.SUCCESS).isFailure());
        assertFalse(new CopyHistoryEntry(0L, "A", "a.txt", "", 1L, 1L, CopyResult.CANCEL).isFailure());
        assertFalse(new CopyHistoryEntry(0L, "A", "a.txt", "", 1L, 1L, CopyResult.SKIPPED).isFailure());
        assertTrue(new CopyHistoryEntry(0L, "A", "a.txt", "", 1L, 1L, CopyResult.FAIL).isFailure());
    }
}
