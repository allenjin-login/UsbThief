package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.AppPaths;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.PathConfig;
import com.superredrock.usbthief.core.config.configs.StorageConfig;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Storage controller for monitoring disk space and providing storage status.
 * <p>
 * This is a utility component (not a Service) that monitors the work directory's
 * disk space usage and provides storage status information. It is queried by
 * RecyclerService and other components to determine when storage cleanup is needed.
 * <p>
 * Storage levels are determined as follows:
 * <ul>
 *   <li>OK: Free space is greater than the reserved threshold</li>
 *   <li>LOW: Free space is approaching the reserved threshold (within 10% buffer zone)</li>
 *   <li>CRITICAL: Free space is at or below the reserved threshold</li>
 * </ul>
 * <p>
 * This class uses the singleton pattern to ensure a single instance monitors the work directory.
 */
public class StorageController {

    protected static final Logger logger = LogManager.getLogger(StorageController.class);

    private static volatile StorageController INSTANCE;

    /**
     * Statistics of the work directory, refreshed by {@link RecyclerService} on its own
     * thread and read here when the storage level is computed on another one, so the field
     * is volatile: the whole instance is published, never mutated in place.
     */
    protected volatile LongSummaryStatistics workSize = new LongSummaryStatistics(0,0,0,0);

    /**
     * How long a {@link StorageStatus} snapshot may be served from cache.
     *
     * <p>Each refresh costs a {@code FileStore} lookup plus {@code getTotalSpace()/getUsableSpace()}
     * (tens of microseconds per call). The copy hot path asks for the status once per file, so a
     * one-second cache removes essentially all of that cost while keeping the status fresh enough
     * for storage gating and GUI display.</p>
     */
    private static final long STATUS_CACHE_NANOS = TimeUnit.SECONDS.toNanos(1);

    private volatile StorageStatus cachedStatus;
    private volatile long cachedStatusNanos;

    private StorageController() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance of StorageController.
     *
     * @return the singleton instance
     */
    public static synchronized StorageController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StorageController();
        }
        return INSTANCE;
    }

    /**
     * Get the current storage status including free/used/total space and storage level.
     * <p>
     * The status is cached for {@value #STATUS_CACHE_NANOS} nanoseconds (one second) so that the
     * per-file copy hot path does not pay for {@code FileStore} queries on every file. Callers
     * that need an immediate re-sample can use {@link #refreshStorageStatus()}.
     *
     * @return the current storage status
     */
    public StorageStatus getStorageStatus() {
        StorageStatus snapshot = cachedStatus;
        long now = System.nanoTime();
        if (snapshot != null && now - cachedStatusNanos < STATUS_CACHE_NANOS) {
            return snapshot;
        }
        return refreshStorageStatus();
    }

    /**
     * Queries the filesystem for a fresh storage status and refreshes the cache.
     *
     * <p>Callers that must observe disk usage immediately (for example an explicit
     * "refresh" action) can use this instead of the cached {@link #getStorageStatus()}.</p>
     *
     * @return a freshly sampled storage status
     */
    public StorageStatus refreshStorageStatus() {
        StorageStatus status = queryStorageStatus();
        cachedStatus = status;
        cachedStatusNanos = System.nanoTime();
        return status;
    }

    /**
     * Drops the cached status so that the next {@link #getStorageStatus()} re-samples the
     * filesystem.
     */
    public void invalidateStorageStatus() {
        cachedStatus = null;
    }

    private StorageStatus queryStorageStatus() {
        try {
            FileStore fileStore = getFileStore();
            long totalBytes = fileStore.getTotalSpace();
            long freeBytes = fileStore.getUsableSpace();
            long usedBytes = totalBytes - freeBytes;
            StorageLevel level = calculateStorageLevel(freeBytes);

            return new StorageStatus(freeBytes, usedBytes, totalBytes, level);
        } catch (IOException e) {
            logger.error("Failed to get storage status", e);
            // Return conservative values on error
            return new StorageStatus(0, 0, 0, StorageLevel.CRITICAL);
        }
    }

    /**
     * Get the current storage level based on free space thresholds.
     *
     * @return the current storage level
     */
    public StorageLevel getStorageLevel() {
        return getStorageStatus().level();
    }

    /**
     * Check if storage is OK (not low or critical).
     *
     * @return true if storage is OK, false otherwise
     */
    public boolean isStorageOK() {
        return getStorageLevel() == StorageLevel.OK;
    }

    /**
     * Check if storage is critical (at or below reserved threshold).
     *
     * @return true if storage is critical, false otherwise
     */
    public boolean isStorageCritical() {
        return getStorageLevel() == StorageLevel.CRITICAL;
    }

    /**
     * Get the work directory path.
     *
     * @return the work directory path
     */
    public Path getWorkDirectory() {
        String workPath = ConfigManager.getInstance().get(PathConfig.WORK_PATH);
        return AppPaths.resolve(workPath);
    }

    /**
     * Get the FileStore for the work directory.
     * <p>
     * If the work directory doesn't exist, creates it or falls back to the current directory.
     *
     * @return the FileStore
     * @throws IOException if an I/O error occurs
     */
    public FileStore getFileStore() throws IOException {
        Path workPath = getWorkDirectory();
        
        // If work path doesn't exist, try parent directory or current directory
        if (!Files.exists(workPath)) {
            // Try to create the directory
            try {
                Files.createDirectories(workPath);
            } catch (IOException e) {
                // Fall back to current working directory
                workPath = AppPaths.getAppHome();
            }
        }
        
        return Files.getFileStore(workPath);
    }

    /**
     * Calculate the storage level based on free space thresholds.
     *
     * @param freeBytes the amount of free space in bytes
     * @return the storage level
     */
    private StorageLevel calculateStorageLevel(long freeBytes) {
        ConfigManager config = ConfigManager.getInstance();
        if (!config.get(StorageConfig.STORAGE_ENABLED)) {
            return StorageLevel.OK;
        }

        long reservedBytes = config.get(StorageConfig.STORAGE_RESERVED_BYTES);
        long maxBytes = config.get(StorageConfig.STORAGE_MAX_BYTES);

        // CRITICAL: free space at or below reserved threshold
        if (freeBytes <= reservedBytes) {
            return StorageLevel.CRITICAL;
        }
        if (workSize.getSum() >= maxBytes){
            return StorageLevel.CRITICAL;
        }

        // LOW: free space approaching reserved threshold (within 10% buffer zone)
        long bufferZone = (long) (reservedBytes * 0.1);
        if (freeBytes <= reservedBytes + bufferZone) {
            return StorageLevel.LOW;
        }

        // OK: free space is above threshold
        return StorageLevel.OK;
    }

    /**
     * Storage status record containing free, used, and total space information.
     *
     * @param freeBytes the amount of free space in bytes
     * @param usedBytes the amount of used space in bytes
     * @param totalBytes the total space in bytes
     * @param level the storage level (OK, LOW, CRITICAL)
     */
    public record StorageStatus(
            long freeBytes,
            long usedBytes,
            long totalBytes,
            StorageLevel level
    ) {}
}
