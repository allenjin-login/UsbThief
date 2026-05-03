package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class SessionProgressCollector implements MetricCollector {
    public static final String ID = "session.progress";
    private final AtomicLong bytesDiscovered = new AtomicLong(0);
    private final AtomicLong bytesCopied = new AtomicLong(0);
    private final AtomicLong filesCopied = new AtomicLong(0);
    private final AtomicLong foldersCopied = new AtomicLong(0);

    public SessionProgressCollector() {
        EventBus.getInstance().register(FileDiscoveredEvent.class, this::onFileDiscovered);
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onFileDiscovered(FileDiscoveredEvent event) {
        bytesDiscovered.addAndGet(event.fileSize());
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (!event.isSuccess()) return;
        if (Files.isDirectory(event.sourcePath())) {
            foldersCopied.incrementAndGet();
        } else {
            filesCopied.incrementAndGet();
            bytesCopied.addAndGet(event.bytesCopied());
        }
    }

    @Override public String getId() { return ID; }
    @Override public boolean isPersistent() { return false; }

    @Override
    public MetricSnapshot snapshot() {
        return new MetricSnapshot(ID, bytesCopied.get(), 0.0,
                Map.of(
                    "bytesDiscovered", bytesDiscovered.get(),
                    "bytesCopied", bytesCopied.get(),
                    "filesCopied", filesCopied.get(),
                    "foldersCopied", foldersCopied.get(),
                    "progressPercentage", getProgressPercentage()
                ));
    }

    @Override public void load(MetricStore store) {}
    @Override public void save(MetricStore store) {}

    @Override
    public void reset() {
        bytesDiscovered.set(0);
        bytesCopied.set(0);
        filesCopied.set(0);
        foldersCopied.set(0);
    }

    public long getBytesDiscovered() { return bytesDiscovered.get(); }
    public long getBytesCopied() { return bytesCopied.get(); }
    public long getFilesCopied() { return filesCopied.get(); }
    public long getFoldersCopied() { return foldersCopied.get(); }

    public int getProgressPercentage() {
        long discovered = bytesDiscovered.get();
        if (discovered == 0) return 0;
        return (int) (bytesCopied.get() * 100 / discovered);
    }
}
