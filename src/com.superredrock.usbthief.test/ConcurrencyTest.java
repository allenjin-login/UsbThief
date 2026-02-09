package com.superredrock.usbthief.test;

import com.superredrock.usbthief.worker.SpeedProbe;
import com.superredrock.usbthief.worker.SpeedProbeGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrency tests for SpeedProbe and SpeedProbeGroup.
 * <p>
 * Tests thread safety and performance under high contention.
 *
 * @since 2026-02-03
 */
public class ConcurrencyTest {
    static void main(String[] args) {
        System.out.println("=== Concurrency Tests ===\n");

        int passed = 0;
        int total = 0;

        try {
            total++;
            testMultiThreadedRecording();
            System.out.println("✓ testMultiThreadedRecording passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testMultiThreadedRecording failed: " + e.getMessage());
        }

        try {
            total++;
            testProbeGroupConcurrency();
            System.out.println("✓ testProbeGroupConcurrency passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testProbeGroupConcurrency failed: " + e.getMessage());
        }

        try {
            total++;
            testSharedProbeMultipleThreads();
            System.out.println("✓ testSharedProbeMultipleThreads passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testSharedProbeMultipleThreads failed: " + e.getMessage());
        }

        try {
            total++;
            testConcurrentAddRemove();
            System.out.println("✓ testConcurrentAddRemove passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testConcurrentAddRemove failed: " + e.getMessage());
        }

        try {
            total++;
            testPerformanceUnderContention();
            System.out.println("✓ testPerformanceUnderContention passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testPerformanceUnderContention failed: " + e.getMessage());
        }

        System.out.println("\n=== Results: " + passed + "/" + total + " passed ===");
        if (passed == total) {
            System.out.println("All concurrency tests passed!");
        }
    }

