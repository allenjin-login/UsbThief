package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the change-batching fix: previously every change event
 * incremented a counter and the sub-threshold remainder was discarded by the
 * reset tick (files could be lost); now changes are queued and never dropped --
 * the batch flushes at the threshold, and the periodic tick flushes the rest.
 */
class SnifferBatchingTest {

    private int originalThreshold;

    @BeforeEach
    void saveConfig() {
        originalThreshold = ConfigManager.getInstance().get(FileWatchConfig.WATCH_THRESHOLD);
    }

    @AfterEach
    void restoreConfig() {
        ConfigManager.getInstance().set(FileWatchConfig.WATCH_THRESHOLD, originalThreshold);
    }

    @Test
    @Timeout(30)
    void subThresholdChangesAreFlushedPeriodicallyNotDropped() throws Exception {
        ConfigManager.getInstance().set(FileWatchConfig.WATCH_THRESHOLD, 10);
        Path root = Files.createTempDirectory("sniffer-batch-test-");
        try {
            Path f1 = Files.createFile(root.resolve("a.txt"));
            Path f2 = Files.createFile(root.resolve("b.txt"));
            Path f3 = Files.createFile(root.resolve("c.txt"));

            Sniffer sniffer = new Sniffer(new Volume(root, "SNIFFER-BATCH-TEST"));
            try {
                sniffer.enqueueChange(f1, StandardWatchEventKinds.ENTRY_CREATE);
                sniffer.enqueueChange(f2, StandardWatchEventKinds.ENTRY_CREATE);
                sniffer.enqueueChange(f3, StandardWatchEventKinds.ENTRY_CREATE);
                assertEquals(3, sniffer.getChangeCount(), "sub-threshold changes must stay queued");

                sniffer.flushPendingChanges();
                assertEquals(0, sniffer.getChangeCount(), "flush must process the entire queue");
            } finally {
                sniffer.close();
            }
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    @Timeout(30)
    void thresholdTriggersAutomaticFlush() throws Exception {
        ConfigManager.getInstance().set(FileWatchConfig.WATCH_THRESHOLD, 3);
        Path root = Files.createTempDirectory("sniffer-batch-threshold-");
        try {
            Sniffer sniffer = new Sniffer(new Volume(root, "SNIFFER-BATCH-THRESHOLD"));
            try {
                for (int i = 0; i < 3; i++) {
                    Path f = Files.createFile(root.resolve("f" + i + ".txt"));
                    sniffer.enqueueChange(f, StandardWatchEventKinds.ENTRY_CREATE);
                }
                assertEquals(0, sniffer.getChangeCount(),
                        "reaching the threshold must trigger an automatic flush");
            } finally {
                sniffer.close();
            }
        } finally {
            deleteRecursively(root);
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
