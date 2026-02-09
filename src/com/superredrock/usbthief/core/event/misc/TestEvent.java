package com.superredrock.usbthief.core.event.misc;

import com.superredrock.usbthief.core.event.Event;

/**
 * Test event for demonstrating async event framework.
 */
public class TestEvent implements Event {

    private final String message;
    private final long timestamp;

    public TestEvent(String message) {
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public String message() {
        return message;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return "TestEvent: " + message + " at " + timestamp;
    }

    @Override
    public String toString() {
        return description();
    }
}
