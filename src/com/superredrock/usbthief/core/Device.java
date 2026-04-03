package com.superredrock.usbthief.core;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Represents a USB storage device with its state and metadata.
 * <p>
 * Device is responsible for storing device information and managing its state.
 * DeviceManager handles device lifecycle and state transitions.
 */
public class Device {

    public enum DeviceState {
        OFFLINE,       // Device not present
        UNAVAILABLE,   // Device exists but inaccessible (AccessDeniedException / IOException)
        IDLE,          // Ready, no active operations
        // Temporarily paused due to storage constraints (system-controlled, can auto-resume)
        DISABLED       // Manually disabled by user (user-controlled, requires manual action)
    }

    protected static final Logger logger = Logger.getLogger(Device.class.getName());

    private final Map<String, Volume> volumes = new ConcurrentHashMap<>();
    private final String serialNumber;
    private volatile DeviceState state;
    private volatile boolean stateChange;

    /**
     * Creates a Device with only serial number (no volumes initially).
     * Volumes are added later via addVolume().
     *
     * @param serialNumber the device serial number
     */
    public Device(String serialNumber) {
        this.serialNumber = serialNumber;
        this.state = DeviceState.UNAVAILABLE;
    }



    /**
     * Gets the first volume's root path.
     * For backward compatibility with single-volume usage.
     *
     * @return the first volume's root path, or null if no volumes
     */
    public Path getRootPath() {
        return volumes.isEmpty() ? null : volumes.values().iterator().next().getRootPath();
    }

    /**
     * Gets a volume by drive letter.
     *
     * @param driveLetter the drive letter (e.g., "E:")
     * @return the volume, or null if not found
     */
    public Volume getVolume(String driveLetter) {
        return volumes.get(driveLetter);
    }

    /**
     * Gets all volumes of this device.
     *
     * @return unmodifiable collection of volumes
     */
    public Collection<Volume> getVolumes() {
        return Collections.unmodifiableCollection(volumes.values());
    }

    /**
     * Adds or updates a volume for this device.
     *
     * @param volume the volume to add
     */
    public void addVolume(Volume volume) {
        volume.setDevice(this);
        volumes.put(volume.getDriveLetter(), volume);
    }

    /**
     * Removes a volume by drive letter.
     *
     * @param driveLetter the drive letter to remove
     */
    public void removeVolume(String driveLetter) {
        volumes.remove(driveLetter);
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    /**
     * Gets the FileStore of the first volume.
     *
     * @return the first volume's FileStore, or null if no volumes
     */
    public FileStore getFileStore() {
        return volumes.isEmpty() ? null : volumes.values().iterator().next().getFileStore();
    }

    /**
     * Gets the volume name of the first volume.
     *
     * @return the first volume's name, or empty string if no volumes
     */
    public String getVolumeName() {
        return volumes.isEmpty() ? "" : volumes.values().iterator().next().getVolumeName();
    }


    /**
     * Checks if this device has any volumes.
     *
     * @return true if device has at least one volume
     */
    public boolean hasVolumes() {
        return !volumes.isEmpty();
    }

    public DeviceState getState() {
        return state;
    }

    /**
     * Sets the device state and tracks if state changed.
     *
     * @param newState the new state
     */
    public void setState(DeviceState newState) {
        if (this.state != newState) {
            this.stateChange = true;
        }
        this.state = newState;
    }

    /**
     * Checks if state changed since last call and resets the flag.
     *
     * @return true if state changed
     */
    public boolean isChangeAndReset() {
        boolean changed = this.stateChange;
        this.stateChange = false;
        return changed;
    }

    /**
     * Disables the device. Transition to IDLE state on next update.
     */
    public void enable() {
        if (this.state == DeviceState.DISABLED) {
            setState(DeviceState.IDLE);
        }
    }

    /**
     * Disables the device and prevents automatic operations.
     */
    public void disable() {
        setState(DeviceState.DISABLED);
    }

    /**
     * Updates all volumes' state based on filesystem accessibility.
     * Disabled devices are not updated.
     */
    public void updateState() {
        if (state == DeviceState.DISABLED) {
            return;
        }

        // Refresh all volumes
        boolean hasAccessible = false;
        for (Volume volume : volumes.values()) {
            volume.refreshMetadata();
            if (volume.isAccessible()) {
                hasAccessible = true;
            }
        }

        // Update device state based on volume accessibility
        if (hasAccessible) {
            if (state == DeviceState.OFFLINE || state == DeviceState.UNAVAILABLE) {
                setState(DeviceState.IDLE);
            }
        } else {
            setState(volumes.isEmpty() ? DeviceState.OFFLINE : DeviceState.UNAVAILABLE);
        }
    }



    @Override
    public int hashCode() {
        return Objects.hash(serialNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Device device)) return false;
        return Objects.equals(serialNumber, device.serialNumber);
    }

    @Override
    public String toString() {
        return "Device{" +
                "volumes=" + volumes.keySet() +
                ", serialNumber='" + serialNumber + '\'' +
                ", state=" + state +
                '}';
}
}
