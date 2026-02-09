package com.superredrock.usbthief.core.event;

import java.util.concurrent.CompletableFuture;

/**
 * Listener interface for asynchronous event handling with return values.
 * Listeners implementing this interface can return results asynchronously.
 *
 * @param <T> the type of event this listener handles, must extend Event
 * @param <R> the type of result this listener returns
 */
@FunctionalInterface
public interface AsyncEventListener<T extends Event, R> {
    /**
     * Called asynchronously when an event of type T is dispatched.
     * The method returns a CompletableFuture that will complete with the result.
     *
     * @param event the event that was dispatched
     * @return a CompletableFuture that will complete with the listener's result
     */
    CompletableFuture<R> onEventAsync(T event);
}
