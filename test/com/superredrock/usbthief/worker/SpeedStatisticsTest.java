package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.statistics.SpeedStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpeedStatistics.
 */
class SpeedStatisticsTest {

    private SpeedStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new SpeedStatistics();
    }

    @Test
    void testRecordBytes() {
        // Initial state
        assertEquals(0, stats.getTotalBytesCopied());

        // Record some bytes
        stats.recordBytes(1024);
        assertEquals(1024, stats.getTotalBytesCopied());

        // Record more bytes
        stats.recordBytes(2048);
        assertEquals(3072, stats.getTotalBytesCopied());
    }

    @Test
    void testRecordBytes_shouldIgnoreZeroAndNegative() {
        long initial = stats.getTotalBytesCopied();

        stats.recordBytes(0);
        assertEquals(initial, stats.getTotalBytesCopied());

        stats.recordBytes(-100);
        assertEquals(initial, stats.getTotalBytesCopied());
    }

    @Test
    void testCurrentSpeedCalculation() throws InterruptedException {
        // Record 10 MB of data
        long bytes = 10L * 1024 * 1024; // 10 MB
        stats.recordBytes(bytes);

        // Wait for sample interval (1 second) to trigger speed calculation
        Thread.sleep(1100);

        // Force update to recalculate speeds
        stats.update();

        // Speed should be approximately 10 MB/s (may vary due to timing)
        double currentSpeed = stats.getCurrentSpeed();
        assertTrue(currentSpeed > 0, "Current speed should be greater than 0");
        // Allow 50% tolerance due to timing variations
        assertTrue(currentSpeed >= 5.0 && currentSpeed <= 20.0,
                "Current speed should be around 10 MB/s, was: " + currentSpeed);
    }

    @Test
    void testCurrentSpeed_shouldBeZeroInitially() {
        // Without recording any bytes, speed should be 0
        assertEquals(0.0, stats.getCurrentSpeed(), 0.001);
    }

    @Test
    void testAverageSpeedCalculation() throws InterruptedException {
        // Record bytes
        long bytes = 5L * 1024 * 1024; // 5 MB
        stats.recordBytes(bytes);

        // Wait some time
        Thread.sleep(100);

        // Get average speed (triggers merge)
        double avgSpeed = stats.getAverageSpeed();

        // Average speed should be positive since we recorded bytes
        assertTrue(avgSpeed >= 0, "Average speed should be non-negative");
    }

    @Test
    void testAverageSpeed_shouldBeZeroInitially() {
        assertEquals(0.0, stats.getAverageSpeed(), 0.001);
    }

    @Test
    void testReset() {
        // Record some bytes
        stats.recordBytes(1024 * 1024); // 1 MB
        assertEquals(1024 * 1024, stats.getTotalBytesCopied());

        // Reset
        stats.reset();

        // Verify all values are reset
        assertEquals(0, stats.getTotalBytesCopied());
        assertEquals(0.0, stats.getCurrentSpeed(), 0.001);
        assertEquals(0.0, stats.getAverageSpeed(), 0.001);
    }

    @Test
    void testReset_shouldClearAfterRecording() throws InterruptedException {
        // Record bytes and wait for speed calculation
        stats.recordBytes(10L * 1024 * 1024);
        Thread.sleep(1100);
        stats.update();

        // Verify we have non-zero stats before reset
        assertTrue(stats.getTotalBytesCopied() > 0);

        // Reset
        stats.reset();

        // Verify everything is cleared
        assertEquals(0, stats.getTotalBytesCopied());
        assertEquals(0.0, stats.getCurrentSpeed(), 0.001);
        assertEquals(0.0, stats.getAverageSpeed(), 0.001);

        // Verify we can record new data after reset
        stats.recordBytes(512 * 1024);
        assertEquals(512 * 1024, stats.getTotalBytesCopied());
    }

    @Test
    void testGetElapsedTimeMs() throws InterruptedException {
        long start = stats.getElapsedTimeMs();

        // Wait a bit
        Thread.sleep(50);

        long elapsed = stats.getElapsedTimeMs();
        assertTrue(elapsed >= start + 50, "Elapsed time should be at least 50ms");
    }

    @Test
    @Timeout(10)
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 1000;
        long bytesPerRecord = 1024; // 1 KB
        long expectedTotal = (long) threadCount * recordsPerThread * bytesPerRecord;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < recordsPerThread; j++) {
                        stats.recordBytes(bytesPerRecord);
                    }
                    // Trigger merge of this thread's accumulated bytes
                    stats.getTotalBytesCopied();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        // Verify total bytes matches expected (no data loss)
        long actualTotal = stats.getTotalBytesCopied();
        assertEquals(expectedTotal, actualTotal,
                "Total bytes should match expected without data loss");
    }

    @Test
    @Timeout(10)
    void testThreadSafety_highContention() throws InterruptedException {
        int threadCount = 50;
        long bytesPerThread = 100 * 1024 * 1024; // 100 MB per thread
        long expectedTotal = (long) threadCount * bytesPerThread;

        Thread[] threads = new Thread[threadCount];
        AtomicLong recordedTotal = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                // Record in one batch to maximize contention
                stats.recordBytes(bytesPerThread);
                recordedTotal.addAndGet(bytesPerThread);
                // Trigger merge of this thread's accumulated bytes
                stats.getTotalBytesCopied();
            });
        }

        // Start all threads
        for (Thread t : threads) {
            t.start();
        }

        // Wait for all threads
        for (Thread t : threads) {
            t.join(5000);
        }

        // Verify no data loss
        assertEquals(expectedTotal, stats.getTotalBytesCopied(),
                "No bytes should be lost under high contention");
    }

    @Test
    void testToString() {
        stats.recordBytes(1024 * 1024); // 1 MB

        String result = stats.toString();

        assertTrue(result.contains("SpeedStatistics"));
        assertTrue(result.contains("totalBytes="));
        assertTrue(result.contains("currentSpeed="));
        assertTrue(result.contains("averageSpeed="));
        assertTrue(result.contains("MB/s"));
    }

    @Test
    void testMultipleRecordings_accumulateCorrectly() {
        long[] amounts = {1024, 2048, 4096, 8192, 16384};
        long expected = 0;

        for (long amount : amounts) {
            stats.recordBytes(amount);
            expected += amount;
        }

        assertEquals(expected, stats.getTotalBytesCopied());
    }
}
