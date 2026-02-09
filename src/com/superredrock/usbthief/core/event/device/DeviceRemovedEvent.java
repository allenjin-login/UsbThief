package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a device is removed or goes offline.
 */
public final class DeviceRemovedEvent extends DeviceEvent {

    public DeviceRemovedEvent(Device device) {
        super(device);
    }

    @Override
    public String description() {
        return String.format("Device removed: %s at %d", device(), timestamp());
    }
}
