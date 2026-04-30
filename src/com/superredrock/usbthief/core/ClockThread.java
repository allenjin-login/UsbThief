package com.superredrock.usbthief.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClockThread extends Thread {

    private final TimeUnit unit;
    private final long initialDelay;
    private volatile long remaining;
    private volatile boolean paused = false;
    private volatile boolean done = false;
    private volatile boolean cancelled = false;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    public ClockThread(TimeUnit unit, long delay) {
        this.unit = unit;
        this.initialDelay = delay;
        this.remaining = delay;
        setDaemon(true);
        setName("ClockThread");
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
                if (paused) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        if (!cancelled) {
                            future.completeExceptionally(e);
                        }
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
            }

            try {
                unit.sleep(1);
            } catch (InterruptedException e) {
                if (!cancelled) {
                    future.completeExceptionally(e);
                }
                Thread.currentThread().interrupt();
                return;
            }

            remaining--;
        }

        if (remaining <= 0 && !cancelled) {
            done = true;
            future.complete(null);
        }
    }

    public void cancel() {
        cancelled = true;
        interrupt();
        future.cancel(true);
        synchronized (this) {
            notifyAll();
        }
    }

    public void pause() {
        synchronized (this) {
            paused = true;
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
        return done;
    }

    public boolean isPaused() {
        return paused;
    }
}
