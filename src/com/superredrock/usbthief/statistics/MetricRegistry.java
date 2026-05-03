package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.statistics.collector.MetricCollector;
import com.superredrock.usbthief.statistics.collector.MetricSnapshot;
import com.superredrock.usbthief.statistics.collector.MetricStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class MetricRegistry {
    private static final Logger logger = LogManager.getLogger(MetricRegistry.class);
    private final ConcurrentHashMap<String, MetricCollector> collectors = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<MetricSnapshot>> listeners = new CopyOnWriteArrayList<>();

    public void register(MetricCollector collector) {
        collectors.put(collector.getId(), collector);
        logger.debug("Registered metric collector: {}", collector.getId());
    }

    public MetricSnapshot getSnapshot(String metricId) {
        MetricCollector collector = collectors.get(metricId);
        return collector != null ? collector.snapshot() : new MetricSnapshot(metricId, 0L, 0.0);
    }

    public Map<String, MetricSnapshot> getAllSnapshots() {
        Map<String, MetricSnapshot> result = new LinkedHashMap<>();
        collectors.forEach((id, collector) -> result.put(id, collector.snapshot()));
        return Collections.unmodifiableMap(result);
    }

    public void loadAll(MetricStore store) {
        collectors.forEach((id, collector) -> {
            if (collector.isPersistent()) {
                try {
                    collector.load(store);
                } catch (Exception e) {
                    logger.warn("Failed to load collector {}: {}", id, e.getMessage());
                }
            }
        });
        logger.info("Loaded {} collectors", collectors.size());
    }

    public void saveAll(MetricStore store) {
        collectors.forEach((id, collector) -> {
            if (collector.isPersistent()) {
                try {
                    collector.save(store);
                } catch (Exception e) {
                    logger.warn("Failed to save collector {}: {}", id, e.getMessage());
                }
            }
        });
        store.flush();
        logger.info("Saved collectors");
    }

    public void resetAll() {
        collectors.forEach((id, collector) -> collector.reset());
        logger.info("Reset all collectors");
    }

    public void addListener(Consumer<MetricSnapshot> listener) {
        listeners.add(listener);
    }

    public void notifyListeners(MetricSnapshot snapshot) {
        listeners.forEach(l -> {
            try {
                l.accept(snapshot);
            } catch (Exception e) {
                logger.warn("Metric listener error: {}", e.getMessage());
            }
        });
    }
}
