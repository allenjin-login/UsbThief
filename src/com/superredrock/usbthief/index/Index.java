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

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File Index Service
 * <p>
 * Provides checksum-based file deduplication and file history tracking.
 * Extends Service, lifecycle managed by ServiceManager.
 * Supports persistent storage, automatically saves periodically when modified.
 */
public class Index extends Service {
    private static final Logger logger = Logger.getLogger(Index.class.getName());

    // Index data
    private final Set<CheckSum> digest;

    // Persistence
    private final Path indexPath;
    private volatile boolean dirty;

    // Configuration
    private final long saveInitialDelaySeconds;
    private final long saveDelaySeconds;


    public Index(){
        Path path = Path.of(ConfigManager.getInstance().get(ConfigSchema.INDEX_PATH));

        Path indexBasePath = path.getParent() != null
                ? path.getParent()
                : Paths.get(".");

        this(indexBasePath,ConfigManager.getInstance().get(ConfigSchema.SAVE_INITIAL_DELAY_SECONDS), ConfigManager.getInstance().get(ConfigSchema.SAVE_DELAY_SECONDS));
    }

    /**
     * Create a new index with default configuration.
     *
     * @param basePath base path for index files (index.obj and history.obj will be created)
     */
    public Index(Path basePath) {
        this(basePath, 30L, 60L);
    }

    /**
     * Create a new index with custom configuration.
     *
     * @param basePath base path for index files
     * @param saveInitialDelaySeconds delay before first save
     * @param saveDelaySeconds interval between saves
     */
    public Index(Path basePath, long saveInitialDelaySeconds, long saveDelaySeconds) {
        this.digest = ConcurrentHashMap.newKeySet();
        this.indexPath = basePath.resolve("index.obj");
        this.dirty = false;
        this.saveInitialDelaySeconds = saveInitialDelaySeconds;
        this.saveDelaySeconds = saveDelaySeconds;

        ensureDirectories();
    }

    /**
     * Ensure index directory exists.
     */
    private void ensureDirectories() {
        try {
            if (!Files.exists(indexPath.getParent())) {
                Files.createDirectories(indexPath.getParent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create index directory", e);
        }
    }

    /**
     * Load index data from disk.
     */
    public void load() {
        loadDigest();
        dirty = false;

        logger.info(String.format("Index loaded: %d checksums", digest.size()));

        // Dispatch IndexLoadedEvent
        EventBus.getInstance().dispatch(new IndexLoadedEvent(digest.size()));
    }

    /**
     * Load checksum digest from disk.
     */
    private void loadDigest() {
        if (!Files.exists(indexPath)) {
            logger.info("Index file not found, starting with empty digest");
            return;
        }

        try (ObjectInputStream objectInput = new ObjectInputStream(Files.newInputStream(indexPath))) {
            while (true) {
                CheckSum checksum = (CheckSum) objectInput.readObject();
                if (checksum != null) {
                    digest.add(checksum);
                } else {
                    break;
                }
            }
        } catch (EOFException _) {
            // End of file, normal
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Failed to load digest", e);
            digest.clear();
        }
    }



    /**
     * Save index data to disk if dirty.
     */
    public void save() {
        if (!dirty) {
            logger.fine("Index not dirty, skipping save");
            return;
        }

        saveDigest();
        dirty = false;

        logger.info(String.format("Index saved: %d checksums", digest.size()));

        // Dispatch IndexSavedEvent
        EventBus.getInstance().dispatch(new IndexSavedEvent(digest.size()));
    }

    /**
     * Save checksum digest to disk.
     */
    private void saveDigest() {
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(
                Files.newOutputStream(indexPath, StandardOpenOption.CREATE))) {
            for (CheckSum checksum : digest) {
                objectOutput.writeObject(checksum);
            }
            objectOutput.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save digest", e);
        }
    }



    /**
     * Start auto-save scheduler.
     * @deprecated Use {@link #start(ScheduledThreadPoolExecutor)} instead
     */
    @Deprecated
    public void installTicker() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            logger.warning("Save ticker already running");
            return;
        }

