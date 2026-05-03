package com.superredrock.usbthief.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Calculates task priority based on file attributes (extension, size, path).
 * Priority range: 0-100, higher = more important.
 */
public class PriorityRule {
    private static final Logger logger = LogManager.getLogger(PriorityRule.class);

    private static final Map<String, Integer> EXTENSION_PRIORITIES = Map.of(
        "pdf", 10,
        "docx", 9,
        "xlsx", 9,
        "pptx", 8,
        "txt", 7,
        "jpg", 6,
        "png", 6,
        "mp4", 5,
        "mp3", 5
    );

    private static final int DEFAULT_PRIORITY = 5;
    private static final long SMALL_FILE_THRESHOLD = 1024 * 1024; // 1MB
    private static final long LARGE_FILE_THRESHOLD = 10 * 1024 * 1024; // 10MB
    private static final int SMALL_FILE_BONUS = -2;
    private static final int LARGE_FILE_PENALTY = 4;

    public int calculatePriority(Path file) {
        try {
            if (Files.isDirectory(file)){
                return 11;
            }

            String fileName = file.getFileName().toString();
            String extension = getExtension(fileName).toLowerCase();

            // Base priority from extension
            int basePriority = EXTENSION_PRIORITIES.getOrDefault(extension, DEFAULT_PRIORITY);

            // Size adjustment
            int sizeAdjustment = calculateSizeAdjustment(file);

            // Clamp to valid range
            return Math.max(0, Math.min(100, basePriority + sizeAdjustment));

        } catch (Exception e) {
            logger.warn("优先级计算失败，使用默认值: {}", file, e);
            return DEFAULT_PRIORITY;
        }
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    private int calculateSizeAdjustment(Path file) {
        try {
            if (Files.isDirectory(file)){
                return 0;
            }
            long size = Files.size(file);

            if (size < SMALL_FILE_THRESHOLD) {
                return SMALL_FILE_BONUS;
            } else if (size >= LARGE_FILE_THRESHOLD) {
                return LARGE_FILE_PENALTY;
            }
            return 0; // Medium files, no adjustment

        } catch (IOException e) {
            logger.debug("无法获取文件大小: {}", file, e);
            return 0; // If we can't get size, no adjustment
        }
    }

    public int calculatePriority(Callable<?> task) {
        if (task instanceof CopyTask) {
            CopyTask copyTask = (CopyTask) task;
            return calculatePriority(copyTask.getProcessingPath());
        } else if (task instanceof VerifyTask) {
            VerifyTask verifyTask = (VerifyTask) task;
            return calculatePriority(verifyTask.getProcessingPath());
        } else {
            return DEFAULT_PRIORITY;
        }
    }
}
