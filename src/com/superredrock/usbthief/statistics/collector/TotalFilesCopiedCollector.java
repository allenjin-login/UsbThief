package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

public final class TotalFilesCopiedCollector implements MetricCollector {
    public static final String ID = "files.copied";
    private static final String KEY = "totalFilesCopied";
    private final AtomicLong counter = new AtomicLong(0);

    public TotalFilesCopiedCollector() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (event.isSuccess() && !Files.isDirectory(event.sourcePath())) {
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
