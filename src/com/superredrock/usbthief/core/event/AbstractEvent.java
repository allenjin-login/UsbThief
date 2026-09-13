package com.superredrock.usbthief.core.event;

/**
 * Shared base class for events. Standardizes the creation-time timestamp and the
 * default description/toString plumbing that every event class previously
 * hand-rolled (~15 lines each). Subclasses keep their own fields and may
 * override {@link #description()} to add detail.
 *
 * <p>Deliberately not a record: the codebase must remain Java 11
 * source-compatible for the dual-version release process.
 */
public abstract class AbstractEvent implements Event {

    private final long timestamp;

    protected AbstractEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public final long timestamp() {
        return timestamp;
    }

    @Override
    public String description() {
        return String.format("%s at %d", getClass().getSimpleName(), timestamp);
    }

    @Override
    public String toString() {
        return description();
    }
}
