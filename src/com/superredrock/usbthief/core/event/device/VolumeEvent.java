package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.event.Event;

/**
 * Base class for volume-level (drive) events.
 * Holds a Volume (operational entity with state).
 */
public abstract class VolumeEvent implements Event {

    private final Volume volume;
    private final long timestamp;

    protected VolumeEvent(Volume volume) {
        if (volume == null) {
            throw new IllegalArgumentException("volume cannot be null");
        }
        this.volume = volume;
        this.timestamp = System.currentTimeMillis();
    }

    public Volume volume() {
        return volume;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("%s: %s at %d", getClass().getSimpleName(), volume, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
