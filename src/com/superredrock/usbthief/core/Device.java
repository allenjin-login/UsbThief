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
        OFFLINE,       // Device not found (NoSuchFileException)
        UNAVAILABLE,   // Device exists but inaccessible (AccessDeniedException / IOException)

        // Available status
        IDLE,          // Device ready, no active operations
        SCANNING,      // DeviceScanner is running

        // User control
        DISABLED       // Manually disabled by user (no automatic transitions)
    }

    protected static final Logger logger = Logger.getLogger(Device.class.getName());

    private final Path rootPath;
    private final String serialNumber;
    private FileStore fileStore = null;
    private DeviceScanner scanner;
    private DeviceState state;
    private boolean stateChange = false;

    protected Device(Path rootPath) {
        this.rootPath = rootPath;
        this.serialNumber = getHardDiskSN(rootPath.toString());
        if (Files.exists(rootPath) && Files.isDirectory(rootPath)){
            try {
                this.fileStore = Files.getFileStore(rootPath);
                state = DeviceState.IDLE;
            } catch (IOException e) {
                this.state = DeviceState.UNAVAILABLE;
            }
        }else {
            state = DeviceState.UNAVAILABLE;
        }
        if (this.fileStore != null) {
            if (!(this.fileStore.type().equals("exFAT") || this.fileStore.type().equals("FAT32"))){
                this.state = DeviceState.DISABLED;
            }
        }

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
            // Device is accessible - keep current state (IDLE/SCANNING handled by update())
        } catch (IOException e) {
            if (e instanceof NoSuchFileException) {
                checkChangeAndUpdate(DeviceState.OFFLINE);
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
            default -> {} // OFFLINE, UNAVAILABLE, DISABLED: no transitions
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
