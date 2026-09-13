package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.DelayCopyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.superredrock.usbthief.worker.FileStabilityGate.Fingerprint;
import static com.superredrock.usbthief.worker.FileStabilityGate.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the delayed-copy gate.
 *
 * <p>Every wait is driven by an injected {@link FileStabilityGate.Sleeper} and a scripted
 * {@link FileStabilityGate.Probe}, and the wait budget counts quiet periods instead of wall-clock
 * time - so these tests are deterministic and never sleep. The three tests that use the
 * production probe do so on files nobody writes to (or that the injected sleeper writes to at a
 * controlled moment), which keeps them just as deterministic.</p>
 */
class FileStabilityGateTest {

    private static final Path PATH = Path.of("/tmp/usbthief-stability-test.bin");

    private boolean originalEnabled;
    private int originalStableMillis;

    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void saveConfig() {
        originalEnabled = ConfigManager.getInstance().get(DelayCopyConfig.DELAY_COPY_ENABLED);
        originalStableMillis = ConfigManager.getInstance().get(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS);
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_ENABLED, true);
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS,
                DelayCopyConfig.DELAY_COPY_STABLE_MILLIS.defaultValue());
    }

    @AfterEach
    void restoreConfig() {
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_ENABLED, originalEnabled);
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS, originalStableMillis);
    }

    // ---------------------------------------------------------------- stability

    @Test
    void stableFilePassesAfterOneQuietPeriod() {
        FileStabilityGate gate = gate(15, probe(sample(10), sample(10)));

        assertEquals(STABLE, gate.awaitStable(PATH));
        assertEquals(List.of(400L), sleeps, "an unchanged file costs exactly one quiet period");
    }

    @Test
    void configuredQuietPeriodIsUsedBetweenSamples() {
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS, 750);
        FileStabilityGate gate = gate(15, probe(sample(1), sample(1)));

        assertEquals(STABLE, gate.awaitStable(PATH));
        assertEquals(List.of(750L), sleeps);
    }

    @Test
    void growingFileIsAcceptedOnlyOnceItStopsGrowing() {
        FileStabilityGate gate = gate(15, probe(sample(10), sample(20), sample(30), sample(30)));

        assertEquals(STABLE, gate.awaitStable(PATH));
        assertEquals(3, sleeps.size(), "one quiet period per observed change, then one quiet period");
    }

    @Test
    void sameSizeRewriteIsDetectedThroughTheModificationTime() {
        // Same length, newer timestamp: an in-place rewrite, not a finished file.
        FileStabilityGate gate = gate(15, probe(
                () -> Optional.of(new Fingerprint(10, 1_000L)),
                () -> Optional.of(new Fingerprint(10, 2_000L)),
                () -> Optional.of(new Fingerprint(10, 2_000L))));

        assertEquals(STABLE, gate.awaitStable(PATH));
        assertEquals(2, sleeps.size());
    }

    @Test
    void fileThatNeverSettlesTimesOutAndIsStillCopied() {
        AtomicInteger size = new AtomicInteger();
        FileStabilityGate gate = gate(3, path -> {
            long next = size.incrementAndGet();
            return Optional.of(new Fingerprint(next, next));
        });

        FileStabilityGate.Outcome outcome = gate.awaitStable(PATH);

        assertEquals(TIMEOUT, outcome);
        assertTrue(outcome.copyPermitted(),
                "an unfinished file must still be copied eventually - losing it is the worse outcome");
        assertEquals(3, sleeps.size(), "the wait is bounded by the configured number of quiet periods");
    }

    // ------------------------------------------------------------------ boundaries

    @Test
    void deletedFileIsAbandonedWithoutWaiting() {
        FileStabilityGate gate = gate(15, probe(GONE));

        FileStabilityGate.Outcome outcome = gate.awaitStable(PATH);

        assertEquals(ABANDONED, outcome);
        assertFalse(outcome.copyPermitted());
        assertEquals(List.of(), sleeps, "a file that is already gone must not hold a worker thread");
    }

    @Test
    void fileThatDisappearsWhileWaitingIsAbandoned() {
        FileStabilityGate gate = gate(15, probe(sample(10), GONE));

        assertEquals(ABANDONED, gate.awaitStable(PATH));
        assertEquals(1, sleeps.size());
    }

    @Test
    void nullPathIsAbandonedInsteadOfThrowing() {
        assertEquals(ABANDONED, gate(15, probe(sample(1))).awaitStable(null));
    }

    @Test
    void directoryIsNotGated() {
        FileStabilityGate gate = gate(15, probe(NOT_A_REGULAR_FILE));

        FileStabilityGate.Outcome outcome = gate.awaitStable(PATH);

        assertEquals(NOT_APPLICABLE, outcome);
        assertTrue(outcome.copyPermitted(), "directory tasks must still create their folder");
        assertEquals(List.of(), sleeps, "directories have no 'still being written' state to wait for");
    }

    @Test
    void pathThatStopsBeingARegularFileWhileWaitingIsAbandoned() {
        FileStabilityGate gate = gate(15, probe(sample(10), NOT_A_REGULAR_FILE));

        assertEquals(ABANDONED, gate.awaitStable(PATH));
    }

    @Test
    void unreadableFileIsRetriedAndThenAbandoned() {
        AtomicInteger probes = new AtomicInteger();
        FileStabilityGate gate = gate(15, path -> {
            probes.incrementAndGet();
            throw new AccessDeniedException("denied");
        });

        assertEquals(ABANDONED, gate.awaitStable(PATH));
        assertEquals(FileStabilityGate.DEFAULT_MAX_PROBE_FAILURES, probes.get(),
                "a temporary read failure must be retried, a permanent one must give up");
    }

    @Test
    void readFailuresDoNotResetTheStabilityCounter() {
        // Sample fails once in the middle: the file has to settle again afterwards.
        AtomicInteger call = new AtomicInteger();
        FileStabilityGate gate = gate(15, path -> {
            int n = call.incrementAndGet();
            if (n == 1) {
                throw new AccessDeniedException("denied");
            }
            return Optional.of(new Fingerprint(5, 5));
        });

        assertEquals(STABLE, gate.awaitStable(PATH));
        assertEquals(2, sleeps.size(), "the failed sample restarts the observation, so one more period is needed");
    }

    @Test
    void interruptedWaitIsNotCopiedAndRestoresTheFlag() {
        FileStabilityGate gate = new FileStabilityGate(2, 3, 15, probe(sample(1), sample(1)),
                millis -> {
                    throw new InterruptedException("cancelled");
                });

        try {
            assertEquals(INTERRUPTED, gate.awaitStable(PATH));
            assertFalse(INTERRUPTED.copyPermitted());
            assertTrue(Thread.interrupted(), "the interrupt flag must be handed back to the caller");
        } finally {
            Thread.interrupted();
        }
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    @Timeout(30)
    void duplicateSubmissionIsDroppedWhileTheFirstCheckRuns() throws Exception {
        CountDownLatch inQuietPeriod = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FileStabilityGate gate = new FileStabilityGate(2, 3, 15, probe(sample(1), sample(1)),
                millis -> {
                    inQuietPeriod.countDown();
                    release.await();
                });

        AtomicReference<FileStabilityGate.Outcome> first = new AtomicReference<>();
        Thread worker = new Thread(() -> first.set(gate.awaitStable(PATH)), "gate-test-worker");
        worker.start();
        try {
            assertTrue(inQuietPeriod.await(5, TimeUnit.SECONDS), "the first check must reach its quiet period");

            assertEquals(DUPLICATE, gate.awaitStable(PATH),
                    "CREATE + MODIFY storms must not occupy one worker thread per event");
            assertEquals(DUPLICATE, gate.awaitStable(PATH.toAbsolutePath().normalize()),
                    "the same file reached through another path spelling is still the same file");
        } finally {
            release.countDown();
            worker.join(5_000);
        }

        assertEquals(STABLE, first.get());
    }

    @Test
    void aSecondFileIsNotBlockedByTheFirstOne() {
        FileStabilityGate gate = gate(15, probe(sample(1), sample(1)));

        assertEquals(STABLE, gate.awaitStable(PATH));
        assertEquals(STABLE, gate.awaitStable(Path.of("/tmp/usbthief-stability-other.bin")));
    }

    // ------------------------------------------------------------------- switches

    @Test
    void disabledGateCopiesWithoutTouchingTheFile() {
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_ENABLED, false);
        AtomicInteger probes = new AtomicInteger();
        FileStabilityGate gate = gate(15, path -> {
            probes.incrementAndGet();
            return Optional.of(new Fingerprint(1, 1));
        });

        FileStabilityGate.Outcome outcome = gate.awaitStable(PATH);

        assertEquals(DISABLED, outcome);
        assertTrue(outcome.copyPermitted());
        assertEquals(0, probes.get(), "the disabled gate must not probe the file system");
        assertEquals(0, sleeps.size());
    }

    @Test
    void quietPeriodIsClampedToASaneRange() {
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS, 1_234);
        assertEquals(1_234, FileStabilityGate.quietPeriodMillis());

        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS, 0);
        assertEquals(FileStabilityGate.MIN_QUIET_MILLIS, FileStabilityGate.quietPeriodMillis());

        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS, Integer.MAX_VALUE);
        assertEquals(FileStabilityGate.MAX_QUIET_MILLIS, FileStabilityGate.quietPeriodMillis());
    }

    // ------------------------------------------------------------ production probe

    @Test
    void productionProbeReportsSizeAndModificationTime(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("real.bin");
        Files.writeString(file, "abc");

        Fingerprint before = FileStabilityGate.readFingerprint(file).orElseThrow();
        assertEquals(3, before.size());

        Files.writeString(file, "def", StandardOpenOption.APPEND);
        Fingerprint after = FileStabilityGate.readFingerprint(file).orElseThrow();

        assertEquals(6, after.size());
        assertNotEquals(before, after, "an append must be visible to the gate");
    }

    @Test
    void productionProbeRejectsDirectoriesAndMissingFiles(@TempDir Path dir) throws Exception {
        assertTrue(FileStabilityGate.readFingerprint(dir).isEmpty(),
                "a directory is not a file that can be 'still being written'");
        assertThrows(NoSuchFileException.class,
                () -> FileStabilityGate.readFingerprint(dir.resolve("missing.bin")));
    }

    @Test
    void defaultGateUsesRealSleepsAndRealAttributes(@TempDir Path dir) throws Exception {
        ConfigManager.getInstance().set(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS,
                (int) FileStabilityGate.MIN_QUIET_MILLIS);
        Path file = dir.resolve("settled.bin");
        Files.writeString(file, "done");

        assertEquals(STABLE, new FileStabilityGate().awaitStable(file),
                "a finished file passes the production gate after one real quiet period");
    }

    @Test
    void writeDuringTheQuietPeriodDelaysStability(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("slow.bin");
        Files.writeString(file, "");

        AtomicInteger sleepsSoFar = new AtomicInteger();
        FileStabilityGate gate = new FileStabilityGate(2, 3, 15, FileStabilityGate::readFingerprint,
                millis -> {
                    if (sleepsSoFar.incrementAndGet() == 1) {
                        try {
                            // Simulates the tail of an ongoing write landing between two samples.
                            Files.writeString(file, "first chunk", StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });

        assertEquals(STABLE, gate.awaitStable(file));
        assertEquals(2, sleepsSoFar.get(), "the write costs one extra quiet period");
    }

    // ---------------------------------------------------------------------- helpers

    private FileStabilityGate gate(int maxWaitRounds, FileStabilityGate.Probe probe) {
        return new FileStabilityGate(
                FileStabilityGate.DEFAULT_STABLE_SAMPLES,
                FileStabilityGate.DEFAULT_MAX_PROBE_FAILURES,
                maxWaitRounds,
                probe,
                millis -> sleeps.add(millis));
    }

    /**
     * One scripted answer of {@link #probe}.
     *
     * <p>Its own type rather than a {@link java.util.function.Supplier} because a step has to be
     * able to fail with a checked {@link java.io.IOException}.</p>
     */
    @FunctionalInterface
    private interface Step {
        Optional<Fingerprint> sample() throws IOException;
    }

    /** Scripted probe: each call consumes the next step, and the last step repeats forever. */
    private static FileStabilityGate.Probe probe(Step... steps) {
        AtomicInteger index = new AtomicInteger();
        return path -> steps[Math.min(index.getAndIncrement(), steps.length - 1)].sample();
    }

    private static Step sample(long size) {
        return () -> Optional.of(new Fingerprint(size, size));
    }

    private static final Step GONE = () -> {
        throw new NoSuchFileException("gone");
    };

    private static final Step NOT_A_REGULAR_FILE = Optional::empty;
}
