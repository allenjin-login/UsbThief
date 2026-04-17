package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VerifyTask implements Callable<CopyResult> {

    private static final Logger logger = Logger.getLogger(VerifyTask.class.getName());

    private final Path processingPath;
    private final String deviceSerial;

    public VerifyTask(Path path, String deviceSerial) {
        this.processingPath = path;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
    }

    public Path getProcessingPath() {
        return processingPath;
    }

    @Override
    public CopyResult call() {
        long size = 0;
        CopyResult result = CopyResult.SUCCESS;

        try {
            size = Files.size(processingPath);
            CheckSum hash = CheckSum.verify(processingPath);

            if (QueueManager.getIndex().checkDuplicate(processingPath, hash)) {
                logger.info("Path Ignore (verify): " + processingPath);
                result = CopyResult.SKIPPED;
            } else {
                TaskScheduler.getInstance().submit(new CopyTask(processingPath, deviceSerial, hash));
                return result;
            }
        } catch (IOException e) {
            result = CopyResult.FAIL;
            logger.log(Level.WARNING, "Verify failed: " + processingPath, e);
        }

        EventBus.getInstance().dispatch(new CopyCompletedEvent(
                processingPath, null, size, size, result, deviceSerial));

        return result;
    }
}
