package com.superredrock.usbthief.core.event.worker;

import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class CopyCompletedEventTest {

    @Test
    void constructor_shouldCreateEvent() {
        Path source = Paths.get("/source/file.txt");
        Path dest = Paths.get("/dest/file.txt");

        CopyCompletedEvent event = new CopyCompletedEvent(
                source, dest, 1024L, 1024L, CopyResult.SUCCESS, "ABC123");

        assertEquals(source, event.sourcePath());
        assertEquals(dest, event.destinationPath());
        assertEquals(1024L, event.fileSize());
        assertEquals(1024L, event.bytesCopied());
        assertEquals(CopyResult.SUCCESS, event.result());
        assertEquals("ABC123", event.deviceSerial());
        assertTrue(event.timestamp() > 0);
    }

    @Test
    void constructor_shouldHandleNullDestination() {
        Path source = Paths.get("/source/file.txt");

        CopyCompletedEvent event = new CopyCompletedEvent(
                source, null, 1024L, 0L, CopyResult.FAIL, "ABC123");

        assertNull(event.destinationPath());
    }

    @Test
    void constructor_shouldHandleNullDeviceSerial() {
        Path source = Paths.get("/source/file.txt");

        CopyCompletedEvent event = new CopyCompletedEvent(
                source, null, 1024L, 0L, CopyResult.FAIL, null);

        assertEquals("", event.deviceSerial());
    }

    @Test
    void constructor_shouldRejectNullSourcePath() {
        assertThrows(IllegalArgumentException.class, () ->
                new CopyCompletedEvent(null, Paths.get("/dest"), 1024L, 1024L, CopyResult.SUCCESS, "ABC"));
    }

    @Test
    void constructor_shouldRejectNullResult() {
        assertThrows(IllegalArgumentException.class, () ->
                new CopyCompletedEvent(Paths.get("/source"), null, 1024L, 0L, null, "ABC"));
    }

    @Test
    void isSuccess_shouldReturnTrueForSuccessResult() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), Paths.get("/dest"), 1024L, 1024L, CopyResult.SUCCESS, "ABC");

        assertTrue(event.isSuccess());
        assertFalse(event.isFailure());
        assertFalse(event.isCancelled());
    }

    @Test
    void isFailure_shouldReturnTrueForFailResult() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), null, 1024L, 512L, CopyResult.FAIL, "ABC");

        assertTrue(event.isFailure());
        assertFalse(event.isSuccess());
        assertFalse(event.isCancelled());
    }

    @Test
    void isCancelled_shouldReturnTrueForCancelResult() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), null, 1024L, 256L, CopyResult.CANCEL, "ABC");

        assertTrue(event.isCancelled());
        assertFalse(event.isSuccess());
        assertFalse(event.isFailure());
    }

    @Test
    void progressPercentage_shouldReturnCorrectValue() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), Paths.get("/dest"), 1000L, 500L, CopyResult.SUCCESS, "ABC");

        assertEquals(0.5, event.progressPercentage(), 0.001);
    }

    @Test
    void progressPercentage_shouldReturnZeroForZeroFileSize() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), Paths.get("/dest"), 0L, 0L, CopyResult.SUCCESS, "ABC");

        assertEquals(0.0, event.progressPercentage(), 0.001);
    }

    @Test
    void progressPercentage_shouldReturnOneForCompleteCopy() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), Paths.get("/dest"), 1024L, 1024L, CopyResult.SUCCESS, "ABC");

        assertEquals(1.0, event.progressPercentage(), 0.001);
    }

    @Test
    void description_shouldContainAllInfo() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source/file.txt"), Paths.get("/dest/file.txt"),
                1024L, 512L, CopyResult.SUCCESS, "ABC");

        String desc = event.description();

        assertTrue(desc.contains("file.txt"));
        assertTrue(desc.contains("1024"));
        assertTrue(desc.contains("512"));
        assertTrue(desc.contains("SUCCESS"));
    }

    @Test
    void toString_shouldReturnDescription() {
        CopyCompletedEvent event = new CopyCompletedEvent(
                Paths.get("/source"), Paths.get("/dest"), 1024L, 1024L, CopyResult.SUCCESS, "ABC");

        assertEquals(event.description(), event.toString());
    }
}
