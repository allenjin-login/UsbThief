package com.superredrock.usbthief.statistics.collector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class VolumeStats {
    private final AtomicLong filesCopied = new AtomicLong(0);
    private final AtomicLong bytesCopied = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> extensionCounts = new ConcurrentHashMap<>();
    private final long firstSeenTime;

    public VolumeStats() {
        this.firstSeenTime = System.currentTimeMillis();
    }

    public VolumeStats(long firstSeenTime) {
        this.firstSeenTime = firstSeenTime;
    }

    public long getFilesCopied() { return filesCopied.get(); }
    public long getBytesCopied() { return bytesCopied.get(); }
    public long getErrors() { return errors.get(); }
    public long getFirstSeenTime() { return firstSeenTime; }

    public Map<String, Long> getExtensionCounts() {
        return extensionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(
                        java.util.Comparator.comparingLong(AtomicLong::get)).reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    AtomicLong filesCopiedRef() { return filesCopied; }
    AtomicLong bytesCopiedRef() { return bytesCopied; }
    AtomicLong errorsRef() { return errors; }
    ConcurrentHashMap<String, AtomicLong> extensionCountsMap() { return extensionCounts; }
}
