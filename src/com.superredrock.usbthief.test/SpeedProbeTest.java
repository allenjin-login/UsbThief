package com.superredrock.usbthief.test;

import com.superredrock.usbthief.worker.SpeedProbe;

/**
 * Unit tests for SpeedProbe class.
 *
 * @since 2026-02-03
 */
public class SpeedProbeTest {
    static void main(String[] args) {
        System.out.println("=== SpeedProbe Tests ===\n");

        int passed = 0;
        int total = 0;

        try {
            total++;
            testBasicFunctionality();
            System.out.println("✓ testBasicFunctionality passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testBasicFunctionality failed: " + e.getMessage());
        }

        try {
            total++;
            testSlidingWindow();
            System.out.println("✓ testSlidingWindow passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testSlidingWindow failed: " + e.getMessage());
        }

        try {
            total++;
            testCloseBehavior();
            System.out.println("✓ testCloseBehavior passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testCloseBehavior failed: " + e.getMessage());
        }

        try {
            total++;
            testEdgeCases();
            System.out.println("✓ testEdgeCases passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testEdgeCases failed: " + e.getMessage());
        }

        try {
            total++;
            testTotalBytes();
            System.out.println("✓ testTotalBytes passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testTotalBytes failed: " + e.getMessage());
        }

        System.out.println("\n=== Results: " + passed + "/" + total + " passed ===");
        if (passed == total) {
            System.out.println("All tests passed!");
        }
    }

    private static void testBasicFunctionality() {
        SpeedProbe probe = new SpeedProbe("test-probe");

        // Record some bytes
        probe.record(1024 * 1024); // 1 MB

        // Sleep to allow time for speed calculation
        try {
            Thread.sleep(600); // 600ms > MIN_WINDOW_TIME_NS (500ms)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double speed = probe.getSpeed();

        assert speed > 0 : "Speed should be positive after recording bytes";
        assert probe.getTotalBytes() > 0 : "Total bytes should be positive";

        probe.close();
    }

    private static void testSlidingWindow() {
        SpeedProbe probe = new SpeedProbe("window-test");

        // Record multiple times to fill the window
        for (int i = 0; i < 12; i++) {
            probe.record(1024 * 1024); // 1 MB each
            try {
                Thread.sleep(100); // 100ms between records
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        double speed = probe.getSpeed();

        assert speed > 0 : "Speed should be calculated from sliding window";
        assert speed < 100 : "Speed should be reasonable (< 100 MB/s for this test)";

        probe.close();
    }

    private static void testCloseBehavior() {
        SpeedProbe probe = new SpeedProbe("close-test");

        probe.record(1024);

        // Close the probe
        probe.close();

        assert probe.isClosed() : "Probe should be marked as closed";

        // Recording after close should be ignored
        probe.record(1024 * 1024);

        // Speed should be 0 or last calculated value, but no crash
        double speed = probe.getSpeed();
        assert speed >= 0 : "Speed should be valid after close";

        // Multiple closes should be safe
        probe.close();
        probe.close();

        assert probe.isClosed() : "Probe should still be marked as closed";
    }

    private static void testEdgeCases() {
        SpeedProbe probe = new SpeedProbe("edge-test");

        // Test recording zero bytes
        probe.record(0);
        assert probe.getSpeed() >= 0 : "Recording zero should not cause issues";

        // Test recording negative bytes
        probe.record(-100);
        assert probe.getSpeed() >= 0 : "Recording negative should be ignored";

        // Test very small recording
        probe.record(1);
        probe.record(2);
        probe.record(3);

        // Test getting speed immediately (should be 0 due to MIN_WINDOW_TIME)
        SpeedProbe newProbe = new SpeedProbe("immediate-test");
        double immediateSpeed = newProbe.getSpeed();
        assert immediateSpeed == 0.0 : "Immediate speed should be 0 without time window";

        newProbe.close();
        probe.close();
    }

    private static void testTotalBytes() {
        SpeedProbe probe = new SpeedProbe("total-bytes-test");

        probe.record(1000);
        probe.record(2000);
        probe.record(3000);

        long total = probe.getTotalBytes();

        assert total == 6000 : "Total bytes should be 6000, got " + total;

        probe.close();

        // After close, total should still be accessible
        long totalAfterClose = probe.getTotalBytes();
        assert totalAfterClose == 6000 : "Total bytes should persist after close";
    }
}
