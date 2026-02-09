package com.superredrock.usbthief.test;

import com.superredrock.usbthief.worker.SpeedProbe;
import com.superredrock.usbthief.worker.SpeedProbeGroup;

/**
 * Unit tests for SpeedProbeGroup class.
 *
 * @since 2026-02-03
 */
public class SpeedProbeGroupTest {
    static void main(String[] args) {
        System.out.println("=== SpeedProbeGroup Tests ===\n");

        int passed = 0;
        int total = 0;

        try {
            total++;
            testBasicAggregation();
            System.out.println("✓ testBasicAggregation passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testBasicAggregation failed: " + e.getMessage());
        }

        try {
            total++;
            testAddRemoveProbe();
            System.out.println("✓ testAddRemoveProbe passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testAddRemoveProbe failed: " + e.getMessage());
        }

        try {
            total++;
            testAutoCleanupClosedProbes();
            System.out.println("✓ testAutoCleanupClosedProbes passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testAutoCleanupClosedProbes failed: " + e.getMessage());
        }

        try {
            total++;
            testEmptyGroup();
            System.out.println("✓ testEmptyGroup passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testEmptyGroup failed: " + e.getMessage());
        }

        try {
            total++;
            testGetProbeCount();
            System.out.println("✓ testGetProbeCount passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testGetProbeCount failed: " + e.getMessage());
        }

        try {
            total++;
            testCloseBehavior();
            System.out.println("✓ testCloseBehavior passed");
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ testCloseBehavior failed: " + e.getMessage());
        }

        System.out.println("\n=== Results: " + passed + "/" + total + " passed ===");
        if (passed == total) {
            System.out.println("All tests passed!");
        }
    }

    private static void testBasicAggregation() {
        SpeedProbeGroup group = new SpeedProbeGroup("test-group");

        SpeedProbe probe1 = new SpeedProbe("probe-1");
        SpeedProbe probe2 = new SpeedProbe("probe-2");
        SpeedProbe probe3 = new SpeedProbe("probe-3");

        group.addProbe(probe1);
        group.addProbe(probe2);
        group.addProbe(probe3);

        // Record some data
        probe1.record(1024 * 1024); // 1 MB
        probe2.record(2 * 1024 * 1024); // 2 MB
        probe3.record(3 * 1024 * 1024); // 3 MB

        // Sleep to allow speed calculation
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double totalSpeed = group.getTotalSpeed();

        assert totalSpeed > 0 : "Total speed should be positive";
        assert group.getProbeCount() == 3 : "Should have 3 probes";

        group.close();
    }

    private static void testAddRemoveProbe() {
        SpeedProbeGroup group = new SpeedProbeGroup("add-remove-group");
        SpeedProbe probe1 = new SpeedProbe("probe-1");
        SpeedProbe probe2 = new SpeedProbe("probe-2");

        group.addProbe(probe1);
        group.addProbe(probe2);

        assert group.getProbeCount() == 2 : "Should have 2 probes after adding";

        boolean removed = group.removeProbe(probe1);

        assert removed : "Should return true when removing existing probe";
        assert group.getProbeCount() == 1 : "Should have 1 probe after removal";

        // Try removing again
        boolean removedAgain = group.removeProbe(probe1);
        assert !removedAgain : "Should return false when removing non-existent probe";

        group.close();
        probe1.close();
    }

    private static void testAutoCleanupClosedProbes() {
        SpeedProbeGroup group = new SpeedProbeGroup("cleanup-group");

        SpeedProbe probe1 = new SpeedProbe("probe-1");
        SpeedProbe probe2 = new SpeedProbe("probe-2");
        SpeedProbe probe3 = new SpeedProbe("probe-3");

        group.addProbe(probe1);
        group.addProbe(probe2);
        group.addProbe(probe3);

        assert group.getProbeCount() == 3 : "Should start with 3 probes";

        // Close one probe
        probe2.close();

        // Trigger cleanup by calling getTotalSpeed
        double speed = group.getTotalSpeed();

        assert group.getProbeCount() == 2 : "Should auto-cleanup closed probe, have 2 probes";

        // Close remaining probes
        probe1.close();
        probe3.close();

        // Trigger cleanup again
        double speed2 = group.getTotalSpeed();

        assert group.getProbeCount() == 0 : "Should have 0 probes after all closed";
        assert speed2 == 0.0 : "Total speed should be 0 with no active probes";

        group.close();
    }

    private static void testEmptyGroup() {
        SpeedProbeGroup group = new SpeedProbeGroup("empty-group");

        assert group.getProbeCount() == 0 : "New group should have 0 probes";
        assert group.getTotalSpeed() == 0.0 : "Empty group should have 0 speed";
        assert group.getTotalBytes() == 0 : "Empty group should have 0 total bytes";

        group.close();
    }

    private static void testGetProbeCount() {
        SpeedProbeGroup group = new SpeedProbeGroup("count-group");

        assert group.getProbeCount() == 0 : "Initial count should be 0";

        SpeedProbe probe1 = new SpeedProbe("probe-1");
        SpeedProbe probe2 = new SpeedProbe("probe-2");

        group.addProbe(probe1);
        assert group.getProbeCount() == 1 : "Count should be 1 after adding first probe";

        group.addProbe(probe2);
        assert group.getProbeCount() == 2 : "Count should be 2 after adding second probe";

        probe1.close();

        // Trigger cleanup
        group.getTotalSpeed();

        assert group.getProbeCount() == 1 : "Count should be 1 after closing one probe";

        group.close();
    }

    private static void testCloseBehavior() {
        SpeedProbeGroup group = new SpeedProbeGroup("close-group");

        SpeedProbe probe1 = new SpeedProbe("probe-1");
        SpeedProbe probe2 = new SpeedProbe("probe-2");

        group.addProbe(probe1);
        group.addProbe(probe2);

        // Close the group
        group.close();

        // All probes should be closed
        assert probe1.isClosed() : "Probe 1 should be closed when group closes";
        assert probe2.isClosed() : "Probe 2 should be closed when group closes";

        // Group should have no probes
        assert group.getProbeCount() == 0 : "Group should have 0 probes after close";

        // Multiple closes should be safe
        group.close();
        group.close();
    }
}
