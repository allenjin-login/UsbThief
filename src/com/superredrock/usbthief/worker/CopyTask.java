package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CopyTask implements Callable<CopyResult> {

    protected static final Logger logger = LogManager.getLogger(CopyTask.class);

    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(ConfigManager.getInstance().get(ConfigSchema.BUFFER_SIZE)));

    protected final Path processingPath;
    private final String deviceSerial;
    private final CheckSum preVerifiedHash;
    private static volatile RateLimiter rateLimiter;
    private static final Object rateLimiterLock = new Object();
    private static final SpeedProbeGroup speedProbeGroup = new SpeedProbeGroup("copy-tasks");
    private static final AtomicLong lastLogTime = new AtomicLong(0);
    private static final long LOG_INTERVAL_MS = 1000;

    private final SpeedProbe taskProbe;



    public CopyTask(Path path, String deviceSerial){
        this(path, deviceSerial, null);
    }

    public CopyTask(Path path, String deviceSerial, CheckSum preVerifiedHash){
        this.processingPath = path;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.preVerifiedHash = preVerifiedHash;
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

        try {
            // Space check at start - skip copy if storage is CRITICAL
            StorageController storage = StorageController.getInstance();
            if (storage.isStorageCritical()) {
                logger.warn("Storage critical, skipping copy: {}", processingPath);
                result = CopyResult.SKIPPED;
            } else {
                Volume volume = QueueManager.getDeviceManager().getVolume(processingPath);
                if (volume != null && !volume.isAccessible()) {
                    return CopyResult.FAIL;
                }

                size = Files.size(processingPath);
                destinationPath = getPath(processingPath);

                // Check if file fits in available space with 10% buffer
                StorageController.StorageStatus status = storage.getStorageStatus();
                long availableWithBuffer = (long) (status.freeBytes() * 0.9);
                if (size > availableWithBuffer) {
                    logger.warn("File too large for available space (size: {}, available with buffer: {}), skipping copy: {}", size, availableWithBuffer, processingPath);
                    result = CopyResult.SKIPPED;
                } else {
                    // File fits - proceed with copy
                    if (Files.isDirectory(processingPath)){
                        Files.createDirectories(destinationPath);
                    } else if (preVerifiedHash != null) {
                        doCopy(processingPath, destinationPath, size, preVerifiedHash, buffer, volume);
                        bytesCopied = size;
                    } else {
                        CheckSum hash = CheckSum.verify(processingPath);
                        if (QueueManager.getIndex().checkDuplicate(processingPath, hash)){
                            logger.info("Path Ignore: {}", processingPath);
                        } else {
                            doCopy(processingPath, destinationPath, size, hash, buffer, volume);
                        }
                        bytesCopied = size;
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            result = CopyResult.FAIL;
            logger.warn("Fail Copy" ,e);
        } finally {
            buffer.clear();
            // Dispatch CopyCompletedEvent - ALWAYS dispatch, even for SKIPPED
            EventBus.getInstance().dispatch(new CopyCompletedEvent(
                    processingPath,
                    destinationPath,
                    size,
                    bytesCopied,
                    result,
                    deviceSerial
            ));
        }

        // Check for interruption after finally (in case interruption occurred during file operations)
        if (Thread.currentThread().isInterrupted() && result == CopyResult.SUCCESS){
            result = CopyResult.CANCEL;
        }
        return result;
    }

    private void doCopy(Path source, Path dest, long size, CheckSum hash, ByteBuffer buffer, Volume volume) throws IOException, InterruptedException {
        Files.createDirectories(dest.getParent());
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        try (FileChannel readChannel = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel writeChannel = FileChannel.open(dest, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            logger.debug("Copying:{} to {}", source, dest);
            while (readChannel.read(buffer) != -1) {
                if (Thread.currentThread().isInterrupted()){
                    throw new InterruptedException("Copy cancelled");
                }
                if (volume != null && !volume.isAccessible()) {
                    throw new IOException("Volume ejecting, aborting copy: " + source);
                }
                buffer.flip();
                int bytesWritten = writeChannel.write(buffer);
                taskProbe.record(bytesWritten);
                getRateLimiter().acquire(bytesWritten);

                long now = System.currentTimeMillis();
                long lastLog = lastLogTime.get();
                if (now - lastLog >= LOG_INTERVAL_MS) {
                    if (lastLogTime.compareAndSet(lastLog, now)) {
                        double speed = speedProbeGroup.getTotalSpeed();
                        logger.info("Copying: {} - Global: {} MB/s",
                            source.getFileName(), String.format("%.2f", speed));
                    }
                }

                buffer.clear();
            }
        }
        copyFileAttributes(source, dest, attributes);
        QueueManager.getIndex().addFile(hash, source, size);
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
            // Note: creationTime is not set as it requires elevated privileges on some filesystems
            
            logger.debug("Copied timestamps: modified={}, access={}, creation={}", lastModified, lastAccess, creation);
            
            // Try to copy DOS attributes (Windows)
            try {
                DosFileAttributes dosAttrs = Files.readAttributes(source, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                Files.setAttribute(destination, "dos:readonly", dosAttrs.isReadOnly());
                Files.setAttribute(destination, "dos:hidden", dosAttrs.isHidden());
                Files.setAttribute(destination, "dos:system", dosAttrs.isSystem());
                Files.setAttribute(destination, "dos:archive", dosAttrs.isArchive());
                logger.debug("Copied DOS attributes: readonly={}, hidden={}", dosAttrs.isReadOnly(), dosAttrs.isHidden());
            } catch (UnsupportedOperationException e) {
                // Not a DOS filesystem (e.g., Linux), ignore
                logger.debug("DOS attributes not supported on this filesystem");
            }
            
        } catch (IOException e) {
            logger.warn("Failed to copy file attributes:", e);
        }
    }

    private static Path getPath(Path target) throws IOException {
        Path workPath = Paths.get(ConfigManager.getInstance().get(ConfigSchema.WORK_PATH));
        return DeviceUtils.getPath(workPath, target);
    }


}
