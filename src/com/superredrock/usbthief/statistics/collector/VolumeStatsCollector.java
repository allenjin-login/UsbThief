package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VolumeStatsCollector implements MetricCollector {
    private static final Logger logger = LogManager.getLogger(VolumeStatsCollector.class);
    public static final String ID = "volume.stats";
    private static final String KEY_COUNT = "volumeStats.count";
    private static final String KEY_PREFIX = "vs.";
    private final ConcurrentHashMap<String, VolumeStats> statsMap = new ConcurrentHashMap<>();

    public VolumeStatsCollector() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        String serial = event.deviceSerial();
        if (serial.isEmpty()) return;

        VolumeStats vs = statsMap.computeIfAbsent(serial, _ -> new VolumeStats());

        if (event.isSuccess() && !Files.isDirectory(event.sourcePath())) {
            vs.filesCopiedRef().incrementAndGet();
            vs.bytesCopiedRef().addAndGet(event.bytesCopied());
            String ext = ExtensionCountCollector.getFileExtension(event.sourcePath().getFileName().toString());
            if (ext != null) {
                vs.extensionCountsMap().computeIfAbsent(ext, _ -> new java.util.concurrent.atomic.AtomicLong(0))
                        .incrementAndGet();
            }
        } else if (event.isFailure()) {
            vs.errorsRef().incrementAndGet();
        }
    }

    @Override public String getId() { return ID; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public MetricSnapshot snapshot() {
        return new MetricSnapshot(ID, 0L, 0.0, Map.of("volumeCount", statsMap.size()));
    }

    @Override
    public void load(MetricStore store) {
        try {
            int count = (int) store.getLong(KEY_COUNT).orElse(0);
            for (int i = 0; i < count; i++) {
                String prefix = KEY_PREFIX + i + ".";
                String serial = store.getString(prefix + "serial").orElse(null);
                if (serial == null || serial.isEmpty()) continue;

                long firstSeenTime = store.getLong(prefix + "firstSeenTime").orElse(System.currentTimeMillis());
                VolumeStats vs = new VolumeStats(firstSeenTime);
                vs.filesCopiedRef().set(store.getLong(prefix + "filesCopied").orElse(0));
                vs.bytesCopiedRef().set(store.getLong(prefix + "bytesCopied").orElse(0));
                vs.errorsRef().set(store.getLong(prefix + "errors").orElse(0));

                for (String key : store.keys()) {
                    if (key.startsWith(prefix + "ext.")) {
                        String ext = key.substring((prefix + "ext.").length());
                        long extCount = store.getLong(key).orElse(0);
                        if (extCount > 0) {
                            vs.extensionCountsMap().put(ext, new java.util.concurrent.atomic.AtomicLong(extCount));
                        }
                    }
                }

                statsMap.put(serial, vs);
            }
        } catch (Exception e) {
            logger.warn("Failed to load volume stats: {}", e.getMessage());
        }
    }

    @Override
    public void save(MetricStore store) {
        // Clear old keys
        try {
            int oldCount = (int) store.getLong(KEY_COUNT).orElse(0);
            for (int i = 0; i < oldCount; i++) {
                String prefix = KEY_PREFIX + i + ".";
                for (String key : store.keys()) {
                    if (key.startsWith(prefix)) store.remove(key);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to clear old volume stats keys: {}", e.getMessage());
        }

        int idx = 0;
        for (var entry : statsMap.entrySet()) {
            String prefix = KEY_PREFIX + idx + ".";
            store.put(prefix + "serial", entry.getKey());
            VolumeStats vs = entry.getValue();
            store.put(prefix + "filesCopied", vs.getFilesCopied());
            store.put(prefix + "bytesCopied", vs.getBytesCopied());
            store.put(prefix + "errors", vs.getErrors());
            store.put(prefix + "firstSeenTime", vs.getFirstSeenTime());
            vs.extensionCountsMap().forEach((ext, count) ->
                    store.put(prefix + "ext." + ext, count.get()));
            idx++;
        }
        store.put(KEY_COUNT, idx);
    }

    @Override
    public void reset() { statsMap.clear(); }

    public VolumeStats getVolumeStats(String serial) {
        return statsMap.computeIfAbsent(serial, _ -> new VolumeStats());
    }

    public Map<String, VolumeStats> getAllVolumeStats() {
        return new LinkedHashMap<>(statsMap);
    }
}
