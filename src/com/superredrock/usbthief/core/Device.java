package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.logging.Logger;

import static com.superredrock.usbthief.core.DeviceUtils.getHardDiskSN;

/**
 * Represents a USB storage device with its state and metadata.
 * <p>
 * Device is responsible for storing device information and managing its state.
 * Scanner lifecycle and ghost device management are handled by DeviceManager.
 */
public class Device {

    public enum DeviceState {
        OFFLINE,       // Device not present
        UNAVAILABLE,   // Device exists but inaccessible (AccessDeniedException / IOException)
        IDLE,          // Ready, no active operations
        SCANNING,      // Scanner is running
        DISABLED       // Manually disabled by user
    }

    protected static final Logger logger = Logger.getLogger(Device.class.getName());

    private final Path rootPath;
    private final String serialNumber;
    private final FileStore fileStore;
    private final String volumeName;
    private final boolean systemDisk;

    private volatile DeviceState state;
    private volatile boolean stateChange;

    /**
     * Creates a Device from a root path.
     * Initializes fileStore, volumeName, and detects system disk.
     *
     * @param rootPath the root path of the device
     */
    public Device(Path rootPath) {
        this.rootPath = rootPath;
        this.serialNumber = getHardDiskSN(rootPath.toString());
        
        FileStore fs = null;
        String volName = "";
        boolean sysDisk = false;
        DeviceState initialState = DeviceState.UNAVAILABLE;
        
        if (Files.exists(rootPath) && Files.isDirectory(rootPath)) {
            try {
                fs = Files.getFileStore(rootPath);
                volName = fs.name();
                initialState = DeviceState.IDLE;
                String fsType = fs.type();
                Path workPath = Paths.get(ConfigManager.getInstance().get(ConfigSchema.WORK_PATH));
                if (fsType.equals("NTFS") || fsType.equals("ReFS") || fs.equals(Files.getFileStore(workPath))) {
                    sysDisk = true;
                    initialState = DeviceState.DISABLED;
                }
            } catch (IOException e) {
                logger.fine("Failed to get FileStore for " + rootPath + ": " + e.getMessage());
                initialState = DeviceState.UNAVAILABLE;
            }
        }
        
        this.fileStore = fs;
        this.volumeName = volName;
        this.systemDisk = sysDisk;
        this.state = initialState;
    }

    /**
     * Creates a ghost Device from a DeviceRecord.
     * Ghost devices have no rootPath and are in OFFLINE state.
     *
     * @param record the device record containing serial number and volume name
     */
    Device(DeviceRecord record) {
        this.rootPath = null;
        this.serialNumber = record.serialNumber();
        this.fileStore = null;
        this.volumeName = record.volumeName();
        this.systemDisk = false;
        this.state = DeviceState.OFFLINE;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public String getVolumeName() {
        return volumeName;
    }

    public boolean isSystemDisk() {
        return systemDisk;
    }

    /**
     * Returns true if this is a ghost device (no rootPath).
     * Ghost devices represent known devices that are currently offline.
     *
     * @return true if ghost device
     */
    public boolean isGhost() {
        return rootPath == null;
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
     * Updates the device state based on filesystem accessibility.
     * Ghost devices and disabled devices are not updated.
     */
    public void updateState() {
        if (isGhost()) {
            return;
        }
        if (state == DeviceState.DISABLED) {
            return;
        }

        try {
            Files.getFileStore(rootPath);
            if (state == DeviceState.OFFLINE || state == DeviceState.UNAVAILABLE) {
                setState(DeviceState.IDLE);
            }
        } catch (NoSuchFileException e) {
            setState(DeviceState.OFFLINE);
        } catch (AccessDeniedException e) {
            setState(DeviceState.UNAVAILABLE);
        } catch (IOException e) {
            setState(DeviceState.UNAVAILABLE);
            logger.fine("Error checking device state: " + e.getMessage());
        }
    }

    /**
     * Creates a DeviceRecord from this device for persistence.
     *
     * @return DeviceRecord containing serial number and volume name
     */
    public DeviceRecord toRecord() {
        return new DeviceRecord(serialNumber, volumeName);
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
                "rootPath=" + rootPath +
                ", serialNumber='" + serialNumber + '\'' +
                ", volumeName='" + volumeName + '\'' +
                ", state=" + state +
                ", systemDisk=" + systemDisk +
                '}';
    }
}
