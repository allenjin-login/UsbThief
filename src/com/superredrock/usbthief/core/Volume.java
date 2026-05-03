package com.superredrock.usbthief.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
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
        DISABLED,      // Manually disabled by user (user-controlled, requires manual action)
        EJECTING;      // Windows requested eject, stopping tasks

        /** Device is physically present (not OFFLINE). */
        public boolean isPresent() { return this != OFFLINE; }
    }

    private static final Logger logger = LogManager.getLogger(Volume.class);

    /**
     * Valid state transitions. Any transition not in this map is logged as a warning.
     */
    private static final EnumMap<VolumeState, Set<VolumeState>> VALID_TRANSITIONS;
    static {
        VALID_TRANSITIONS = new EnumMap<>(VolumeState.class);
        var idleTargets = EnumSet.of(VolumeState.DISABLED, VolumeState.EJECTING, VolumeState.OFFLINE, VolumeState.UNAVAILABLE);
        VALID_TRANSITIONS.put(VolumeState.UNAVAILABLE, EnumSet.of(VolumeState.IDLE, VolumeState.OFFLINE));
        VALID_TRANSITIONS.put(VolumeState.OFFLINE, EnumSet.of(VolumeState.IDLE, VolumeState.UNAVAILABLE));
        VALID_TRANSITIONS.put(VolumeState.IDLE, Collections.unmodifiableSet(idleTargets));
        VALID_TRANSITIONS.put(VolumeState.DISABLED, EnumSet.of(VolumeState.IDLE, VolumeState.OFFLINE));
        VALID_TRANSITIONS.put(VolumeState.EJECTING, EnumSet.of(VolumeState.OFFLINE));
    }

    private Path rootPath;
    private FileStore fileStore;
    private String volumeName;
    private String driveLetter;
    private final String serialNumber;
    private volatile Device device;
    private volatile VolumeState state;
    private volatile boolean stateChange;

    public Volume(Path rootPath, String serialNumber) {
        this.rootPath = rootPath;
        this.serialNumber = serialNumber;
        this.driveLetter = extractDriveLetter(rootPath);
        this.state = VolumeState.UNAVAILABLE;
        refreshMetadata();
    }

    private String extractDriveLetter(Path path) {
        String pathStr = path.toString();
        if (pathStr.length() >= 2 && pathStr.charAt(1) == ':') {
            return pathStr.substring(0, 2);
        }
        return "";
    }

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

    public void updateRootPath(Path newRootPath) {
        this.rootPath = newRootPath;
        this.driveLetter = extractDriveLetter(newRootPath);
        refreshMetadata();
    }

    /**
     * Checks if the volume filesystem is currently accessible.
     */
    public boolean isConnected() {
        if (fileStore == null) {
            try { fileStore = Files.getFileStore(this.rootPath); } catch (IOException ignored) { return false; }
        }
        return fileStore != null && Files.exists(rootPath);
    }

    /**
     * Updates the volume state based on filesystem accessibility.
     * DISABLED and EJECTING states are not auto-updated.
     */
    public void updateState() {
        if (state == VolumeState.DISABLED || state == VolumeState.EJECTING) {
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

    // ========== State query helpers ==========

    public boolean isOffline() { return state == VolumeState.OFFLINE; }
    public boolean isUnavailable() { return state == VolumeState.UNAVAILABLE; }
    public boolean isActive() { return state == VolumeState.IDLE; }
    public boolean isDisabled() { return state == VolumeState.DISABLED; }
    public boolean isEjecting() { return state == VolumeState.EJECTING; }

    /** Device is physically present (not OFFLINE). */
    public boolean isPresent() { return state != VolumeState.OFFLINE; }

    // ========== Getters ==========

    public Path getRootPath() { return rootPath; }
    public FileStore getFileStore() { return fileStore; }
    public String getVolumeName() { return volumeName; }
    public String getDriveLetter() { return driveLetter; }
    public String getSerialNumber() { return serialNumber; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }

    public VolumeState getState() { return state; }

    /**
     * Sets the volume state. Validates the transition and logs warnings for invalid ones.
     */
    public void setState(VolumeState newState) {
        VolumeState old = this.state;
        if (old != newState) {
            Set<VolumeState> allowed = VALID_TRANSITIONS.get(old);
            if (allowed != null && !allowed.contains(newState)) {
                logger.warn("Invalid state transition: {} -> {} for volume {}", old, newState, driveLetter);
            }
            this.stateChange = true;
        }
        this.state = newState;
    }

    public boolean isChangeAndReset() {
        boolean changed = this.stateChange;
        this.stateChange = false;
        return changed;
    }

    public void enable() {
        if (this.state == VolumeState.DISABLED) {
            setState(VolumeState.IDLE);
        }
    }

    public void disable() {
        setState(VolumeState.DISABLED);
    }

    public void setEjecting() {
        setState(VolumeState.EJECTING);
        logger.info("Volume set to EJECTING: {}", driveLetter);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Volume)) return false;
        Volume volume = (Volume) o;
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
