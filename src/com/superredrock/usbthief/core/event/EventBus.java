package com.superredrock.usbthief.core.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thread-safe event bus for dispatching events to registered listeners.
 * Supports dynamic listener registration and unregistration.
 *
 * <p>Listeners are indexed by the event type they subscribed to. A subscription on a
 * supertype (a base class such as {@code VolumeEvent} or the {@link Event} interface)
 * receives every event whose class is assignable to that type. Lookups therefore only
 * touch the supertype chain of the dispatched event instead of scanning every listener.
 *
 * <p>Synchronous listeners are invoked in registration order on the <em>calling</em>
 * thread; {@code dispatch} never hands work to another thread. Listeners that need to
 * run off the calling thread must register through {@link #registerAsync} or dispatch
 * to an executor themselves.
 *
 * <p>Usage example:
 * <pre>
 * // Create listener
 * EventListener&lt;VolumeInsertedEvent&gt; listener = event -> {
 *     logger.info("Volume inserted: " + event.volume());
 * };
 *
 * // Register listener
 * EventBus.getInstance().register(VolumeInsertedEvent.class, listener);
 *
 * // Dispatch event (from anywhere in codebase)
 * EventBus.getInstance().dispatch(new VolumeInsertedEvent(volume));
 * </pre>
 */
public final class EventBus {

    private static final EventBus INSTANCE = new EventBus();
    private static final Logger logger = LogManager.getLogger(EventBus.class);

    /**
     * Cached supertype chain (the class itself, all superclasses and all implemented
     * interfaces, transitively) used to resolve subtype subscriptions. The chain of a
     * class never changes, so it is computed at most once per event class and is
     * independent of which listeners happen to be registered.
     */
    private static final ClassValue<List<Class<?>>> TYPE_CHAIN = new ClassValue<List<Class<?>>>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            List<Class<?>> chain = new ArrayList<>();
            Deque<Class<?>> pending = new ArrayDeque<>();
            pending.add(type);
            while (!pending.isEmpty()) {
                Class<?> current = pending.poll();
                if (current == null || current == Object.class || chain.contains(current)) {
                    continue;
                }
                chain.add(current);
                pending.addAll(Arrays.asList(current.getInterfaces()));
                Class<?> superClass = current.getSuperclass();
                if (superClass != null) {
                    pending.add(superClass);
                }
            }
            return Collections.unmodifiableList(chain);
        }
    };

    // Synchronous listeners indexed by the event type they registered for. Values are
    // CopyOnWriteArrayList so dispatch can iterate lock-free while registrations happen.
    private final Map<Class<?>, List<EventListenerWrapper<?>>> syncByType = new ConcurrentHashMap<>();

    // Asynchronous listeners, indexed the same way.
    private final Map<Class<?>, List<AsyncEventListenerWrapper<?, ?>>> asyncByType = new ConcurrentHashMap<>();

    // Per-event-class resolution of the listener index. Compared against
    // registryVersion, so an entry is only reused while no registration changed.
    private final Map<Class<?>, ResolvedListeners<EventListenerWrapper<?>>> syncCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, ResolvedListeners<AsyncEventListenerWrapper<?, ?>>> asyncCache = new ConcurrentHashMap<>();

    // Bumped on every registration change; invalidates the resolution caches.
    private final AtomicLong registryVersion = new AtomicLong();

    // Assigns a globally increasing registration sequence, used to keep dispatch order
    // identical to registration order even when listeners are matched through different
    // entries of the type index.
    private final AtomicLong registrationSequence = new AtomicLong();

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
     * <p>The listener receives every event assignable to {@code eventClass}, including
     * events of subclasses and of implementing classes.
     *
     * @param eventClass the class of events to listen for
     * @param listener   the listener to register
     * @param <T>        the event type
     */
    public <T extends Event> void register(Class<T> eventClass, EventListener<T> listener) {
        if (eventClass == null || listener == null) {
            throw new IllegalArgumentException("eventClass and listener cannot be null");
        }

        List<EventListenerWrapper<?>> registered =
                syncByType.computeIfAbsent(eventClass, key -> new CopyOnWriteArrayList<>());

        EventListenerWrapper<T> wrapper =
                new EventListenerWrapper<>(eventClass, listener, registrationSequence.incrementAndGet());

        if (registered.contains(wrapper)) {
            logger.warn("Listener already registered for event type: {}", eventClass.getName());
            return;
        }

        registered.add(wrapper);
        registryVersion.incrementAndGet();
        logger.debug("Registered listener for event type: {}", eventClass.getName());
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

        List<EventListenerWrapper<?>> registered = syncByType.get(eventClass);
        if (registered == null) {
            return;
        }

        if (registered.remove(new EventListenerWrapper<>(eventClass, listener, -1L))) {
            registryVersion.incrementAndGet();
            logger.debug("Unregistered listener for event type: {}", eventClass.getName());
        }
    }

    /**
     * Dispatches an event to all matching listeners.
     *
     * <p>Listeners are executed serially on the calling thread, in the order they were
     * registered. Exceptions thrown by a listener are logged and do not stop dispatch
     * to the remaining listeners. If no listener is registered for the event type (or
     * any of its supertypes) the method returns immediately.
     *
     * @param event the event to dispatch
     * @param <T>   the event type
     */
    public <T extends Event> void dispatch(T event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        List<EventListenerWrapper<?>> resolved = resolveSyncListeners(event.getClass());
        if (resolved.isEmpty()) {
            return; // fast path: nobody listens for this type
        }

        for (int i = 0; i < resolved.size(); i++) {
            EventListenerWrapper<?> wrapper = resolved.get(i);
            try {
                @SuppressWarnings("unchecked")
                EventListener<T> listener = (EventListener<T>) wrapper.listener();
                listener.onEvent(event);
            } catch (Exception e) {
                logger.error("Exception in event listener for {}: {}", event.getClass().getName(), e);
            }
        }
    }

    /**
     * Clears all registered listeners. Useful for testing or application shutdown.
     */
    public void clearAll() {
        syncByType.clear();
        asyncByType.clear();
        registryVersion.incrementAndGet();
        logger.debug("Cleared all event listeners");
    }

    /**
     * @return the number of registered listeners (both sync and async)
     */
    public int listenerCount() {
        int count = 0;
        for (List<EventListenerWrapper<?>> wrappers : syncByType.values()) {
            count += wrappers.size();
        }
        for (List<AsyncEventListenerWrapper<?, ?>> wrappers : asyncByType.values()) {
            count += wrappers.size();
        }
        return count;
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

        AsyncEventListenerWrapper<T, R> wrapper = new AsyncEventListenerWrapper<>(
                eventClass, listener, resultType, registrationSequence.incrementAndGet());

        if (addAsyncListener(eventClass, wrapper)) {
            logger.debug("Registered result-collecting async listener for event type: {}", eventClass.getName());
        }
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

        AsyncEventListenerWrapper<T, R> wrapper = new AsyncEventListenerWrapper<>(
                eventClass, listener, registrationSequence.incrementAndGet());

        if (addAsyncListener(eventClass, wrapper)) {
            logger.debug("Registered async listener for event type: {}", eventClass.getName());
        }
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

        List<AsyncEventListenerWrapper<?, ?>> registered = asyncByType.get(eventClass);
        if (registered == null) {
            return;
        }

        if (registered.remove(new AsyncEventListenerWrapper<>(eventClass, listener, -1L))) {
            registryVersion.incrementAndGet();
            logger.debug("Unregistered async listener for event type: {}", eventClass.getName());
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
        for (EventListenerWrapper<?> wrapper : resolveSyncListeners(event.getClass())) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    EventListener<T> listener = (EventListener<T>) wrapper.listener();
                    listener.onEvent(event);
                } catch (Exception e) {
                    logger.error("Exception in event listener for {}: {}", event.getClass().getName(), e);
                }
            });
            futures.add(future);
        }

        // Handle asynchronous listeners
        for (AsyncEventListenerWrapper<?, ?> wrapper : resolveAsyncListeners(event.getClass())) {
            @SuppressWarnings("unchecked")
            AsyncEventListener<T, ?> listener = (AsyncEventListener<T, ?>) wrapper.listener();
            CompletableFuture<?> future = listener.onEventAsync(event);
            futures.add(future);
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

        for (AsyncEventListenerWrapper<?, ?> wrapper : resolveAsyncListeners(event.getClass())) {
            if (wrapper.canReturn(resultType)) {
                @SuppressWarnings("unchecked")
                AsyncEventListener<T, R> listener = (AsyncEventListener<T, R>) wrapper.listener();
                resultFutures.add(listener.onEventAsync(event));
            }
        }

        // Handle synchronous listeners (notify but don't collect results)
        for (EventListenerWrapper<?> wrapper : resolveSyncListeners(event.getClass())) {
            CompletableFuture.runAsync(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    EventListener<T> listener = (EventListener<T>) wrapper.listener();
                    listener.onEvent(event);
                } catch (Exception e) {
                    logger.error("Exception in event listener for {}: {}", event.getClass().getName(), e);
                }
            });
        }

        // Return a future that completes with all collected results
        return CompletableFuture.allOf(resultFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<R> results = new ArrayList<>();
                    for (CompletableFuture<R> future : resultFutures) {
                        try {
                            results.add(future.join());
                        } catch (Exception e) {
                            logger.error("Exception collecting result from async listener:", e);
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

        for (AsyncEventListenerWrapper<?, ?> wrapper : resolveAsyncListeners(event.getClass())) {
            if (wrapper.canReturn(resultType)) {
                @SuppressWarnings("unchecked")
                AsyncEventListener<T, R> listener = (AsyncEventListener<T, R>) wrapper.listener();
                CompletableFuture<R> future = listener.onEventAsync(event);
                listenerFutures.put(listener, future);
            }
        }

        // Handle synchronous listeners (notify but don't collect results)
        for (EventListenerWrapper<?> wrapper : resolveSyncListeners(event.getClass())) {
            CompletableFuture.runAsync(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    EventListener<T> listener = (EventListener<T>) wrapper.listener();
                    listener.onEvent(event);
                } catch (Exception e) {
                    logger.error("Exception in event listener for {}: {}", event.getClass().getName(), e);
                }
            });
        }

        // Return a future that completes with all collected results as a map
        return CompletableFuture.allOf(listenerFutures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<AsyncEventListener<T, R>, R> results = new ConcurrentHashMap<>();
                    listenerFutures.forEach((listener, future) -> {
                        try {
                            results.put(listener, future.join());
                        } catch (Exception e) {
                            logger.error("Exception collecting result from async listener:", e);
                        }
                    });
                    return results;
                });
    }

    private boolean addAsyncListener(Class<?> eventClass, AsyncEventListenerWrapper<?, ?> wrapper) {
        List<AsyncEventListenerWrapper<?, ?>> registered =
                asyncByType.computeIfAbsent(eventClass, key -> new CopyOnWriteArrayList<>());

        if (registered.contains(wrapper)) {
            logger.warn("Async listener already registered for event type: {}", eventClass.getName());
            return false;
        }

        registered.add(wrapper);
        registryVersion.incrementAndGet();
        return true;
    }

    /**
     * Returns the synchronous listeners matching the given event class, in registration
     * order. Resolutions are cached until the registry changes.
     */
    private List<EventListenerWrapper<?>> resolveSyncListeners(Class<?> eventClass) {
        long version = registryVersion.get();
        ResolvedListeners<EventListenerWrapper<?>> cached = syncCache.get(eventClass);
        if (cached != null && cached.version == version) {
            return cached.wrappers;
        }

        List<EventListenerWrapper<?>> resolved = resolve(syncByType, eventClass);
        syncCache.put(eventClass, new ResolvedListeners<>(version, resolved));
        return resolved;
    }

    /**
     * Returns the asynchronous listeners matching the given event class, in registration
     * order. Resolutions are cached until the registry changes.
     */
    private List<AsyncEventListenerWrapper<?, ?>> resolveAsyncListeners(Class<?> eventClass) {
        long version = registryVersion.get();
        ResolvedListeners<AsyncEventListenerWrapper<?, ?>> cached = asyncCache.get(eventClass);
        if (cached != null && cached.version == version) {
            return cached.wrappers;
        }

        List<AsyncEventListenerWrapper<?, ?>> resolved = resolve(asyncByType, eventClass);
        asyncCache.put(eventClass, new ResolvedListeners<>(version, resolved));
        return resolved;
    }

    /**
     * Walks the supertype chain of {@code eventClass} and collects every listener
     * registered against one of its types, ordered by registration sequence.
     */
    private static <W extends RegisteredWrapper> List<W> resolve(Map<Class<?>, List<W>> byType, Class<?> eventClass) {
        List<W> matched = new ArrayList<>();
        for (Class<?> type : TYPE_CHAIN.get(eventClass)) {
            List<W> registered = byType.get(type);
            if (registered != null && !registered.isEmpty()) {
                matched.addAll(registered);
            }
        }

        if (matched.size() > 1) {
            matched.sort((a, b) -> Long.compare(a.sequence(), b.sequence()));
        }

        return Collections.unmodifiableList(matched);
    }

    /**
     * Common contract of the listener wrappers, used to restore registration order after
     * the type index has gathered them from several supertypes.
     */
    private interface RegisteredWrapper {
        long sequence();
    }

    /**
     * Cached resolution of the listener index for one event class.
     */
    private static final class ResolvedListeners<W> {
        private final long version;
        private final List<W> wrappers;

        ResolvedListeners(long version, List<W> wrappers) {
            this.version = version;
            this.wrappers = wrappers;
        }
    }

    /**
     * Internal wrapper class that associates a listener with its event type.
     * Uses equality based on the event type and listener instance to prevent duplicates.
     *
     * @param <T> the event type
     */
    private static final class EventListenerWrapper<T extends Event> implements RegisteredWrapper {
        private final Class<T> eventClass;
        private final EventListener<T> listener;
        private final long sequence;

        EventListenerWrapper(Class<T> eventClass, EventListener<T> listener, long sequence) {
            this.eventClass = eventClass;
            this.listener = listener;
            this.sequence = sequence;
        }

        EventListener<T> listener() {
            return listener;
        }

        @Override
        public long sequence() {
            return sequence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EventListenerWrapper)) {
                return false;
            }
            EventListenerWrapper<?> other = (EventListenerWrapper<?>) o;
            // Equality based on same event type and same listener instance
            return eventClass.equals(other.eventClass) && listener.equals(other.listener);
        }

        @Override
        public int hashCode() {
            return 31 * eventClass.hashCode() + listener.hashCode();
        }
    }

    /**
     * Internal wrapper class for asynchronous event listeners.
     * Associates a listener with its event type and result type.
     *
     * @param <T> the event type
     * @param <R> the result type
     */
    private static final class AsyncEventListenerWrapper<T extends Event, R> implements RegisteredWrapper {
        private final Class<T> eventClass;
        private final AsyncEventListener<T, R> listener;
        private final Class<R> resultType;
        private final long sequence;

        <R2> AsyncEventListenerWrapper(Class<T> eventClass, AsyncEventListener<T, R> listener, Class<R2> resultType, long sequence) {
            // Note: This constructor accepts a generic resultType parameter but stores it as Class<R>
            // In practice, resultType should match the actual return type of the listener
            this.eventClass = eventClass;
            this.listener = listener;
            @SuppressWarnings("unchecked")
            Class<R> castResultType = (Class<R>) resultType;
            this.resultType = castResultType;
            this.sequence = sequence;
        }

        @SuppressWarnings("unchecked")
        AsyncEventListenerWrapper(Class<T> eventClass, AsyncEventListener<T, R> listener, long sequence) {
            this(eventClass, listener, null, sequence);
        }

        Class<T> eventClass() {
            return eventClass;
        }

        AsyncEventListener<T, R> listener() {
            return listener;
        }

        boolean canReturn(Class<?> type) {
            return resultType != null && resultType.isAssignableFrom(type);
        }

        @Override
        public long sequence() {
            return sequence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AsyncEventListenerWrapper)) {
                return false;
            }
            AsyncEventListenerWrapper<?, ?> other = (AsyncEventListenerWrapper<?, ?>) o;
            // Equality based on same event type and same listener instance
            return eventClass.equals(other.eventClass) && listener.equals(other.listener);
        }

        @Override
        public int hashCode() {
            return 31 * eventClass.hashCode() + listener.hashCode();
        }
    }
}
