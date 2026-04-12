package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a USB device interface is removed.
 */
public final class DeviceRemovalEvent extends DeviceEvent {

    public DeviceRemovalEvent(Device device) {
        super(device);
    }

    @Override
    public String description() {
        return String.format("Device removed: %s at %d", device(), timestamp());
    }
}
