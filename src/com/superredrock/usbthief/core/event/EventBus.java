package com.superredrock.usbthief.core.event;

import com.superredrock.usbthief.core.QueueManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Thread-safe event bus for dispatching events to registered listeners.
 * Supports dynamic listener registration and unregistration.
 *
 * <p>Usage example:
 * <pre>
 * // Create listener
 * EventListener&lt;DeviceInsertedEvent&gt; listener = event -> {
 *     logger.info("Device inserted: " + event.device());
 * };
 *
 * // Register listener
 * EventBus.getInstance().register(DeviceInsertedEvent.class, listener);
 *
 * // Dispatch event (from anywhere in codebase)
 * EventBus.getInstance().dispatch(new DeviceInsertedEvent(device));
 * </pre>
 */
public final class EventBus {

    private static final EventBus INSTANCE = new EventBus();
    private static final Logger logger = Logger.getLogger(EventBus.class.getName());

    // Synchronous listeners stored per event type, using CopyOnWriteArrayList for thread-safe iteration
    @SuppressWarnings("rawtypes")
    private final List<EventListenerWrapper> listeners = new CopyOnWriteArrayList<>();

    // Asynchronous listeners stored per event type, using CopyOnWriteArrayList for thread-safe iteration
    @SuppressWarnings("rawtypes")
    private final List<AsyncEventListenerWrapper> asyncListeners = new CopyOnWriteArrayList<>();

    private EventBus() {
        // Singleton
    }

    /**
     * @return the singleton instance of EventBus
     */
    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a listener for events of the specified type.
     * Duplicate listeners are not added; each listener is called at most once per event.
     *
     * @param eventClass the class of events to listen for
     * @param listener   the listener to register
     * @param <T>        the event type
     */
    public <T extends Event> void register(Class<T> eventClass, EventListener<T> listener) {
        if (eventClass == null || listener == null) {
            throw new IllegalArgumentException("eventClass and listener cannot be null");
        }

        EventListenerWrapper<T> wrapper = new EventListenerWrapper<>(eventClass, listener);

        // Check for duplicates
        for (EventListenerWrapper<?> existing : listeners) {
            if (existing.equals(wrapper)) {
                logger.warning("Listener already registered for event type: " + eventClass.getName());
                return;
            }
        }

        listeners.add(wrapper);
        logger.fine("Registered listener for event type: " + eventClass.getName());
    }

    /**
     * Unregisters a previously registered listener.
     * If the listener was not registered, this method does nothing.
     *
     * @param eventClass the class of events the listener was registered for
     * @param listener   the listener to unregister
     * @param <T>        the event type
     */
    public <T extends Event> void unregister(Class<T> eventClass, EventListener<T> listener) {
        if (eventClass == null || listener == null) {
            throw new IllegalArgumentException("eventClass and listener cannot be null");
        }

        EventListenerWrapper<T> wrapper = new EventListenerWrapper<>(eventClass, listener);

        boolean removed = listeners.remove(wrapper);
        if (removed) {
            logger.fine("Unregistered listener for event type: " + eventClass.getName());
        }
    }

    /**
     * Dispatches an event to all registered listeners for its type.
     * Listeners are notified synchronously in registration order.
     * Exceptions thrown by listeners are logged but do not stop dispatch to other listeners.
     *
     * @param event the event to dispatch
     * @param <T>   the event type
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void dispatch(T event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        for (EventListenerWrapper<?> wrapper : listeners) {
            if (wrapper.canHandle(event)) {
                try {
                    ((EventListener<T>) wrapper.listener()).onEvent(event);
                } catch (Exception e) {
                    logger.severe("Exception in event listener for " + event.getClass().getName()
                            + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Clears all registered listeners. Useful for testing or application shutdown.
     */
    public void clearAll() {
        listeners.clear();
        asyncListeners.clear();
        logger.fine("Cleared all event listeners");
    }

    /**
     * @return the number of registered listeners (both sync and async)
     */
    public int listenerCount() {
        return listeners.size() + asyncListeners.size();
    }

    /**
     * Registers an asynchronous listener for events of the specified type.
     * Duplicate listeners are not added; each listener is called at most once per event.
     *
     * @param eventClass the class of events to listen for
     * @param listener   the async listener to register
     * @param resultType the type of result this listener returns (used for result collection)
     * @param <T>        the event type
     * @param <R>        the result type
     */
    public <T extends Event, R> void registerAsync(Class<T> eventClass, AsyncEventListener<T, R> listener, Class<R> resultType) {
        if (eventClass == null || listener == null) {
            throw new IllegalArgumentException("eventClass and listener cannot be null");
        }

        if (resultType == null) {
            throw new IllegalArgumentException("resultType cannot be null");
        }

        AsyncEventListenerWrapper<T, R> wrapper = new AsyncEventListenerWrapper<>(eventClass, listener, resultType);

        // Check for duplicates
        for (AsyncEventListenerWrapper<?, ?> existing : asyncListeners) {
            if (existing.equals(wrapper)) {
                logger.warning("Async listener already registered for event type: " + eventClass.getName());
                return;
            }
        }

        asyncListeners.add(wrapper);
        logger.fine("Registered async listener for event type: " + eventClass.getName());
    }

