package com.superredrock.usbthief.core.event.device;

import com.superredrock.usbthief.core.Volume;

/**
 * Event fired when a volume is removed or goes offline.
 */
public final class VolumeRemovedEvent extends VolumeEvent {

    public VolumeRemovedEvent(Volume volume) {
        super(volume);
    }

    @Override
    public String description() {
        return String.format("Volume removed: %s at %d", volume(), timestamp());
    }
}
