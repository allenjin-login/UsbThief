package com.superredrock.usbthief.worker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class RateLimiter {
    private final long rateLimitBytesPerSecond;
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

    public void acquire(long bytes) throws InterruptedException {
        if (rateLimitBytesPerSecond <= 0) {
            return;
        }

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
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);

        if (elapsedSeconds > 0) {
            long newTokens = elapsedSeconds * rateLimitBytesPerSecond;
            tokens = Math.min(burstSize, tokens + newTokens);
            lastRefillTimestamp = now;
        }
    }

    private long calculateWaitTime(long bytes) {
        if (tokens >= bytes) {
            return 0;
        }
        long deficit = bytes - tokens;
        long waitSeconds = (deficit + rateLimitBytesPerSecond - 1) / rateLimitBytesPerSecond;
        return TimeUnit.SECONDS.toNanos(waitSeconds);
    }
}