    /**
     * Registers an asynchronous listener for events of the specified type.
     * Use this method if you don't need to collect results.
     *
     * @param eventClass the class of events to listen for
     * @param listener   the async listener to register
     * @param <T>        the event type
     * @param <R>        the result type
     */
    public <T extends Event, R> void registerAsync(Class<T> eventClass, AsyncEventListener<T, R> listener) {
        if (eventClass == null || listener == null) {
            throw new IllegalArgumentException("eventClass and listener cannot be null");
        }

        AsyncEventListenerWrapper<T, R> wrapper = new AsyncEventListenerWrapper<>(eventClass, listener, null);

        // Check for duplicates
        for (AsyncEventListenerWrapper<?, ?> existing : asyncListeners) {
            if (existing.equals(wrapper)) {
                logger.warning("Async listener already registered for event type: " + eventClass.getName());
                return;
            }
        }

        asyncListeners.add(wrapper);
        logger.fine("Registered async listener for event type: " + eventClass.getName());
    }

    /**
     * Unregisters a previously registered asynchronous listener.
     * If the listener was not registered, this method does nothing.
     *
     * @param eventClass the class of events the listener was registered for
     * @param listener   the async listener to unregister
     * @param <T>        the event type
     * @param <R>        the result type
     */
    public <T extends Event, R> void unregisterAsync(Class<T> eventClass, AsyncEventListener<T, R> listener) {
        if (eventClass == null || listener == null) {
            throw new IllegalArgumentException("eventClass and listener cannot be null");
        }

        AsyncEventListenerWrapper<T, R> wrapper = new AsyncEventListenerWrapper<>(eventClass, listener);

        boolean removed = asyncListeners.remove(wrapper);
        if (removed) {
            logger.fine("Unregistered async listener for event type: " + eventClass.getName());
        }
    }

