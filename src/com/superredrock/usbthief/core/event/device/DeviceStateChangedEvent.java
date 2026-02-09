package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Device;

/**
 * Event fired when a device's state changes.
 * Holds both the old and new states for transition tracking.
 */
public final class DeviceStateChangedEvent extends DeviceEvent {

    private final Device.DeviceState oldState;
    private final Device.DeviceState newState;

    public DeviceStateChangedEvent(Device device, Device.DeviceState oldState, Device.DeviceState newState) {
        super(device);
        this.oldState = oldState;
        this.newState = newState;
    }

    /**
     * @return the previous state before the transition
     */
    public Device.DeviceState oldState() {
        return oldState;
    }

    /**
     * @return the new state after the transition
     */
    public Device.DeviceState newState() {
        return newState;
    }

    @Override
    public String description() {
        return String.format("Device state changed: %s %s -> %s at %d",
                device(), oldState, newState, timestamp());
    }
}
