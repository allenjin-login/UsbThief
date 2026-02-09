package com.superredrock.usbthief.core.event;

/**
 * Listener interface for handling events of a specific type.
 *
 * @param <T> the type of event this listener handles, must extend Event
 */
@FunctionalInterface
public interface EventListener<T extends Event> {
    /**
     * Called when an event of type T is dispatched.
     *
     * @param event the event that was dispatched
     */
    void onEvent(T event);
}
