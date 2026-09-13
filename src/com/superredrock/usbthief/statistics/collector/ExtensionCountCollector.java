package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ExtensionCountCollector implements MetricCollector {
    private static final Logger logger = LogManager.getLogger(ExtensionCountCollector.class);
    public static final String ID = "extensions.count";
    private static final String KEY_PREFIX = "ext.";
    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

    public ExtensionCountCollector() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (!event.isSuccess() || Files.isDirectory(event.sourcePath())) return;
        String ext = getFileExtension(event.sourcePath().getFileName().toString());
        if (ext != null) {
            counts.computeIfAbsent(ext, _ -> new AtomicLong(0)).incrementAndGet();
        }
    }

    static String getFileExtension(String fileName) {
        if (fileName.startsWith(".")) return null;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return null;
    }

    @Override public String getId() { return ID; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public MetricSnapshot snapshot() {
        return new MetricSnapshot(ID, 0L, 0.0, Map.of("counts", getExtensionCounts()));
    }

    @Override
    public void load(MetricStore store) {
        // Single keys() round trip; the set-based view is shared with the other collectors.
        for (String key : store.keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                String ext = key.substring(KEY_PREFIX.length());
                long count = store.getLong(key).orElse(0);
                if (count > 0) counts.put(ext, new AtomicLong(count));
            }
        }
    }

    @Override
    public void save(MetricStore store) {
        MetricWriteBatch batch = new MetricWriteBatch();
        counts.forEach((ext, count) -> batch.putLong(KEY_PREFIX + ext, count.get()));
        int applied = store.apply(batch);
        logger.debug("Saved {} extension counters ({} key mutations applied)", counts.size(), applied);
    }

    @Override
    public void reset() { counts.clear(); }

    public Map<String, Long> getExtensionCounts() {
        var result = new ConcurrentHashMap<String, Long>();
        counts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
}
