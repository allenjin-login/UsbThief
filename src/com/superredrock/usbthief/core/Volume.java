package com.superredrock.usbthief.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a USB storage volume (drive) with its metadata and state.
 * <p>
 * Contains root path, FileStore, volume name, serial number, accessibility state,
 * and state management. This is the primary entity for USB device tracking.
 */
public class Volume {

    public enum VolumeState {
        OFFLINE,       // Volume not present
        UNAVAILABLE,   // Volume exists but inaccessible (AccessDeniedException / IOException)
        IDLE,          // Ready, no active operations
        DISABLED       // Manually disabled by user (user-controlled, requires manual action)
    }

    private static final Logger logger = LogManager.getLogger(Volume.class);

    private Path rootPath;
    private FileStore fileStore;
    private String volumeName;
    private String driveLetter;
    private final String serialNumber;
    private volatile VolumeState state;
    private volatile boolean stateChange;

    /**
     * Creates a Volume from a root path and serial number.
     * Initializes FileStore and volume name if the path is accessible.
     *
     * @param rootPath     the root path of the volume (e.g., Path.of("E:\\"))
     * @param serialNumber the serial number identifying this volume's device
     */
    public Volume(Path rootPath, String serialNumber) {
        this.rootPath = rootPath;
        this.serialNumber = serialNumber;
        this.driveLetter = extractDriveLetter(rootPath);
        this.state = VolumeState.UNAVAILABLE;
        refreshMetadata();
    }

    /**
     * Extracts the drive letter from a root path.
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
            logger.debug("Failed to get FileStore for {}: {}", rootPath, e);
            this.fileStore = null;
            this.volumeName = "";
        }
    }

    /**
     * Updates the volume's root path and refreshes metadata.
     * Used when a device reconnects with a different drive letter.
     */
    public void updateRootPath(Path newRootPath) {
        this.rootPath = newRootPath;
        this.driveLetter = extractDriveLetter(newRootPath);
        refreshMetadata();
    }

    /**
     * Checks if the volume is currently accessible and in IDLE state.
     */
    public boolean isAccessible() {
        return fileStore != null && Files.exists(rootPath) && state == VolumeState.IDLE;
    }

    /**
     * Updates the volume state based on filesystem accessibility.
     * Disabled volumes are not updated.
     */
    public void updateState() {
        if (state == VolumeState.DISABLED) {
            return;
        }

        refreshMetadata();

        if (fileStore != null && Files.exists(rootPath)) {
            if (state == VolumeState.OFFLINE || state == VolumeState.UNAVAILABLE) {
                setState(VolumeState.IDLE);
            }
        } else {
            setState(VolumeState.OFFLINE);
        }
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public VolumeState getState() {
        return state;
    }

    /**
     * Sets the volume state and tracks if state changed.
     */
    public void setState(VolumeState newState) {
        if (this.state != newState) {
            this.stateChange = true;
        }
        this.state = newState;
    }

    /**
     * Checks if state changed since last call and resets the flag.
     */
    public boolean isChangeAndReset() {
        boolean changed = this.stateChange;
        this.stateChange = false;
        return changed;
    }

    /**
     * Enables the volume. Transition to IDLE state on next update.
     */
    public void enable() {
        if (this.state == VolumeState.DISABLED) {
            setState(VolumeState.IDLE);
        }
    }

    /**
     * Disables the volume and prevents automatic operations.
     */
    public void disable() {
        setState(VolumeState.DISABLED);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Volume volume)) return false;
        return Objects.equals(serialNumber, volume.serialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serialNumber);
    }

    @Override
    public String toString() {
        return "Volume{" +
                "driveLetter='" + driveLetter + '\'' +
                ", rootPath=" + rootPath +
                ", serialNumber='" + serialNumber + '\'' +
                ", volumeName='" + volumeName + '\'' +
                ", state=" + state +
                '}';
    }
}
