package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.event.AbstractEvent;

/**
 * Base class for volume-level (drive) events.
 * Holds a Volume (operational entity with state).
 */
public abstract class VolumeEvent extends AbstractEvent {

    private final Volume volume;

    protected VolumeEvent(Volume volume) {
        if (volume == null) {
            throw new IllegalArgumentException("volume cannot be null");
        }
        this.volume = volume;
    }

    public Volume volume() {
        return volume;
    }

    @Override
    public String description() {
        return String.format("%s: %s at %d", getClass().getSimpleName(), volume, timestamp());
    }

}
