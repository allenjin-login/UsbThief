package com.superredrock.usbthief.core.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry of the application's dedicated thread pools.
 *
 * <p>Every pool here exists to keep a specific workload off
 * {@link java.util.concurrent.ForkJoinPool#commonPool()}, whose parallelism is
 * {@code availableProcessors() - 1} and which is shared with every
 * {@code CompletableFuture} async stage in the JVM. Long blocking disk scans, event
 * listener notification and storage statistics used to contend for that single pool,
 * so a large scan could starve event delivery (and vice versa).
 *
 * <p>Pools are created lazily, never shut down (every thread is a daemon, so the JVM can
 * exit regardless) and are all named {@code usbthief-<pool>-#} so they can be identified
 * in a thread dump.
 *
 * <p>No virtual-thread APIs are used: the code base is still back-ported to Java 11.
 */
public final class ThreadPools {

    /** Prefix shared by every thread created here. */
    public static final String THREAD_NAME_PREFIX = "usbthief-";

    /** Lower/upper bound for the IO-bound scan pool: small and bounded on purpose. */
    private static final int MIN_SCAN_THREADS = 2;
    private static final int MAX_SCAN_THREADS = 4;

    /** Event listener notification is short and must not be starved by scans. */
    private static final int EVENT_THREADS = 2;

    private static volatile ScheduledExecutorService cooldownScheduler;
    private static volatile ExecutorService scanExecutor;
    private static volatile ExecutorService eventExecutor;
    private static volatile ExecutorService recycleExecutor;

    private ThreadPools() {
    }

    /**
     * Creates a factory for daemon threads named {@code usbthief-<poolName>-#}.
     *
     * @param poolName short pool identifier, e.g. {@code scan}
     * @return the thread factory
     */
    public static ThreadFactory namedDaemonThreadFactory(String poolName) {
        if (poolName == null) {
            throw new IllegalArgumentException("poolName cannot be null");
        }
        final String name = THREAD_NAME_PREFIX + poolName + "-";
        final AtomicInteger counter = new AtomicInteger(0);
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * Shared scheduler used for cooldowns (Sniffer restart delays).
     *
     * <p>A single thread serves every volume: {@link java.util.concurrent.ScheduledThreadPoolExecutor}
     * keeps the pending delays in a heap, so N volumes in cooldown cost one thread and no
     * polling, instead of one busy-waiting thread each.
     *
     * <p>Cancelled delays are removed eagerly ({@code setRemoveOnCancelPolicy(true)});
     * otherwise a cancelled 30 minute cooldown would keep occupying the queue until its
     * original deadline.
     *
     * @return the shared cooldown scheduler
     */
    public static ScheduledExecutorService cooldownScheduler() {
        ScheduledExecutorService local = cooldownScheduler;
        if (local == null) {
            synchronized (ThreadPools.class) {
                local = cooldownScheduler;
                if (local == null) {
                    ScheduledThreadPoolExecutor scheduler =
                            new ScheduledThreadPoolExecutor(1, namedDaemonThreadFactory("cooldown"));
                    scheduler.setRemoveOnCancelPolicy(true);
                    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
                    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
                    local = scheduler;
                    cooldownScheduler = local;
                }
            }
        }
        return local;
    }

    /**
     * Pool for directory scans ({@code Files.find} walks of a whole volume).
     *
     * <p>These tasks are blocking, IO-bound and long running, so the pool is deliberately
     * fixed and small ({@value #MIN_SCAN_THREADS}-{@value #MAX_SCAN_THREADS} threads depending
     * on the CPU count) rather than sized by CPU: more threads would only add random IO and
     * compete with the copy workers. Tasks that do not get a thread wait in the queue.
     *
     * @return the shared scan executor
     */
    public static ExecutorService scanExecutor() {
        return SCAN_POOL.get();
    }

    /**
     * Pool used to notify listeners from {@code EventBus}'s asynchronous dispatch paths.
     *
     * @return the shared event executor
     */
    public static ExecutorService eventExecutor() {
        return EVENT_POOL.get();
    }

    /**
     * Single-threaded pool for the recycler's periodic work-directory statistics.
     *
     * <p>Serialising this scan keeps it from competing with volume scans or with event
     * delivery, and the single thread makes the (occasional) overlap of two ticks
     * impossible by construction.
     *
     * @return the shared recycle executor
     */
    public static ExecutorService recycleExecutor() {
        ExecutorService local = recycleExecutor;
        if (local == null) {
            synchronized (ThreadPools.class) {
                local = recycleExecutor;
                if (local == null) {
                    local = Executors.newSingleThreadExecutor(namedDaemonThreadFactory("recycle"));
                    recycleExecutor = local;
                }
            }
        }
        return local;
    }

    /**
     * @return the number of threads the scan pool uses on this machine
     */
    public static int scanThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(MIN_SCAN_THREADS, Math.min(MAX_SCAN_THREADS, cpus));
    }

    // ========== Lazy pool holders ==========

    // The fixed-size pools are kept in tiny holder objects: the instance is created on the
    // first task and published through a volatile field, so a pool only exists (and only
    // spawns threads) once something actually uses it.

    private static final PoolHolder SCAN_POOL = new PoolHolder("scan", scanThreads());
    private static final PoolHolder EVENT_POOL = new PoolHolder("events", EVENT_THREADS);

    /** Lazily creates one fixed-size pool and publishes it once. */
    private static final class PoolHolder {
        private final String poolName;
        private final int threads;
        private volatile ExecutorService pool;

        PoolHolder(String poolName, int threads) {
            this.poolName = poolName;
            this.threads = threads;
        }

        ExecutorService get() {
            ExecutorService local = pool;
            if (local == null) {
                synchronized (this) {
                    local = pool;
                    if (local == null) {
                        local = Executors.newFixedThreadPool(threads, namedDaemonThreadFactory(poolName));
                        pool = local;
                    }
                }
            }
            return local;
        }
    }
}
