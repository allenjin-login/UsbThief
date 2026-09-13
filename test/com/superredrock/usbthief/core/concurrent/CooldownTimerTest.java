package com.superredrock.usbthief.core.concurrent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scheduler-backed cooldown timers that replaced the previous
 * one-busy-waiting-thread-per-volume {@code ClockThread} model.
 */
class CooldownTimerTest {

    @Test
    @Timeout(10)
    void cooldownActionRunsAfterTheDelay() throws Exception {
        CooldownTimer timer = new CooldownTimer();
        CountDownLatch fired = new CountDownLatch(1);

        timer.schedule("vol-1", 50, fired::countDown);

        assertTrue(timer.isPending("vol-1"), "timer must be pending right after scheduling");
        assertTrue(fired.await(5, TimeUnit.SECONDS), "cooldown action must run after the delay");
    }

    @Test
    @Timeout(10)
    void elapsedCooldownRemovesItselfFromThePendingSet() throws Exception {
        CooldownTimer timer = new CooldownTimer();
        CountDownLatch fired = new CountDownLatch(1);

        timer.schedule("vol-1", 30, fired::countDown);
        assertTrue(fired.await(5, TimeUnit.SECONDS));

        // The action removes its own entry before running; give the scheduler thread a
        // moment to finish the removal.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (timer.isPending("vol-1") && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(timer.isPending("vol-1"), "an elapsed cooldown must not stay pending");
        assertEquals(0, timer.pendingCount());
        assertEquals(0L, timer.remainingMs("vol-1"));
    }

    @Test
    @Timeout(10)
    void cancelledCooldownNeverRuns() throws Exception {
        CooldownTimer timer = new CooldownTimer();
        AtomicInteger runs = new AtomicInteger(0);

        timer.schedule("vol-1", 150, runs::incrementAndGet);
        assertTrue(timer.cancel("vol-1"), "cancel must report the pending timer");
        assertFalse(timer.isPending("vol-1"));

        Thread.sleep(300);
        assertEquals(0, runs.get(), "a cancelled cooldown must not run its action");
    }

    @Test
    void cancelForUnknownKeyIsNoop() {
        CooldownTimer timer = new CooldownTimer();
        assertFalse(timer.cancel("does-not-exist"));
    }

    @Test
    @Timeout(10)
    void reschedulingReplacesThePreviousCooldown() throws Exception {
        CooldownTimer timer = new CooldownTimer();
        AtomicInteger runs = new AtomicInteger(0);

        timer.schedule("vol-1", 250, runs::incrementAndGet);
        timer.schedule("vol-1", 50, runs::incrementAndGet);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (runs.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, runs.get(), "only the replacing cooldown may run");

        // The replaced (longer) cooldown must have been cancelled, not left armed.
        Thread.sleep(300);
        assertEquals(1, runs.get(), "the replaced cooldown must have been cancelled");
        assertEquals(0, timer.pendingCount());
    }

    @Test
    @Timeout(10)
    void remainingMsCountsDownAndNeverGoesNegative() throws Exception {
        CooldownTimer timer = new CooldownTimer();
        CountDownLatch fired = new CountDownLatch(1);

        timer.schedule("vol-1", 1000, fired::countDown);

        long remaining = timer.remainingMs("vol-1");
        assertTrue(remaining > 0 && remaining <= 1000, "remaining must be within the delay, was " + remaining);
        assertTrue(timer.remainingMs("other") == 0L, "an unknown key has no remaining time");

        timer.cancel("vol-1");
        assertEquals(0L, timer.remainingMs("vol-1"), "a cancelled cooldown reports no remaining time");
        assertTrue(timer.remainingMsSnapshot().isEmpty());
    }

    @Test
    @Timeout(10)
    void cancelAllStopsEveryCooldown() {
        CooldownTimer timer = new CooldownTimer();
        AtomicInteger runs = new AtomicInteger(0);

        timer.schedule("a", 200, runs::incrementAndGet);
        timer.schedule("b", 200, runs::incrementAndGet);
        timer.schedule("c", 200, runs::incrementAndGet);
        assertEquals(3, timer.pendingCount());

        timer.cancelAll();

        assertFalse(timer.isPending("a"));
        assertFalse(timer.isPending("b"));
        assertFalse(timer.isPending("c"));
        assertEquals(0, timer.pendingCount());
        assertEquals(0, runs.get());
    }

    @Test
    @Timeout(10)
    void actionRunsOnTheNamedSchedulerThreadAndFailureDoesNotKillTheScheduler() throws Exception {
        CooldownTimer timer = new CooldownTimer();
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<Boolean> daemon = new AtomicReference<>();
        CountDownLatch fired = new CountDownLatch(1);

        timer.schedule("boom", 20, () -> {
            throw new IllegalStateException("listener failure");
        });
        timer.schedule("ok", 40, () -> {
            Thread current = Thread.currentThread();
            threadName.set(current.getName());
            daemon.set(current.isDaemon());
            fired.countDown();
        });

        assertTrue(fired.await(5, TimeUnit.SECONDS),
                "a failing cooldown must not prevent later cooldowns from running");
        assertTrue(threadName.get().startsWith("usbthief-cooldown-"),
                "cooldowns must run on the named shared scheduler thread, was " + threadName.get());
        assertTrue(daemon.get(), "scheduler threads must be daemons");
    }

    @Test
    @Timeout(10)
    void injectedSchedulerIsUsed() throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1, ThreadPools.namedDaemonThreadFactory("test-cooldown"));
        try {
            CooldownTimer timer = new CooldownTimer(scheduler);
            CountDownLatch fired = new CountDownLatch(1);
            timer.schedule("vol-1", 20, fired::countDown);
            assertTrue(fired.await(5, TimeUnit.SECONDS));
            assertEquals(1, scheduler.getTaskCount());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void scheduleValidatesArguments() {
        CooldownTimer timer = new CooldownTimer();
        assertThrows(NullPointerException.class, () -> timer.schedule(null, 1, () -> {}));
        assertThrows(NullPointerException.class, () -> timer.schedule("k", 1, null));
        assertThrows(IllegalArgumentException.class, () -> timer.schedule("k", -1, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> new CooldownTimer(null));
    }
}
