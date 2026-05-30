package com.superredrock.usbthief.worker;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token bucket rate limiter with dynamic rate adjustment.
 *
 * <p>Thread safety:</p>
 * <ul>
 *   <li>ReentrantLock protects token state during acquire/refill operations</li>
 *   <li>Volatile fields for rate limit and burst size allow safe dynamic updates</li>
 * </ul>
 *
 * @since 2026-02-03
 */
public class RateLimiter {
    private volatile long rateLimitBytesPerSecond;
    private final long burstSize;
    private long tokens;
    private long lastRefillTimestamp;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public RateLimiter(long rateLimitBytesPerSecond, long burstSize) {
        this.rateLimitBytesPerSecond = rateLimitBytesPerSecond;
        this.burstSize = burstSize;
        this.tokens = burstSize;
        this.lastRefillTimestamp = System.nanoTime();
    }

    public long getRateLimitBytesPerSecond() {
        return rateLimitBytesPerSecond;
    }

    public long getBurstSize() {
        return burstSize;
    }

    public void setRateLimit(long bytesPerSecond) {
        this.rateLimitBytesPerSecond = bytesPerSecond;
    }

    public void acquire(long bytes) throws InterruptedException {
        if (rateLimitBytesPerSecond <= 0) return;

        lock.lock();
        try {
            refillTokens();
            long waitNanos = calculateWaitTime(bytes);

            while (waitNanos > 0) {
                condition.awaitNanos(waitNanos);
                refillTokens();
                waitNanos = calculateWaitTime(bytes);
            }

            tokens -= bytes;
        } finally {
            lock.unlock();
        }
    }

    private void refillTokens() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimestamp;

        if (elapsedNanos > 0) {
            long currentRateLimit = rateLimitBytesPerSecond;
            long newTokens = elapsedNanos * currentRateLimit / 1_000_000_000L;
            if (newTokens > 0) {
                tokens = Math.min(burstSize, tokens + newTokens);
                lastRefillTimestamp = now;
            }
        }
    }

    private long calculateWaitTime(long bytes) {
        if (tokens >= bytes) return 0;
        long deficit = bytes - tokens;
        long currentRateLimit = rateLimitBytesPerSecond;
        if (currentRateLimit <= 0) return 0;
        return (deficit * 1_000_000_000L + currentRateLimit - 1) / currentRateLimit;
    }
}
