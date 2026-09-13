package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

            // Fetch the keys once and group them per entry instead of scanning the full key array
            // for every volume (that made loading O(volumes x keys)).
            Map<Integer, List<String>> keysByIndex = MetricKeyGroups.byEntryIndex(store.keySet(), KEY_PREFIX);
            Set<Integer> indices = new TreeSet<>(keysByIndex.keySet());
            for (int i = 0; i < count; i++) {
                indices.add(i);
            }

            for (Integer i : indices) {
                String prefix = KEY_PREFIX + i + ".";
                String serial = store.getString(prefix + "serial").orElse(null);
                if (serial == null || serial.isEmpty()) continue;

                long firstSeenTime = store.getLong(prefix + "firstSeenTime").orElse(System.currentTimeMillis());
                VolumeStats vs = new VolumeStats(firstSeenTime);
                vs.filesCopiedRef().set(store.getLong(prefix + "filesCopied").orElse(0));
                vs.bytesCopiedRef().set(store.getLong(prefix + "bytesCopied").orElse(0));
                vs.errorsRef().set(store.getLong(prefix + "errors").orElse(0));

                List<String> entryKeys = keysByIndex.get(i);
                if (entryKeys != null) {
                    String extPrefix = prefix + "ext.";
                    for (String key : entryKeys) {
                        if (!key.startsWith(extPrefix)) continue;
                        String ext = key.substring(extPrefix.length());
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
        try {
            // One round trip for the old keys; the diff is then computed entirely in memory.
            Set<String> existingKeys = store.keySet();
            Map<String, List<String>> existingByPrefix =
                    MetricKeyGroups.byEntryPrefix(existingKeys, KEY_PREFIX);

            MetricWriteBatch batch = new MetricWriteBatch();
            int idx = 0;
            for (Map.Entry<String, VolumeStats> entry : statsMap.entrySet()) {
                String prefix = KEY_PREFIX + idx + ".";
                Set<String> desiredKeys = new HashSet<>();
                VolumeStats vs = entry.getValue();

                batch.putString(prefix + "serial", entry.getKey());
                desiredKeys.add(prefix + "serial");
                batch.putLong(prefix + "filesCopied", vs.getFilesCopied());
                desiredKeys.add(prefix + "filesCopied");
                batch.putLong(prefix + "bytesCopied", vs.getBytesCopied());
                desiredKeys.add(prefix + "bytesCopied");
                batch.putLong(prefix + "errors", vs.getErrors());
                desiredKeys.add(prefix + "errors");
                batch.putLong(prefix + "firstSeenTime", vs.getFirstSeenTime());
                desiredKeys.add(prefix + "firstSeenTime");

                for (Map.Entry<String, java.util.concurrent.atomic.AtomicLong> ext
                        : vs.extensionCountsMap().entrySet()) {
                    String key = prefix + "ext." + ext.getKey();
                    batch.putLong(key, ext.getValue().get());
                    desiredKeys.add(key);
                }

                List<String> previousKeys = existingByPrefix.remove(prefix);
                if (previousKeys != null) {
                    for (String key : previousKeys) {
                        if (!desiredKeys.contains(key)) {
                            batch.remove(key);
                        }
                    }
                }
                idx++;
            }

            // Volumes that disappeared leave their whole entry prefix behind.
            for (List<String> staleKeys : existingByPrefix.values()) {
                for (String key : staleKeys) {
                    batch.remove(key);
                }
            }

            batch.putLong(KEY_COUNT, idx);
            int applied = store.apply(batch);
            logger.debug("Saved {} volumes ({} key mutations applied)", idx, applied);
        } catch (Exception e) {
            logger.warn("Failed to save volume stats: {}", e.getMessage());
        }
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
