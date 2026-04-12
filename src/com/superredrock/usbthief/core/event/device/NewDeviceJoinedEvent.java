package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a USB device is detected for the first time.
 * Holds a Device (info board), not a Volume.
 */
public final class NewDeviceJoinedEvent extends DeviceEvent {

    public NewDeviceJoinedEvent(Device device) {
        super(device);
    }

    @Override
    public String description() {
        return String.format("New device joined: serial=%s at %d",
            device().getSerialNumber(), timestamp());
    }
}
