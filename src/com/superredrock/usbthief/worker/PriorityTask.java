package com.superredrock.usbthief.worker;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class PriorityTask<T extends Callable<R>, R> implements Comparable<PriorityTask<T, R>> {

    private final T delegate;
    private final int priority;
    private Future<?> future = null;
    private final Instant creationTime = Instant.now();

    public PriorityTask(T delegate, int priority) {
        this.delegate = delegate;
        this.priority = Math.max(0, Math.min(100, priority));
    }


    public T unwrap() {
        return delegate;
    }

    public int getPriority() {
        return priority;
    }

    public Future<?> getFuture() {
        return future;
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    @Override
    public int compareTo(PriorityTask<T, R> other) {
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return this.creationTime.compareTo(other.creationTime);
    }
}
