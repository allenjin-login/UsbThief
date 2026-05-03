package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VerifyTask implements Callable<CopyResult> {

    private static final Logger logger = LogManager.getLogger(VerifyTask.class);
    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(ConfigManager.getInstance().get(ConfigSchema.HASH_BUFFER_SIZE)));

    private final Path processingPath;
    private final String deviceSerial;

    public VerifyTask(Path path, String deviceSerial) {
        this.processingPath = path;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
    }

    public static CheckSum verify(Path path) throws IOException {
        MessageDigest digest;
        try {digest = MessageDigest.getInstance("SHA-256");} catch (Exception e) {throw new RuntimeException(e);}
        ByteBuffer buffer = bufferThreadLocal.get();
        try (FileChannel readChannel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (readChannel.read(buffer) != -1) {
                if (Thread.interrupted()) {
                    throw new IOException("Hash computation interrupted");
                }
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        } finally {
            buffer.clear();
        }
        return new CheckSum(digest.digest());
    }

    public Path getProcessingPath() {
        return processingPath;
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    @Override
    public CopyResult call() {
        long size = 0;
        CopyResult result = CopyResult.SUCCESS;

        try {
            BasicFileAttributes attrs = Files.readAttributes(processingPath, BasicFileAttributes.class);
            if (!attrs.isRegularFile()) {
                return CopyResult.SKIPPED;
            }

            Volume volume = QueueManager.getDeviceManager().getVolume(processingPath);
            if (volume != null && volume.isEjecting()) {
                return CopyResult.SKIPPED;
            }
            if (volume != null && !volume.isConnected() && volume.isActive()) {
                return CopyResult.FAIL;
            }

            size = attrs.size();
            if (size > 10L * 1024 * 1024 * 1024) {
                logger.warn("File too big, skipping verify: {}", processingPath);
                TaskScheduler.getInstance().submit(new CopyTask(processingPath, deviceSerial, null));
                return CopyResult.SKIPPED;
            }
            CheckSum hash = verify(processingPath);

            if (QueueManager.getIndex().checkDuplicate(processingPath, hash)) {
                logger.info("Path Ignore (verify): {}", processingPath);
                result = CopyResult.SKIPPED;
            } else {
                TaskScheduler.getInstance().submit(new CopyTask(processingPath, deviceSerial, hash));
                return result;
            }
        } catch (IOException e) {
            result = CopyResult.FAIL;
            logger.warn("Verify failed: {}", processingPath, e);
        }

        EventBus.getInstance().dispatch(new CopyCompletedEvent(
                processingPath, null, size, size, result, deviceSerial));

        return result;
    }
}
