package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class RateLimiterTest {

    @Test
    void acquireUnlimitedReturnsImmediately() throws InterruptedException {
        RateLimiter rl = new RateLimiter(0, 0);
        long start = System.nanoTime();
        rl.acquire(1_000_000_000L);
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1),
                "Should not block when rate limit is 0");
    }

    @Test
    void acquireWithinBurstDoesNotBlock() throws InterruptedException {
        RateLimiter rl = new RateLimiter(1000, 1000);
        long start = System.nanoTime();
        rl.acquire(1000);
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(100),
                "Should not block when tokens available");
    }

    @Test
    void acquireExceedsBurstBlocks() throws InterruptedException {
        RateLimiter rl = new RateLimiter(1000, 1000);
        // Consume initial tokens
        rl.acquire(1000);
        // Now acquiring more should block
        long start = System.nanoTime();
        rl.acquire(1000);
        long elapsed = System.nanoTime() - start;
        // Should have waited ~1 second for tokens to refill
        assertTrue(elapsed >= TimeUnit.MILLISECONDS.toNanos(500),
                "Should block when tokens insufficient");
    }

    @Test
    void tokenRefillOverTime() throws InterruptedException {
        RateLimiter rl = new RateLimiter(1000, 1000);
        rl.acquire(1000); // Exhaust tokens

        // Wait for refill
        Thread.sleep(1100);

        // Should not block now
        long start = System.nanoTime();
        rl.acquire(1000);
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(100),
                "Should not block after waiting for refill");
    }

    @Test
    void setRateLimitDynamic() throws InterruptedException {
        RateLimiter rl = new RateLimiter(1000, 1000);
        assertEquals(1000, rl.getRateLimitBytesPerSecond());

        rl.setRateLimit(5000);
        assertEquals(5000, rl.getRateLimitBytesPerSecond());
    }

    @Test
    void burstSizeCap() throws InterruptedException {
        RateLimiter rl = new RateLimiter(1000, 1000);
        rl.acquire(1000); // Exhaust

        // Wait 5 seconds - should refill at most burstSize tokens
        Thread.sleep(5500);

        // Can acquire up to burstSize without blocking
        long start = System.nanoTime();
        rl.acquire(1000);
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(100));
    }

    @Test
    void interruptDuringAcquire() throws Exception {
        RateLimiter rl = new RateLimiter(1000, 1000);
        rl.acquire(1000); // Exhaust tokens

        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            try {
                rl.acquire(1000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        t.start();
        Thread.sleep(100);
        t.interrupt();
        t.join(3000);
        assertTrue(interrupted.get(), "Thread should have been interrupted");
    }

    @Test
    void exactRateVerification() throws InterruptedException {
        // 1000 bytes/sec, burst 1000
        RateLimiter rl = new RateLimiter(1000, 1000);
        rl.acquire(1000); // Exhaust initial tokens

        // Acquire another 1000 bytes - should wait ~1 second
        long start = System.nanoTime();
        rl.acquire(1000);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // Allow ±20% tolerance
        assertTrue(elapsedMs >= 800 && elapsedMs <= 1500,
                "Expected ~1000ms wait, got " + elapsedMs + "ms");
    }

    @Test
    void constructorEdgeCases() {
        assertDoesNotThrow(() -> new RateLimiter(0, 0));
        assertDoesNotThrow(() -> new RateLimiter(0, 100));
    }

    @Test
    void getBurstSize() {
        RateLimiter rl = new RateLimiter(1000, 500);
        assertEquals(500, rl.getBurstSize());
    }

    @Test
    void multipleAcquiresWithinBurst() throws InterruptedException {
        RateLimiter rl = new RateLimiter(10000, 10000);
        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            rl.acquire(1000);
        }
        long elapsed = System.nanoTime() - start;
        // All should fit within burst
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(100));
    }
}
