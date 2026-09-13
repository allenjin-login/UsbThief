package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;

import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link CopyTask} that first waits for its source file to stop changing.
 *
 * <p>This is the delayed-copy integration point: {@link Sniffer} keeps submitting tasks the
 * moment a change is seen, but the waiting happens here - on a task scheduler worker - instead
 * of on the watcher thread (P0: the monitoring thread never does heavy work).</p>
 *
 * <p>Extends {@code CopyTask} rather than wrapping it so that
 * {@link PriorityRule#calculatePriority(java.util.concurrent.Callable)} keeps recognising the
 * task and applies the per-extension/size priority it applies to plain copies; a wrapper would
 * silently fall back to {@code DEFAULT_PRIORITY} and disable copy ordering.</p>
 */
public class GatedCopyTask extends CopyTask {

    private static final Logger logger = LogManager.getLogger(GatedCopyTask.class);

    private final FileStabilityGate stabilityGate;

    /**
     * @param path         file or directory to copy
     * @param deviceSerial serial number of the owning device
     * @param volume       the owning volume, or {@code null} to resolve it through the device manager
     * @param stabilityGate the gate to pass before copying
     */
    public GatedCopyTask(Path path, String deviceSerial, Volume volume, FileStabilityGate stabilityGate) {
        super(path, deviceSerial, volume, null);
        this.stabilityGate = stabilityGate;
    }

    @Override
    public CopyResult call() {
        FileStabilityGate.Outcome outcome = stabilityGate.awaitStable(processingPath);

        if (!outcome.copyPermitted()) {
            logger.info("Delayed copy skipped for {} ({})", processingPath, outcome);
            return CopyResult.SKIPPED;
        }
        if (outcome == FileStabilityGate.Outcome.TIMEOUT) {
            // Bounded wait exhausted: copy anyway rather than lose the file (see FileStabilityGate).
            logger.warn("File kept changing for the whole delay budget, copying anyway: {}", processingPath);
        }
        return super.call();
    }

    /**
     * {@inheritDoc}
     *
     * @return the gate this task consults
     */
    public FileStabilityGate getStabilityGate() {
        return stabilityGate;
    }
}
