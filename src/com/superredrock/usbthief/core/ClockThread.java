package com.superredrock.usbthief.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClockThread extends Thread {

    private static final AtomicInteger counter = new AtomicInteger(0);

    private volatile long remaining;
    private final TimeUnit unit;
    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    public ClockThread(TimeUnit unit, long delay) {
        if (unit == null) throw new IllegalArgumentException("TimeUnit cannot be null");
        if (delay < 0) throw new IllegalArgumentException("Delay must be non-negative: " + delay);
        this.remaining = delay;
        this.unit = unit;
        setDaemon(true);
        setName("ClockThread-" + counter.incrementAndGet());
    }

    public ClockThread(long delayMillis) {
        this(TimeUnit.MILLISECONDS, delayMillis);
    }

    public CompletableFuture<Void> onCountdown() {
        return future;
    }

    public ClockThread thenRun(Runnable action) {
        this.onCountdown().thenRun(action);
        return this;
    }

    @Override
    public void run() {
        while (remaining > 0 && !cancelled) {
            synchronized (this){
                if (paused) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        completeExceptionally(e);
                        return;
                    }
                }
            }

            long start = System.currentTimeMillis();
            try {
                unit.sleep(1);
            } catch (InterruptedException e) {
                completeExceptionally(e);
                return;
            }

            if (!paused && !cancelled) {
                long elapsed = unit.convert(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
                remaining -= elapsed;
            }
        }

        if (!cancelled && !future.isDone()) {
            future.complete(null);
        }
    }

    private void completeExceptionally(InterruptedException e) {
        if (!cancelled && !future.isDone()) {
            future.completeExceptionally(e);
        }
        Thread.currentThread().interrupt();
    }

    public void cancel() {
        cancelled = true;
        future.cancel(true);
        interrupt();
    }

    public synchronized void pause() {
        if (!future.isDone() && !cancelled) {
            paused = true;
        }
    }

    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    public long getRemaining(TimeUnit targetUnit) {
        return targetUnit.convert(remaining, this.unit);
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
