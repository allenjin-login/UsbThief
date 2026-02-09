package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Task scheduler with priority queue and adaptive load-based dispatching.
 * Uses graceful degradation: falls back to FIFO submission on errors.
 */
public class TaskScheduler {
    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());

    private static TaskScheduler INSTANCE;

    private final PriorityQueue<PriorityCopyTask> priorityQueue;
    private final LoadEvaluator loadEvaluator;
    private final ExecutorService executor;
    private final PriorityRule priorityRule;
    private volatile boolean isShutdown = false;

    private TaskScheduler() {
        this.priorityQueue = new PriorityQueue<>();
        this.loadEvaluator = new LoadEvaluator();
        this.executor = QueueManager.getCopyExecutor();
        this.priorityRule = new PriorityRule();
    }

    public static synchronized TaskScheduler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TaskScheduler();
        }
        return INSTANCE;
    }

    public PriorityRule getPriorityRule() {
        return priorityRule;
    }

    /**
     * Submit task to scheduler with adaptive dispatch based on current load.
     */
    public void submit(PriorityCopyTask task) {
        if (isShutdown) {
            logger.warning("TaskScheduler已关闭，忽略任务提交");
            return;
        }

        try {
            LoadScore load = loadEvaluator.evaluateLoad();
            dispatchByLoadLevel(task, load.level());

        } catch (Exception e) {
            logger.log(Level.WARNING, "自适应调度失败，降级为FIFO", e);
            // Fallback: direct submission (bypass priority queue)
            try {
                executor.submit(task.unwrap());
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "FIFO降级也失败，任务被丢弃", ex);
            }
        }
    }

    private void dispatchByLoadLevel(PriorityCopyTask task, LoadLevel level) {
        ConfigManager config = ConfigManager.getInstance();

        switch (level) {
            case LOW -> {
                // Immediate mode: high priority goes straight to pool
                int highPriorityThreshold = config.get(ConfigSchema.SCHEDULER_HIGH_PRIORITY_THRESHOLD);
                if (task.getPriority() >= highPriorityThreshold) {
                    executor.submit(task.unwrap());
                } else {
                    priorityQueue.offer(task);
                    drainQueue();
                }
            }

            case MEDIUM -> {
                // Mini-batch mode
                priorityQueue.offer(task);
                int mediumBatchSize = config.get(ConfigSchema.SCHEDULER_MEDIUM_BATCH);
                if (priorityQueue.size() >= mediumBatchSize) {
                    submitBatch(mediumBatchSize);
                }
            }

            case HIGH -> {
                // Large-batch mode
                priorityQueue.offer(task);
                int highBatchSize = config.get(ConfigSchema.SCHEDULER_HIGH_BATCH);
                if (priorityQueue.size() >= highBatchSize) {
                    submitBatch(highBatchSize);
                }
            }
        }
    }

    private void submitBatch(int batchSize) {
        for (int i = 0; i < batchSize && !priorityQueue.isEmpty(); i++) {
            PriorityCopyTask task = priorityQueue.poll();
            if (task != null) {
                executor.submit(task.unwrap());
            }
        }
    }

    private void drainQueue() {
        // Drain all queued tasks on low load
        while (!priorityQueue.isEmpty()) {
            PriorityCopyTask task = priorityQueue.poll();
            if (task != null) {
                executor.submit(task.unwrap());
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

    public void shutdown() {
        isShutdown = true;
        drainQueue(); // Submit remaining tasks
        priorityQueue.clear();
        logger.info("TaskScheduler已关闭");
    }
}
