package com.superredrock.usbthief.core.event.index;

import com.superredrock.usbthief.core.event.Event;

/**
 * Event fired when the index has been saved to persistent storage.
 */
public class IndexSavedEvent implements Event {

    private final int savedCount;
    private final long timestamp;

    public IndexSavedEvent(int savedCount) {
        this.savedCount = savedCount;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return the number of entries saved from the index
     */
    public int savedCount() {
        return savedCount;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("IndexSavedEvent: %d entries saved at %s",
            savedCount, timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
