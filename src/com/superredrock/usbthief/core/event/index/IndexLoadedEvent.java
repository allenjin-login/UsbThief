package com.superredrock.usbthief.core.event.index;

import com.superredrock.usbthief.core.event.Event;

/**
 * Event fired when the index has been loaded from persistent storage.
 */
public class IndexLoadedEvent implements Event {

    private final int loadedCount;
    private final long timestamp;

    public IndexLoadedEvent(int loadedCount) {
        this.loadedCount = loadedCount;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the number of entries loaded into the index
     */
    public int loadedCount() {
        return loadedCount;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("IndexLoadedEvent: %d entries loaded at %s",
            loadedCount, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
