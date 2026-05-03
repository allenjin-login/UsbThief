package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Service;

import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaskScheduler extends Service {
    private static final Logger logger = LogManager.getLogger(TaskScheduler.class);
    
    private static volatile TaskScheduler INSTANCE;


    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            ConfigManager.getInstance().get(ConfigSchema.CORE_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.MAX_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.KEEP_ALIVE_TIME_SECONDS),
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(ConfigManager.getInstance().get(ConfigSchema.TASK_QUEUE_CAPACITY)));

    private final PriorityBlockingQueue<PriorityTask<?, ?>> priorityQueue;
    private final PriorityRule priorityRule;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Future<?>>> futuresBySerial = new ConcurrentHashMap<>();
    private volatile int dispatchBudget = Integer.MAX_VALUE;

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

        List<PriorityTask<?, ?>> batch = new ArrayList<>();
        priorityQueue.drainTo(batch, dispatchBudget);

        if (!batch.isEmpty()) {
            logger.debug("Dispatching {} tasks (budget: {})", batch.size(),
                    dispatchBudget == Integer.MAX_VALUE ? "∞" : dispatchBudget);
            dispatchTask(batch);
        }

        pruneCompletedFutures();
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
        return 500;
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
        return "Adaptive priority scheduler with load-based task accumulation";
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
    private void dispatchTask(List<PriorityTask<?, ?>> allTasks) {
        int dispatched = 0;
        for (int i = 0; i < allTasks.size(); i++) {
            PriorityTask<?, ?> task = allTasks.get(i);
            try {
                Future<?> future = pool.submit((Callable<Object>) task.unwrap());
                task.setFuture(future);
                trackFuture(task.unwrap(), future);
                dispatched++;
            } catch (RejectedExecutionException e) {
                int requeued = allTasks.size() - i;
                priorityQueue.offer(task);
                for (int j = i + 1; j < allTasks.size(); j++) {
                    priorityQueue.offer(allTasks.get(j));
                }
                // Negative feedback: halve budget based on successful dispatch count
                dispatchBudget = Math.max(1, dispatched / 2 + 1);
                logger.debug("Pool saturated, {} tasks re-queued (budget → {})", requeued, dispatchBudget);
                return;
            } catch (Exception e) {
                logger.error("Failed to submit task, dropping", e);
            }
        }

        // Positive feedback: gradually double budget on full success
        if (dispatchBudget < Integer.MAX_VALUE) {
            dispatchBudget = (int) Math.min((long) dispatchBudget * 2, Integer.MAX_VALUE);
        }
    }

    private void trackFuture(Object delegate, Future<?> future) {
        String serial = null;
        if (delegate instanceof CopyTask ct) serial = ct.getDeviceSerial();
        else if (delegate instanceof VerifyTask vt) serial = vt.getDeviceSerial();
        if (serial != null && !serial.isEmpty()) {
            futuresBySerial.computeIfAbsent(serial, ignored -> new CopyOnWriteArrayList<>()).add(future);
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
