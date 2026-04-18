package com.superredrock.usbthief.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Rejection handler that tracks rejection rate for load evaluation.
 * Falls back to CallerRunsPolicy for backpressure.
 */
public class RejectionAwarePolicy implements RejectedExecutionHandler {
    private static final Logger logger = LogManager.getLogger(RejectionAwarePolicy.class);

    private final AtomicInteger totalRejections = new AtomicInteger(0);
    private final AtomicInteger rejectedInWindow = new AtomicInteger(0);
    private volatile long windowStartMs = System.currentTimeMillis();
    private static final long WINDOW_MS = 5000;

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        totalRejections.incrementAndGet();
        int current = rejectedInWindow.incrementAndGet();

        logger.debug("Task rejected - recent rejections: {}", current);

        long now = System.currentTimeMillis();
        if (now - windowStartMs > WINDOW_MS) {
            rejectedInWindow.set(0);
            windowStartMs = now;
        }
    }

    public int getRecentRejections() {
        long now = System.currentTimeMillis();
        if (now - windowStartMs > WINDOW_MS) {
            return 0;
        }
        return rejectedInWindow.get();
    }

    public int getTotalRejections() {
        return totalRejections.get();
    }
}
