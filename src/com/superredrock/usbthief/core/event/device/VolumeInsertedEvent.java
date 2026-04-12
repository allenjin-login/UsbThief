package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Volume;

/**
 * Event fired when a volume (drive letter) is mounted and ready.
 */
public final class VolumeInsertedEvent extends VolumeEvent {

    public VolumeInsertedEvent(Volume volume) {
        super(volume);
    }

    @Override
    public String description() {
        return String.format("Volume inserted: %s at %d", volume(), timestamp());
    }
}
