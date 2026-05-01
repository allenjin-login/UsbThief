package com.superredrock.usbthief.index;

import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Index extends Service {
    private static final Logger logger = LogManager.getLogger(Index.class);

    private static volatile Index INSTANCE;

    private final Cache<Path, CheckSum> cache;
    private final IndexDiskStore diskStore;
    private volatile boolean dirty;
    private final AtomicInteger totalEntries = new AtomicInteger(0);

    private Index() {
        this(resolveIndexPath());
    }

    private Index(Path indexPath) {
        int maxSize = ConfigManager.getInstance().get(ConfigSchema.INDEX_CACHE_SIZE);
        this.diskStore = new IndexDiskStore(indexPath);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .removalListener((Path key, CheckSum value, RemovalCause cause) -> {
                    if (cause.wasEvicted() && value != null) {
                        diskStore.append(key, value);
                        logger.debug("Evicted entry to disk: {}", key);
                    }
                })
                .build();
        this.dirty = false;
        ensureDirectories(indexPath);
    }

    static Index createForTest(Path indexPath) {
        return new Index(indexPath);
    }

    private static Path resolveIndexPath() {
        Path path = Path.of(ConfigManager.getInstance().get(ConfigSchema.INDEX_PATH));
        Path base = path.getParent() != null ? path.getParent() : Paths.get(".");
        return base.resolve("index.dat");
    }

    private void ensureDirectories(Path indexPath) {
        try {
            Path parent = indexPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create index directory", e);
        }
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

    public void load() {
        Map<Path, CheckSum> diskEntries = diskStore.load();
        totalEntries.set(diskEntries.size());

        int toCache = Math.min(diskEntries.size(), ConfigManager.getInstance().get(ConfigSchema.INDEX_CACHE_SIZE));
        int count = 0;
        for (var entry : diskEntries.entrySet()) {
            if (count >= toCache) break;
            cache.put(entry.getKey(), entry.getValue());
            count++;
        }

        dirty = false;
        logger.info("Index loaded: {} entries ({} in cache)", diskEntries.size(), toCache);

        EventBus.getInstance().dispatch(new IndexLoadedEvent(diskEntries.size()));
    }

    public void save() {
        if (!dirty) {
            logger.debug("Index not dirty, skipping save");
            return;
        }

        Map<Path, CheckSum> diskEntries = diskStore.load();
        diskEntries.putAll(cache.asMap());
        diskStore.compact(diskEntries);

        dirty = false;
        totalEntries.set(diskEntries.size());
        logger.info("Index saved: {} entries", diskEntries.size());

        EventBus.getInstance().dispatch(new IndexSavedEvent(diskEntries.size()));
    }

    public boolean checkDuplicate(Path filePath, CheckSum checksum) {
        CheckSum cached = cache.getIfPresent(filePath);
        if (cached != null) {
            boolean isDuplicate = cached.equals(checksum);
            if (isDuplicate) {
                EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, filePath, 1));
            }
            return isDuplicate;
        }

        CheckSum fromDisk = diskStore.lookup(filePath);
        if (fromDisk != null) {
            cache.put(filePath, fromDisk);
            boolean isDuplicate = fromDisk.equals(checksum);
            if (isDuplicate) {
                EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, filePath, 1));
            }
            return isDuplicate;
        }

        return false;
    }

    public boolean addFile(CheckSum checksum, Path filePath, long fileSize) {
        CheckSum existing = cache.getIfPresent(filePath);
        boolean isNew = existing == null || !existing.equals(checksum);

        cache.put(filePath, checksum);
        markDirty();

        if (isNew) {
            totalEntries.incrementAndGet();
            EventBus.getInstance().dispatch(new FileIndexedEvent(checksum, filePath, fileSize, totalEntries.get()));
        }
        return isNew;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clear() {
        int oldSize = totalEntries.getAndSet(0);
        cache.invalidateAll();
        diskStore.clear();
        dirty = false;
        logger.info("Index cleared: {} entries removed", oldSize);
    }

    public int getDigestSize() {
        return (int) cache.estimatedSize();
    }

    public Path getIndexPath() {
        return diskStore.getFilePath();
    }

    @Override
    protected void tick() {
        try {
            if (dirty) {
                logger.debug("Executing periodic index save");
                save();
            }
        } catch (Exception e) {
            logger.error("Index save failed: ", e);
            state = ServiceState.FAILED;
        }
    }

    @Override
    protected long getTickInterval() {
        return ConfigManager.getInstance().get(ConfigSchema.SAVE_DELAY_SECONDS);
    }

    @Override
    protected TimeUnit getTickUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    public String getServiceName() {
        return "Index";
    }

    @Override
    public String getDescription() {
        return "File index with LRU cache and disk persistence";
    }

    @Override
    protected void cleanup() {
        save();
    }

    @Override
    public String getStatus() {
        return String.format("Index[%s] - Cache: %d, State: %s",
                state, cache.estimatedSize(), dirty ? "dirty" : "clean");
    }
}
