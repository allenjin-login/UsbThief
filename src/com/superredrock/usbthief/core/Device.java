package com.superredrock.usbthief.core;

import java.util.Objects;

/**
 * Pure information board for a USB device.
 * <p>
 * Holds hardware-level metadata (VID, PID, serial, device path) extracted
 * from the device instance path. Does NOT manage volumes or state.
 * <p>
 * Device and Volume are independent entities registered separately
 * in DeviceManager. Use DeviceManager to look up auxiliary info.
 */
public class Device {

    private final String serialNumber;
    private final String vid;
    private final String pid;
    private final String devicePath;

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
