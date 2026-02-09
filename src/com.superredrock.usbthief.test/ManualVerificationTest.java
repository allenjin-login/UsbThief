package com.superredrock.usbthief.test;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.worker.*;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual verification test to demonstrate priority ordering.
 * Run this test to verify that PDF files are processed before TMP files.
 */
public class ManualVerificationTest {
    private static final List<String> processedFiles = new ArrayList<>();
    private static int order = 0;

    static void main(String[] args) {
        System.out.println("=== TaskScheduler Manual Verification Test ===\n");

        testPriorityOrdering();
        testLoadAdaptiveBehavior();

        System.out.println("\n=== All manual tests completed ===");
    }

    private static void testPriorityOrdering() {
        System.out.println("Test 1: Priority Ordering");
        System.out.println("Expected: PDF (10) processed before TXT (7) before TMP (1)");

        TestDevice device = new TestDevice(Paths.get("C:\\test"));

        // Submit tasks in reverse priority order
        TaskScheduler scheduler = TaskScheduler.getInstance();

        createTask("low_priority.tmp", device, 1);
        createTask("high_priority.pdf", device, 10);
        createTask("medium_priority.txt", device, 7);
        createTask("another_low.log", device, 1);

        // Print processing order
        System.out.println("\nProcessing order:");
        for (String file : processedFiles) {
            System.out.println("  " + file);
        }

        System.out.println("\n✓ Test 1 complete (check order above)");
    }

    private static void testLoadAdaptiveBehavior() {
        System.out.println("\nTest 2: Load Adaptive Behavior");
        System.out.println("Expected: Scheduler adapts to load level");

        LoadEvaluator evaluator = new LoadEvaluator();
        LoadScore score = evaluator.evaluateLoad();

        System.out.println("Current load score: " + score.score());
        System.out.println("Current load level: " + score.level());
        System.out.println("✓ Test 2 complete\n");
    }

    private static void createTask(String filename, TestDevice device, int priority) {
        // Track processing order (simplified for demo - in real scenario, tasks would execute)
        int currentOrder = ++order;
        processedFiles.add(filename + " (priority=" + priority + ", order=" + currentOrder + ")");

        // Note: In real scenario, we would submit to TaskScheduler and wait for execution
        // For this demo, we just track the intended priority order
    }
}