    /**
     * Dispatches an event asynchronously to all registered listeners for its type.
     * Both synchronous and asynchronous listeners are notified asynchronously.
     * Returns a CompletableFuture that completes when all listeners have finished processing.
     *
     * @param event the event to dispatch
     * @param <T>   the event type
     * @return a CompletableFuture that completes when all listeners finish
     */
    public <T extends Event> CompletableFuture<Void> dispatchAsync(T event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        // Collect all futures from async listeners
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Handle synchronous listeners asynchronously
        for (EventListenerWrapper<?> wrapper : listeners) {
            if (wrapper.canHandle(event)) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        @SuppressWarnings("unchecked")
                        EventListener<T> listener = (EventListener<T>) wrapper.listener();
                        listener.onEvent(event);
                    } catch (Exception e) {
                        logger.severe("Exception in event listener for " + event.getClass().getName()
                                + ": " + e.getMessage());
                    }
                }, QueueManager.getPool());
                futures.add(future);
            }
        }

        // Handle asynchronous listeners
        for (AsyncEventListenerWrapper<?, ?> wrapper : asyncListeners) {
            if (wrapper.canHandle(event)) {
                @SuppressWarnings("unchecked")
                AsyncEventListener<T, ?> listener = (AsyncEventListener<T, ?>) wrapper.listener();
                CompletableFuture<?> future = listener.onEventAsync(event);
                futures.add(future);
            }
        }

        // Return a future that completes when all listeners finish
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Dispatches an event and collects results from all asynchronous listeners.
     * Synchronous listeners are notified but their results are not collected.
     *
     * @param event      the event to dispatch
     * @param resultType the class type of results to collect
     * @param <T>        the event type
     * @param <R>        the result type
     * @return a CompletableFuture that completes with a list of results from all async listeners
     */
    public <T extends Event, R> CompletableFuture<List<R>> dispatchWithResult(T event, Class<R> resultType) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        if (resultType == null) {
            throw new IllegalArgumentException("resultType cannot be null");
        }

        // Collect results from async listeners that return the specified type
        List<CompletableFuture<R>> resultFutures = new ArrayList<>();

        for (AsyncEventListenerWrapper<?, ?> wrapper : asyncListeners) {
            if (wrapper.canHandle(event) && wrapper.canReturn(resultType)) {
                @SuppressWarnings("unchecked")
                AsyncEventListener<T, R> listener = (AsyncEventListener<T, R>) wrapper.listener();
                resultFutures.add(listener.onEventAsync(event));
            }
        }

        // Handle synchronous listeners (notify but don't collect results)
        for (EventListenerWrapper<?> wrapper : listeners) {
            if (wrapper.canHandle(event)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        @SuppressWarnings("unchecked")
                        EventListener<T> listener = (EventListener<T>) wrapper.listener();
                        listener.onEvent(event);
                    } catch (Exception e) {
                        logger.severe("Exception in event listener for " + event.getClass().getName()
                                + ": " + e.getMessage());
                    }
                }, QueueManager.getPool());
            }
        }

        // Return a future that completes with all collected results
        return CompletableFuture.allOf(resultFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<R> results = new ArrayList<>();
                    for (CompletableFuture<R> future : resultFutures) {
                        try {
                            results.add(future.join());
                        } catch (Exception e) {
                            logger.severe("Exception collecting result from async listener: " + e.getMessage());
                        }
                    }
                    return results;
                });
    }

    /**
     * Dispatches an event and collects results from all asynchronous listeners as a map.
     * The map keys are the listener instances and values are their results.
     * Synchronous listeners are notified but their results are not collected.
     *
     * @param event      the event to dispatch
     * @param resultType the class type of results to collect
     * @param <T>        the event type
     * @param <R>        the result type
     * @return a CompletableFuture that completes with a map of listener -> result from all async listeners
     */
    public <T extends Event, R> CompletableFuture<Map<AsyncEventListener<T, R>, R>> dispatchWithResultMap(T event, Class<R> resultType) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        if (resultType == null) {
            throw new IllegalArgumentException("resultType cannot be null");
        }

        // Store listener -> future mapping
        Map<AsyncEventListener<T, R>, CompletableFuture<R>> listenerFutures = new ConcurrentHashMap<>();

        for (AsyncEventListenerWrapper<?, ?> wrapper : asyncListeners) {
            if (wrapper.canHandle(event) && wrapper.canReturn(resultType)) {
                @SuppressWarnings("unchecked")
                AsyncEventListener<T, R> listener = (AsyncEventListener<T, R>) wrapper.listener();
                CompletableFuture<R> future = listener.onEventAsync(event);
                listenerFutures.put(listener, future);
            }
        }

        // Handle synchronous listeners (notify but don't collect results)
        for (EventListenerWrapper<?> wrapper : listeners) {
            if (wrapper.canHandle(event)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        @SuppressWarnings("unchecked")
                        EventListener<T> listener = (EventListener<T>) wrapper.listener();
                        listener.onEvent(event);
                    } catch (Exception e) {
                        logger.severe("Exception in event listener for " + event.getClass().getName()
                                + ": " + e.getMessage());
                    }
                }, QueueManager.getPool());
            }
        }

        // Return a future that completes with all collected results as a map
        return CompletableFuture.allOf(listenerFutures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<AsyncEventListener<T, R>, R> results = new ConcurrentHashMap<>();
                    listenerFutures.forEach((listener, future) -> {
                        try {
                            results.put(listener, future.join());
                        } catch (Exception e) {
                            logger.severe("Exception collecting result from async listener: " + e.getMessage());
                        }
                    });
                    return results;
                });
    }

    /**
     * Internal wrapper class that associates a listener with its event type.
     * Uses equality based on the event type and listener instance to prevent duplicates.
     *
     * @param <T> the event type
     */
    private record EventListenerWrapper<T extends Event>(Class<T> eventClass, EventListener<T> listener) {

        boolean canHandle(Event event) {
            return eventClass.isInstance(event);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventListenerWrapper<?>(Class<?> aClass, EventListener<?> listener1))) return false;
            // Equality based on same event type and same listener instance
            return eventClass.equals(aClass) && listener.equals(listener1);
        }
    }

    /**
     * Internal wrapper class for asynchronous event listeners.
     * Associates a listener with its event type and result type.
     *
     * @param <T> the event type
     * @param <R> the result type
     */
    private static class AsyncEventListenerWrapper<T extends Event, R> {
        private final Class<T> eventClass;
        private final AsyncEventListener<T, R> listener;
        private final Class<R> resultType;

        AsyncEventListenerWrapper(Class<T> eventClass, AsyncEventListener<T, R> listener) {
            this(eventClass, listener, null);
        }

        <R2> AsyncEventListenerWrapper(Class<T> eventClass, AsyncEventListener<T, R> listener, Class<R2> resultType) {
            // Note: This constructor accepts a generic resultType parameter but stores it as Class<R>
            // In practice, resultType should match the actual return type of the listener
            this.eventClass = eventClass;
            this.listener = listener;
            @SuppressWarnings("unchecked")
            Class<R> castResultType = (Class<R>) resultType;
            this.resultType = castResultType;
        }

        Class<T> eventClass() {
            return eventClass;
        }

        AsyncEventListener<T, R> listener() {
            return listener;
        }

        boolean canHandle(Event event) {
            return eventClass.isInstance(event);
        }

        boolean canReturn(Class<?> type) {
            return resultType != null && resultType.isAssignableFrom(type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AsyncEventListenerWrapper<?, ?> wrapper)) return false;
            // Equality based on same event type and same listener instance
            return eventClass.equals(wrapper.eventClass) && listener.equals(wrapper.listener);
        }
    }
}

