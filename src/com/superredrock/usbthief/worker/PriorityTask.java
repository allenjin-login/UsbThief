package com.superredrock.usbthief.worker;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class PriorityTask<T extends Callable<R>, R> implements Comparable<PriorityTask<T, R>> {

    private final T delegate;
    private final int priority;
    private Future<R> future = null;

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

    public Future<R> getFuture() {
        return future;
    }

    public void setFuture(Future<R> future) {
        this.future = future;
    }

    @Override
    public int compareTo(PriorityTask<T, R> other) {
        return Integer.compare(other.priority, this.priority);
    }
}
