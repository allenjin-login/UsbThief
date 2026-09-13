package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.event.AbstractEvent;

/**
 * Base class for device-level (hardware) events.
 * Holds a Device (pure info board, no state).
 */
public abstract class DeviceEvent extends AbstractEvent {

    private final Device device;

    protected DeviceEvent(Device device) {
        if (device == null) {
            throw new IllegalArgumentException("device cannot be null");
        }
        this.device = device;
    }

    public Device device() {
        return device;
    }

    @Override
    public String description() {
        return String.format("%s: %s at %d", getClass().getSimpleName(), device, timestamp());
    }

}
