package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.RecycleStrategy;
import com.superredrock.usbthief.core.event.storage.StorageLevel;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for periodic storage cleanup and file recycling.
 *
 * <p>RecyclerService runs on a tick-based schedule and performs two main operations:
 * <ul>
 *   <li>Empty folder deletion: Recursively removes empty folders when storage is OK</li>
 *   <li>File recycling: Deletes files based on strategy when storage is LOW or CRITICAL</li>
 * </ul>
 *
 * <p>Recycling strategies:
 * <ul>
 *   <li>TIME_FIRST: Delete oldest files first (when storage is LOW)</li>
 *   <li>SIZE_FIRST: Delete largest files first (when storage is CRITICAL)</li>
 *   <li>AUTO: Automatically select based on storage level</li>
 * </ul>
 *
 * <p>Protected files (new, locked, or system files) are never recycled.
 */
public class RecyclerService extends Service {

    // Tick interval: 5 minutes
    private static final long TICK_INTERVAL_MS = 5 * 60 * 1000;

    // Batch size for file processing to avoid blocking tick()
    private static final int MAX_FOLDERS_PER_TICK = 100;
    private static final int MAX_FILES_PER_TICK = 50;

    private final ConfigManager configManager;
    private final StorageController storageController;

    public RecyclerService() {
        configManager = ConfigManager.getInstance();
        storageController = StorageController.getInstance();
    }

    @Override
    protected void tick() {
        try {
            // Get current storage status
            StorageController.StorageStatus status = storageController.getStorageStatus();
            StorageLevel level = status.level();

            // Act based on storage level
            switch (level) {
                case OK -> deleteEmptyFolders();
                case LOW -> recycleFiles(RecycleStrategy.TIME_FIRST);
                case CRITICAL -> recycleFiles(RecycleStrategy.SIZE_FIRST);
            }

        } catch (Exception e) {
            logger.severe("RecyclerService tick failed: " + e.getMessage());
            logger.throwing(getClass().getName(), "tick", e);
        }
    }

    @Override
    protected long getTickIntervalMs() {
        return TICK_INTERVAL_MS;
    }

    @Override
    public String getServiceName() {
        return "RecyclerService";
    }

    @Override
    public String getDescription() {
        return "Periodic storage cleanup and file recycling service";
    }

