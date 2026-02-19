package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.DeviceUtils;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CopyTask implements Callable<CopyResult> {

    protected static final Logger logger = Logger.getLogger(CopyTask.class.getName());

    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(ConfigManager.getInstance().get(ConfigSchema.BUFFER_SIZE)));

    protected Path processingPath;
    private final String deviceSerial;
    private static volatile RateLimiter rateLimiter;
    private static final Object rateLimiterLock = new Object();
    private static final SpeedProbeGroup speedProbeGroup = new SpeedProbeGroup("copy-tasks");
    private static final AtomicLong lastLogTime = new AtomicLong(0);
    private static final long LOG_INTERVAL_MS = 1000;

    private final SpeedProbe taskProbe;



    public CopyTask(Path path, String deviceSerial){
        this.processingPath = path;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.taskProbe = new SpeedProbe("CopyTask-" + path.getFileName());
        speedProbeGroup.addProbe(taskProbe);
    }

    public Path getProcessingPath() {
        return processingPath;
    }

    public static SpeedProbeGroup getSpeedProbeGroup() {
        return speedProbeGroup;
    }

    private static RateLimiter getRateLimiter() {
        RateLimiter current = rateLimiter;
        long limit = ConfigManager.getInstance().get(ConfigSchema.COPY_RATE_LIMIT);
        long burst = ConfigManager.getInstance().get(ConfigSchema.COPY_RATE_BURST_SIZE);

        if (current == null || limit != current.getRateLimitBytesPerSecond() 
                || burst != current.getBurstSize()) {
            synchronized (rateLimiterLock) {
                current = rateLimiter;
                if (current == null || limit != current.getRateLimitBytesPerSecond() 
                        || burst != current.getBurstSize()) {
                    rateLimiter = new RateLimiter(limit, burst);
                }
            }
        }
        return rateLimiter;
    }



    @Override
    public CopyResult call() {
        ByteBuffer buffer = bufferThreadLocal.get();
        long bytesCopied = 0;
        long size = 0;
        Path destinationPath = null;
        CopyResult result = CopyResult.SUCCESS;

        // Space check at start - skip copy if storage is CRITICAL
        StorageController storage = StorageController.getInstance();
        if (storage.isStorageCritical()) {
            logger.warning("Storage critical, skipping copy: " + processingPath);
            result = CopyResult.SKIPPED;
        } else {
            try {
                size = Files.size(processingPath);
                destinationPath = getPath(processingPath);

                // Check if file fits in available space with 10% buffer
                StorageController.StorageStatus status = storage.getStorageStatus();
                long availableWithBuffer = (long) (status.freeBytes() * 0.9);
                if (size > availableWithBuffer) {
                    logger.warning("File too large for available space (size: " + size +
                        ", available with buffer: " + availableWithBuffer + "), skipping copy: " + processingPath);
                    result = CopyResult.SKIPPED;
                } else {
                    // File fits - proceed with copy
                    if (Files.isDirectory(processingPath)){
                        Files.createDirectories(destinationPath);
                    }else {
                        CheckSum hash = CheckSum.verify(processingPath);
                        if (QueueManager.getIndex().checkDuplicate(processingPath, hash)){
                            logger.info("Path Ignore: " + processingPath);
                            // File already exists in index - treat as success (no copy needed)
                            bytesCopied = size;
                        } else {
                            Files.createDirectories(destinationPath.getParent());
                            BasicFileAttributes attributes = Files.readAttributes(processingPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                            try (FileChannel readChannel = FileChannel.open(processingPath, StandardOpenOption.READ);
                                 FileChannel writeChannel = FileChannel.open(destinationPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                                logger.fine("Copying:" + processingPath + " to " + destinationPath);
                                while (readChannel.read(buffer) != -1) {
                                    if (Thread.currentThread().isInterrupted()){
                                        return CopyResult.CANCEL;
                                    }
                                    buffer.flip();
                                    int bytesWritten = writeChannel.write(buffer);
                                    bytesCopied += bytesWritten;
                                    taskProbe.record(bytesWritten);  // Record to task-specific probe
                                    getRateLimiter().acquire(bytesWritten);

                                    long now = System.currentTimeMillis();
                                    long lastLog = lastLogTime.get();
                                    if (now - lastLog >= LOG_INTERVAL_MS) {
                                        if (lastLogTime.compareAndSet(lastLog, now)) {
                                            double speed = speedProbeGroup.getTotalSpeed();
                                            logger.info(String.format("Copying: %s - Global: %.2f MB/s",
                                                processingPath.getFileName(),
                                                speed));
                                        }
                                    }

                                    buffer.clear();
                                }
                            }

                            // Copy file attributes (timestamps, read-only, etc.)
                            copyFileAttributes(processingPath, destinationPath, attributes);

                    // Add to index (checksum + history) - FileIndexedEvent will be dispatched by Index.addFile()
                    QueueManager.getIndex().addFile(hash, processingPath, size);
                }
                }
            }
        } catch (IOException | InterruptedException e) {
                result = CopyResult.FAIL;
                logger.log(Level.WARNING,"Fail Copy" ,e);
            } finally {
                buffer.clear();
                // Dispatch CopyCompletedEvent
                EventBus.getInstance().dispatch(new CopyCompletedEvent(
                        processingPath,
                        destinationPath,
                        size,
                        bytesCopied,
                        result,
                        deviceSerial
                ));
            }
        }

        // Check for interruption after finally (in case interruption occurred during file operations)
        if (Thread.currentThread().isInterrupted() && result == CopyResult.SUCCESS){
            result = CopyResult.CANCEL;
        }
        return result;
    }

    /**
     * Copies file attributes from source to destination.
     * Includes timestamps (modified, access, creation) and DOS attributes (readonly, hidden, etc.).
     */
    private static void copyFileAttributes(Path source, Path destination, BasicFileAttributes sourceAttrs) {
        try {
            // Copy timestamps
            FileTime lastModified = sourceAttrs.lastModifiedTime();
            FileTime lastAccess = sourceAttrs.lastAccessTime();
            FileTime creation = sourceAttrs.creationTime();
            
            Files.setAttribute(destination, "basic:lastModifiedTime", lastModified);
            Files.setAttribute(destination, "basic:lastAccessTime", lastAccess);
            //Files.setAttribute(destination, "basic:creationTime", creation);
            
            logger.fine("Copied timestamps: modified=" + lastModified + ", access=" + lastAccess + ", creation=" + creation);
            
            // Try to copy DOS attributes (Windows)
            try {
                DosFileAttributes dosAttrs = Files.readAttributes(source, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                Files.setAttribute(destination, "dos:readonly", dosAttrs.isReadOnly());
                Files.setAttribute(destination, "dos:hidden", dosAttrs.isHidden());
                Files.setAttribute(destination, "dos:system", dosAttrs.isSystem());
                Files.setAttribute(destination, "dos:archive", dosAttrs.isArchive());
                logger.fine("Copied DOS attributes: readonly=" + dosAttrs.isReadOnly() + ", hidden=" + dosAttrs.isHidden());
            } catch (UnsupportedOperationException e) {
                // Not a DOS filesystem (e.g., Linux), ignore
                logger.fine("DOS attributes not supported on this filesystem");
            }
            
        } catch (IOException e) {
            logger.warning("Failed to copy file attributes: " + e.getMessage());
        }
    }

    private static Path getPath(Path target) throws IOException {
        Path workPath = Paths.get(ConfigManager.getInstance().get(ConfigSchema.WORK_PATH));
        return DeviceUtils.getPath(workPath, target);
    }


}
