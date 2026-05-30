package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;
import com.superredrock.usbthief.index.HashAlgorithm;
import com.superredrock.usbthief.index.IndexKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Callable;

import com.superredrock.usbthief.statistics.SpeedProbe;
import com.superredrock.usbthief.statistics.Statistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VerifyTask implements Callable<CopyResult> {

    private static final Logger logger = LogManager.getLogger(VerifyTask.class);
    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(ConfigManager.getInstance().get(ConfigSchema.HASH_BUFFER_SIZE)));

    private final Path processingPath;
    private final String deviceSerial;

    private final SpeedProbe readProbe;

    public VerifyTask(Path path, String deviceSerial) {
        this.processingPath = path;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.readProbe = Statistics.getInstance().getSpeedCollector().createReadProbe("Verify" + "-read");
    }

    public static CheckSum verify(Path path) throws IOException {
        HashAlgorithm algorithm = HashAlgorithm.fromId(
                ConfigManager.getInstance().get(ConfigSchema.HASH_ALGORITHM));
        ByteBuffer buffer = bufferThreadLocal.get();
        try {
            return algorithm.compute(path, buffer);
        } finally {
            buffer.clear();
        }
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
            HashAlgorithm algorithm = HashAlgorithm.fromId(
                    ConfigManager.getInstance().get(ConfigSchema.HASH_ALGORITHM));
            ByteBuffer buffer = bufferThreadLocal.get();
            CheckSum hash;
            try {
                hash = algorithm.compute(processingPath, buffer, readProbe::record);
            } finally {
                buffer.clear();
            }

            if (QueueManager.getIndex().checkDuplicate(new IndexKey(deviceSerial, processingPath), hash)) {
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
