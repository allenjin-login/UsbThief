package com.superredrock.usbthief.core.event.worker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FileDiscoveredEventTest {

    @Test
    void constructor_shouldCreateEvent() {
        Path filePath = Paths.get("/test/file.txt");
        long fileSize = 1024L;
        String deviceSerial = "ABC123";

        FileDiscoveredEvent event = new FileDiscoveredEvent(filePath, fileSize, deviceSerial);

        assertEquals(filePath, event.filePath());
        assertEquals(fileSize, event.fileSize());
        assertEquals(deviceSerial, event.deviceSerial());
        assertTrue(event.timestamp() > 0);
    }

    @Test
    void constructor_shouldHandleNullDeviceSerial() {
        Path filePath = Paths.get("/test/file.txt");

        FileDiscoveredEvent event = new FileDiscoveredEvent(filePath, 1024L, null);

        assertEquals("", event.deviceSerial());
    }

    @Test
    void constructor_shouldRejectNullFilePath() {
        assertThrows(IllegalArgumentException.class, () ->
                new FileDiscoveredEvent(null, 1024L, "ABC123"));
    }

    @Test
    void description_shouldContainFileInfo() {
        Path filePath = Paths.get("/test/file.txt");

        FileDiscoveredEvent event = new FileDiscoveredEvent(filePath, 1024L, "ABC123");

        String description = event.description();

        assertTrue(description.contains("file.txt"));
        assertTrue(description.contains("1024"));
        assertTrue(description.contains("ABC123"));
    }

    @Test
    void toString_shouldReturnDescription() {
        Path filePath = Paths.get("/test/file.txt");

        FileDiscoveredEvent event = new FileDiscoveredEvent(filePath, 1024L, "ABC123");

        assertEquals(event.description(), event.toString());
    }

    @Test
    void timestamp_shouldBeSetAtCreation() {
        long before = System.currentTimeMillis();
        FileDiscoveredEvent event = new FileDiscoveredEvent(Paths.get("/test/file.txt"), 1024L, "ABC123");
        long after = System.currentTimeMillis();

        assertTrue(event.timestamp() >= before);
        assertTrue(event.timestamp() <= after);
    }
}
