package com.superredrock.usbthief.worker;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static final Logger logger = LogManager.getLogger(RateLimiter.class);

    /**
     * Upper bound on the number of await/refill rounds spent on a single chunk.
     *
     * <p>A chunk never exceeds the burst size, so one or two rounds are enough in practice.
     * The bound exists so that a future regression in token accounting can never make
     * {@link #acquire(long)} wait forever.</p>
     */
    private static final int MAX_REFILL_ITERATIONS = 64;

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

    /**
     * Fast path used by the copy hot loop with a rate value sampled once per file.
     *
     * <p>When the sampled rate is {@code 0} (the default: no limiting) this returns without
     * touching the lock, the clock or the token state, so an unlimited copy pays no per-chunk
     * rate-limiter cost at all. Otherwise the call is delegated to {@link #acquire(long)}; the
     * caller is responsible for having resolved {@code rateSnapshot} from the shared limiter
     * configuration.</p>
     *
     * @param bytes        number of bytes about to be transferred
     * @param rateSnapshot rate limit in bytes per second sampled by the caller ({@code 0} = unlimited)
     * @throws InterruptedException if the thread is interrupted while waiting for tokens
     */
    public void acquire(long bytes, long rateSnapshot) throws InterruptedException {
        if (bytes <= 0 || rateSnapshot <= 0) {
            return;
        }
        acquire(bytes);
    }

    public void acquire(long bytes) throws InterruptedException {
        if (bytes <= 0 || rateLimitBytesPerSecond <= 0) return;

        // A bucket can never hold more than burstSize tokens, so a request larger than the burst
        // size is split into burst-sized chunks. Waiting for more tokens than the cap allows would
        // otherwise never complete (the loop below could never see tokens >= bytes).
        long chunkSize = burstSize > 0 ? burstSize : bytes;
        long remaining = bytes;
        while (remaining > 0) {
            long chunk = Math.min(remaining, chunkSize);
            acquireChunk(chunk);
            remaining -= chunk;
        }
    }

    private void acquireChunk(long bytes) throws InterruptedException {
        lock.lock();
        try {
            refillTokens();
            long waitNanos = calculateWaitTime(bytes);
            int iterations = 0;

            while (waitNanos > 0) {
                if (++iterations > MAX_REFILL_ITERATIONS) {
                    // Safety valve: never spin indefinitely. Consume what we have and let future
                    // refills repay the (possibly negative) token balance.
                    logger.warn("RateLimiter gave up waiting after {} iterations (requested {} bytes, {} tokens available, burst {})",
                            iterations, bytes, tokens, burstSize);
                    break;
                }
                // noinspection ResultOfMethodCallIgnored - loop re-evaluates waiting time after refill
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
