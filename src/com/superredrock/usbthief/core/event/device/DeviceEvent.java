package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.event.Event;

/**
 * Base class for device-level (hardware) events.
 * Holds a Device (pure info board, no state).
 */
public abstract class DeviceEvent implements Event {

    private final Device device;
    private final long timestamp;

    protected DeviceEvent(Device device) {
        if (device == null) {
            throw new IllegalArgumentException("device cannot be null");
        }
        this.device = device;
        this.timestamp = System.currentTimeMillis();
    }

    public Device device() {
        return device;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("%s: %s at %d", getClass().getSimpleName(), device, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
