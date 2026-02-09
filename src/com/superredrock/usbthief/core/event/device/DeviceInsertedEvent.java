package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a new device is detected and added to the system.
 */
public final class DeviceInsertedEvent extends DeviceEvent {

    public DeviceInsertedEvent(Device device) {
        super(device);
    }

    @Override
    public String description() {
        return String.format("Device inserted: %s at %d", device(), timestamp());
    }
}
