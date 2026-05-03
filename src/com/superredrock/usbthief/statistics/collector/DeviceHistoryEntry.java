package com.superredrock.usbthief.statistics.collector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class DeviceHistoryEntry {
    private final String serialNumber;
    private final String vid;
    private final String pid;
    private final AtomicLong insertionCount = new AtomicLong(0);
    private volatile long firstSeenTime;
    private volatile long lastSeenTime;
    private final ConcurrentHashMap<Long, String> timelineLog = new ConcurrentHashMap<>();

    public DeviceHistoryEntry(String serialNumber, String vid, String pid, long firstSeenTime) {
        this.serialNumber = serialNumber;
        this.vid = vid;
        this.pid = pid;
        this.firstSeenTime = firstSeenTime;
        this.lastSeenTime = firstSeenTime;
    }

    public DeviceHistoryEntry(String serialNumber, String vid, String pid,
                              long insertionCount, long firstSeenTime, long lastSeenTime) {
        this.serialNumber = serialNumber;
        this.vid = vid;
        this.pid = pid;
        this.insertionCount.set(insertionCount);
        this.firstSeenTime = firstSeenTime;
        this.lastSeenTime = lastSeenTime;
    }

    public String getSerialNumber() { return serialNumber; }
    public String getVid() { return vid; }
    public String getPid() { return pid; }
    public long getInsertionCount() { return insertionCount.get(); }
    public long getFirstSeenTime() { return firstSeenTime; }
    public long getLastSeenTime() { return lastSeenTime; }
    public Map<Long, String> getTimelineLog() {
        return timelineLog.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    void recordConnection(long timestamp) {
        insertionCount.incrementAndGet();
        lastSeenTime = timestamp;
        timelineLog.put(timestamp, "CONNECTED");
        evictOldest();
    }

    void recordDisconnection(long timestamp) {
        lastSeenTime = timestamp;
        timelineLog.put(timestamp, "DISCONNECTED");
        evictOldest();
    }

    void addTimelineEntry(long timestamp, String eventType) {
        timelineLog.put(timestamp, eventType);
    }

    private void evictOldest() {
        while (timelineLog.size() > 100) {
            timelineLog.entrySet().stream()
                .min(Map.Entry.comparingByKey())
                .ifPresent(e -> timelineLog.remove(e.getKey()));
        }
    }
}
