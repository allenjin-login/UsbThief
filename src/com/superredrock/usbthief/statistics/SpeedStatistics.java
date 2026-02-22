package com.superredrock.usbthief.statistics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread-safe speed statistics with sliding window calculation.
 *
 * <p>Provides real-time tracking of copy speed using:</p>
 * <ul>
 *   <li>Thread-local accumulation for low-contention recording</li>
 *   <li>Sliding window for current speed calculation (10 samples)</li>
 *   <li>Total bytes / total time for average speed</li>
 * </ul>
 *
 * <p>Thread safety optimizations:</p>
 * <ul>
 *   <li>Thread-local byte accumulation: 99% of recordBytes() calls are contention-free</li>
 *   <li>Periodic merging: AtomicLong operations only occur at intervals</li>
 *   <li>Volatile fields for currentSpeed and averageSpeed for safe reads</li>
 * </ul>
 *
 * @since 2026-02-21
 */
public final class SpeedStatistics {
    private static final Logger logger = Logger.getLogger(SpeedStatistics.class.getName());

    // Window configuration: 10 samples over ~10 seconds
    private static final int WINDOW_SIZE = 10;
    private static final long SAMPLE_INTERVAL_NS = 1_000_000_000L; // 1 second per sample

    // Merge interval to reduce contention (1ms)
    private static final long MERGE_INTERVAL_NS = 1_000_000L;

    // Thread-local accumulator (reduces recordBytes() contention)
    private final ThreadLocal<Long> threadLocalBytes = ThreadLocal.withInitial(() -> 0L);

    // Global accumulator (periodically merged from thread-local)
    private final AtomicLong totalBytesCopied = new AtomicLong(0);

    // Timing
    private final long creationTime;
    private volatile long lastMergeTime;
    private volatile long lastSampleTime;

    // Speed values (volatile for safe reads from any thread)
    private volatile double currentSpeed;  // MB/s from sliding window
    private volatile double averageSpeed;  // MB/s from total bytes / total time

    // Sliding window for speed calculation
    private final Window window;

    /**
     * Window data structure for sliding window speed calculation.
     */
    private static class Window {
        final long[] byteSamples;   // Bytes transferred in each sample period
        final long[] timeSamples;   // Duration of each sample period in nanos
        int index;
        final Object lock = new Object();

        Window() {
            this.byteSamples = new long[WINDOW_SIZE];
            this.timeSamples = new long[WINDOW_SIZE];
            this.index = -1; // Start at -1, first sample will be at index 0
        }
    }

    /**
     * Creates a new SpeedStatistics instance.
     */
    public SpeedStatistics() {
        this.window = new Window();
        this.creationTime = System.nanoTime();
        this.lastMergeTime = creationTime;
        this.lastSampleTime = creationTime;
        this.currentSpeed = 0.0;
        this.averageSpeed = 0.0;
    }

    /**
     * Records the specified number of bytes copied.
     *
     * <p>This method is thread-safe and optimized for high-frequency calls.
     * Bytes are accumulated in thread-local storage and periodically merged
     * to minimize contention.</p>
     *
     * @param bytes the number of bytes copied (must be positive)
     */
    public void recordBytes(long bytes) {
        if (bytes <= 0) {
            return;
        }

        // Accumulate in thread-local storage
        long localBytes = threadLocalBytes.get();
        threadLocalBytes.set(localBytes + bytes);

        // Check if merge is needed
        long now = System.nanoTime();
        if (now - lastMergeTime >= MERGE_INTERVAL_NS) {
            merge(now);
        }
    }

    /**
     * Merges thread-local bytes into global accumulator and updates window.
     *
     * @param now current nano time
     */
    private void merge(long now) {
        long localBytes = threadLocalBytes.get();
        if (localBytes > 0) {
            threadLocalBytes.set(0L);
            totalBytesCopied.addAndGet(localBytes);
            updateWindow(localBytes, now);
        }
        lastMergeTime = now;
    }

    /**
     * Updates the sliding window with a new sample.
     *
     * @param deltaBytes bytes transferred since last sample
     * @param now current nano time
     */
    private void updateWindow(long deltaBytes, long now) {
        synchronized (window.lock) {
            long elapsedSinceLastSample = now - lastSampleTime;

            // Only create new sample if enough time has passed
            if (elapsedSinceLastSample >= SAMPLE_INTERVAL_NS) {
                // Move to next position in circular buffer
                window.index = (window.index + 1) % WINDOW_SIZE;
                window.byteSamples[window.index] = deltaBytes;
                window.timeSamples[window.index] = elapsedSinceLastSample;
                lastSampleTime = now;

                // Recalculate speeds
                recalculateSpeeds(now);
            } else {
                // Accumulate bytes in current sample slot
                if (window.index >= 0) {
                    window.byteSamples[window.index] += deltaBytes;
                }
            }
        }
    }

