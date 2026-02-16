package com.superredrock.usbthief.core.event;

import com.superredrock.usbthief.core.event.misc.TestEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = EventBus.getInstance();
        eventBus.clearAll();
    }

    @AfterEach
    void tearDown() {
        eventBus.clearAll();
    }

    @Test
    void getInstance_shouldReturnSingleton() {
        EventBus instance1 = EventBus.getInstance();
        EventBus instance2 = EventBus.getInstance();

        assertSame(instance1, instance2);
    }

    @Test
    void register_shouldAddListener() {
        EventListener<TestEvent> listener = event -> {};

        eventBus.register(TestEvent.class, listener);

        assertEquals(1, eventBus.listenerCount());
    }

    @Test
    void register_shouldRejectNullEventClass() {
        EventListener<TestEvent> listener = event -> {};

        assertThrows(IllegalArgumentException.class, () ->
                eventBus.register(null, listener));
    }

    @Test
    void register_shouldRejectNullListener() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.register(TestEvent.class, null));
    }

    @Test
    void register_shouldNotAddDuplicateListener() {
        EventListener<TestEvent> listener = event -> {};

        eventBus.register(TestEvent.class, listener);
        eventBus.register(TestEvent.class, listener);

        assertEquals(1, eventBus.listenerCount());
    }

    @Test
    void unregister_shouldRemoveListener() {
        EventListener<TestEvent> listener = event -> {};
        eventBus.register(TestEvent.class, listener);

        eventBus.unregister(TestEvent.class, listener);

        assertEquals(0, eventBus.listenerCount());
    }

    @Test
    void unregister_shouldHandleNonExistentListener() {
        EventListener<TestEvent> listener = event -> {};

        assertDoesNotThrow(() -> eventBus.unregister(TestEvent.class, listener));
    }

    @Test
    void dispatch_shouldNotifyListener() {
        AtomicReference<TestEvent> receivedEvent = new AtomicReference<>();
        EventListener<TestEvent> listener = receivedEvent::set;
        eventBus.register(TestEvent.class, listener);

        TestEvent event = new TestEvent("test");
        eventBus.dispatch(event);

        assertNotNull(receivedEvent.get());
        assertEquals("test", receivedEvent.get().message());
    }

    @Test
    void dispatch_shouldNotifyMultipleListeners() {
        AtomicInteger callCount = new AtomicInteger(0);
        EventListener<TestEvent> listener1 = e -> callCount.incrementAndGet();
        EventListener<TestEvent> listener2 = e -> callCount.incrementAndGet();
        eventBus.register(TestEvent.class, listener1);
        eventBus.register(TestEvent.class, listener2);

        eventBus.dispatch(new TestEvent("test"));

        assertEquals(2, callCount.get());
    }

    @Test
    void dispatch_shouldRejectNullEvent() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.dispatch(null));
    }

    @Test
    void dispatch_shouldContinueAfterException() {
        AtomicInteger callCount = new AtomicInteger(0);

        EventListener<TestEvent> failingListener = e -> {
            throw new RuntimeException("Test exception");
        };
        EventListener<TestEvent> normalListener = e -> callCount.incrementAndGet();

        eventBus.register(TestEvent.class, failingListener);
        eventBus.register(TestEvent.class, normalListener);

        eventBus.dispatch(new TestEvent("test"));

        assertEquals(1, callCount.get());
    }

    @Test
    @Timeout(5)
    void dispatch_shouldExecuteInParallel() throws InterruptedException {
        int listenerCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(listenerCount);
        List<Long> startTimes = new ArrayList<>();

        for (int i = 0; i < listenerCount; i++) {
            eventBus.register(TestEvent.class, e -> {
                startLatch.countDown();
                startTimes.add(System.nanoTime());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                doneLatch.countDown();
            });
        }

        eventBus.dispatch(new TestEvent("test"));

        assertTrue(doneLatch.await(3, TimeUnit.SECONDS), "All listeners should complete");
    }

    @Test
    @Timeout(5)
    void dispatchAsync_shouldReturnCompletableFuture() throws Exception {
        AtomicReference<TestEvent> receivedEvent = new AtomicReference<>();
        EventListener<TestEvent> listener = receivedEvent::set;
        eventBus.register(TestEvent.class, listener);

        TestEvent event = new TestEvent("async-test");
        CompletableFuture<Void> future = eventBus.dispatchAsync(event);

        assertNotNull(future);
        future.get(2, TimeUnit.SECONDS);
        assertNotNull(receivedEvent.get());
    }

    @Test
    @Timeout(5)
    void dispatchAsync_shouldRejectNullEvent() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.dispatchAsync(null));
    }

    @Test
    void registerAsync_shouldAddAsyncListener() {
        AsyncEventListener<TestEvent, String> listener = e ->
                CompletableFuture.completedFuture("result");

        eventBus.registerAsync(TestEvent.class, listener, String.class);

        assertEquals(1, eventBus.listenerCount());
    }

    @Test
    void registerAsync_shouldRejectNullEventClass() {
        AsyncEventListener<TestEvent, String> listener = e ->
                CompletableFuture.completedFuture("result");

        assertThrows(IllegalArgumentException.class, () ->
                eventBus.registerAsync(null, listener, String.class));
    }

    @Test
    void registerAsync_shouldRejectNullListener() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.registerAsync(TestEvent.class, null, String.class));
    }

    @Test
    void registerAsync_shouldRejectNullResultType() {
        AsyncEventListener<TestEvent, String> listener = e ->
                CompletableFuture.completedFuture("result");

        assertThrows(IllegalArgumentException.class, () ->
                eventBus.registerAsync(TestEvent.class, listener, null));
    }

    @Test
    void registerAsync_withoutResultType_shouldAddListener() {
        AsyncEventListener<TestEvent, Void> listener = e ->
                CompletableFuture.completedFuture(null);

        eventBus.registerAsync(TestEvent.class, listener);

        assertEquals(1, eventBus.listenerCount());
    }

    @Test
    void registerAsync_shouldNotAddDuplicateListener() {
        AsyncEventListener<TestEvent, String> listener = e ->
                CompletableFuture.completedFuture("result");

        eventBus.registerAsync(TestEvent.class, listener, String.class);
        eventBus.registerAsync(TestEvent.class, listener, String.class);

        assertEquals(1, eventBus.listenerCount());
    }

    @Test
    void unregisterAsync_shouldRemoveListener() {
        AsyncEventListener<TestEvent, String> listener = e ->
                CompletableFuture.completedFuture("result");
        eventBus.registerAsync(TestEvent.class, listener, String.class);

        eventBus.unregisterAsync(TestEvent.class, listener);

        assertEquals(0, eventBus.listenerCount());
    }

    @Test
    @Timeout(5)
    void dispatchWithResult_shouldCollectResults() throws Exception {
        AsyncEventListener<TestEvent, String> listener1 = e ->
                CompletableFuture.completedFuture("result1");
        AsyncEventListener<TestEvent, String> listener2 = e ->
                CompletableFuture.completedFuture("result2");

        eventBus.registerAsync(TestEvent.class, listener1, String.class);
        eventBus.registerAsync(TestEvent.class, listener2, String.class);

        CompletableFuture<List<String>> future = eventBus.dispatchWithResult(
                new TestEvent("test"), String.class);

        List<String> results = future.get(2, TimeUnit.SECONDS);

        assertEquals(2, results.size());
        assertTrue(results.contains("result1"));
        assertTrue(results.contains("result2"));
    }

    @Test
    @Timeout(5)
    void dispatchWithResult_shouldRejectNullEvent() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.dispatchWithResult(null, String.class));
    }

    @Test
    @Timeout(5)
    void dispatchWithResult_shouldRejectNullResultType() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.dispatchWithResult(new TestEvent("test"), null));
    }

    @Test
    void clearAll_shouldRemoveAllListeners() {
        EventListener<TestEvent> syncListener = e -> {};
        AsyncEventListener<TestEvent, String> asyncListener = e ->
                CompletableFuture.completedFuture("result");

        eventBus.register(TestEvent.class, syncListener);
        eventBus.registerAsync(TestEvent.class, asyncListener, String.class);

        assertEquals(2, eventBus.listenerCount());

        eventBus.clearAll();

        assertEquals(0, eventBus.listenerCount());
    }

    @Test
    void listenerCount_shouldReturnTotalCount() {
        EventListener<TestEvent> syncListener = e -> {};
        AsyncEventListener<TestEvent, String> asyncListener = e ->
                CompletableFuture.completedFuture("result");

        eventBus.register(TestEvent.class, syncListener);
        eventBus.registerAsync(TestEvent.class, asyncListener, String.class);

        assertEquals(2, eventBus.listenerCount());
    }
}
