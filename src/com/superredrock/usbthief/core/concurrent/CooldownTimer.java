package com.superredrock.usbthief.core.concurrent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keyed cooldown timers backed by a shared {@link ScheduledExecutorService}.
 *
 * <p>Replaces the previous "one busy-waiting {@code ClockThread} per volume" model: a
 * cooldown is a single scheduled task in the shared scheduler's delay queue, so an idle
 * cooldown consumes no CPU and no thread. Scheduling a key that already has a pending
 * timer replaces it (the previous delay is cancelled), and a timer removes itself from
 * the pending map before its action runs.
 *
 * <p>All methods are thread-safe.
 */
public final class CooldownTimer {

    private static final Logger logger = LogManager.getLogger(CooldownTimer.class);

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    /**
     * Creates a timer table on the application's shared cooldown scheduler.
     */
    public CooldownTimer() {
        this(ThreadPools.cooldownScheduler());
    }

    /**
     * Creates a timer table on the given scheduler. Used to inject a scheduler in tests.
     *
     * @param scheduler the scheduler that will run the cooldown actions
     */
    public CooldownTimer(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }
        this.scheduler = scheduler;
    }

    /**
     * Schedules {@code action} to run once after {@code delayMs} for the given key.
     *
     * <p>If the key already has a pending timer it is cancelled and replaced, so at most
     * one timer per key can be pending.
     *
     * @param key      the cooldown key (e.g. a volume serial number)
     * @param delayMs  the delay in milliseconds, must be non-negative
     * @param action   the action to run when the delay elapses
     */
    public void schedule(final String key, long delayMs, final Runnable action) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        if (delayMs < 0) {
            throw new IllegalArgumentException("Delay must be non-negative: " + delayMs);
        }

        // The task needs its own future to remove itself from the map; the reference is
        // published through an AtomicReference because it only exists after scheduling.
        final AtomicReference<ScheduledFuture<?>> self = new AtomicReference<>();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                ScheduledFuture<?> mine = self.get();
                if (mine != null) {
                    pending.remove(key, mine);
                }
                try {
                    action.run();
                } catch (RuntimeException e) {
                    // A failing cooldown action must not take the shared scheduler (or the
                    // other volumes' cooldowns) down with it.
                    logger.warn("Cooldown action failed for {}", key, e);
                }
            }
        };

        ScheduledFuture<?> future = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        self.set(future);

        ScheduledFuture<?> previous = pending.put(key, future);
        if (previous != null) {
            previous.cancel(false);
        }
        if (future.isDone()) {
            // delayMs == 0 and the action already ran before the reference was published.
            pending.remove(key, future);
        }
    }

    /**
     * Cancels the pending timer for the key, if any.
     *
     * @param key the cooldown key
     * @return true if a pending timer was cancelled
     */
    public boolean cancel(String key) {
        ScheduledFuture<?> future = pending.remove(key);
        if (future == null) {
            return false;
        }
        future.cancel(false);
        return true;
    }

    /**
     * Cancels every pending timer.
     */
    public void cancelAll() {
        for (Map.Entry<String, ScheduledFuture<?>> entry : pending.entrySet()) {
            entry.getValue().cancel(false);
        }
        pending.clear();
    }

    /**
     * @param key the cooldown key
     * @return true if a timer is currently pending for the key
     */
    public boolean isPending(String key) {
        return pending.containsKey(key);
    }

    /**
     * @param key the cooldown key
     * @return the remaining delay in milliseconds, or 0 when nothing is pending
     */
    public long remainingMs(String key) {
        ScheduledFuture<?> future = pending.get(key);
        if (future == null) {
            return 0L;
        }
        return Math.max(0L, future.getDelay(TimeUnit.MILLISECONDS));
    }

    /**
     * @return a snapshot of every pending cooldown mapped to its remaining milliseconds
     */
    public Map<String, Long> remainingMsSnapshot() {
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<String, ScheduledFuture<?>> entry : pending.entrySet()) {
            long remaining = entry.getValue().getDelay(TimeUnit.MILLISECONDS);
            snapshot.put(entry.getKey(), Math.max(0L, remaining));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * @return the number of pending cooldowns
     */
    public int pendingCount() {
        return pending.size();
    }
}
