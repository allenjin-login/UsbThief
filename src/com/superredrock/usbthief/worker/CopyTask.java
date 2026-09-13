package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.AppPaths;
import com.superredrock.usbthief.core.Volume;

import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.CategoryConfig;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.OverwriteConfig;
import com.superredrock.usbthief.core.config.configs.PathConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.DeviceUtils;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;
import com.superredrock.usbthief.index.IndexKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import com.superredrock.usbthief.statistics.SpeedProbe;
import com.superredrock.usbthief.statistics.Statistics;
import com.superredrock.usbthief.statistics.collector.SpeedCollector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CopyTask implements Callable<CopyResult>, DeviceBoundTask {

    protected static final Logger logger = LogManager.getLogger(CopyTask.class);

    /** Lowest chunk size accepted from configuration (guards against degenerate values). */
    static final int MIN_BUFFER_SIZE = 4 * 1024;
    /** Highest chunk size accepted from configuration (guards against accidental OOM). */
    static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024;
    /** Above this chunk size the reusable per-thread buffer is allocated off-heap. */
    private static final int DIRECT_BUFFER_THRESHOLD = 32 * 1024;

    /**
     * Reusable per-thread copy buffer.
     *
     * <p>The buffer used to be fetched from a {@code ThreadLocal.withInitial(...)} whose stored
     * value was never replaced, so a later configuration change was ignored; it is now sized from
     * the per-file configuration snapshot and only reallocated when that size changes. Reusing the
     * allocation keeps large chunks (1 MB by default) out of the GC path.</p>
     */
    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = new ThreadLocal<>();

    protected final Path processingPath;
    private final String deviceSerial;
    private final CheckSum preVerifiedHash;
    /** Volume that owns {@link #processingPath}; may be {@code null} for ad-hoc copies. */
    private final Volume volume;

    private static volatile RateLimiter readRateLimiter;
    private static volatile RateLimiter writeRateLimiter;
    private static final Object readRateLimiterLock = new Object();
    private static final Object writeRateLimiterLock = new Object();

    /**
     * Shared speed collector, resolved on first use instead of during class initialisation.
     *
     * <p>This used to be a static initialiser ({@code Statistics.getInstance().getSpeedCollector()}),
     * so merely <em>loading</em> {@code CopyTask} pulled in {@code Statistics} - which then loaded
     * the persisted metrics and bound the statistics HTTP port as a side effect of class loading.
     * The lookup is now deferred to the first copy; the collector instance is still resolved once
     * and cached, so nothing about which collector is used has changed.
     */
    private static volatile SpeedCollector speedCollector;

    private static final AtomicLong lastLogTime = new AtomicLong(0);
    private static final long LOG_INTERVAL_MS = 1000;

    /**
     * @return the shared speed collector, resolving and caching it on the first call
     */
    private static SpeedCollector speedCollector() {
        SpeedCollector collector = speedCollector;
        if (collector == null) {
            collector = Statistics.getInstance().getSpeedCollector();
            speedCollector = collector;
        }
        return collector;
    }

    public CopyTask(Path path, String deviceSerial){
        this(path, deviceSerial, null, null);
    }

    public CopyTask(Path path, String deviceSerial, CheckSum preVerifiedHash){
        this(path, deviceSerial, null, preVerifiedHash);
    }

    /**
     * Creates a copy task for a path owned by a known volume.
     *
     * <p>Passing the volume lets {@link #getPath(Path)} derive the destination folder from
     * {@link Volume#getVolumeName()} instead of querying the file store for every file.</p>
     *
     * @param path the file or directory to copy
     * @param deviceSerial serial number of the owning device
     * @param volume the owning volume, or {@code null} to resolve it through the device manager
     * @param preVerifiedHash checksum already computed for this file, or {@code null}
     */
    public CopyTask(Path path, String deviceSerial, Volume volume, CheckSum preVerifiedHash){
        this.processingPath = path;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.volume = volume;
        this.preVerifiedHash = preVerifiedHash;
    }

    public Path getProcessingPath() {
        return processingPath;
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }


    private static RateLimiter getReadRateLimiter(long limit, long burst) {
        RateLimiter current = readRateLimiter;

        if (current == null || limit != current.getRateLimitBytesPerSecond()
                || burst != current.getBurstSize()) {
            synchronized (readRateLimiterLock) {
                current = readRateLimiter;
                if (current == null || limit != current.getRateLimitBytesPerSecond()
                        || burst != current.getBurstSize()) {
                    readRateLimiter = new RateLimiter(limit, burst);
                }
            }
        }
        return readRateLimiter;
    }

    private static RateLimiter getWriteRateLimiter(long limit, long burst) {
        RateLimiter current = writeRateLimiter;

        if (current == null || limit != current.getRateLimitBytesPerSecond()
                || burst != current.getBurstSize()) {
            synchronized (writeRateLimiterLock) {
                current = writeRateLimiter;
                if (current == null || limit != current.getRateLimitBytesPerSecond()
                        || burst != current.getBurstSize()) {
                    writeRateLimiter = new RateLimiter(limit, burst);
                }
            }
        }
        return writeRateLimiter;
    }



    @Override
    public CopyResult call() {
        // PF-02: snapshot the copy-relevant configuration once per file. The hot loop below uses
        // these locals instead of re-reading Preferences four times per chunk.
        CopySettings settings = CopySettings.snapshot();

        long bytesCopied = 0;
        long size = 0;
        Path destinationPath = null;
        CopyResult result = CopyResult.SUCCESS;

        try {
            // PF-05: a single readAttributes() replaces Files.size() plus the two
            // Files.isDirectory() calls this method used to make.
            BasicFileAttributes attributes =
                    Files.readAttributes(processingPath, BasicFileAttributes.class);
            size = attributes.size();
            // Batch 2-B: only regular files are categorised. Folder tasks (PF-06) keep the
            // plain destination so an empty folder is still created where it was before.
            String categoryDir = attributes.isDirectory()
                    ? null : settings.categoryMode.categoryDirectory(processingPath);
            destinationPath = getPath(processingPath, categoryDir);

            // PF-06: directories are a lightweight task - create the folder and stop. They skip
            // the storage gate, the space check and all speed probes. The completion event below
            // is still dispatched so folder counters and GUI listeners see exactly what they saw
            // before.
            if (attributes.isDirectory()) {
                Files.createDirectories(destinationPath);
            } else {
                // Space check at start - skip copy if storage is CRITICAL. Both calls below are
                // served from StorageController's one-second status cache.
                StorageController storage = StorageController.getInstance();
                if (storage.isStorageCritical()) {
                    logger.warn("Storage critical, skipping copy: {}", processingPath);
                    result = CopyResult.SKIPPED;
                } else {
                    // Check if file fits in available space with 10% buffer
                    StorageController.StorageStatus status = storage.getStorageStatus();
                    long availableWithBuffer = (long) (status.freeBytes() * 0.9);
                    if (size > availableWithBuffer) {
                        logger.warn("File too large for available space (size: {}, available with buffer: {}), skipping copy: {}", size, availableWithBuffer, processingPath);
                        result = CopyResult.SKIPPED;
                    } else {
                        // Overwrite strategy check — must happen before doCopy
                        if (Files.exists(destinationPath)) {
                            OverwriteStrategy strategy = OverwriteStrategy.safeValueOf(
                                    ConfigManager.getInstance().get(OverwriteConfig.OVERWRITE_STRATEGY));
                            if (strategy.shouldOverwrite(processingPath, destinationPath)) {
                                Files.deleteIfExists(destinationPath);
                            } else {
                                Path resolved = strategy.resolveTarget(destinationPath);
                                if (resolved.equals(destinationPath)) {
                                    // TIME_COMPARE: source is older — skip entirely
                                    logger.info("Skipping older file: {}", processingPath);
                                    result = CopyResult.SKIPPED;
                                } else {
                                    // RENAME: use new path with timestamp
                                    destinationPath = resolved;
                                }
                            }
                        }

                        // Proceed with copy (unless strategy decided to skip)
                        if (result != CopyResult.SKIPPED) {
                            doCopy(processingPath, destinationPath, size, preVerifiedHash, settings, attributes);
                            bytesCopied = size;
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            result = CopyResult.FAIL;
            logger.warn("Fail Copy" ,e);
        } finally {
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
        logger.info("Copied: {}",processingPath);

        // Check for interruption after finally (in case interruption occurred during file operations)
        if (Thread.currentThread().isInterrupted() && result == CopyResult.SUCCESS){
            result = CopyResult.CANCEL;
        }
        return result;
    }

    private void doCopy(Path source, Path dest, long size, CheckSum hash, CopySettings settings,
                        BasicFileAttributes attributes) throws IOException, InterruptedException {
        Files.createDirectories(dest.getParent());
        ByteBuffer buffer = acquireBuffer(settings.bufferSize);
        // Reusable per-thread probes: no allocation per file, no leak of one probe pair per task.
        SpeedCollector collector = speedCollector();
        SpeedProbe readProbe = collector.getThreadReadProbe();
        SpeedProbe writeProbe = collector.getThreadWriteProbe();
        // Limiters are resolved once per file from the snapshot; null when the matching rate is 0,
        // which makes the per-chunk limiter call disappear entirely.
        RateLimiter readLimiter = settings.readLimit > 0
                ? getReadRateLimiter(settings.readLimit, settings.burstSize) : null;
        RateLimiter writeLimiter = settings.writeLimit > 0
                ? getWriteRateLimiter(settings.writeLimit, settings.burstSize) : null;

        try (FileChannel readChannel = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel writeChannel = FileChannel.open(dest, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            logger.debug("Copying:{} to {}", source, dest);
            while (true) {
                buffer.clear();
                int read = readChannel.read(buffer);
                if (read == -1) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                if (Thread.currentThread().isInterrupted()){
                    throw new InterruptedException("Copy cancelled");
                }
                buffer.flip();

                int bytesRead = buffer.remaining();
                if (readLimiter != null) {
                    readLimiter.acquire(bytesRead, settings.readLimit);
                }
                readProbe.record(bytesRead);

                int bytesWritten = writeChannel.write(buffer);
                writeProbe.record(bytesWritten);
                if (writeLimiter != null) {
                    writeLimiter.acquire(bytesWritten, settings.writeLimit);
                }

                long now = System.currentTimeMillis();
                long lastLog = lastLogTime.get();
                if (now - lastLog >= LOG_INTERVAL_MS) {
                    if (lastLogTime.compareAndSet(lastLog, now)) {
                        double readSpeed = collector.getReadProbeGroup().getTotalSpeed();
                        double writeSpeed = collector.getWriteProbeGroup().getTotalSpeed();
                        logger.debug("Copying: {} - Read: {} MB/s, Write: {} MB/s",
                            source.getFileName(), String.format("%.2f", readSpeed), String.format("%.2f", writeSpeed));
                    }
                }
            }
        }
        copyFileAttributes(source, dest, attributes);
        if (hash != null){
            QueueManager.getIndex().addFile(hash, new IndexKey(deviceSerial, source), size);
        }
    }

    /**
     * Returns the reusable buffer for the calling thread, resizing it when the configured chunk
     * size changed since the previous copy on this thread.
     *
     * @param bufferSize chunk size in bytes
     * @return a cleared buffer with the requested capacity
     */
    private static ByteBuffer acquireBuffer(int bufferSize) {
        ByteBuffer buffer = bufferThreadLocal.get();
        if (buffer == null || buffer.capacity() != bufferSize) {
            buffer = bufferSize >= DIRECT_BUFFER_THRESHOLD
                    ? ByteBuffer.allocateDirect(bufferSize)
                    : ByteBuffer.allocate(bufferSize);
            bufferThreadLocal.set(buffer);
        }
        buffer.clear();
        return buffer;
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

//          Files.setAttribute(destination, "basic:lastModifiedTime", lastModified);
//          Files.setAttribute(destination, "basic:lastAccessTime", lastAccess);
//          Files.setAttribute(destination, "basic:creationTime", creation);
            // Note: creationTime is not set as it requires elevated privileges on some filesystems

//            logger.debug("Copied timestamps: modified={}, access={}, creation={}", lastModified, lastAccess, creation);

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

    /**
     * Destination path for a file, optionally with the category-folder layer applied.
     *
     * @param target the file being copied
     * @param categoryDir folder to insert below the volume folder, or {@code null}
     *                    for the uncategorised layout
     */
    private Path getPath(Path target, String categoryDir) throws IOException {
        Path workPath = AppPaths.resolve(ConfigManager.getInstance().get(PathConfig.WORK_PATH));
        // PF-05: the owning volume is injected by the submitter, so the destination folder comes
        // from the cached volume name instead of a per-file Files.getFileStore(target).name().
        return DeviceUtils.getPath(workPath, target, volume, categoryDir);
    }

    /**
     * Clamps a configured chunk size into a sane range.
     *
     * @param configured configured buffer size in bytes
     * @return usable chunk size
     */
    static int clampBufferSize(int configured) {
        if (configured < MIN_BUFFER_SIZE) {
            return MIN_BUFFER_SIZE;
        }
        if (configured > MAX_BUFFER_SIZE) {
            return MAX_BUFFER_SIZE;
        }
        return configured;
    }

    /**
     * Immutable per-file snapshot of the copy-related configuration.
     *
     * <p>Read once at the start of {@link #call()} so the copy loop never asks
     * {@link ConfigManager} (and therefore {@code Preferences}) for a value again.</p>
     */
    static final class CopySettings {
        final int bufferSize;
        final long readLimit;
        final long writeLimit;
        final long burstSize;
        final CategoryMode categoryMode;

        private CopySettings(int bufferSize, long readLimit, long writeLimit, long burstSize,
                             CategoryMode categoryMode) {
            this.bufferSize = bufferSize;
            this.readLimit = readLimit;
            this.writeLimit = writeLimit;
            this.burstSize = burstSize;
            this.categoryMode = categoryMode;
        }

        /**
         * Reads the copy configuration once.
         *
         * @return an immutable snapshot for a single copy operation
         */
        static CopySettings snapshot() {
            ConfigManager config = ConfigManager.getInstance();
            return new CopySettings(
                    clampBufferSize(config.get(FileCopyConfig.BUFFER_SIZE)),
                    config.get(RateLimitConfig.COPY_READ_RATE_LIMIT),
                    config.get(RateLimitConfig.COPY_WRITE_RATE_LIMIT),
                    config.get(RateLimitConfig.COPY_RATE_BURST_SIZE),
                    CategoryMode.safeValueOf(config.get(CategoryConfig.CATEGORY_MODE)));
        }
    }
}
