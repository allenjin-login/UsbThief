package com.superredrock.usbthief.worker;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Aggregates multiple speed probes for combined monitoring.
 *
 * <p>Automatically cleans up probes that have been closed or garbage collected
 * using weak references and a reference queue.</p>
 *
 * <p>Thread-safe: all operations are lock-free using ConcurrentLinkedQueue
 * and weak references.</p>
 *
 * @since 2026-02-03
 */
public final class SpeedProbeGroup implements Closeable {
    private static final Logger logger = Logger.getLogger(SpeedProbeGroup.class.getName());

    private final String name;
    private final ConcurrentLinkedQueue<WeakReference<SpeedProbe>> probes;
    private final ReferenceQueue<SpeedProbe> refQueue;

    /**
     * Creates a new probe group with the given name.
     *
     * @param name the group name for identification
     */
    public SpeedProbeGroup(String name) {
        this.name = name;
        this.probes = new ConcurrentLinkedQueue<>();
        this.refQueue = new ReferenceQueue<>();
    }

    /**
     * Adds a probe to this group.
     *
     * <p>The probe is held via weak reference, allowing automatic cleanup
     * when no longer referenced elsewhere.</p>
     *
     * @param probe the probe to add (must not be null)
     * @throws NullPointerException if probe is null
     */
    public void addProbe(SpeedProbe probe) {
        Objects.requireNonNull(probe, "Probe cannot be null");
        probes.offer(new WeakReference<>(probe, refQueue));
        logger.fine("Added probe [" + probe.getName() + "] to group [" + name + "]");
    }

    /**
     * Removes a probe from this group.
     *
     * @param probe the probe to remove
     * @return true if the probe was removed, false otherwise
     */
    public boolean removeProbe(SpeedProbe probe) {
        boolean removed = probes.removeIf(ref -> {
            SpeedProbe p = ref.get();
            return p != null && p.equals(probe);
        });

        if (removed) {
            logger.fine("Removed probe [" + probe.getName() + "] from group [" + name + "]");
        }
        return removed;
    }

    /**
     * Cleans up probes that have been garbage collected or closed.
     *
     * <p>This method is lazy - it only runs when explicitly called
     * (typically within getTotalSpeed()).</p>
     */
    private void cleanup() {
        // Remove garbage-collected probes via reference queue
        java.lang.ref.Reference<? extends SpeedProbe> ref;
        while ((ref = refQueue.poll()) != null) {
            probes.remove(ref);
        }

        // Remove closed or stale weak references
        probes.removeIf(weakRef -> {
            SpeedProbe probe = weakRef.get();
            return probe == null || probe.isClosed();
        });
    }

    /**
     * Returns the total speed across all active probes.
     *
     * <p>Automatically cleans up closed/garbage-collected probes before calculation.</p>
     *
     * @return total speed in MB/s
     */
    public double getTotalSpeed() {
        cleanup();

        return probes.stream()
                .map(WeakReference::get)
                .filter(Objects::nonNull)
                .filter(p -> !p.isClosed())
                .mapToDouble(SpeedProbe::getSpeed)
                .sum();
    }

    /**
     * Returns the total bytes transferred across all active probes.
     *
     * @return total bytes
     */
    public long getTotalBytes() {
        cleanup();

        return probes.stream()
                .map(WeakReference::get)
                .filter(Objects::nonNull)
                .filter(p -> !p.isClosed())
                .mapToLong(SpeedProbe::getTotalBytes)
                .sum();
    }

    /**
     * Returns the number of active probes in this group.
     *
     * @return active probe count
     */
    public int getProbeCount() {
        cleanup();
        return (int) probes.stream()
                .map(WeakReference::get)
                .filter(Objects::nonNull)
                .filter(p -> !p.isClosed())
                .count();
    }

    /**
     * Returns the group name.
     *
     * @return group name
     */
    public String getName() {
        return name;
    }

    @Override
    public void close() {
        // Close all probes
        probes.forEach(ref -> {
            SpeedProbe probe = ref.get();
            if (probe != null && !probe.isClosed()) {
                probe.close();
            }
        });
        probes.clear();
        logger.fine("SpeedProbeGroup [" + name + "] closed");
    }

    @Override
    public String toString() {
        return String.format("SpeedProbeGroup[name=%s, probes=%d, speed=%.2f MB/s]",
                name, getProbeCount(), getTotalSpeed());
    }
}
