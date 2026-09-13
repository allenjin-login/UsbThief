package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cooldown behaviour of {@link SnifferLifecycleManager} after the cooldown timers moved
 * from one busy-waiting {@code ClockThread} per volume to the shared scheduler.
 *
 * <p>These tests only exercise the cooldown bookkeeping: a restart is never performed
 * because the test serial numbers map to no volume.
 */
class SnifferLifecycleManagerCooldownTest {

    private static final String SERIAL = "SLM-COOLDOWN-TEST-SERIAL";

    @Test
    @Timeout(10)
    void elapsedCooldownFiresAndClearsThePendingRestart() throws Exception {
        SnifferLifecycleManager manager = SnifferLifecycleManager.getInstance();

        manager.scheduleRestart(SERIAL, 50, SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION);

        assertTrue(manager.isRestartPending(SERIAL), "the cooldown must be registered as pending");
        assertTrue(manager.getRemainingCooldownMs(SERIAL) > 0,
                "a pending cooldown must report remaining time");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (manager.isRestartPending(SERIAL) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertFalse(manager.isRestartPending(SERIAL), "an elapsed cooldown must be cleared");
        assertEquals(0L, manager.getRemainingCooldownMs(SERIAL));
    }

    @Test
    @Timeout(10)
    void pausingAVolumeArmsTheNormalCompletionCooldown() throws Exception {
        Path root = Files.createTempDirectory("slm-cooldown-");
        try {
            SnifferLifecycleManager manager = SnifferLifecycleManager.getInstance();
            Volume volume = new Volume(root, SERIAL);

            manager.pause(volume);

            assertTrue(manager.isRestartPending(SERIAL), "pause must schedule a restart");
            long expected = TimeUnit.MINUTES.toMillis(
                    ConfigManager.getInstance().get(StorageConfig.SNIFFER_WAIT_NORMAL_MINUTES));
            long remaining = manager.getRemainingCooldownMs(SERIAL);
            assertTrue(remaining > expected - TimeUnit.SECONDS.toMillis(5) && remaining <= expected,
                    "remaining cooldown " + remaining + "ms must match the configured " + expected + "ms");
        } finally {
            SnifferLifecycleManager.getInstance().stop(SERIAL);
            deleteRecursively(root);
        }
    }

    @Test
    @Timeout(10)
    void stoppingAVolumeCancelsThePendingCooldown() throws Exception {
        Path root = Files.createTempDirectory("slm-cooldown-cancel-");
        try {
            SnifferLifecycleManager manager = SnifferLifecycleManager.getInstance();
            manager.pause(new Volume(root, SERIAL));
            assertTrue(manager.isRestartPending(SERIAL));

            manager.stop(SERIAL);

            assertFalse(manager.isRestartPending(SERIAL), "stop must cancel the cooldown");
            assertEquals(0L, manager.getRemainingCooldownMs(SERIAL));
        } finally {
            SnifferLifecycleManager.getInstance().stop(SERIAL);
            deleteRecursively(root);
        }
    }

    @Test
    void cooldownDelaysFollowTheReason() {
        SnifferLifecycleManager manager = SnifferLifecycleManager.getInstance();
        ConfigManager config = ConfigManager.getInstance();

        assertEquals(TimeUnit.MINUTES.toMillis(config.get(StorageConfig.SNIFFER_WAIT_NORMAL_MINUTES)),
                manager.getRestartDelayMs(SnifferLifecycleManager.RestartReason.NORMAL_COMPLETION));
        assertEquals(TimeUnit.MINUTES.toMillis(config.get(StorageConfig.SNIFFER_WAIT_ERROR_MINUTES)),
                manager.getRestartDelayMs(SnifferLifecycleManager.RestartReason.ERROR));
        assertEquals(0L, manager.getRestartDelayMs(SnifferLifecycleManager.RestartReason.STORAGE_PAUSE),
                "a storage pause restarts immediately instead of waiting out a cooldown");
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        Files.deleteIfExists(dir);
    }
}
