package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.event.Event;

/**
 * Base class for all device-related events.
 * Provides access to the device that triggered the event.
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

    /**
     * @return the device associated with this event
     */
    public Device device() {
        return device;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("%s: %s at %s", getClass().getSimpleName(), device, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
