package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.RejectionAwarePolicy;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Evaluates system load based on queue length, copy speed, and thread activity.
 * Returns weighted score (0-100) and load level (LOW/MEDIUM/HIGH).
 */
public class LoadEvaluator {
    private static final Logger logger = Logger.getLogger(LoadEvaluator.class.getName());
    private static final long CACHE_DURATION_MS = 500; // 500ms cache

    private LoadScore cachedScore;
    private long lastEvaluateTime = 0;

    private final RejectionAwarePolicy rejectionPolicy;

    public LoadEvaluator() {
        this.rejectionPolicy = QueueManager.getRejectionPolicy();
    }

    public LoadScore evaluateLoad() {
        // Check cache
        long now = System.currentTimeMillis();
        if (cachedScore != null && (now - lastEvaluateTime) < CACHE_DURATION_MS) {
            return cachedScore;
        }

        // Cache miss or expired - compute new score
        try {
            // Collect metrics
            int queueLength = safeGetQueueSize();
            double copySpeed = safeGetCopySpeed();
            double threadRatio = safeGetThreadRatio();
            int recentRejections = safeGetRecentRejections();

            // Normalize to 0-100
            int queueScore = normalizeQueueLength(queueLength);
            int speedScore = normalizeSpeed(copySpeed);
            int threadScore = normalizeThreadRatio(threadRatio);
            int rejectionScore = normalizeRejections(recentRejections);

            // Get weights from config (as percentages 0-100)
            ConfigManager config = ConfigManager.getInstance();
            int queueWeightPercent = config.get(ConfigSchema.LOAD_QUEUE_WEIGHT_PERCENT);
            int speedWeightPercent = config.get(ConfigSchema.LOAD_SPEED_WEIGHT_PERCENT);
            int threadWeightPercent = config.get(ConfigSchema.LOAD_THREAD_WEIGHT_PERCENT);
            int rejectionWeightPercent = config.get(ConfigSchema.LOAD_REJECTION_WEIGHT_PERCENT);

            // Calculate weighted score
            int totalScore = (int) Math.round(
                queueScore * queueWeightPercent / 100.0 +
                speedScore * speedWeightPercent / 100.0 +
                threadScore * threadWeightPercent / 100.0 +
                rejectionScore * rejectionWeightPercent / 100.0
            );

            // Determine load level
            LoadLevel level = determineLoadLevel(totalScore);

            cachedScore = new LoadScore(totalScore, level);
            lastEvaluateTime = now;
            return cachedScore;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Load evaluation failed, using default value", e);
            return new LoadScore(50, LoadLevel.MEDIUM); // Conservative fallback
        }
    }

    private int safeGetQueueSize() {
        try {
            return QueueManager.getQueueSize();
        } catch (Exception e) {
            logger.log(Level.FINE, "Unable to get queue size", e);
            return 0; // Default: empty queue
        }
    }

    private double safeGetCopySpeed() {
        try {
            // Get current copy speed from SpeedProbeGroup (already in MB/s)
            double speedMBps = CopyTask.getSpeedProbeGroup().getTotalSpeed();

            // If no active copies (speed = 0), treat as high load
            // This helps the scheduler recognize when system is idle vs busy
            if (speedMBps <= 0) {
                return 0.0; // No copies = idle = low load score needed
            }

            return speedMBps;
        } catch (Exception e) {
            logger.log(Level.FINE, "Unable to get copy speed", e);
            return 10.0; // Default: good speed (conservative fallback)
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
