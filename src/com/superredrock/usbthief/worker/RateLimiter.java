package com.superredrock.usbthief.worker;

import java.util.concurrent.TimeUnit;

public class RateLimiter {
    private final long rateLimitBytesPerSecond;
    private final long burstSize;
    private long tokens;
    private long lastRefillTimestamp;

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

        synchronized (this) {
            refillTokens();
            long waitNanos = calculateWaitTime(bytes);
            if (waitNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
                refillTokens();
            }
            tokens -= bytes;
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
