package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DeviceHistoryCollector implements MetricCollector {
    private static final Logger logger = LogManager.getLogger(DeviceHistoryCollector.class);
    public static final String ID = "device.history";
    private static final String KEY_COUNT = "deviceHistory.count";
    private static final String KEY_PREFIX = "dh.";
    private final ConcurrentHashMap<String, DeviceHistoryEntry> historyMap = new ConcurrentHashMap<>();

    public DeviceHistoryCollector() {
        EventBus.getInstance().register(DeviceArrivalEvent.class, this::onDeviceArrival);
        EventBus.getInstance().register(DeviceRemovalEvent.class, this::onDeviceRemoval);
    }

    private void onDeviceArrival(DeviceArrivalEvent event) {
        String serial = event.device().getSerialNumber();
        DeviceHistoryEntry entry = historyMap.computeIfAbsent(serial, _ ->
                new DeviceHistoryEntry(serial, event.device().getVid(), event.device().getPid(), event.timestamp()));
        entry.recordConnection(event.timestamp());
    }

    private void onDeviceRemoval(DeviceRemovalEvent event) {
        String serial = event.device().getSerialNumber();
        DeviceHistoryEntry entry = historyMap.get(serial);
        if (entry != null) {
            entry.recordDisconnection(event.timestamp());
        }
    }

    @Override public String getId() { return ID; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public MetricSnapshot snapshot() {
        return new MetricSnapshot(ID, 0L, 0.0, Map.of("deviceCount", historyMap.size()));
    }

    @Override
    public void load(MetricStore store) {
        try {
            int count = (int) store.getLong(KEY_COUNT).orElse(0);
            for (int i = 0; i < count; i++) {
                String prefix = KEY_PREFIX + i + ".";
                String serial = store.getString(prefix + "serial").orElse(null);
                if (serial == null || serial.isEmpty()) continue;

                String vid = store.getString(prefix + "vid").orElse("");
                String pid = store.getString(prefix + "pid").orElse("");
                long insertCount = store.getLong(prefix + "insertionCount").orElse(0);
                long firstSeen = store.getLong(prefix + "firstSeenTime").orElse(0);
                long lastSeen = store.getLong(prefix + "lastSeenTime").orElse(0);

                DeviceHistoryEntry entry = new DeviceHistoryEntry(serial, vid, pid, insertCount, firstSeen, lastSeen);

                int timelineCount = (int) store.getLong(prefix + "timelineCount").orElse(0);
                for (int j = 0; j < timelineCount && j < 100; j++) {
                    long ts = store.getLong(prefix + "timeline." + j + ".ts").orElse(0);
                    String evt = store.getString(prefix + "timeline." + j + ".event").orElse("");
                    if (ts > 0 && !evt.isEmpty()) {
                        entry.addTimelineEntry(ts, evt);
                    }
                }

                historyMap.put(serial, entry);
            }
        } catch (Exception e) {
            logger.warn("Failed to load device history: {}", e.getMessage());
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
            for (Map.Entry<String, DeviceHistoryEntry> entry : historyMap.entrySet()) {
                String prefix = KEY_PREFIX + idx + ".";
                Set<String> desiredKeys = new HashSet<>();
                DeviceHistoryEntry dhe = entry.getValue();

                batch.putString(prefix + "serial", entry.getKey());
                desiredKeys.add(prefix + "serial");
                batch.putString(prefix + "vid", dhe.getVid() != null ? dhe.getVid() : "");
                desiredKeys.add(prefix + "vid");
                batch.putString(prefix + "pid", dhe.getPid() != null ? dhe.getPid() : "");
                desiredKeys.add(prefix + "pid");
                batch.putLong(prefix + "insertionCount", dhe.getInsertionCount());
                desiredKeys.add(prefix + "insertionCount");
                batch.putLong(prefix + "firstSeenTime", dhe.getFirstSeenTime());
                desiredKeys.add(prefix + "firstSeenTime");
                batch.putLong(prefix + "lastSeenTime", dhe.getLastSeenTime());
                desiredKeys.add(prefix + "lastSeenTime");

                int tIdx = 0;
                for (Map.Entry<Long, String> te : dhe.getTimelineLog().entrySet()) {
                    if (tIdx >= 100) break;
                    String tsKey = prefix + "timeline." + tIdx + ".ts";
                    String eventKey = prefix + "timeline." + tIdx + ".event";
                    batch.putLong(tsKey, te.getKey());
                    desiredKeys.add(tsKey);
                    batch.putString(eventKey, te.getValue());
                    desiredKeys.add(eventKey);
                    tIdx++;
                }
                batch.putLong(prefix + "timelineCount", tIdx);
                desiredKeys.add(prefix + "timelineCount");

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

            // Devices that disappeared leave their whole entry prefix behind.
            for (List<String> staleKeys : existingByPrefix.values()) {
                for (String key : staleKeys) {
                    batch.remove(key);
                }
            }

            batch.putLong(KEY_COUNT, idx);
            int applied = store.apply(batch);
            logger.debug("Saved {} device history entries ({} key mutations applied)", idx, applied);
        } catch (Exception e) {
            logger.warn("Failed to save device history: {}", e.getMessage());
        }
    }

    @Override
    public void reset() { historyMap.clear(); }

    public DeviceHistoryEntry getDeviceHistory(String serial) {
        return historyMap.get(serial);
    }

    public Map<String, DeviceHistoryEntry> getAllDeviceHistory() {
        return new LinkedHashMap<>(historyMap);
    }
}
