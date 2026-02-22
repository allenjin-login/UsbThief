package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.ServiceManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.statistics.SpeedStatistics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token bucket rate limiter with dynamic rate adjustment and load-aware adaptation.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Dynamic rate limit changes via {@link #setRateLimit(long)}</li>
 *   <li>Load-aware adjustment via {@link #adjustRateByLoadLevel(LoadLevel)}</li>
 *   <li>Optional SpeedStatistics integration for tracking copy speeds</li>
 * </ul>
 *
 * <p>Thread safety:</p>
 * <ul>
 *   <li>ReentrantLock protects token state during acquire/refill operations</li>
 *   <li>Volatile fields for rate limit and burst size allow safe dynamic updates</li>
 *   <li>SpeedStatistics uses internal ThreadLocal pattern (no additional sync needed)</li>
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

    // Optional dependencies for load-aware and statistics features
    private final LoadEvaluator loadEvaluator;
    private final SpeedStatistics speedStats;

    /**
     * Creates a rate limiter with the specified limits.
     *
     * <p>This constructor creates a rate limiter without load-aware adjustment
     * or speed statistics tracking.</p>
     *
     * @param rateLimitBytesPerSecond maximum bytes per second (0 = no limit)
     * @param burstSize maximum burst size in bytes
     */
    public RateLimiter(long rateLimitBytesPerSecond, long burstSize) {
        this(rateLimitBytesPerSecond, burstSize,LoadEvaluator.getInstance(), null);
    }

    /**
     * Creates a rate limiter with load-aware adjustment and speed statistics.
     *
     * @param rateLimitBytesPerSecond base rate limit in bytes per second (0 = no limit)
     * @param burstSize maximum burst size in bytes
     * @param loadEvaluator optional LoadEvaluator for load-aware adjustment (can be null)
     * @param speedStats optional SpeedStatistics for tracking copy speeds (can be null)
     */
    public RateLimiter(long rateLimitBytesPerSecond, long burstSize,
                       LoadEvaluator loadEvaluator, SpeedStatistics speedStats) {
        this.rateLimitBytesPerSecond = rateLimitBytesPerSecond;
        this.burstSize = burstSize;
        this.tokens = burstSize;
        this.lastRefillTimestamp = System.nanoTime();
        this.loadEvaluator = loadEvaluator;
        this.speedStats = speedStats;
    }

    /**
     * Returns the current rate limit in bytes per second.
     *
     * @return rate limit in bytes per second
     */
    public long getRateLimitBytesPerSecond() {
        return rateLimitBytesPerSecond;
    }

    /**
     * Returns the current burst size in bytes.
     *
     * @return burst size in bytes
     */
    public long getBurstSize() {
        return burstSize;
    }

    /**
     * Sets a new rate limit dynamically.
     *
     * <p>The new rate limit takes effect immediately for subsequent token refills.
     * Existing waiting threads will use the new rate for their wait calculations.</p>
     *
     * @param bytesPerSecond new rate limit in bytes per second (0 = no limit)
     */
    public void setRateLimit(long bytesPerSecond) {
        this.rateLimitBytesPerSecond = bytesPerSecond;
    }

    /**
     * Adjusts the rate limit based on the current load level.
     *
     * <p>Uses percentage values from ConfigSchema:</p>
     * <ul>
     *   <li>LOW: RATE_LIMIT_LOW_PERCENT (default 100%)</li>
     *   <li>MEDIUM: RATE_LIMIT_MEDIUM_PERCENT (default 70%)</li>
     *   <li>HIGH: RATE_LIMIT_HIGH_PERCENT (default 40%)</li>
     * </ul>
     *
     * <p>The adjustment is relative to the current rateLimitBytesPerSecond value,
     * which should be set to the base (maximum) rate before calling this method.</p>
     *
     * @param level the load level to adjust for
     */
    public void adjustRateByLoadLevel(LoadLevel level) {
        if (level == null) {
            return;
        }

        ConfigManager config = ConfigManager.getInstance();
        int percent = switch (level) {
            case LOW -> config.get(ConfigSchema.RATE_LIMIT_LOW_PERCENT);
            case MEDIUM -> config.get(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT);
            case HIGH -> config.get(ConfigSchema.RATE_LIMIT_HIGH_PERCENT);
        };

        // Get the base rate limit from config for load-adjusted calculations
        long baseRate = config.get(ConfigSchema.COPY_RATE_LIMIT_BASE);
        if (baseRate <= 0) {
            // If no base rate configured, use current rate as base
            baseRate = rateLimitBytesPerSecond;
        }

        long adjustedRate = (baseRate * percent) / 100;
        setRateLimit(adjustedRate);
    }

    /**
     * Acquires permission to transfer the specified number of bytes.
     *
     * <p>Blocks until enough tokens are available in the bucket.
     * After successful acquisition, records bytes to SpeedStatistics if configured.</p>
     *
     * @param bytes the number of bytes to acquire
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void acquire(long bytes) throws InterruptedException {
        if (rateLimitBytesPerSecond <= 0) {
            // No rate limiting - just record statistics if available
            if (speedStats != null) {
                speedStats.recordBytes(bytes);
            }
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

        // Record bytes to statistics after successful acquire (outside lock)
        if (speedStats != null) {
            speedStats.recordBytes(bytes);
        }
    }

    /**
     * Refills tokens based on elapsed time.
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimestamp;
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);

        if (elapsedSeconds > 0) {
            // Use current volatile rate limit value
            long currentRateLimit = rateLimitBytesPerSecond;
            long newTokens = elapsedSeconds * currentRateLimit;
            tokens = Math.min(burstSize, tokens + newTokens);
            lastRefillTimestamp = now;
        }
    }

    /**
     * Calculates the wait time needed for the requested bytes.
     *
     * @param bytes the number of bytes requested
     * @return wait time in nanoseconds (0 if tokens available)
     */
    private long calculateWaitTime(long bytes) {
        if (tokens >= bytes) {
            return 0;
        }
        long deficit = bytes - tokens;
        // Use current volatile rate limit value
        long currentRateLimit = rateLimitBytesPerSecond;
        if (currentRateLimit <= 0) {
            return 0;
        }
        long waitSeconds = (deficit + currentRateLimit - 1) / currentRateLimit;
        return TimeUnit.SECONDS.toNanos(waitSeconds);
    }

    /**
     * Returns the LoadEvaluator associated with this rate limiter.
     *
     * @return LoadEvaluator or null if not configured
     */
    public LoadEvaluator getLoadEvaluator() {
        return loadEvaluator;
    }

    /**
     * Returns the SpeedStatistics associated with this rate limiter.
     *
     * @return SpeedStatistics or null if not configured
     */
    public SpeedStatistics getSpeedStatistics() {
        return speedStats;
    }
}
