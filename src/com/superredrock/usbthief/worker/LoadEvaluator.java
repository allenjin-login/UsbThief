package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.RejectionAwarePolicy;
import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.ServiceManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Evaluates system load based on queue length, copy speed, and thread activity.
 * Returns weighted score (0-100) and load level (LOW/MEDIUM/HIGH).
 *
 * <p>Runs as an independent Service with 500ms tick interval, caching results
 * for synchronous access via evaluateLoad().
 */
public class LoadEvaluator extends Service {
    private static final Logger logger = Logger.getLogger(LoadEvaluator.class.getName());

    private volatile LoadScore cachedScore = new LoadScore(50, LoadLevel.MEDIUM);

    private final RejectionAwarePolicy rejectionPolicy;

    public LoadEvaluator() {
        this.rejectionPolicy = QueueManager.getRejectionPolicy();
    }

    public static LoadEvaluator getInstance(){
        return ServiceManager.getInstance().findService(LoadEvaluator.class).orElse(null);
    }

    @Override
    protected void tick() {
        try {
            // Collect metrics
            int queueLength = safeGetQueueSize();
            double threadRatio = safeGetThreadRatio();
            int recentRejections = safeGetRecentRejections();

            // Normalize to 0-100
            int queueScore = normalizeQueueLength(queueLength);
            int threadScore = normalizeThreadRatio(threadRatio);
            int rejectionScore = normalizeRejections(recentRejections);

            // Get weights from config (as percentages 0-100)
            ConfigManager config = ConfigManager.getInstance();
            int queueWeightPercent = config.get(ConfigSchema.LOAD_QUEUE_WEIGHT_PERCENT);
            int threadWeightPercent = config.get(ConfigSchema.LOAD_THREAD_WEIGHT_PERCENT);
            int rejectionWeightPercent = config.get(ConfigSchema.LOAD_REJECTION_WEIGHT_PERCENT);

            // Calculate weighted score
            int totalScore = (int) Math.round(
                queueScore * queueWeightPercent / 100.0 +
                threadScore * threadWeightPercent / 100.0 +
                rejectionScore * rejectionWeightPercent / 100.0
            );

            // Determine load level
            LoadLevel level = determineLoadLevel(totalScore);

            cachedScore = new LoadScore(totalScore, level);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Load evaluation failed, keeping cached value", e);
        }
    }

    @Override
    protected long getTickIntervalMs() {
        return 500;
    }

    @Override
    public String getServiceName() {
        return "LoadEvaluator";
    }

    @Override
    public String getDescription() {
        return "Evaluates system load based on queue depth, copy speed, thread activity, and rejection rate";
    }

    /**
     * Returns the cached load score from the most recent tick evaluation.
     * This method is non-blocking and returns immediately with cached data.
     *
     * @return LoadScore containing the weighted score (0-100) and load level
     */
    public LoadScore evaluateLoad() {
        LoadScore score = cachedScore;;
        return score;
    }

    private int safeGetQueueSize() {
        try {
            return QueueManager.getQueueSize();
        } catch (Exception e) {
            logger.log(Level.FINE, "Unable to get queue size", e);
            return 0; // Default: empty queue
        }
    }

    private double safeGetThreadRatio() {
        try {
            return QueueManager.getActiveRatio();
        } catch (Exception e) {
            logger.log(Level.FINE, "Unable to get thread activity", e);
            return 0.5; // Default: 50% active
        }
    }

    private int safeGetRecentRejections() {
        try {
            return rejectionPolicy.getRecentRejections();
        } catch (Exception e) {
            logger.log(Level.FINE, "Unable to get rejection count", e);
            return 0; // Default: no rejections
        }
    }

    private int normalizeQueueLength(int queueLength) {
        // 0 tasks = 0 score, 100+ tasks = 100 score
        return Math.min(100, queueLength);
    }

    private int normalizeSpeed(double speedMBps) {
        // Speed < 1 MB/s = 100 score (bad), Speed >= 10 MB/s = 0 score (good)
        if (speedMBps < 1.0) return 100;
        if (speedMBps >= 10.0) return 0;
        return (int) Math.round(100 - (speedMBps - 1.0) * 100 / 9.0);
    }

    private int normalizeThreadRatio(double ratio) {
        // 0.0 active = 0 score, 1.0 active = 100 score
        return (int) Math.round(ratio * 100);
    }

    private int normalizeRejections(int rejectionCount) {
        // 0 rejections = 0 score, 10+ rejections = 100 score
        return Math.min(100, rejectionCount * 10);
    }

    private LoadLevel determineLoadLevel(int score) {
        ConfigManager config = ConfigManager.getInstance();
        int highThreshold = config.get(ConfigSchema.LOAD_HIGH_THRESHOLD);
        int lowThreshold = config.get(ConfigSchema.LOAD_LOW_THRESHOLD);

        if (score > highThreshold) return LoadLevel.HIGH;
        if (score > lowThreshold) return LoadLevel.MEDIUM;
        return LoadLevel.LOW;
    }
}