    /**
     * Recalculates current and average speeds.
     *
     * @param now current nano time
     */
    private void recalculateSpeeds(long now) {
        // Calculate current speed from sliding window
        long totalWindowBytes = 0;
        long totalWindowTime = 0;
        int sampleCount = 0;

        for (int i = 0; i < WINDOW_SIZE; i++) {
            if (window.timeSamples[i] > 0) {
                totalWindowBytes += window.byteSamples[i];
                totalWindowTime += window.timeSamples[i];
                sampleCount++;
            }
        }

        // Current speed: bytes / time in window, converted to MB/s
        if (totalWindowTime > 0 && totalWindowBytes > 0) {
            double bytesPerSecond = (totalWindowBytes * 1_000_000_000.0) / totalWindowTime;
            currentSpeed = bytesPerSecond / (1024.0 * 1024.0);
        }

        // Average speed: total bytes / total elapsed time, converted to MB/s
        long totalElapsed = now - creationTime;
        long totalBytes = totalBytesCopied.get();
        if (totalElapsed > 0 && totalBytes > 0) {
            double bytesPerSecond = (totalBytes * 1_000_000_000.0) / totalElapsed;
            averageSpeed = bytesPerSecond / (1024.0 * 1024.0);
        }
    }

    /**
     * Returns the current transfer speed in MB/s.
     *
     * <p>Speed is calculated using a sliding window of the last 10 samples
     * (approximately 10 seconds). Returns 0.0 if insufficient data is available.</p>
     *
     * @return speed in MB/s
     */
    public double getCurrentSpeed() {
        // Ensure latest data is merged before returning
        long now = System.nanoTime();
        merge(now);
        return currentSpeed;
    }

    /**
     * Returns the average transfer speed in MB/s.
     *
     * <p>Speed is calculated as total bytes copied divided by total elapsed time.
     * Returns 0.0 if insufficient data is available.</p>
     *
     * @return average speed in MB/s
     */
    public double getAverageSpeed() {
        // Ensure latest data is merged before returning
        long now = System.nanoTime();
        merge(now);
        return averageSpeed;
    }

    /**
     * Returns the total number of bytes recorded.
     *
     * @return total bytes copied
     */
    public long getTotalBytesCopied() {
        // Ensure latest data is merged
        merge(System.nanoTime());
        return totalBytesCopied.get();
    }

    /**
     * Returns the elapsed time since this statistics instance was created.
     *
     * @return elapsed time in milliseconds
     */
    public long getElapsedTimeMs() {
        return (System.nanoTime() - creationTime) / 1_000_000;
    }

    /**
     * Resets all statistics to initial state.
     *
     * <p>Clears all accumulated bytes, resets the sliding window,
     * and resets speed calculations to zero.</p>
     */
    public void reset() {
        // Merge any pending thread-local bytes first
        merge(System.nanoTime());

        // Reset accumulators
        totalBytesCopied.set(0);

        // Reset window
        synchronized (window.lock) {
            for (int i = 0; i < WINDOW_SIZE; i++) {
                window.byteSamples[i] = 0;
                window.timeSamples[i] = 0;
            }
            window.index = -1;
        }

        // Reset speeds
        currentSpeed = 0.0;
        averageSpeed = 0.0;

        // Reset timing
        lastSampleTime = System.nanoTime();

        logger.fine("SpeedStatistics reset");
    }

    /**
     * Forces an update of speed calculations.
     *
     * <p>Useful to call periodically from a timer or scheduler to ensure
     * speed values are current even when no bytes are being recorded.</p>
     */
    public void update() {
        long now = System.nanoTime();
        merge(now);

        // Recalculate speeds even if no new bytes
        synchronized (window.lock) {
            recalculateSpeeds(now);
        }
    }

    @Override
    public String toString() {
        return String.format("SpeedStatistics[totalBytes=%d, currentSpeed=%.2f MB/s, averageSpeed=%.2f MB/s]",
                getTotalBytesCopied(), currentSpeed, averageSpeed);
    }
}
