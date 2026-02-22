package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Sniffer wait mechanism.
 * Tests that Sniffer schedules restart via SnifferLifecycleManager on completion and error.
 */
class SnifferWaitTest {

    private SnifferLifecycleManager lifecycleManager;
    private Device testDevice;
    private Path testRoot;

    @BeforeEach
    void setUp() throws IOException {
        lifecycleManager = SnifferLifecycleManager.getInstance();

        // Create a temporary directory for testing
        testRoot = Files.createTempDirectory("sniffer-test-");
        testDevice = new Device(testRoot);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cancel any pending restarts
        if (testDevice != null && lifecycleManager.isRestartPending(testDevice)) {
            lifecycleManager.cancelRestart(testDevice);
        }

        // Clean up test directory
        if (testRoot != null && Files.exists(testRoot)) {
            Files.walk(testRoot)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }

    @Test
    void sniffer_schedulesRestartAfterNormalCompletion() throws InterruptedException, IOException {
        // Create a sniffer with the real FileStore from testRoot
        Sniffer sniffer = new Sniffer(testDevice, Files.getFileStore(testRoot));
        sniffer.start();
        sniffer.join(5000); // Wait for sniffer to complete

        // Close the sniffer to trigger restart scheduling (monitoring blocks indefinitely)
        sniffer.close();
        Thread.sleep(100); // Allow restart to be scheduled

        // Verify that a restart is scheduled
        assertTrue(lifecycleManager.isRestartPending(testDevice),
            "Restart should be scheduled after sniffer completes normally");

        // Clean up
        lifecycleManager.cancelRestart(testDevice);
    }

    @Test
    void sniffer_schedulesRestartAfterInterruption() throws InterruptedException, IOException {
        // Create a sniffer that will run monitoring
        Sniffer sniffer = new Sniffer(testDevice, Files.getFileStore(testRoot));
        sniffer.start();

        // Give it a moment to start
        Thread.sleep(100);

        // Interrupt the sniffer (simulating interruption)
        sniffer.interrupt();
        sniffer.join(1000); // Wait for sniffer to stop
        
        // Close to ensure restart is scheduled
        sniffer.close();
        Thread.sleep(100); // Allow restart to be scheduled

        // Verify that a restart is scheduled
        assertTrue(lifecycleManager.isRestartPending(testDevice),
            "Restart should be scheduled after sniffer is interrupted");

        // Clean up
        lifecycleManager.cancelRestart(testDevice);
    }

    @Test
    void sniffer_schedulesRestartAfterClose() throws InterruptedException, IOException {
        // Create a sniffer and let it monitor
        Sniffer sniffer = new Sniffer(testDevice, Files.getFileStore(testRoot));

        // Start monitoring in a thread
        Thread snifferThread = new Thread(sniffer);
        snifferThread.start();

        // Give it time to start monitoring
        Thread.sleep(200);

        // Close the sniffer (normal monitoring end)
        try {
            sniffer.close();
        } catch (Exception e) {
            // Ignore
        }

        snifferThread.join(1000);

        // Verify that a restart is scheduled
        assertTrue(lifecycleManager.isRestartPending(testDevice),
            "Restart should be scheduled after sniffer is closed");

        // Clean up
        lifecycleManager.cancelRestart(testDevice);
    }

    @Test
    void sniffer_schedulesRestartWithErrorReason() throws InterruptedException {
        // Verify that restart can be scheduled with ERROR reason
        lifecycleManager.scheduleResume(testDevice, SnifferLifecycleManager.RestartReason.ERROR);

        assertTrue(lifecycleManager.isRestartPending(testDevice),
            "Restart should be schedulable with ERROR reason");

        // Clean up
        lifecycleManager.cancelRestart(testDevice);
    }

    @Test
    void sniffer_schedulesRestartWithNormalCompletionReason() throws InterruptedException {
        // Verify that restart can be scheduled with NORMAL_COMPLETION reason
        lifecycleManager.scheduleResume(testDevice, SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);

        assertTrue(lifecycleManager.isRestartPending(testDevice),
            "Restart should be schedulable with NORMAL_COMPLETION reason");

        // Clean up
        lifecycleManager.cancelRestart(testDevice);
    }
}
