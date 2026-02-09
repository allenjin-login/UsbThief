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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Task scheduler with priority queue and adaptive load-based dispatching.
 * Extends Service for lifecycle management and periodic task submission.
 * Uses graceful degradation: falls back to FIFO submission on errors.
 */
public class TaskScheduler extends Service {
    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());

    private final PriorityQueue<PriorityCopyTask> priorityQueue;
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
    protected ScheduledFuture<?> scheduleTask(ScheduledThreadPoolExecutor scheduler) {
        long initialDelay = ConfigManager.getInstance().get(ConfigSchema.SCHEDULER_INITIAL_DELAY_MS);
        long interval = ConfigManager.getInstance().get(ConfigSchema.SCHEDULER_TICK_INTERVAL_MS);

        logger.info("Scheduling TaskScheduler with " + interval + "ms interval");

        return scheduler.scheduleAtFixedRate(
            this,
            initialDelay,
            interval,
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public String getName() {
        return "TaskScheduler";
    }

    @Override
    public String getDescription() {
        return "Adaptive priority scheduler with load-based task accumulation";
    }

    @Override
    public void run() {
        if (getState() != ServiceState.RUNNING) {
            return;
        }

        LoadScore score = loadEvaluator.evaluateLoad();

        switch (score.level()) {
            case HIGH -> handleHighLoad();
            case MEDIUM -> dispatchBatch(ConfigManager.getInstance().get(ConfigSchema.SCHEDULER_MEDIUM_BATCH_SIZE));
            case LOW -> dispatchBatch(ConfigManager.getInstance().get(ConfigSchema.SCHEDULER_LOW_BATCH_SIZE));
        }
    }

    /**
     * Submit task to scheduler. Tasks are queued and periodically dispatched.
     */
    public void submit(PriorityCopyTask task) {
        ServiceState state = getState();
        if (state == ServiceState.STOPPED || state == ServiceState.FAILED) {
            throw new IllegalStateException("TaskScheduler is not running, state: " + state);
        }

        synchronized (priorityQueue) {
            int maxQueue = ConfigManager.getInstance().get(ConfigSchema.SCHEDULER_ACCUMULATION_MAX_QUEUE);

            if (priorityQueue.size() >= maxQueue) {
                logger.warning("Task queue full (" + maxQueue + "), rejecting task");
                throw new IllegalStateException("Task queue is full");
            }

            priorityQueue.offer(task);
        }
    }

    private void handleHighLoad() {
        if (!accumulating) {
            accumulating = true;
            int queueDepth = getQueueDepth();
            logger.warning("High load detected - entering accumulation mode, queue depth: " + queueDepth);
        }

        // Don't submit tasks while accumulating - let them queue up
    }

    private void dispatchBatch(int batchSize) {
        if (accumulating) {
            accumulating = false;
            int queueDepth = getQueueDepth();
            logger.info("Load decreased - resuming submissions, accumulated tasks: " + queueDepth);
        }

        List<PriorityCopyTask> batch = new ArrayList<>(batchSize);

        synchronized (priorityQueue) {
            for (int i = 0; i < batchSize && !priorityQueue.isEmpty(); i++) {
                batch.add(priorityQueue.poll());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        logger.fine("Dispatching batch of " + batch.size() + " tasks");

        for (PriorityCopyTask task : batch) {
            try {
                executor.submit(task.unwrap());
            } catch (RejectedExecutionException e) {
                logger.warning("Task rejected during dispatch, re-queuing");
                synchronized (priorityQueue) {
                    priorityQueue.offer(task);
                }
                break; // Stop dispatching on rejection
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to submit task, dropping", e);
            }
        }
    }

    /**
     * Get current queue depth (number of pending tasks).
     * Thread-safe: synchronized on priorityQueue access.
     */
    public synchronized int getQueueDepth() {
        return priorityQueue.size();
    }

    @Override
    protected void cleanup() {
        logger.info("Cleaning up TaskScheduler...");

        // Try to drain remaining tasks
        int drained = 0;
        synchronized (priorityQueue) {
            while (!priorityQueue.isEmpty()) {
                PriorityCopyTask task = priorityQueue.poll();
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
