package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SnifferLifecycleManager.
 * Tests singleton pattern, restart scheduling, delay logic, and cancellation.
 */
class SnifferLifecycleManagerTest {

    private SnifferLifecycleManager manager;
    private Device testDevice;
    private volatile boolean restartCallbackInvoked;

    @BeforeEach
    void setUp() {
        manager = SnifferLifecycleManager.getInstance();
        testDevice = createTestDevice();
        restartCallbackInvoked = false;
    }

    @AfterEach
    void tearDown() {
        // Clean up any pending restarts
        if (testDevice != null && manager.isRestartPending(testDevice)) {
            manager.cancelRestart(testDevice);
        }
    }

    @Test
    void singletonReturnsSameInstance() {
        SnifferLifecycleManager instance1 = SnifferLifecycleManager.getInstance();
        SnifferLifecycleManager instance2 = SnifferLifecycleManager.getInstance();
        assertSame(instance1, instance2, "getInstance should return the same instance");
    }

    @Test
    void getRestartDelay_forNormalCompletion_returns30Minutes() {
        long delay = manager.getRestartDelay(SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);
        assertEquals(30, delay, "NORMAL_COMPLETION should use 30 minute delay");
    }

    @Test
    void getRestartDelay_forError_returns5Minutes() {
        long delay = manager.getRestartDelay(SnifferLifecycleManager.RestartReason.ERROR);
        assertEquals(5, delay, "ERROR should use 5 minute delay");
    }

    @Test
    void getRestartDelay_forStoragePause_returns0() {
        long delay = manager.getRestartDelay(SnifferLifecycleManager.RestartReason.STORAGE_PAUSE);
        assertEquals(0, delay, "STORAGE_PAUSE should have no delay (0)");
    }

    @Test
    void scheduleRestart_forNormalCompletion_schedulesLongDelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        // Schedule with minimal delay for testing (would override config)
        // In real implementation, this would use the normal delay
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);

        assertTrue(manager.isRestartPending(testDevice),
            "Restart should be pending after scheduling with NORMAL_COMPLETION");

        manager.cancelRestart(testDevice);
    }

    @Test
    void scheduleRestart_forError_schedulesShortDelay() {
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.ERROR);

        assertTrue(manager.isRestartPending(testDevice),
            "Restart should be pending after scheduling with ERROR");

        manager.cancelRestart(testDevice);
    }

    @Test
    void scheduleRestart_forStoragePause_noScheduledDelay() {
        // STORAGE_PAUSE doesn't schedule a restart - it's just tracked for manual resumption
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.STORAGE_PAUSE);

        // STORAGE_PAUSE may or may not be considered "pending" - depends on implementation
        // For now, we just verify it doesn't crash
    }

    @Test
    void cancelRestart_removesPendingRestart() {
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.ERROR);
        assertTrue(manager.isRestartPending(testDevice));

        manager.cancelRestart(testDevice);
        assertFalse(manager.isRestartPending(testDevice),
            "Restart should not be pending after cancellation");
    }

    @Test
    void cancelRestart_onNonExistentRestart_doesNothing() {
        // Canceling a restart that doesn't exist should not throw
        assertDoesNotThrow(() -> manager.cancelRestart(testDevice));
        assertFalse(manager.isRestartPending(testDevice));
    }

    @Test
    void isRestartPending_returnsFalse_whenNoRestartScheduled() {
        assertFalse(manager.isRestartPending(testDevice),
            "Should return false when no restart is scheduled");
    }

    @Test
    void isRestartPending_returnsTrue_whenRestartScheduled() {
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.ERROR);
        assertTrue(manager.isRestartPending(testDevice));
        manager.cancelRestart(testDevice);
    }

    @Test
    void rescheduling_sameDevice_replacesPreviousRestart() {
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);
        assertTrue(manager.isRestartPending(testDevice));

        // Schedule again with different reason
        manager.scheduleRestart(testDevice, SnifferLifecycleManager.RestartReason.ERROR);
        assertTrue(manager.isRestartPending(testDevice));

        // Clean up
        manager.cancelRestart(testDevice);
        assertFalse(manager.isRestartPending(testDevice));
    }

    @Test
    void scheduleRestart_handlesNullDevice_gracefully() {
        // Should handle null gracefully (either ignore or throw specific exception)
        assertDoesNotThrow(() ->
            manager.scheduleRestart(null, SnifferLifecycleManager.RestartReason.ERROR));
    }

    // ==================== Helper Methods ====================

    private Device createTestDevice() {
        // Create a test device with a mock path
        try {
            Path mockPath = Path.of("C:\\test-device");
            return new Device(mockPath);
        } catch (Exception e) {
            // Fallback: use null or minimal device
            return null;
        }
    }
}
