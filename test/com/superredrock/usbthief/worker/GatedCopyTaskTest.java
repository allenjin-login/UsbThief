package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.DelayCopyConfig;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.PathConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.config.configs.StorageConfig;
import com.superredrock.usbthief.core.event.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the delayed copy path: the gate decides <em>whether</em> a copy runs,
 * the copy itself is the ordinary {@link CopyTask} work.
 *
 * <p>All waits are injected away (no-op sleeper), so these tests verify the wiring, not the
 * timing - the timing lives in {@link FileStabilityGateTest}.</p>
 */
class GatedCopyTaskTest {

    private static final String SERIAL = "GATED-SERIAL";
    private static final String VOLUME_NAME = "gatedvol";

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private Path destDir;

    private boolean originalEnabled;

    @BeforeEach
    void setUp() throws IOException {
        sourceDir = tempDir.resolve("source");
        destDir = tempDir.resolve("dest");
        Files.createDirectories(sourceDir);
        Files.createDirectories(destDir);

        originalEnabled = ConfigManager.getInstance().get(DelayCopyConfig.DELAY_COPY_ENABLED);
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_ENABLED, true);

        EventBus.getInstance().clearAll();
    }

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clearAll();
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_ENABLED, originalEnabled);
        ConfigManager.getInstance().set(PathConfig.WORK_PATH, PathConfig.WORK_PATH.defaultValue());
        ConfigManager.getInstance().set(FileCopyConfig.BUFFER_SIZE, FileCopyConfig.BUFFER_SIZE.defaultValue());
        ConfigManager.getInstance().set(StorageConfig.STORAGE_ENABLED, StorageConfig.STORAGE_ENABLED.defaultValue());
        StorageController.getInstance().invalidateStorageStatus();
    }

    @Test
    void fileThatNeverSettlesIsStillCopied() throws Exception {
        enableCopying();
        Path source = sourceDir.resolve("stream.bin");
        Files.write(source, new byte[2048]);

        AtomicInteger sample = new AtomicInteger();
        // Never the same fingerprint twice: the gate runs out of patience and lets the copy run.
        FileStabilityGate impatientGate = new FileStabilityGate(2, 3, 2, path -> {
            long next = sample.incrementAndGet();
            return Optional.of(new FileStabilityGate.Fingerprint(next, next));
        }, millis -> { });

        CopyResult result = new GatedCopyTask(source, SERIAL, namedVolume(), impatientGate).call();

        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.exists(destinationOf(source)), "the file must not be lost when it never settles");
    }

    @Test
    void fileThatDisappearedIsSkippedInsteadOfCopied() throws Exception {
        enableCopying();
        Path source = sourceDir.resolve("vanished.bin");

        FileStabilityGate goneGate = new FileStabilityGate(2, 3, 15, path -> {
            throw new NoSuchFileException("gone");
        }, millis -> { });

        assertEquals(CopyResult.SKIPPED, new GatedCopyTask(source, SERIAL, namedVolume(), goneGate).call());
        assertFalse(Files.exists(destinationOf(source)));
    }

    @Test
    void stableFileIsCopiedThroughTheOrdinaryCopyPath() throws Exception {
        enableCopying();
        Path source = sourceDir.resolve("payload.bin");
        byte[] payload = {1, 2, 3, 4, 5};
        Files.write(source, payload);

        CopyResult result = new GatedCopyTask(source, SERIAL, namedVolume(), noWaitGate()).call();

        assertEquals(CopyResult.SUCCESS, result);
        assertArrayEquals(payload, Files.readAllBytes(destinationOf(source)));
    }

    @Test
    void directoriesAreNotDelayedButStillCreated() throws Exception {
        enableCopying();
        Path source = sourceDir.resolve("nested").resolve("deeper");
        Files.createDirectories(source);

        AtomicInteger probes = new AtomicInteger();
        FileStabilityGate directoryGate = new FileStabilityGate(2, 3, 15, path -> {
            probes.incrementAndGet();
            return Optional.empty();
        }, millis -> fail("a directory must not wait for a quiet period"));

        CopyResult result = new GatedCopyTask(source, SERIAL, namedVolume(), directoryGate).call();

        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.isDirectory(destinationOf(source)), "folder creation must survive the delay feature");
        assertEquals(1, probes.get(), "one sample is enough to see that this is not a regular file");
    }

    /**
     * The delayed task must keep the per-extension/size priority of a plain copy.
     *
     * <p>This is the reason {@code GatedCopyTask} extends {@code CopyTask} instead of wrapping it:
     * {@link PriorityRule} switches on the task type, and a wrapper would silently fall back to
     * {@code DEFAULT_PRIORITY}, flattening the copy ordering.</p>
     */
    @Test
    void gatedTaskKeepsThePlainCopyPriority() throws Exception {
        Path pdf = sourceDir.resolve("report.pdf");
        Files.writeString(pdf, "small document");
        PriorityRule rule = new PriorityRule();

        int plain = rule.calculatePriority(new CopyTask(pdf, SERIAL));
        int gated = rule.calculatePriority(new GatedCopyTask(pdf, SERIAL, null, noWaitGate()));

        assertEquals(plain, gated);
    }

    // ---------------------------------------------------------------------- helpers

    /** A gate that never waits: the file is accepted as stable on the second identical sample. */
    private static FileStabilityGate noWaitGate() {
        return new FileStabilityGate(2, 3, 15, FileStabilityGate::readFingerprint, millis -> { });
    }

    private void enableCopying() {
        ConfigManager.getInstance().set(PathConfig.WORK_PATH, destDir.toString());
        ConfigManager.getInstance().set(StorageConfig.STORAGE_ENABLED, false);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_WRITE_RATE_LIMIT, 0L);
        StorageController.getInstance().invalidateStorageStatus();
    }

    /**
     * Volume with a deterministic name, so the destination does not depend on the file store.
     */
    private Volume namedVolume() {
        return new Volume(sourceDir, SERIAL) {
            @Override
            public String getVolumeName() {
                return VOLUME_NAME;
            }
        };
    }

    private Path destinationOf(Path source) {
        return destDir.resolve(VOLUME_NAME + "_" + SERIAL).resolve(source.getRoot().relativize(source));
    }
}
