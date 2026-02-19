package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.storage.StorageLevel;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

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

    protected static final Logger logger = Logger.getLogger(StorageController.class.getName());

    private static volatile StorageController INSTANCE;

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
     * This method queries the FileStore for fresh values on each call, so the returned
     * information is always up-to-date.
     *
     * @return the current storage status
     */
    public StorageStatus getStorageStatus() {
        try {
            FileStore fileStore = getFileStore();
            long totalBytes = fileStore.getTotalSpace();
            long freeBytes = fileStore.getUsableSpace();
            long usedBytes = totalBytes - freeBytes;
            StorageLevel level = calculateStorageLevel(freeBytes);

            return new StorageStatus(freeBytes, usedBytes, totalBytes, level);
        } catch (IOException e) {
            logger.throwing("StorageController", "getStorageStatus", e);
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
        try {
            long freeBytes = getFileStore().getUsableSpace();
            return calculateStorageLevel(freeBytes);
        } catch (IOException e) {
            logger.throwing("StorageController", "getStorageLevel", e);
            // Return conservative value on error
            return StorageLevel.CRITICAL;
        }
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
        String workPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
        return Paths.get(workPath);
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
                workPath = Paths.get(".");
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
        long reservedBytes = ConfigManager.getInstance().get(ConfigSchema.STORAGE_RESERVED_BYTES);

        // CRITICAL: free space at or below reserved threshold
        if (freeBytes <= reservedBytes) {
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
