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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class CopyTask implements Callable<CopyResult> {

    protected static final Logger logger = Logger.getLogger(CopyTask.class.getName());

    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(ConfigManager.getInstance().get(ConfigSchema.BUFFER_SIZE)));

    protected Path processingPath;
    private static volatile RateLimiter rateLimiter;
    private static final Object rateLimiterLock = new Object();
    private static final SpeedProbeGroup speedProbeGroup = new SpeedProbeGroup("copy-tasks");
    private static final AtomicLong lastLogTime = new AtomicLong(0);
    private static final long LOG_INTERVAL_MS = 1000;

    // Per-task speed probe for individual file tracking
    private final SpeedProbe taskProbe;



    public CopyTask(Path path){
        this.processingPath = path;
        this.taskProbe = new SpeedProbe("CopyTask-" + path.getFileName());
        // Register this task's probe with the group
        speedProbeGroup.addProbe(taskProbe);
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
            size = Files.size(processingPath);
            destinationPath = getPath(processingPath);

            if (Files.isDirectory(processingPath)){
                Files.createDirectories(destinationPath);
            }else {
                CheckSum hash = CheckSum.verify(processingPath);
                if (QueueManager.getIndex().checkDuplicate(processingPath, hash)){
                    logger.info("Path Ignore: " + processingPath);
                    // File already exists in index - treat as success (no copy needed)
                    bytesCopied = size;
                    destinationPath = null;
                } else {
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
                    // Add to index (checksum + history) - FileIndexedEvent will be dispatched by Index.addFile()
                    QueueManager.getIndex().addFile(hash, processingPath, size);
                }
            }

        } catch (IOException | InterruptedException e) {
            result = CopyResult.FAIL;
        } finally {
            buffer.clear();
            // Dispatch CopyCompletedEvent
            EventBus.getInstance().dispatch(new CopyCompletedEvent(
                    processingPath,
                    destinationPath,
                    size, // Use file size from try block or 0
                    bytesCopied,
                    result
            ));
        }

        // Check for interruption after finally (in case interruption occurred during file operations)
        if (Thread.currentThread().isInterrupted() && result == CopyResult.SUCCESS){
            result = CopyResult.CANCEL;
        }
        return result;
    }

    private static Path getPath(Path target) throws IOException {
        String workPath = ConfigManager.getInstance().get(ConfigSchema.WORK_PATH);
        return DeviceUtils.getPath(workPath != null ? java.nio.file.Paths.get(workPath) : null, target);
    }


}
