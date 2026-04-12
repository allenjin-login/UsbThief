package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a USB device interface is detected (before volume mount).
 */
public final class DeviceArrivalEvent extends DeviceEvent {

    public DeviceArrivalEvent(Device device) {
        super(device);
    }

    @Override
    public String description() {
        return String.format("Device arrived: %s at %d", device(), timestamp());
    }
}
