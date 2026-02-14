package com.superredrock.usbthief.core;

/**
 * Immutable record representing a known device's persistent data.
 * Used for ghost device creation and persistence.
 *
 * @param serialNumber the device serial number (never null or blank)
 * @param volumeName the device volume name (empty string if unknown)
 */
public record DeviceRecord(String serialNumber, String volumeName) {
    
    public DeviceRecord {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber cannot be null or blank");
        }
        volumeName = volumeName != null ? volumeName : "";
    }
    
    /**
     * Parse from storage format: "serialNumber::volumeName"
     *
     * @param stored the stored string
     * @return parsed DeviceRecord
     */
    public static DeviceRecord fromString(String stored) {
        if (stored == null || stored.isBlank()) {
            throw new IllegalArgumentException("stored string cannot be null or blank");
        }
        String[] parts = stored.split("::", 2);
        String serial = parts[0].trim();
        String name = parts.length > 1 ? parts[1].trim() : "";
        return new DeviceRecord(serial, name);
    }
    
    /**
     * Convert to storage format: "serialNumber::volumeName"
     *
     * @return string representation for storage
     */
    @Override
    public String toString() {
        return serialNumber + "::" + volumeName;
    }
}
