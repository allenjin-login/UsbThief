package com.superredrock.usbthief.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class ClockThreadTest {

    @Test
    void normalCountdownCompletes() throws Exception {
        ClockThread ct = new ClockThread(100);
        ct.start();
        ct.onCountdown().get(2, TimeUnit.SECONDS);
        assertTrue(ct.isDone());
        assertFalse(ct.isCancelled());
    }

    @Test
    void thenRunExecutesAction() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        ClockThread ct = new ClockThread(300);
        CompletableFuture<Void> chain = ct.onCountdown().thenRun(() -> executed.set(true));
        ct.start();
        chain.get(3, TimeUnit.SECONDS);
        assertTrue(executed.get());
    }

    @Test
    void thenRunChaining() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        ClockThread ct = new ClockThread(300);
        CompletableFuture<Void> chain1 = ct.onCountdown().thenRun(count::incrementAndGet);
        CompletableFuture<Void> chain2 = ct.onCountdown().thenRun(count::incrementAndGet);
        ct.start();
        CompletableFuture.allOf(chain1, chain2).get(3, TimeUnit.SECONDS);
        assertEquals(2, count.get());
    }

    @Test
    void cancelMidCountdown() throws Exception {
        ClockThread ct = new ClockThread(TimeUnit.SECONDS, 10);
        ct.start();
        Thread.sleep(50);
        ct.cancel();

        assertTrue(ct.isCancelled());
        assertTrue(ct.onCountdown().isCancelled() || ct.onCountdown().isCompletedExceptionally());
    }

    @Test
    void cancelIdempotent() throws Exception {
        ClockThread ct = new ClockThread(TimeUnit.SECONDS, 10);
        ct.start();
        Thread.sleep(50);
        ct.cancel();
        assertDoesNotThrow(ct::cancel);
    }

    @Test
    void pauseAndResume() throws Exception {
        ClockThread ct = new ClockThread(300);
        ct.start();
        Thread.sleep(50);

        ct.pause();
        assertTrue(ct.isPaused());
        long remaining1 = ct.getRemaining(TimeUnit.MILLISECONDS);
        assertTrue(remaining1 > 0);

        Thread.sleep(200);
        long remaining2 = ct.getRemaining(TimeUnit.MILLISECONDS);
        // While paused, remaining should not decrease significantly
        assertTrue(remaining2 >= remaining1 - 50);

        ct.resume();
        assertFalse(ct.isPaused());
        ct.onCountdown().get(5, TimeUnit.SECONDS);
        assertTrue(ct.isDone());
    }

    @Test
    void pauseWhenDoneIsNoop() throws Exception {
        ClockThread ct = new ClockThread(50);
        ct.start();
        ct.onCountdown().get(2, TimeUnit.SECONDS);
        assertTrue(ct.isDone());
        assertDoesNotThrow(ct::pause);
    }

    @Test
    void resumeWhenNotPausedIsNoop() throws Exception {
        ClockThread ct = new ClockThread(200);
        ct.start();
        assertFalse(ct.isPaused());
        assertDoesNotThrow(ct::resume);
        ct.cancel();
    }

    @Test
    void getRemainingAfterDone() throws Exception {
        ClockThread ct = new ClockThread(50);
        ct.start();
        ct.onCountdown().get(2, TimeUnit.SECONDS);
        assertTrue(ct.getRemaining(TimeUnit.MILLISECONDS) <= 0);
    }

    @Test
    void isDoneIsPausedIsCancelledStates() throws Exception {
        ClockThread ct = new ClockThread(50);
        assertFalse(ct.isDone());
        assertFalse(ct.isPaused());
        assertFalse(ct.isCancelled());

        ct.start();
        ct.onCountdown().get(2, TimeUnit.SECONDS);
        assertTrue(ct.isDone());
        assertFalse(ct.isCancelled());
    }

    @Test
    void constructorNullUnitThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ClockThread(null, 100));
    }

    @Test
    void constructorNegativeDelayThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ClockThread(-1));
    }

    @Test
    void onCountdownAlreadyDoneReturnsCompletedFuture() throws Exception {
        ClockThread ct = new ClockThread(50);
        ct.start();
        ct.onCountdown().get(2, TimeUnit.SECONDS);

        CompletableFuture<Void> future = ct.onCountdown();
        assertTrue(future.isDone());
    }

    @Test
    void getRemainingDuringCountdown() throws Exception {
        ClockThread ct = new ClockThread(TimeUnit.SECONDS, 5);
        ct.start();
        Thread.sleep(50);
        long remaining = ct.getRemaining(TimeUnit.SECONDS);
        assertTrue(remaining > 0 && remaining <= 5);
        ct.cancel();
    }

    @Test
    void constructorMillis() {
        ClockThread ct = new ClockThread(100);
        assertNotNull(ct);
        assertFalse(ct.isDone());
    }
}
