package com.superredrock.usbthief.core.event.worker;

import com.superredrock.usbthief.core.event.AbstractEvent;

import java.nio.file.Path;

public final class FileDiscoveredEvent extends AbstractEvent {

    private final Path filePath;
    private final long fileSize;
    private final String deviceSerial;

    public FileDiscoveredEvent(Path filePath, long fileSize, String deviceSerial) {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath cannot be null");
        }
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
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
    public String description() {
        return String.format("FileDiscoveredEvent: %s (size: %d) on device %s at %d",
                filePath.getFileName(), fileSize, deviceSerial, timestamp());
    }

}
