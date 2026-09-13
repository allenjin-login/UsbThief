package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.DelayCopyConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decides whether a file has stopped changing, so that the copy path only ever reads
 * files that are completely written.
 *
 * <p>Files that are still being written - the normal case when a big file is copied onto a
 * USB stick - used to be handed to {@link CopyTask} the moment the watcher saw them, which
 * produced truncated destinations, failed size/checksum checks and wasted retry IO. This gate
 * implements USBCopyer's "delayed copy": the file must look identical across
 * {@code delayCopy.stableMillis} (default 400 ms) before the copy is allowed to start.</p>
 *
 * <p>Deliberately <em>not</em> called on the watcher thread: {@link Sniffer} only queues
 * {@link GatedCopyTask}s, and the waiting happens on a task scheduler worker (the P0 rule that
 * the monitoring thread must never do heavy work).</p>
 *
 * <p>The clock and the sleeping are injectable ({@link Probe}, {@link Sleeper}), so tests can
 * drive every boundary deterministically instead of sleeping through real quiet periods. The
 * wait is bounded by {@code maxWaitRounds} quiet periods rather than by wall-clock time, which
 * keeps the budget testable without a fake clock.</p>
 *
 * <p><b>Degradation rule:</b> a file that is still changing after the whole budget is copied
 * anyway ({@link Outcome#TIMEOUT}). Losing a file is worse than copying a possibly incomplete
 * one - that is exactly the pre-existing behaviour - and the next change event re-copies it.</p>
 */
public final class FileStabilityGate {

    private static final Logger logger = LogManager.getLogger(FileStabilityGate.class);

    /** Consecutive identical samples required by default before a file counts as stable. */
    static final int DEFAULT_STABLE_SAMPLES = 2;

    /** Temporary read failures tolerated before a path is abandoned. */
    static final int DEFAULT_MAX_PROBE_FAILURES = 3;

    /** Upper bound on the wait, counted in quiet periods (default: 15 x 400 ms = 6 s). */
    static final int DEFAULT_MAX_WAIT_ROUNDS = 15;

    /** Accepted range for {@code delayCopy.stableMillis}, guarding against degenerate values. */
    static final long MIN_QUIET_MILLIS = 50;
    static final long MAX_QUIET_MILLIS = 10_000;

    /**
     * Result of a stability check.
     *
     * <p>{@link #copyPermitted()} is the only thing the copy path needs to look at; the
     * remaining values only exist so that logs and tests can tell the cases apart.</p>
     */
    public enum Outcome {
        /** The file looked identical across the quiet period: unambiguously safe to copy. */
        STABLE(true),
        /** The file kept changing for the whole budget: copied anyway (see class javadoc). */
        TIMEOUT(true),
        /** {@code delayCopy.enabled} is false: legacy behaviour, no waiting. */
        DISABLED(true),
        /** Not a regular file (a directory, for instance): the gate does not apply. */
        NOT_APPLICABLE(true),
        /** Gone, unreadable, or stopped being a regular file: nothing to copy. */
        ABANDONED(false),
        /** Another task is already gating this path; this submission is redundant. */
        DUPLICATE(false),
        /** The task was cancelled while waiting: do not copy. */
        INTERRUPTED(false);

        private final boolean copyPermitted;

        Outcome(boolean copyPermitted) {
            this.copyPermitted = copyPermitted;
        }

        /**
         * @return {@code true} when the copy may proceed
         */
        public boolean copyPermitted() {
            return copyPermitted;
        }
    }

    /**
     * One observation of a file: the two attributes that move while a writer is appending.
     *
     * <p>Size is the reliable signal on every file system; the modification time is compared as
     * well so that an in-place rewrite of the same length is noticed on file systems with a fine
     * enough timestamp granularity (exFAT/FAT round to seconds, where a same-size rewrite inside
     * one second is indistinguishable - size still catches the common append case).</p>
     */
    public record Fingerprint(long size, long lastModifiedMillis) {
    }

    /**
     * Reads the current state of a path.
     *
     * @return the fingerprint, or an empty optional when the path exists but is not a regular
     *         file (directory, device, ...)
     * @throws NoSuchFileException when the path is gone
     * @throws IOException         when the attributes cannot be read for any other reason
     */
    @FunctionalInterface
    public interface Probe {
        Optional<Fingerprint> sample(Path path) throws IOException;
    }

    /** Waits between two samples; injectable so that tests never sleep for real. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final int requiredStableSamples;
    private final int maxProbeFailures;
    private final int maxWaitRounds;
    private final Probe probe;
    private final Sleeper sleeper;

    /**
     * Paths currently being gated.
     *
     * <p>{@code CREATE} plus every {@code MODIFY} of the same file used to submit one copy task
     * each; without this set each of them would hold a scheduler worker for the whole quiet
     * period. The first submission wins and the redundant ones return
     * {@link Outcome#DUPLICATE} immediately.</p>
     */
    private final ConcurrentHashMap<Path, Boolean> inFlight = new ConcurrentHashMap<>();

    /** Production gate: real file attributes, real sleeps, settings read per call. */
    public FileStabilityGate() {
        this(DEFAULT_STABLE_SAMPLES, DEFAULT_MAX_PROBE_FAILURES, DEFAULT_MAX_WAIT_ROUNDS,
                FileStabilityGate::readFingerprint, Thread::sleep);
    }

    /**
     * Injection constructor for tests.
     *
     * @param requiredStableSamples consecutive identical samples needed, clamped to at least 2
     * @param maxProbeFailures      read failures tolerated in a row before abandoning
     * @param maxWaitRounds         quiet periods to wait before {@link Outcome#TIMEOUT}
     * @param probe                 attribute reader
     * @param sleeper               wait between two samples
     */
    FileStabilityGate(int requiredStableSamples, int maxProbeFailures, int maxWaitRounds,
                      Probe probe, Sleeper sleeper) {
        this.requiredStableSamples = Math.max(2, requiredStableSamples);
        this.maxProbeFailures = Math.max(1, maxProbeFailures);
        this.maxWaitRounds = Math.max(1, maxWaitRounds);
        this.probe = Objects.requireNonNull(probe, "probe");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * Waits until {@code path} stops changing.
     *
     * <p>Blocking; must be called from a task scheduler worker, never from a watcher thread.</p>
     *
     * @param path the file about to be copied
     * @return the outcome; callers should only act on {@link Outcome#copyPermitted()}
     */
    public Outcome awaitStable(Path path) {
        if (!isEnabled()) {
            return Outcome.DISABLED;
        }
        if (path == null) {
            return Outcome.ABANDONED;
        }

        Path key = path.toAbsolutePath().normalize();
        if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) {
            logger.debug("Stability check already running for {}, dropping duplicate submission", path);
            return Outcome.DUPLICATE;
        }
        try {
            return awaitStableInternal(path);
        } finally {
            inFlight.remove(key);
        }
    }

    private Outcome awaitStableInternal(Path path) {
        long quietMillis = quietPeriodMillis();
        Fingerprint previous = null;
        int equalObservations = 0;
        int failures = 0;

        // One initial sample plus up to maxWaitRounds further samples, one per quiet period.
        for (int round = 0; round <= maxWaitRounds; round++) {
            if (round > 0) {
                try {
                    sleeper.sleep(quietMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("Stability check interrupted for {}", path);
                    return Outcome.INTERRUPTED;
                }
            }

            Optional<Fingerprint> sample;
            try {
                sample = probe.sample(path);
            } catch (NoSuchFileException e) {
                logger.debug("File disappeared while waiting for it to settle: {}", path);
                return Outcome.ABANDONED;
            } catch (IOException e) {
                if (++failures >= maxProbeFailures) {
                    logger.debug("Giving up on {} after {} unreadable samples", path, failures);
                    return Outcome.ABANDONED;
                }
                logger.debug("Sample {} of {} failed for {}: {}", failures, maxProbeFailures, path, e.toString());
                continue;
            }
            failures = 0;

            if (sample.isEmpty()) {
                if (round == 0) {
                    // Not a regular file (typically a directory): there is nothing that can be
                    // "still being written", and CopyTask handles it on its own.
                    return Outcome.NOT_APPLICABLE;
                }
                logger.debug("Path stopped being a regular file while waiting: {}", path);
                return Outcome.ABANDONED;
            }

            Fingerprint current = sample.get();
            if (current.equals(previous)) {
                equalObservations++;
                if (equalObservations >= requiredStableSamples - 1) {
                    return Outcome.STABLE;
                }
            } else {
                equalObservations = 0;
                previous = current;
            }
        }

        return Outcome.TIMEOUT;
    }

    /**
     * @return the configured quiet period in milliseconds, clamped to a sane range
     */
    static long quietPeriodMillis() {
        Integer configured = ConfigManager.getInstance().get(DelayCopyConfig.DELAY_COPY_STABLE_MILLIS);
        long millis = configured != null ? configured : DelayCopyConfig.DELAY_COPY_STABLE_MILLIS.defaultValue();
        return Math.clamp(millis, MIN_QUIET_MILLIS, MAX_QUIET_MILLIS);
    }

    private static boolean isEnabled() {
        return Boolean.TRUE.equals(ConfigManager.getInstance().get(DelayCopyConfig.DELAY_COPY_ENABLED));
    }

    /**
     * Production {@link Probe}: reads the attributes that change while a file is being written.
     *
     * @param path the path to sample
     * @return the fingerprint, or empty when the path is not a regular file
     * @throws IOException when the attributes cannot be read
     */
    static Optional<Fingerprint> readFingerprint(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) {
            return Optional.empty();
        }
        return Optional.of(new Fingerprint(attributes.size(), attributes.lastModifiedTime().toMillis()));
    }
}
