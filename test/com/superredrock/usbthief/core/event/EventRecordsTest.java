package com.superredrock.usbthief.core.event;

import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.superredrock.usbthief.index.CheckSum;
import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EventRecordsTest {

    // === CopyCompletedEvent ===

    @Test
    void copyCompletedSuccess() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), Path.of("out\\src.txt"), 100, 100, CopyResult.SUCCESS, "s1");
        assertTrue(e.isSuccess());
        assertFalse(e.isFailure());
        assertFalse(e.isCancelled());
    }

    @Test
    void copyCompletedFailure() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), null, 100, 50, CopyResult.FAIL, "s1");
        assertFalse(e.isSuccess());
        assertTrue(e.isFailure());
        assertFalse(e.isCancelled());
    }

    @Test
    void copyCompletedCancelled() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), null, 100, 30, CopyResult.CANCEL, "s1");
        assertTrue(e.isCancelled());
    }

    @Test
    void copyCompletedSkipped() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), null, 100, 0, CopyResult.SKIPPED, "s1");
        assertFalse(e.isSuccess());
        assertFalse(e.isFailure());
    }

    @Test
    void copyCompletedProgressPercentage() {
        CopyCompletedEvent full = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), Path.of("out"), 100, 100, CopyResult.SUCCESS, "s1");
        assertEquals(1.0, full.progressPercentage(), 0.001);

        CopyCompletedEvent partial = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), Path.of("out"), 100, 50, CopyResult.FAIL, "s1");
        assertEquals(0.5, partial.progressPercentage(), 0.001);
    }

    @Test
    void copyCompletedProgressZeroSize() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\src.txt"), Path.of("out"), 0, 0, CopyResult.SUCCESS, "s1");
        assertEquals(0.0, e.progressPercentage(), 0.001);
    }

    @Test
    void copyCompletedNullSourceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CopyCompletedEvent(null, null, 0, 0, CopyResult.SUCCESS, "s1"));
    }

    @Test
    void copyCompletedNullResultThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CopyCompletedEvent(Path.of("E:\\a"), null, 0, 0, null, "s1"));
    }

    @Test
    void copyCompletedNullSerialDefaultsEmpty() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\a"), Path.of("out"), 10, 10, CopyResult.SUCCESS, null);
        assertEquals("", e.deviceSerial());
    }

    @Test
    void copyCompletedDescription() {
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 10, 10, CopyResult.SUCCESS, "s1");
        assertNotNull(e.description());
        assertFalse(e.description().isEmpty());
    }

    @Test
    void copyCompletedTimestamp() {
        long before = System.currentTimeMillis();
        CopyCompletedEvent e = new CopyCompletedEvent(
                Path.of("E:\\a.txt"), Path.of("out"), 10, 10, CopyResult.SUCCESS, "s1");
        long after = System.currentTimeMillis();
        assertTrue(e.timestamp() >= before && e.timestamp() <= after);
    }

    // === FileDiscoveredEvent ===

    @Test
    void fileDiscoveredFields() {
        FileDiscoveredEvent e = new FileDiscoveredEvent(Path.of("E:\\doc.pdf"), 2048, "dev1");
        assertEquals(Path.of("E:\\doc.pdf"), e.filePath());
        assertEquals(2048, e.fileSize());
        assertEquals("dev1", e.deviceSerial());
    }

    @Test
    void fileDiscoveredNullSerialDefaultsEmpty() {
        FileDiscoveredEvent e = new FileDiscoveredEvent(Path.of("E:\\a.txt"), 100, null);
        assertEquals("", e.deviceSerial());
    }

    @Test
    void fileDiscoveredNullPathThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileDiscoveredEvent(null, 100, "s1"));
    }

    @Test
    void fileDiscoveredDescriptionAndTimestamp() {
        FileDiscoveredEvent e = new FileDiscoveredEvent(Path.of("E:\\a.txt"), 50, "s1");
        assertNotNull(e.description());
        assertTrue(e.timestamp() > 0);
    }

    // === DuplicateDetectedEvent ===

    @Test
    void duplicateDetectedFields() {
        CheckSum cs = new CheckSum(new byte[]{1, 2, 3});
        DuplicateDetectedEvent e = new DuplicateDetectedEvent(cs, Path.of("E:\\dup.txt"), 5);
        assertEquals(cs, e.checksum());
        assertEquals(Path.of("E:\\dup.txt"), e.filePath());
        assertEquals(5, e.duplicateCount());
    }

    @Test
    void duplicateDetectedDescription() {
        CheckSum cs = new CheckSum(new byte[]{1});
        DuplicateDetectedEvent e = new DuplicateDetectedEvent(cs, Path.of("E:\\a.txt"), 1);
        assertNotNull(e.description());
        assertTrue(e.timestamp() > 0);
    }

    // === FileIndexedEvent ===

    @Test
    void fileIndexedFields() {
        CheckSum cs = new CheckSum(new byte[]{4, 5, 6});
        FileIndexedEvent e = new FileIndexedEvent(cs, Path.of("E:\\new.txt"), 500, 1);
        assertEquals(cs, e.checksum());
        assertEquals(Path.of("E:\\new.txt"), e.filePath());
    }

    @Test
    void fileIndexedDescription() {
        CheckSum cs = new CheckSum(new byte[]{1});
        FileIndexedEvent e = new FileIndexedEvent(cs, Path.of("E:\\a.txt"), 100, 1);
        assertNotNull(e.description());
    }
}
