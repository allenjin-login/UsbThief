package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Service;

import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaskScheduler extends Service {
    private static final Logger logger = LogManager.getLogger(TaskScheduler.class);
    
    private static volatile TaskScheduler INSTANCE;

    /**
     * How long {@link #stopService()} waits for tasks that were already handed to the pool
     * before it gives up and lets {@link #close()} force the pool down.
     */
    static final long STOP_IN_FLIGHT_TIMEOUT_MILLIS = 5000;

    /** Graceful pool termination budget used by {@link #close()}. */
    static final long POOL_TERMINATION_TIMEOUT_MILLIS = 5000;

    /** Extra grace period after {@code shutdownNow()} so workers can react to interruption. */
    static final long POOL_FORCE_TERMINATION_TIMEOUT_MILLIS = 2000;


    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            ConfigManager.getInstance().get(ThreadPoolConfig.CORE_POOL_SIZE),
            ConfigManager.getInstance().get(ThreadPoolConfig.MAX_POOL_SIZE),
            ConfigManager.getInstance().get(ThreadPoolConfig.KEEP_ALIVE_TIME_SECONDS),
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(ConfigManager.getInstance().get(ThreadPoolConfig.TASK_QUEUE_CAPACITY)));

    private final PriorityBlockingQueue<PriorityTask<?, ?>> priorityQueue;
    private final PriorityRule priorityRule;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Future<?>>> futuresBySerial = new ConcurrentHashMap<>();

    /**
     * Flips to {@code false} as soon as a stop is requested and never flips back.
     *
     * <p>It closes the race between {@link #submit(java.util.concurrent.Callable)} and the
     * queue drain in {@link #cleanup()}: a submission that sneaks in after the drain can
     * detect the flag and remove itself again instead of leaking in the queue forever.</p>
     */
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);

    /** Number of queued tasks that were dropped by shutdown and never executed. */
    private final AtomicLong discardedTaskCount = new AtomicLong();

    /**
     * Scheduler tick period in milliseconds.
     *
     * <p>Kept short so that a saturated pool resumes dispatching as soon as queue slots free up.
     * Back-pressure comes from the bounded pool queue (see {@link #availableDispatchCapacity()}),
     * not from the tick interval.</p>
     */
    private static final long DISPATCH_TICK_MILLIS = 20;

    private TaskScheduler() {
        this.priorityQueue = new PriorityBlockingQueue<>();
        this.priorityRule = new PriorityRule();
    }
    
    public static TaskScheduler getInstance() {
        if (INSTANCE == null) {
            synchronized (TaskScheduler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TaskScheduler();
                }
            }
        }
        return INSTANCE;
    }

    public PriorityRule getPriorityRule() {
        return priorityRule;
    }

