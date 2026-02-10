package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a device is detected for the first time.
 * This event is only fired once per unique hardware serial number.
 * Subsequent insertions of the same device trigger DeviceInsertedEvent instead.
 */
public final class NewDeviceJoinedEvent extends DeviceEvent {

    public NewDeviceJoinedEvent(Device device) {
        super(device);
    }

    @Override
    public String description() {
        return String.format("New device joined: serial=%s, path=%s at %d",
            device().getSerialNumber(), device().getRootPath(), timestamp());
    }
}
