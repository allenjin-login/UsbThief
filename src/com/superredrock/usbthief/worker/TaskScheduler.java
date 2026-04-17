package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Service;

import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskScheduler extends Service {
    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());
    
    private static volatile TaskScheduler INSTANCE;


    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            ConfigManager.getInstance().get(ConfigSchema.CORE_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.MAX_POOL_SIZE),
            ConfigManager.getInstance().get(ConfigSchema.KEEP_ALIVE_TIME_SECONDS),
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(ConfigManager.getInstance().get(ConfigSchema.TASK_QUEUE_CAPACITY)),
            QueueManager.getRejectionPolicy()
    );

    private final PriorityQueue<PriorityTask<?, ?>> priorityQueue;
    private final PriorityRule priorityRule;
    private volatile boolean accumulating = false;

    private TaskScheduler() {
        this.priorityQueue = new PriorityQueue<>();
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

        if (priorityQueue.size() > 1000) {
            dispatchBatch(500);
        } else {
            dispatchAll();
        }
    }

    @Override
    protected long getTickIntervalMs() {
        return 500;
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

        synchronized (priorityQueue) {
            priorityQueue.offer(priorityTask);
        }

        return priorityTask;
    }

    private void dispatchBatch(int batchSize) {
        if (accumulating) {
            accumulating = false;
            int queueDepth = getQueueDepth();
            logger.info("Load decreased - resuming submissions, accumulated tasks: " + queueDepth);
        }

        List<PriorityTask<?, ?>> batch = new ArrayList<>(batchSize);

        synchronized (priorityQueue) {
            for (int i = 0; i < batchSize && !priorityQueue.isEmpty(); i++) {
                batch.add(priorityQueue.poll());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        logger.fine("Dispatching batch of " + batch.size() + " tasks");

        dispatchTask(batch);
    }

    private void dispatchAll() {
        if (accumulating) {
            accumulating = false;
            int queueDepth = getQueueDepth();
            logger.info("Load decreased - resuming submissions, accumulated tasks: " + queueDepth);
        }

        List<PriorityTask<?, ?>> allTasks = new ArrayList<>();

        synchronized (priorityQueue) {
            while (!priorityQueue.isEmpty()) {
                allTasks.add(priorityQueue.poll());
            }
        }

        if (allTasks.isEmpty()) {
            return;
        }

        logger.fine("Dispatching all " + allTasks.size() + " tasks (LOW load mode)");

        dispatchTask(allTasks);
    }

    @SuppressWarnings("unchecked")
    private void dispatchTask(List<PriorityTask<?, ?>> allTasks) {
        for (PriorityTask<?, ?> task : allTasks) {
            try {
                Future<?> future = pool.submit((Callable<Object>) task.unwrap());
                task.setFuture(future);
            } catch (RejectedExecutionException e) {
                logger.warning("Task rejected during dispatch, re-queuing");
                synchronized (priorityQueue) {
                    priorityQueue.offer(task);
                }
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to submit task, dropping", e);
            }
        }
    }

    public synchronized int getQueueDepth() {
        return priorityQueue.size();
    }

    @Override
    protected void cleanup() {
        logger.info("Cleaning up TaskScheduler...");

        int drained = 0;
        synchronized (priorityQueue) {
            while (!priorityQueue.isEmpty()) {
                PriorityTask<?, ?> task = priorityQueue.poll();
                if (task != null) {
                    try {
                        pool.submit(task.unwrap());
                        drained++;
                    } catch (Exception e) {
                        logger.warning("Failed to submit task during cleanup: " + e.getMessage());
                    }
                }
            }
        }

        if (drained > 0) {
            logger.info("Drained " + drained + " tasks during cleanup");
        }
    }

    @Override
    public void close() {
        super.close();
        pool.shutdownNow();
    }
}
