package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import java.time.Instant;

/**
 * Wrapper class that adds scheduling metadata to CopyTask for priority queue ordering.
 */
public class PriorityCopyTask implements Comparable<PriorityCopyTask> {
    private final CopyTask delegate;
    private final int priority;
    private final Instant creationTime;
    private final Device device;

    public PriorityCopyTask(CopyTask delegate, int priority, Device device, Instant creationTime) {
        this.delegate = delegate;
        this.priority = Math.max(0, Math.min(100, priority)); // Clamp to 0-100
        this.device = device;
        this.creationTime = creationTime;
    }

    public CopyTask unwrap() {
        return delegate;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public Device getDevice() {
        return device;
    }

    @Override
    public int compareTo(PriorityCopyTask other) {
        // Higher priority = comes first (lower value incomparableTo)
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // Same priority: FIFO (earlier creation time = comes first)
        return this.creationTime.compareTo(other.creationTime);
    }
}
