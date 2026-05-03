package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.event.AsyncEventListener;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.EventListener;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;
import com.superredrock.usbthief.worker.CopyResult;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clearAll();
    }

    @AfterEach
    void tearDown() {
        bus.clearAll();
    }

    @Test
    void registerAndDispatch() {
        AtomicInteger received = new AtomicInteger(0);
        EventListener<FileDiscoveredEvent> listener = e -> received.incrementAndGet();

        bus.register(FileDiscoveredEvent.class, listener);
        bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\test.txt"), 100, "serial1"));

        assertEquals(1, received.get());
    }

    @Test
    void dispatchToCorrectListenerOnly() {
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicInteger copyCount = new AtomicInteger(0);

        bus.register(FileDiscoveredEvent.class, e -> fileCount.incrementAndGet());
        bus.register(CopyCompletedEvent.class, e -> copyCount.incrementAndGet());

        bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\a.txt"), 50, "s1"));
        bus.dispatch(new CopyCompletedEvent(Path.of("E:\\a.txt"), Path.of("out"), 50, 50, CopyResult.SUCCESS, "s1"));

        assertEquals(1, fileCount.get());
        assertEquals(1, copyCount.get());
    }

    @Test
    void registerNullEventClass() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.register(null, e -> {}));
    }

    @Test
    void registerNullListener() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.register(FileDiscoveredEvent.class, null));
    }

    @Test
    void registerDuplicateIgnored() {
        EventListener<FileDiscoveredEvent> listener = e -> {};
        bus.register(FileDiscoveredEvent.class, listener);
        bus.register(FileDiscoveredEvent.class, listener);

        assertEquals(1, bus.listenerCount());
    }

    @Test
    void unregisterStopsDelivery() {
        AtomicInteger count = new AtomicInteger(0);
        EventListener<FileDiscoveredEvent> listener = e -> count.incrementAndGet();

        bus.register(FileDiscoveredEvent.class, listener);
        bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"));
        assertEquals(1, count.get());

        bus.unregister(FileDiscoveredEvent.class, listener);
        bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\b.txt"), 20, "s1"));
        assertEquals(1, count.get());
    }

    @Test
    void unregisterNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.unregister(null, e -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> bus.unregister(FileDiscoveredEvent.class, null));
    }

    @Test
    void unregisterNotRegisteredNoop() {
        assertDoesNotThrow(() ->
                bus.unregister(FileDiscoveredEvent.class, e -> {}));
    }

    @Test
    void dispatchNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> bus.dispatch(null));
    }

    @Test
    void dispatchExceptionIsolation() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        EventListener<FileDiscoveredEvent> throwing = e -> { throw new RuntimeException("boom"); };
        EventListener<FileDiscoveredEvent> normal = e -> count.incrementAndGet();

        bus.register(FileDiscoveredEvent.class, throwing);
        bus.register(FileDiscoveredEvent.class, normal);

        bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\test.txt"), 10, "s1"));

        await().atMost(1, TimeUnit.SECONDS).until(() -> count.get() == 1);
    }

    @Test
    void dispatchAsyncCompletes() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        bus.register(FileDiscoveredEvent.class, e -> count.incrementAndGet());

        CompletableFuture<Void> future = bus.dispatchAsync(
                new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"));

        assertNotNull(future);
        future.get(2, TimeUnit.SECONDS);
        assertEquals(1, count.get());
    }

    @Test
    void dispatchWithResultCollectsResults() throws Exception {
        AsyncEventListener<FileDiscoveredEvent, String> listener1 =
                e -> CompletableFuture.completedFuture("result1");

        bus.registerAsync(FileDiscoveredEvent.class, listener1, String.class);

        CompletableFuture<List<String>> future = bus.dispatchWithResult(
                new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"), String.class);

        List<String> results = future.get(2, TimeUnit.SECONDS);
        assertEquals(1, results.size());
        assertEquals("result1", results.get(0));
    }

    @Test
    void dispatchWithResultMapReturnsListenerMap() throws Exception {
        AsyncEventListener<FileDiscoveredEvent, String> listener1 =
                e -> CompletableFuture.completedFuture("val");

        bus.registerAsync(FileDiscoveredEvent.class, listener1, String.class);

        CompletableFuture<Map<AsyncEventListener<FileDiscoveredEvent, String>, String>> future =
                bus.dispatchWithResultMap(
                        new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"), String.class);

        Map<?, String> map = future.get(2, TimeUnit.SECONDS);
        assertEquals(1, map.size());
        assertTrue(map.containsValue("val"));
    }

    @Test
    void clearAllRemovesListeners() {
        bus.register(FileDiscoveredEvent.class, e -> {});
        bus.registerAsync(FileDiscoveredEvent.class,
                e -> CompletableFuture.completedFuture("x"), String.class);
        assertTrue(bus.listenerCount() > 0);

        bus.clearAll();
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void listenerCountAccurate() {
        assertEquals(0, bus.listenerCount());
        EventListener<FileDiscoveredEvent> l1 = e -> {};
        bus.register(FileDiscoveredEvent.class, l1);
        assertEquals(1, bus.listenerCount());
        bus.unregister(FileDiscoveredEvent.class, l1);
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void concurrentRegisterAndDispatch() throws Exception {
        int threads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger totalReceived = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    bus.register(FileDiscoveredEvent.class, e -> totalReceived.incrementAndGet());
                    bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\test.txt"), 10, "s1"));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // All dispatches should have triggered at least some listeners
        assertTrue(totalReceived.get() > 0);
    }

    @Test
    void registerAsyncWithResultTypeNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.registerAsync(FileDiscoveredEvent.class,
                        e -> CompletableFuture.completedFuture("x"), null));
    }

    @Test
    void dispatchAsyncNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> bus.dispatchAsync(null));
    }

    @Test
    void dispatchWithResultNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.dispatchWithResult(null, String.class));
        assertThrows(IllegalArgumentException.class,
                () -> bus.dispatchWithResult(
                        new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"), null));
    }

    @Test
    void multipleListenersForSameEvent() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        bus.register(FileDiscoveredEvent.class, e -> count.incrementAndGet());
        bus.register(FileDiscoveredEvent.class, e -> count.incrementAndGet());

        bus.dispatch(new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"));
        await().atMost(1, TimeUnit.SECONDS).until(() -> count.get() == 2);
    }

    @Test
    void unregisterAsyncRemovesListener() {
        AsyncEventListener<FileDiscoveredEvent, String> listener =
                e -> CompletableFuture.completedFuture("x");
        bus.registerAsync(FileDiscoveredEvent.class, listener, String.class);
        assertEquals(1, bus.listenerCount());

        bus.unregisterAsync(FileDiscoveredEvent.class, listener);
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void dispatchWithResultNoMatchingListeners() throws Exception {
        CompletableFuture<List<String>> future = bus.dispatchWithResult(
                new FileDiscoveredEvent(Path.of("E:\\a.txt"), 10, "s1"), String.class);
        List<String> results = future.get(2, TimeUnit.SECONDS);
        assertTrue(results.isEmpty());
    }

    private static await await() {
        return new await();
    }

    private static class await {
        private long timeout;
        private TimeUnit unit;

        await atMost(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.unit = unit;
            return this;
        }

        void until(java.util.function.Supplier<Boolean> condition) throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            while (System.currentTimeMillis() < deadline) {
                if (condition.get()) return;
                Thread.sleep(50);
            }
            fail("Condition not met within timeout");
        }
    }
}
