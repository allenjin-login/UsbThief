package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

public final class TotalBytesCopiedCollector implements MetricCollector {
    public static final String ID = "bytes.copied";
    private static final String KEY = "totalBytesCopied";
    private final AtomicLong counter = new AtomicLong(0);

    public TotalBytesCopiedCollector() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (event.isSuccess() && !Files.isDirectory(event.sourcePath())) {
            counter.addAndGet(event.bytesCopied());
        }
    }

    @Override public String getId() { return ID; }
    @Override public MetricSnapshot snapshot() { return new MetricSnapshot(ID, counter.get()); }
    @Override public boolean isPersistent() { return true; }
    @Override public void load(MetricStore store) { counter.set(store.getLong(KEY).orElse(0)); }
    @Override public void save(MetricStore store) { store.put(KEY, counter.get()); }
    @Override public void reset() { counter.set(0); }
}
