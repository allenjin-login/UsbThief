package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Volume;

/**
 * Event fired when a volume's state changes.
 */
public final class VolumeStateChangedEvent extends VolumeEvent {

    private final Volume.VolumeState oldState;
    private final Volume.VolumeState newState;

    public VolumeStateChangedEvent(Volume volume, Volume.VolumeState oldState, Volume.VolumeState newState) {
        super(volume);
        this.oldState = oldState;
        this.newState = newState;
    }

    public Volume.VolumeState oldState() {
        return oldState;
    }

    public Volume.VolumeState newState() {
        return newState;
    }

    @Override
    public String description() {
        return String.format("Volume state changed: %s %s -> %s at %d",
                volume(), oldState, newState, timestamp());
    }
}
