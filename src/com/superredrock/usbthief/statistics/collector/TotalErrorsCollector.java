package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;

import java.util.concurrent.atomic.AtomicLong;

public final class TotalErrorsCollector implements MetricCollector {
    public static final String ID = "errors.total";
    private static final String KEY = "totalErrors";
    private final AtomicLong counter = new AtomicLong(0);

    public TotalErrorsCollector() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (event.isFailure()) {
            counter.incrementAndGet();
        }
    }

    @Override public String getId() { return ID; }
    @Override public MetricSnapshot snapshot() { return new MetricSnapshot(ID, counter.get()); }
    @Override public boolean isPersistent() { return true; }
    @Override public void load(MetricStore store) { counter.set(store.getLong(KEY).orElse(0)); }
    @Override public void save(MetricStore store) { store.put(KEY, counter.get()); }
    @Override public void reset() { counter.set(0); }
}
