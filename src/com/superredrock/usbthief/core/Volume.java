package com.superredrock.usbthief.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Represents a storage volume (drive) with its metadata.
 * <p>
 * A Volume contains the root path, FileStore, volume name, and accessibility state.
 * Device holds a Volume instance which can be updated when the device reconnects.
 */
public class Volume {

    private static final Logger logger = Logger.getLogger(Volume.class.getName());

    private Path rootPath;
    private FileStore fileStore;
    private String volumeName;
    private String driveLetter;
    private Device device;  // Reference to parent device
    /**
     * Creates a Volume from a root path.
     * Initializes FileStore and volume name if the path is accessible.
     *
     * @param rootPath the root path of the volume (e.g., Path.of("E:\\"))
     */
    public Volume(Path rootPath) {
        this.rootPath = rootPath;
        this.driveLetter = extractDriveLetter(rootPath);
        refreshMetadata();
    }

    /**
     * Extracts the drive letter from a root path.
     *
     * @param path the root path
     * @return the drive letter with colon (e.g., "E:"), or empty string if not a drive
     */
    private String extractDriveLetter(Path path) {
        String pathStr = path.toString();
        if (pathStr.length() >= 2 && pathStr.charAt(1) == ':') {
            return pathStr.substring(0, 2);
        }
        return "";
    }

    /**
     * Refreshes FileStore and volume name from the current root path.
     * Called when volume becomes accessible or reconnects.
     */
    public void refreshMetadata() {
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            this.fileStore = null;
            this.volumeName = "";
            return;
        }

        try {
            this.fileStore = Files.getFileStore(rootPath);
            this.volumeName = fileStore.name();
        } catch (IOException e) {
            logger.fine("Failed to get FileStore for " + rootPath + ": " + e.getMessage());
            this.fileStore = null;
            this.volumeName = "";
        }
    }

    /**
     * Updates the volume's root path and refreshes metadata.
     * Used when a device reconnects with a different drive letter.
     *
     * @param newRootPath the new root path
     */
    public void updateRootPath(Path newRootPath) {
        this.rootPath = newRootPath;
        this.driveLetter = extractDriveLetter(newRootPath);
        refreshMetadata();
    }

    /**
     * Checks if the volume is currently accessible.
     *
     * @return true if the volume can be accessed
     */
    public boolean isAccessible() {
        return fileStore != null && Files.exists(rootPath);
    }

    public Path getRootPath() {
        return rootPath;
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public String getVolumeName() {
        return volumeName;
    }

    public String getDriveLetter() {
        return driveLetter;
    }

    /**
     * Gets the device this volume belongs to.
     *
     * @return the parent Device, or null if not associated
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Sets the device reference. Called by Device.addVolume().
     * Package-private to prevent external modification.
     *
     * @param device the parent device
     */
    void setDevice(Device device) {
        this.device = device;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Volume volume)) return false;
        return Objects.equals(driveLetter, volume.driveLetter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driveLetter);
    }

    @Override
    public String toString() {
        return "Volume{" +
                "driveLetter='" + driveLetter + '\'' +
                ", rootPath=" + rootPath +
                ", volumeName='" + volumeName + '\'' +
                ", accessible=" + isAccessible() +
                '}';
    }
}
