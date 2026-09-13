package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Service;

import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import java.util.Iterator;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaskScheduler extends Service {
    private static final Logger logger = LogManager.getLogger(TaskScheduler.class);
    
    private static volatile TaskScheduler INSTANCE;


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
        ServiceState state = getServiceState();
        if (state == ServiceState.STOPPED || state == ServiceState.FAILED) {
            return null;
        }

        int priority = priorityRule.calculatePriority(task);
        PriorityTask<Callable<R>, R> priorityTask = new PriorityTask<>(task, priority);

        priorityQueue.offer(priorityTask);

        return priorityTask;
    }

    @SuppressWarnings("unchecked")
    private boolean submitToPool(PriorityTask<?, ?> task) {
        try {
            Future<?> future = pool.submit((Callable<Object>) task.unwrap());
            task.setFuture(future);
            trackFuture(task.unwrap(), future);
            return true;
        } catch (RejectedExecutionException e) {
            // The pool filled up between the capacity check and this submission: keep the task
            // queued and try again on the next tick.
            priorityQueue.offer(task);
            logger.debug("Pool saturated, task re-queued (queue depth: {})", priorityQueue.size());
            return false;
        } catch (Exception e) {
            logger.error("Failed to submit task, dropping", e);
            return false;
        }
    }

    private void trackFuture(Object delegate, Future<?> future) {
        String serial = null;
        if (delegate instanceof CopyTask ct) serial = ct.getDeviceSerial();
        else if (delegate instanceof VerifyTask vt) serial = vt.getDeviceSerial();
        if (serial != null && !serial.isEmpty()) {
            futuresBySerial.computeIfAbsent(serial, _ -> new CopyOnWriteArrayList<>()).add(future);
        }
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
            String s = null;
            if (delegate instanceof CopyTask ct) s = ct.getDeviceSerial();
            else if (delegate instanceof VerifyTask vt) s = vt.getDeviceSerial();
            if (serial.equals(s)) {
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

    @Override
    protected void cleanup() {
        logger.info("Cleaning up TaskScheduler...");

        int drained = 0;
        PriorityTask<?, ?> task;
        while ((task = priorityQueue.poll()) != null) {
            try {
                pool.submit(task.unwrap());
                drained++;
            } catch (Exception e) {
                logger.warn("Failed to submit task during cleanup: {}", e);
            }
        }

        if (drained > 0) {
            logger.info("Drained {} tasks during cleanup", drained);
        }
    }

    @Override
    public void close() {
        super.close();
        pool.shutdownNow();
    }
}
