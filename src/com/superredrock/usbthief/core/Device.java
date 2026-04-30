package com.superredrock.usbthief.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pure information board for a USB device.
 * <p>
 * Holds hardware-level metadata (VID, PID, serial, device path) extracted
 * from the device instance path. Maintains bidirectional references to
 * associated Volumes (a device may have multiple volumes/partitions).
 */
public class Device {

    private final String serialNumber;
    private final String vid;
    private final String pid;
    private final String devicePath;
    private final Set<Volume> volumes = new CopyOnWriteArraySet<>();

    /**
     * @param serialNumber hardware serial number
     * @param vid          vendor ID (maybe null for USBSTOR devices)
     * @param pid          product ID or name (maybe null for USBSTOR devices)
     * @param devicePath   raw device instance path (dbccName from WM_DEVICECHANGE)
     */
    public Device(String serialNumber, String vid, String pid, String devicePath) {
        this.serialNumber = serialNumber;
        this.vid = vid;
        this.pid = pid;
        this.devicePath = devicePath;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getVid() {
        return vid;
    }

    public String getPid() {
        return pid;
    }

    public String getDevicePath() {
        return devicePath;
    }

    public Set<Volume> getVolumes() {
        return Collections.unmodifiableSet(volumes);
    }

    public void addVolume(Volume volume) {
        volumes.add(volume);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Device device)) return false;
        return Objects.equals(serialNumber, device.serialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serialNumber);
    }

    @Override
    public String toString() {
        return "Device{" +
                "serial='" + serialNumber + '\'' +
                ", vid='" + vid + '\'' +
                ", pid='" + pid + '\'' +
                '}';
    }
}