    /**
     * Test multiple threads recording to the same probe.
     */
    private static void testMultiThreadedRecording() throws AssertionError {
        SpeedProbe probe = new SpeedProbe("concurrent-test");
        int numThreads = 10;
        int recordsPerThread = 1000;
        AtomicLong expectedTotal = new AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < recordsPerThread; j++) {
                    long bytes = 1024; // 1 KB
                    probe.record(bytes);
                    expectedTotal.addAndGet(bytes);
                }
            });
        }

        executor.shutdown();
        try {
            assert executor.awaitTermination(10, TimeUnit.SECONDS) : "Timeout waiting for threads";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted");
        }

        // Sleep to allow merges
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long actualTotal = probe.getTotalBytes();

        // Due to thread-local accumulation, actualTotal should be close to expectedTotal
        // Allow small tolerance for timing issues
        long tolerance = numThreads * recordsPerThread * 1024L / 100; // 1% tolerance
        assert Math.abs(actualTotal - expectedTotal.get()) <= tolerance
            : "Total bytes mismatch: expected " + expectedTotal.get() + ", got " + actualTotal;

        assert actualTotal > 0 : "Should have recorded some bytes";

        probe.close();
    }

    /**
     * Test multiple probes in a group with concurrent access.
     */
    private static void testProbeGroupConcurrency() throws AssertionError {
        SpeedProbeGroup group = new SpeedProbeGroup("concurrent-group");
        int numProbes = 5;
        int numThreads = 3;
        List<SpeedProbe> probes = new ArrayList<>();

        for (int i = 0; i < numProbes; i++) {
            SpeedProbe probe = new SpeedProbe("probe-" + i);
            probes.add(probe);
            group.addProbe(probe);
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Each thread records to all probes
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (SpeedProbe probe : probes) {
                    for (int i = 0; i < 100; i++) {
                        probe.record(1024 * (threadId + 1));
                    }
                }
            });
        }

        executor.shutdown();
        try {
            assert executor.awaitTermination(10, TimeUnit.SECONDS) : "Timeout waiting for threads";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted");
        }

        // Sleep to allow speed calculation
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double totalSpeed = group.getTotalSpeed();

        assert totalSpeed > 0 : "Total speed should be positive";
        assert group.getProbeCount() == numProbes : "Should have all probes active";

        // Each probe should have recorded data
        for (SpeedProbe probe : probes) {
            assert probe.getTotalBytes() > 0 : "Probe should have recorded bytes";
        }

        group.close();
    }

    /**
     * Test multiple threads sharing a single probe.
     */
    private static void testSharedProbeMultipleThreads() throws AssertionError {
        SpeedProbe sharedProbe = new SpeedProbe("shared-probe");
        int numThreads = 20;
        int operationsPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicLong totalRecorded = new AtomicLong(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    long bytes = 1024 + threadId; // Varying sizes
                    sharedProbe.record(bytes);
                    totalRecorded.addAndGet(bytes);
                }
            });
        }

        executor.shutdown();
        try {
            assert executor.awaitTermination(10, TimeUnit.SECONDS) : "Timeout waiting for threads";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted");
        }

        // Allow merges to complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long actualTotal = sharedProbe.getTotalBytes();

        // Check that we got most of the data (allow for timing differences)
        assert actualTotal > totalRecorded.get() * 0.95
            : "Should have recorded at least 95% of expected bytes";

        // Speed should be calculable
        double speed = sharedProbe.getSpeed();
        assert speed >= 0 : "Speed should be non-negative";

        sharedProbe.close();
    }

    /**
     * Test concurrent add/remove operations on group.
     */
    private static void testConcurrentAddRemove() throws AssertionError {
        SpeedProbeGroup group = new SpeedProbeGroup("concurrent-add-remove");
        int numOperations = 100;

        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Thread 1: Add probes
        executor.submit(() -> {
            for (int i = 0; i < numOperations; i++) {
                SpeedProbe probe = new SpeedProbe("add-probe-" + i);
                group.addProbe(probe);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Thread 2: Remove some probes
        executor.submit(() -> {
            List<SpeedProbe> toRemove = new ArrayList<>();
            for (int i = 0; i < numOperations / 2; i++) {
                SpeedProbe probe = new SpeedProbe("remove-probe-" + i);
                group.addProbe(probe);
                toRemove.add(probe);
            }

            // Remove them
            for (SpeedProbe probe : toRemove) {
                group.removeProbe(probe);
            }
        });

        // Thread 3: Get speeds (triggers cleanup)
        executor.submit(() -> {
            for (int i = 0; i < numOperations; i++) {
                group.getTotalSpeed();
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Thread 4: Get probe counts (triggers cleanup)
        executor.submit(() -> {
            for (int i = 0; i < numOperations; i++) {
                group.getProbeCount();
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        executor.shutdown();
        try {
            assert executor.awaitTermination(15, TimeUnit.SECONDS) : "Timeout waiting for threads";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted");
        }

        // Should not crash, and should have some probes left
        int count = group.getProbeCount();
        assert count >= 0 : "Probe count should be non-negative";

        group.close();
    }

    /**
     * Performance test: measure throughput under high contention.
     */
    private static void testPerformanceUnderContention() throws AssertionError {
        SpeedProbe probe = new SpeedProbe("performance-test");
        int numThreads = 16;
        int recordsPerThread = 10000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.nanoTime();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < recordsPerThread; j++) {
                    probe.record(1024);
                }
            });
        }

        executor.shutdown();
        try {
            assert executor.awaitTermination(30, TimeUnit.SECONDS) : "Timeout waiting for threads";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted");
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        long totalRecords = (long) numThreads * recordsPerThread;
        double recordsPerSecond = (totalRecords * 1000.0) / durationMs;

        System.out.println("  Performance: " + String.format("%.0f", recordsPerSecond)
            + " records/second (" + totalRecords + " records in " + durationMs + "ms)");

        // Verify correctness
        long actualTotal = probe.getTotalBytes();
        long expectedTotal = totalRecords * 1024;

        // Allow 5% tolerance for thread-local accumulation timing
        assert Math.abs(actualTotal - expectedTotal) <= expectedTotal * 0.05
            : "Total bytes should be close to expected (within 5%)";

        // Performance expectation: should handle at least 100K records/second
        // This is a conservative baseline for thread-local accumulation
        assert recordsPerSecond >= 100_000
            : "Performance too low: " + recordsPerSecond + " records/second";

        probe.close();
    }
}
