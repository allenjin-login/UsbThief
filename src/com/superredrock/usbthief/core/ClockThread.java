package com.superredrock.usbthief.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClockThread extends Thread {

    private static final AtomicInteger counter = new AtomicInteger(0);

    private final TimeUnit unit;
    private final long initialDelay;
    private volatile long remaining;
    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    public ClockThread(TimeUnit unit, long delay) {
        if (unit == null) throw new IllegalArgumentException("TimeUnit cannot be null");
        if (delay < 0) throw new IllegalArgumentException("Delay must be non-negative: " + delay);
        this.unit = unit;
        this.initialDelay = delay;
        this.remaining = delay;
        setDaemon(true);
        setName("ClockThread-" + counter.incrementAndGet());
    }

    public ClockThread(long delayMillis) {
        this(TimeUnit.MILLISECONDS, delayMillis);
    }

    public CompletableFuture<Void> future() {
        return future;
    }

    public ClockThread thenRun(Runnable action) {
        future.thenRun(action);
        return this;
    }

    @Override
    public void run() {
        while (remaining > 0 && !cancelled) {
            synchronized (this) {
                while (paused && !cancelled) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        if (!cancelled && !future.isDone()) {
                            future.completeExceptionally(e);
                        }
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (cancelled) return;
            }

            try {
                unit.sleep(1);
            } catch (InterruptedException e) {
                if (!cancelled && !future.isDone()) {
                    future.completeExceptionally(e);
                }
                Thread.currentThread().interrupt();
                return;
            }

            remaining--;
        }

        if (remaining <= 0 && !cancelled && !future.isDone()) {
            future.complete(null);
        }
    }

    public void cancel() {
        cancelled = true;
        future.cancel(true);
        synchronized (this) {
            notifyAll();
        }
        interrupt();
    }

    public void pause() {
        synchronized (this) {
            if (!future.isDone() && !cancelled) {
                paused = true;
            }
        }
    }

    public void resume() {
        synchronized (this) {
            paused = false;
            notifyAll();
        }
    }

    public void restart() {
        synchronized (this) {
            remaining = initialDelay;
            paused = false;
            notifyAll();
        }
    }

    public long getRemaining(TimeUnit targetUnit) {
        return targetUnit.convert(remaining, unit);
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
