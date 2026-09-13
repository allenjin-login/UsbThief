package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the "initial scan registers no WatchKeys" defect:
 * with real-time monitoring enabled, the initial scan must register the root
 * directory and every discovered directory with the WatchService -- otherwise
 * files added after the scan completes are never observed, and the monitor
 * loop blocks forever on take() (which also breaks the cooldown-rescan cycle).
 */
class SnifferWatchTest {

    private boolean originalWatchEnabled;

    @BeforeEach
    void saveConfig() {
        originalWatchEnabled = ConfigManager.getInstance().get(FileWatchConfig.WATCH_ENABLED);
    }

    @AfterEach
    void restoreConfig() {
        ConfigManager.getInstance().set(FileWatchConfig.WATCH_ENABLED, originalWatchEnabled);
    }

    @Test
    @Timeout(60)
    void initialScanRegistersWatchesWhenMonitoringEnabled() throws Exception {
        ConfigManager.getInstance().set(FileWatchConfig.WATCH_ENABLED, true);
        Path root = Files.createTempDirectory("sniffer-watch-test-");
        try {
            Files.createDirectories(root.resolve("sub1").resolve("sub2"));

            Volume volume = new Volume(root, "SNIFFER-WATCH-TEST");
            Sniffer sniffer = new Sniffer(volume);
            try {
                sniffer.start();
                awaitPhase(sniffer, SnifferPhase.MONITORING, 20, TimeUnit.SECONDS);
                assertEquals(SnifferPhase.MONITORING, sniffer.getPhase(),
                        "sniffer should reach the MONITORING phase");
                assertTrue(sniffer.getWatchedDirCount() >= 3,
                        "root + sub1 + sub2 must be registered with the WatchService, got "
                                + sniffer.getWatchedDirCount());
            } finally {
                sniffer.close();
            }
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    @Timeout(60)
    void scanWithoutMonitoringRegistersNothingAndCompletes() throws Exception {
        ConfigManager.getInstance().set(FileWatchConfig.WATCH_ENABLED, false);
        Path root = Files.createTempDirectory("sniffer-nowatch-test-");
        try {
            Files.createDirectories(root.resolve("dirA"));

            Volume volume = new Volume(root, "SNIFFER-NOWATCH-TEST");
            Sniffer sniffer = new Sniffer(volume);
            try {
                sniffer.start();
                // run() must complete normally: scan -> monitoring disabled -> completion future done
                sniffer.onFinish().get(20, TimeUnit.SECONDS);
                assertEquals(0, sniffer.getWatchedDirCount(),
                        "no watches should be registered when monitoring is disabled");
            } finally {
                sniffer.close();
            }
        } finally {
            deleteRecursively(root);
        }
    }

    private static void awaitPhase(Sniffer sniffer, SnifferPhase target, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (sniffer.getPhase() != target && sniffer.getPhase() != SnifferPhase.FINISHED
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }
}
