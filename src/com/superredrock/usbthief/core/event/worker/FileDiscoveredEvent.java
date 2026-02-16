package com.superredrock.usbthief.core.event.worker;

import com.superredrock.usbthief.core.event.Event;

import java.nio.file.Path;

public final class FileDiscoveredEvent implements Event {

    private final Path filePath;
    private final long fileSize;
    private final String deviceSerial;
    private final long timestamp;

    public FileDiscoveredEvent(Path filePath, long fileSize, String deviceSerial) {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath cannot be null");
        }
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.timestamp = System.currentTimeMillis();
    }

    public Path filePath() {
        return filePath;
    }

    public long fileSize() {
        return fileSize;
    }

    public String deviceSerial() {
        return deviceSerial;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("FileDiscoveredEvent: %s (size: %d) on device %s at %d",
                filePath.getFileName(), fileSize, deviceSerial, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
