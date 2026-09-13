package com.superredrock.usbthief.core.concurrent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the properties the pools are relied on for: they are shared, stable, bounded
 * where they must be, and their threads are identifiable in a thread dump.
 */
class ThreadPoolsTest {

    private static final String SCAN_PREFIX = "usbthief-scan-";
    private static final String EVENT_PREFIX = "usbthief-events-";

    @Test
    @Timeout(10)
    void scanPoolRunsOnNamedDaemonThreads() throws Exception {
        assertTrue(describe(ThreadPools.scanExecutor()).startsWith(SCAN_PREFIX),
                "scan work must be identifiable by thread name");
    }

    @Test
    @Timeout(10)
    void eventPoolRunsOnNamedDaemonThreads() throws Exception {
        assertTrue(describe(ThreadPools.eventExecutor()).startsWith(EVENT_PREFIX),
                "async event work must be identifiable by thread name");
    }

    @Test
    @Timeout(10)
    void recyclePoolRunsOnNamedDaemonThreads() throws Exception {
        assertTrue(describe(ThreadPools.recycleExecutor()).startsWith("usbthief-recycle-"),
                "the recycler scan must be identifiable by thread name");
    }

    @Test
    void poolsAreSharedSingletons() {
        assertSame(ThreadPools.scanExecutor(), ThreadPools.scanExecutor());
        assertSame(ThreadPools.eventExecutor(), ThreadPools.eventExecutor());
        assertSame(ThreadPools.recycleExecutor(), ThreadPools.recycleExecutor());
        assertSame(ThreadPools.cooldownScheduler(), ThreadPools.cooldownScheduler());
    }

    @Test
    void workloadsDoNotShareOnePool() {
        assertNotSame(ThreadPools.scanExecutor(), ThreadPools.eventExecutor(),
                "scans must not compete with event notification for the same threads");
        assertNotSame(ThreadPools.scanExecutor(), ThreadPools.recycleExecutor());
        assertNotSame(ThreadPools.eventExecutor(), ThreadPools.recycleExecutor());
    }

    @Test
    void scanPoolIsSmallAndBounded() {
        int threads = ThreadPools.scanThreads();
        assertTrue(threads >= 2 && threads <= 4,
                "the IO-bound scan pool must stay small on any machine, was " + threads);
    }

    @Test
    void threadFactoryNamesAndDaemonizesThreads() {
        Thread thread = ThreadPools.namedDaemonThreadFactory("probe").newThread(() -> {});
        assertTrue(thread.getName().startsWith("usbthief-probe-"), thread.getName());
        assertTrue(thread.isDaemon(), "app threads must not keep the JVM alive");
        assertThrows(IllegalArgumentException.class, () -> ThreadPools.namedDaemonThreadFactory(null));
    }

    private static String describe(ExecutorService executor) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> name = new AtomicReference<>();
        AtomicReference<Boolean> daemon = new AtomicReference<>();
        executor.execute(() -> {
            Thread current = Thread.currentThread();
            name.set(current.getName());
            daemon.set(current.isDaemon());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "pool did not run the probe task");
        assertTrue(daemon.get(), "pool threads must be daemons");
        return name.get();
    }
}
