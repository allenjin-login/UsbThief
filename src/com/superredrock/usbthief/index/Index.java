package com.superredrock.usbthief.index;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Index {

    private static final Logger logger = LogManager.getLogger(Index.class);

    private static volatile Index INSTANCE;

    private final Cache<IndexKey, CheckSum> cache;
    private final AtomicInteger totalEntries = new AtomicInteger(0);

    private Index() {
        int maxSize = ConfigManager.getInstance().get(ConfigSchema.INDEX_CACHE_SIZE);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .recordStats()
                .build();
    }

    public static Index getInstance() {
        if (INSTANCE == null) {
            synchronized (Index.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Index();
                }
            }
        }
        return INSTANCE;
    }

    public boolean checkDuplicate(IndexKey key, CheckSum checksum) {
        CheckSum cached = cache.getIfPresent(key);
        if (cached != null) {
            boolean isDuplicate = cached.equals(checksum);
            if (isDuplicate) {
                EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, key.filePath(), 1));
            }
            return isDuplicate;
        }
        return false;
    }

    public boolean addFile(CheckSum checksum, IndexKey key, long fileSize) {
        CheckSum existing = cache.getIfPresent(key);
        boolean isNew = existing == null || !existing.equals(checksum);

        cache.put(key, checksum);

        if (isNew) {
            totalEntries.incrementAndGet();
            EventBus.getInstance().dispatch(new FileIndexedEvent(checksum, key.filePath(), fileSize, totalEntries.get()));
        }
        return isNew;
    }

    public void clear() {
        int oldSize = totalEntries.getAndSet(0);
        cache.invalidateAll();
        logger.info("Index cleared: {} entries removed", oldSize);
    }

    public int getDigestSize() {
        return (int) cache.estimatedSize();
    }

    public String getStatus() {
        return String.format("Index - Cache: %d", cache.estimatedSize());
    }
}
