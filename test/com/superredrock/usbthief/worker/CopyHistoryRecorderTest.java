package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recorder is the data source of the exported report, so its two jobs are covered here:
 * it stays bounded (one session copies hundreds of thousands of files) and it maps every field
 * of a {@link CopyCompletedEvent} into a row.
 */
class CopyHistoryRecorderTest {

    private static CopyHistoryEntry entry(String source, CopyResult result) {
        return new CopyHistoryEntry(1_700_000_000_000L, "VOL", source, "/out/" + source, 10L, 5L, result);
    }

    @Test
    void rejectsANonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new CopyHistoryRecorder(0));
        assertThrows(IllegalArgumentException.class, () -> new CopyHistoryRecorder(-1));
    }

    @Test
    void keepsRecordsInArrivalOrder() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(10);
        recorder.record(entry("a.txt", CopyResult.SUCCESS));
        recorder.record(entry("b.txt", CopyResult.FAIL));

        List<CopyHistoryEntry> snapshot = recorder.snapshot();
        assertEquals(2, snapshot.size());
        assertEquals("a.txt", snapshot.getFirst().sourceFileName());
        assertEquals("b.txt", snapshot.getLast().sourceFileName());
    }

    @Test
    void dropsTheOldestRecordsOnceFull() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(3);
        for (int i = 1; i <= 5; i++) {
            recorder.record(entry("f" + i + ".txt", CopyResult.SUCCESS));
        }

        assertEquals(3, recorder.size());
        assertEquals(List.of("f3.txt", "f4.txt", "f5.txt"),
                recorder.snapshot().stream().map(CopyHistoryEntry::sourceFileName).toList());
    }

    @Test
    void snapshotIsADetachedCopy() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(4);
        recorder.record(entry("a.txt", CopyResult.SUCCESS));

        List<CopyHistoryEntry> snapshot = recorder.snapshot();
        assertNotSame(snapshot, recorder.snapshot());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(entry("b.txt", CopyResult.SUCCESS)));

        recorder.clear();
        assertEquals(1, snapshot.size(), "clearing the recorder must not mutate an issued snapshot");
    }

    @Test
    void clearEmptiesTheBuffer() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(4);
        recorder.record(entry("a.txt", CopyResult.SUCCESS));
        recorder.clear();

        assertEquals(0, recorder.size());
        assertTrue(recorder.snapshot().isEmpty());
    }

    @Test
    void nullRecordsAreIgnored() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(4);
        recorder.record(null);
        recorder.onCopyCompleted(null);

        assertEquals(0, recorder.size());
    }

    @Test
    void mapsEveryFieldOfACompletionEvent() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(4);

        recorder.onCopyCompleted(new CopyCompletedEvent(
                Path.of("/mnt/usb/photos/holiday.jpg"),
                Path.of("/tmp/out/holiday.jpg"),
                4096L, 4096L, CopyResult.SUCCESS, "", 250L));

        CopyHistoryEntry recorded = recorder.snapshot().getLast();
        assertEquals("holiday.jpg", recorded.sourceFileName());
        assertEquals("/tmp/out/holiday.jpg", recorded.destinationPath());
        assertEquals(4096L, recorded.sizeBytes());
        assertEquals(250L, recorded.durationMillis(), "the duration drives the speed column");
        assertEquals(CopyResult.SUCCESS, recorded.result());
        assertEquals("", recorded.volumeLabel(), "an unknown device leaves the label empty");
    }

    /**
     * Without a registered volume the label falls back to the serial number, so a report row is
     * never blank even for a device that has already been ejected.
     */
    @Test
    void volumeLabelFallsBackToTheSerialNumber() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(4);

        recorder.onCopyCompleted(new CopyCompletedEvent(
                Path.of("/mnt/usb/a.txt"), Path.of("/tmp/out/a.txt"),
                1L, 1L, CopyResult.FAIL, "SERIAL-XYZ", 1L));

        assertEquals("SERIAL-XYZ", recorder.snapshot().getLast().volumeLabel());
    }

    @Test
    void recordsFailuresAsWellAsSuccesses() {
        CopyHistoryRecorder recorder = new CopyHistoryRecorder(4);

        recorder.onCopyCompleted(new CopyCompletedEvent(
                Path.of("/mnt/usb/a.txt"), null, 100L, 0L, CopyResult.FAIL, ""));
        recorder.onCopyCompleted(new CopyCompletedEvent(
                Path.of("/mnt/usb/b.txt"), Path.of("/tmp/out/b.txt"),
                100L, 100L, CopyResult.SUCCESS, ""));

        List<CopyHistoryEntry> snapshot = recorder.snapshot();
        assertEquals(2, snapshot.size(), "a failure is part of the history, not an omission");
        assertTrue(snapshot.getFirst().isFailure());
        assertEquals("", snapshot.getFirst().destinationPath(), "a failed copy has no target");
        assertEquals(CopyResult.SUCCESS, snapshot.getLast().result());
    }

    @Test
    void theSharedInstanceIsLazyAndSingle() {
        CopyHistoryRecorder shared = CopyHistoryRecorder.getInstance();

        assertNotNull(shared);
        assertSame(shared, CopyHistoryRecorder.getInstance());
        assertEquals(CopyHistoryRecorder.DEFAULT_CAPACITY, shared.capacity());
    }
}
