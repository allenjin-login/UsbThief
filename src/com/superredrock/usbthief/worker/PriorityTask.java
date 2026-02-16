package com.superredrock.usbthief.worker;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class PriorityTask<T extends Callable<R>, R> implements Comparable<PriorityTask<T, R>> {

    private final T delegate;
    private final int priority;
    private final CompletableFuture<R> future = new CompletableFuture<>();

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

    public CompletableFuture<R> getFuture() {
        return future;
    }

    public void complete(R result) {
        future.complete(result);
    }

    public void completeExceptionally(Throwable ex) {
        future.completeExceptionally(ex);
    }

    @Override
    public int compareTo(PriorityTask<T, R> other) {
        return Integer.compare(other.priority, this.priority);
    }
}