    /**
     * Deletes empty folders recursively from the work directory.
     *
     * <p>Folders are deleted bottom-up (deepest first) to ensure
     * parent folders become empty after child deletion.
     *
     * <p>Dispatches {@link EmptyFoldersDeletedEvent} if any folders are deleted.
     */
    private void deleteEmptyFolders() {
        Path workPath = Path.of(configManager.get(ConfigSchema.WORK_PATH));

        if (!Files.exists(workPath)) {
            logger.warning("Work path does not exist: " + workPath);
            return;
        }

        try {
            // Collect empty folders (deepest first)
            List<Path> emptyFolders = new ArrayList<>();
            Files.walkFileTree(workPath, Collections.singleton(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Don't delete the work path itself
                    if (dir.equals(workPath)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Check if directory is empty
                    try {
                        try (var stream = Files.list(dir)) {
                            if (stream.findFirst().isEmpty()) {
                                emptyFolders.add(dir);
                            }
                        }
                    } catch (IOException e) {
                        logger.warning("Failed to check if directory is empty: " + dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // Sort by depth (deepest first) to ensure parent deletion succeeds
            emptyFolders.sort(Comparator.comparingInt(Path::getNameCount).reversed());

            // Limit batch size to avoid blocking tick()
            List<Path> toDelete = emptyFolders.stream()
                    .limit(MAX_FOLDERS_PER_TICK)
                    .toList();

            // Delete folders
            for (Path folder : toDelete) {
                try {
                    Files.delete(folder);
                    logger.fine("Deleted empty folder: " + folder);
                } catch (IOException e) {
                    logger.warning("Failed to delete empty folder: " + folder + " - " + e.getMessage());
                }
            }

            // Dispatch event if any folders were deleted
            if (!toDelete.isEmpty()) {
                EventBus.getInstance().dispatch(new EmptyFoldersDeletedEvent(toDelete, toDelete.size()));
                logger.info("Deleted " + toDelete.size() + " empty folders");
            }

        } catch (IOException e) {
            logger.severe("Failed to delete empty folders: " + e.getMessage());
        }
    }

    /**
     * Recycles files based on the specified strategy.
     *
     * <p>Process:
     * <ol>
     *   <li>Scan work directory for files</li>
     *   <li>Build FileMetadata list with size and copy time</li>
     *   <li>Mark protected files using ProtectedFileChecker</li>
     *   <li>Use FileSelector to select files based on strategy</li>
     *   <li>Delete selected files</li>
     *   <li>Dispatch FilesRecycledEvent</li>
     * </ol>
     *
     * @param strategy the recycling strategy to use
     */
    private void recycleFiles(RecycleStrategy strategy) {
        Path workPath = Path.of(configManager.get(ConfigSchema.WORK_PATH));

        if (!Files.exists(workPath)) {
            logger.warning("Work path does not exist: " + workPath);
            return;
        }

        try {
            // Get configuration
            int protectedAgeHours = configManager.get(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS);
            String configStrategy = configManager.get(ConfigSchema.RECYCLER_STRATEGY);

            // Determine actual strategy (use config if AUTO)
            RecycleStrategy actualStrategy = strategy;
            if (strategy == RecycleStrategy.AUTO) {
                actualStrategy = parseRecycleStrategy(configStrategy);
            }

            // Scan for files
            List<FileSelector.FileMetadata> files = new ArrayList<>();
            AtomicLong totalSize = new AtomicLong(0);

            Files.walkFileTree(workPath, Collections.singleton(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!Files.isRegularFile(file)) {
                                return FileVisitResult.CONTINUE;
                            }

                            long size = attrs.size();
                            long copyTime = attrs.lastModifiedTime().toMillis();
                            boolean isProtected = ProtectedFileChecker.isProtected(file, protectedAgeHours);

                            files.add(new FileSelector.FileMetadata(file, size, copyTime, isProtected));
                            totalSize.addAndGet(size);

                            // Limit scan to avoid blocking tick()
                            if (files.size() >= MAX_FILES_PER_TICK) {
                                return FileVisitResult.TERMINATE;
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });

            if (files.isEmpty()) {
                logger.fine("No files found for recycling");
                return;
            }

            // Calculate bytes needed to free (free up to 10% of current usage)
            long bytesNeeded = Math.max(1024 * 1024, totalSize.get() / 10);

            // Select files based on strategy
            List<FileSelector.FileMetadata> selectedFiles;
            switch (actualStrategy) {
                case TIME_FIRST -> selectedFiles = FileSelector.selectByTime(files, bytesNeeded);
                case SIZE_FIRST -> selectedFiles = FileSelector.selectBySize(files, bytesNeeded);
                case AUTO -> {
                    // For AUTO, use time-first for LOW, size-first for CRITICAL
                    StorageLevel level = storageController.getStorageStatus().level();
                    selectedFiles = FileSelector.selectAuto(files, bytesNeeded, level);
                }
                default -> selectedFiles = List.of();
            }

            if (selectedFiles.isEmpty()) {
                logger.fine("No files selected for recycling");
                return;
            }

            // Delete selected files
            long bytesFreed = 0;
            List<Path> deletedFiles = new ArrayList<>();

            for (FileSelector.FileMetadata file : selectedFiles) {
                try {
                    Files.delete(file.path());
                    bytesFreed += file.size();
                    deletedFiles.add(file.path());
                    logger.fine("Recycled file: " + file.path() + " (" + file.size() + " bytes)");
                } catch (IOException e) {
                    logger.warning("Failed to delete file: " + file.path() + " - " + e.getMessage());
                }
            }

            // Dispatch event if any files were deleted
            if (!deletedFiles.isEmpty()) {
                EventBus.getInstance().dispatch(
                        new FilesRecycledEvent(deletedFiles, bytesFreed, actualStrategy));
                logger.info("Recycled " + deletedFiles.size() + " files (" + bytesFreed + " bytes freed)");
            }

        } catch (IOException e) {
            logger.severe("Failed to recycle files: " + e.getMessage());
            logger.throwing(getClass().getName(), "recycleFiles", e);
        }
    }

    /**
     * Parses the recycle strategy string from configuration.
     *
     * @param strategy the strategy string from config
     * @return the RecycleStrategy enum value
     */
    private RecycleStrategy parseRecycleStrategy(String strategy) {
        if (strategy == null) {
            return RecycleStrategy.AUTO;
        }

        try {
            return RecycleStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid recycle strategy in config: " + strategy + ", using AUTO");
            return RecycleStrategy.AUTO;
        }
    }

    @Override
    protected void cleanup() {
        logger.info("RecyclerService cleanup completed");
    }
}