        scheduledTask = com.superredrock.usbthief.core.ServiceManager.getInstance().getScheduler()
                .scheduleWithFixedDelay(
                        this,
                        saveInitialDelaySeconds,
                        saveDelaySeconds,
                        TimeUnit.SECONDS
                );
        state = ServiceState.RUNNING;
        logger.info("Save ticker installed");
    }

    /**
     * Stop auto-save scheduler.
     * @deprecated Use {@link #stop()} instead
     */
    @Deprecated
    public void quitTicker() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
            state = ServiceState.STOPPED;
            logger.info("Save ticker stopped");
        }
    }

    // ========== AbstractService method implementations ==========

    @Override
    protected ScheduledFuture<?> scheduleTask(ScheduledThreadPoolExecutor scheduler) {
        return scheduler.scheduleWithFixedDelay(
                this,
                saveInitialDelaySeconds,
                saveDelaySeconds,
                TimeUnit.SECONDS
        );
    }

    @Override
    public String getName() {
        return "Index";
    }

    @Override
    public String getDescription() {
        return "File index and periodic save service";
    }

    @Override
    public void run() {
        try {
            if (dirty) {
                logger.fine("Executing periodic index save");
                save();
            } else {
                logger.fine("Index not modified, skipping periodic save");
            }
        } catch (Exception e) {
            logger.severe("Index save failed: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    @Override
    protected void cleanup() {
        // Save index data
        save();
    }

    @Override
    public String getStatus() {
        return String.format("Index[%s] - Checksums: %d, State: %s",
                state, digest.size(), dirty ? "dirty" : "clean");
    }

    // ========== End of AbstractService method implementations ==========

    /**
     * Check if a file with given checksum exists in index.
     *
     * @param checksum checksum to check
     * @return true if checksum exists, false otherwise
     */
    public boolean contains(CheckSum checksum) {
        return digest.contains(checksum);
    }

    /**
     * Check if a file is duplicate and dispatch DuplicateDetectedEvent if so.
     *
     * @param filePath file path
     * @param checksum file checksum
     * @return true if duplicate, false otherwise
     */
    public boolean checkDuplicate(Path filePath, CheckSum checksum) {
        boolean isDuplicate = contains(checksum);
        if (isDuplicate) {
            // Dispatch DuplicateDetectedEvent
            EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, filePath, 1));
        }
        return isDuplicate;
    }

    /**
     * Add a checksum to the digest and mark index as dirty.
     *
     * @param checksum checksum to add
     * @return true if added, false if already exists
     */
    public boolean addChecksum(CheckSum checksum) {
        boolean added = digest.add(checksum);
        if (added) {
            markDirty();
        }
        return added;
    }

    /**
     * Add a checksum to the digest and mark index as dirty.
     * Dispatches FileIndexedEvent if file was newly added.
     *
     * @param checksum file checksum
     * @param filePath file path
     * @param fileSize file size in bytes
     */
    public void addFile(CheckSum checksum, Path filePath, long fileSize) {
        boolean added = addChecksum(checksum);
        if (added) {
            // Dispatch FileIndexedEvent
            EventBus.getInstance().dispatch(new FileIndexedEvent(checksum, filePath, fileSize, getDigestSize()));
        }
    }

    /**
     * Mark index as dirty (needs saving).
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Check if index is dirty (has unsaved changes).
     *
     * @return true if dirty, false otherwise
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Clear all index data.
     */
    public void clear() {
        int oldDigestSize = digest.size();

        digest.clear();
        markDirty();

        logger.info(String.format("Index cleared: %d checksums removed", oldDigestSize));
    }

    // Getters

    /**
     * Get the checksum digest set.
     *
     * @return unmodifiable view of the digest
     */
    public Set<CheckSum> getDigest() {
        return Set.copyOf(digest);
    }

    /**
     * Get the index file path.
     *
     * @return index file path
     */
    public Path getIndexPath() {
        return indexPath;
    }

    /**
     * Get the number of checksums in digest.
     *
     * @return digest size
     */
    public int getDigestSize() {
        return digest.size();
    }
}
