package com.superredrock.usbthief.core.event;

/**
 * Marker interface for all events in the system.
 * Events represent significant state changes or actions that occur at runtime.
 */
public interface Event {
    /**
     * @return The timestamp when this event was created (milliseconds since epoch)
     */
    long timestamp();

    /**
     * @return A human-readable description of this event
     */
    String description();
}