public ThreadPoolExecutor getPool() {
        return pool;
    }

    /**
     * Returns the ratio of active threads to pool size.
     *
     * @return ratio between 0.0 and 1.0, or 0.0 if pool size is 0
     */
    public double getActiveRatio() {
        int poolSize = pool.getPoolSize();
        if (poolSize == 0) {
            return 0.0;
        }
        return (double) pool.getActiveCount() / poolSize;
    }

    @Override
    protected void tick() {
        if (getServiceState() != ServiceState.RUNNING) {
            return;
        }

        int dispatched = dispatchAvailable();
        if (dispatched > 0) {
            logger.debug("Dispatched {} tasks (queue depth: {})", dispatched, priorityQueue.size());
        }

        pruneCompletedFutures();
    }

    /**
     * Dispatches tasks while the pool reports free capacity.
     *
     * <p>Tasks stay in the priority queue whenever the pool is saturated; nothing is dropped and
     * no dispatch budget is shrunk, so throughput follows the pool's real capacity instead of
     * collapsing to a couple of tasks per tick.</p>
     *
     * @return number of tasks handed to the pool in this call
     */
    private int dispatchAvailable() {
        int capacity = availableDispatchCapacity();
        int dispatched = 0;

        for (int i = 0; i < capacity; i++) {
            if (!acceptingTasks.get()) {
                // A stop was requested while we were dispatching: keep the remaining tasks in the
                // queue so that cleanup() can discard and record them deterministically.
                break;
            }
            PriorityTask<?, ?> task = priorityQueue.poll();
            if (task == null) {
                break;
            }
            if (!submitToPool(task)) {
                // The pool filled up mid-batch (capacity check raced with other submitters);
                // keep the remaining tasks queued for the next tick.
                break;
            }
            dispatched++;
        }

        return dispatched;
    }

    /**
     * Number of tasks the pool can accept right now without rejecting.
     *
     * <p>Free queue slots plus idle worker threads that pick a task up immediately.</p>
     */
    int availableDispatchCapacity() {
        int idleWorkers = Math.max(0, pool.getPoolSize() - pool.getActiveCount());
        return pool.getQueue().remainingCapacity() + idleWorkers;
    }

    private void pruneCompletedFutures() {
        for (var entry : futuresBySerial.entrySet()) {
            entry.getValue().removeIf(Future::isDone);
            if (entry.getValue().isEmpty()) {
                futuresBySerial.remove(entry.getKey());
            }
        }
    }

    @Override
    protected long getTickInterval() {
        return DISPATCH_TICK_MILLIS;
    }

    @Override
    protected TimeUnit getTickUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public String getServiceName() {
        return "TaskScheduler";
    }

    @Override
    public String getDescription() {
        return "Priority scheduler with pool-capacity back-pressure";
    }

    public <R> PriorityTask<Callable<R>, R> submit(Callable<R> task) {
        if (task == null || !acceptingTasks.get()) {
            return null;
        }

        ServiceState state = getServiceState();
        if (state == ServiceState.STOPPED || state == ServiceState.FAILED || state == ServiceState.STOPPING) {
            return null;
        }

        int priority = priorityRule.calculatePriority(task);
        PriorityTask<Callable<R>, R> priorityTask = new PriorityTask<>(task, priority);

        priorityQueue.offer(priorityTask);

        if (!acceptingTasks.get()) {
            // Shutdown raced with this submission: drop it instead of leaking it in the queue.
            if (priorityQueue.remove(priorityTask)) {
                long total = discardedTaskCount.incrementAndGet();
                logger.debug("Discarded task submitted during shutdown (discarded so far: {})", total);
            }
            return null;
        }

        return priorityTask;
    }

    @SuppressWarnings("unchecked")
    private boolean submitToPool(PriorityTask<?, ?> task) {
        Callable<Object> delegate = (Callable<Object>) task.unwrap();
        FutureTask<Object> future = new FutureTask<>(delegate);
        task.setFuture(future);
        // Register the future before the pool can run it: a worker may start the task immediately
        // after execute(), and cancelBySerial(...) must not miss a task that is already running.
        // A rejected submission is unregistered again below.
        trackFuture(delegate, future);
        try {
            pool.execute(future);
            return true;
        } catch (RejectedExecutionException e) {
            // The pool filled up between the capacity check and this submission: keep the task
            // queued and try again on the next tick.
            untrackFuture(delegate, future);
            priorityQueue.offer(task);
            logger.debug("Pool saturated, task re-queued (queue depth: {})", priorityQueue.size());
            return false;
        } catch (Exception e) {
            untrackFuture(delegate, future);
            logger.error("Failed to submit task, dropping", e);
            return false;
        }
    }

    private void trackFuture(Object delegate, Future<?> future) {
        String serial = deviceSerialOf(delegate);
        if (serial != null) {
            futuresBySerial.computeIfAbsent(serial, key -> new CopyOnWriteArrayList<>()).add(future);
        }
    }

    /**
     * Undoes {@link #trackFuture(Object, Future)} for a submission that never reached the pool.
     */
    private void untrackFuture(Object delegate, Future<?> future) {
        String serial = deviceSerialOf(delegate);
        if (serial == null) {
            return;
        }
        CopyOnWriteArrayList<Future<?>> futures = futuresBySerial.get(serial);
        if (futures != null) {
            futures.remove(future);
            if (futures.isEmpty()) {
                futuresBySerial.remove(serial, futures);
            }
        }
    }

    /**
     * Extracts the owning device serial from any task type that opted into {@link DeviceBoundTask}.
     *
     * @return the serial, or {@code null} when the task is not device-bound or has no serial
     */
    private static String deviceSerialOf(Object delegate) {
        if (delegate instanceof DeviceBoundTask) {
            String serial = ((DeviceBoundTask) delegate).getDeviceSerial();
            if (serial != null && !serial.isEmpty()) {
                return serial;
            }
        }
        return null;
    }

    public int cancelBySerial(String serial) {
        int cancelled = 0;

        CopyOnWriteArrayList<Future<?>> futures = futuresBySerial.remove(serial);
        if (futures != null) {
            for (Future<?> f : futures) {
                if (!f.isDone()) {
                    f.cancel(true);
                    cancelled++;
                }
            }
        }

        Iterator<PriorityTask<?, ?>> it = priorityQueue.iterator();
        while (it.hasNext()) {
            Object delegate = it.next().unwrap();
            if (serial.equals(deviceSerialOf(delegate))) {
                it.remove();
                cancelled++;
            }
        }

        if (cancelled > 0) {
            logger.info("Cancelled {} tasks for serial {}", cancelled, serial);
        }
        return cancelled;
    }

    public int getQueueDepth() {
        return priorityQueue.size();
    }

    /**
     * @return how many queued tasks have been dropped by a shutdown and never executed
     */
    public long getDiscardedTaskCount() {
        return discardedTaskCount.get();
    }

    /**
     * Reject new work immediately, then let {@link Service#stopService()} stop the dispatch loop.
     *
     * <p>Both shutdown entry points end up here: {@code Main} stops services through
     * {@link com.superredrock.usbthief.core.ServiceRegistry#shutdownAll()}, which calls
     * {@link #close()}, and {@link #close()} delegates to this method first. Calling this method
     * twice is safe.</p>
     */
    @Override
    public void stopService() {
        if (acceptingTasks.compareAndSet(true, false)) {
            logger.info("{} refusing new tasks", getServiceName());
        }
        super.stopService();
    }

    /**
     * Deterministic stop semantics: no new tasks are accepted, tasks already handed to the pool
     * get a bounded grace period, and tasks still queued in the scheduler are dropped explicitly
     * (logged and counted) instead of being pumped into the pool on the way out.
     */
    @Override
    protected void cleanup() {
        logger.info("Cleaning up TaskScheduler...");

        int discarded = discardQueuedTasks();

        if (!awaitInFlightTasks(STOP_IN_FLIGHT_TIMEOUT_MILLIS)) {
            logger.warn("{} still has tasks in flight after {} ms, continuing shutdown",
                    getServiceName(), STOP_IN_FLIGHT_TIMEOUT_MILLIS);
        }

        futuresBySerial.clear();

        if (discarded > 0) {
            logger.info("Discarded {} queued task(s) during shutdown (total discarded: {})",
                    discarded, discardedTaskCount.get());
        }
    }

    /**
     * Drops every task that is still waiting in the scheduler queue and records it.
     *
     * @return the number of tasks dropped by this call
     */
    private int discardQueuedTasks() {
        int discarded = 0;
        PriorityTask<?, ?> task;
        while ((task = priorityQueue.poll()) != null) {
            long total = discardedTaskCount.incrementAndGet();
            String serial = deviceSerialOf(task.unwrap());
            if (serial != null) {
                logger.debug("Discarding queued {} for device {} during shutdown (discarded so far: {})",
                        task.unwrap().getClass().getSimpleName(), serial, total);
            } else {
                logger.debug("Discarding queued {} during shutdown (discarded so far: {})",
                        task.unwrap().getClass().getSimpleName(), total);
            }
            discarded++;
        }
        return discarded;
    }

    /**
     * Waits for the pool to become idle (no active worker and nothing queued in the pool).
     *
     * <p>Only tasks that were already accepted by the pool are waited for; the scheduler queue is
     * discarded instead of being handed over, which is the semantic difference to the old
     * {@code cleanup()} implementation that pumped everything into the pool right before
     * {@code close()} interrupted it again.</p>
     *
     * @return {@code true} when the pool drained within the timeout
     */
    private boolean awaitInFlightTasks(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty()) {
                return true;
            }
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis <= 0) {
                return false;
            }
            try {
                Thread.sleep(Math.min(50, remainingMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * {@code close()} owns the final pool shutdown: whatever {@link #stopService()} could not
     * finish within its grace period is forced down here.
     *
     * <p>Safe to call more than once; a second call is a no-op because the executor is already
     * terminated.</p>
     */
    @Override
    public void close() {
        super.close();

        // stopService() short-circuits when the service never ran, so make sure queued work is
        // still accounted for exactly once.
        int leftover = discardQueuedTasks();
        if (leftover > 0) {
            logger.info("Discarded {} queued task(s) while closing (total discarded: {})",
                    leftover, discardedTaskCount.get());
        }

        if (pool.isTerminated()) {
            return;
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(POOL_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                int dropped = pool.shutdownNow().size();
                logger.warn("{} pool did not terminate within {} ms, forcing shutdown ({} task(s) dropped)",
                        getServiceName(), POOL_TERMINATION_TIMEOUT_MILLIS, dropped);
                pool.awaitTermination(POOL_FORCE_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    /**
     * Applies a new core pool size at runtime.
     *
     * <p>Exposed so configuration changes can be applied without restarting the application
     * (see {@link #applyPoolConfig()}).</p>
     *
     * @param corePoolSize new core size, clamped to at least 1
     */
    public void setCorePoolSize(int corePoolSize) {
        int size = Math.max(1, corePoolSize);
        pool.setCorePoolSize(size);
        logger.info("{} core pool size set to {}", getServiceName(), size);
    }

    /**
     * Applies a new maximum pool size at runtime.
     *
     * @param maximumPoolSize new maximum size, clamped to at least 1
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        int size = Math.max(1, maximumPoolSize);
        pool.setMaximumPoolSize(size);
        logger.info("{} maximum pool size set to {}", getServiceName(), size);
    }

    /**
     * Re-reads {@link ThreadPoolConfig} and applies the pool sizes to the live pool.
     *
     * <p>Called by configuration-change handling so that {@code corePoolSize}/{@code maxPoolSize}
     * no longer require an application restart.</p>
     */
    public void applyPoolConfig() {
        setMaximumPoolSize(ConfigManager.getInstance().get(ThreadPoolConfig.MAX_POOL_SIZE));
        setCorePoolSize(ConfigManager.getInstance().get(ThreadPoolConfig.CORE_POOL_SIZE));
    }
}
