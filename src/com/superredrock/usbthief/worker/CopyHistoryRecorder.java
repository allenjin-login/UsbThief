package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bounded in-memory log of completed copy operations, feeding the "Export Report" feature.
 *
 * <p>Nothing in the application kept a per-file copy history before this: the statistics
 * collectors aggregate counters (files, bytes, per-volume totals) but drop the individual
 * records, and {@code FileHistoryPanel} only retained <em>failed</em> copies for display. This
 * recorder subscribes to {@link CopyCompletedEvent} - the single point where successes,
 * failures, cancellations and skips are all reported - and keeps the most recent
 * {@value #DEFAULT_CAPACITY} entries in a ring buffer.</p>
 *
 * <p>The buffer is bounded on purpose: a long session copies hundreds of thousands of files and
 * an unbounded list would be a leak. When the buffer is full the oldest entry is dropped, so the
 * report always describes the most recent window of activity. History is process-local and is
 * not persisted across restarts, consistent with the rest of the in-memory statistics.</p>
 *
 * <p>Thread safety: {@code CopyCompletedEvent} is dispatched from copy worker threads while the
 * UI reads the snapshot on the EDT, so the deque is guarded by its own monitor.</p>
 */
public final class CopyHistoryRecorder {

    /** Number of records retained by the application-wide instance. */
    public static final int DEFAULT_CAPACITY = 1000;

    private static final Logger logger = LogManager.getLogger(CopyHistoryRecorder.class);

    private static volatile CopyHistoryRecorder instance;

    private final int capacity;
    private final Deque<CopyHistoryEntry> entries = new ArrayDeque<>();
    private final Object lock = new Object();

    /** Creates a recorder with the default capacity and subscribes it to the event bus. */
    public CopyHistoryRecorder() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a recorder that keeps at most {@code capacity} entries and subscribes it to
     * {@link CopyCompletedEvent} on the {@link EventBus}.
     *
     * @param capacity maximum number of retained records, must be positive
     * @throws IllegalArgumentException if {@code capacity} is not positive
     */
    public CopyHistoryRecorder(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
    }

    /**
     * @return the application-wide recorder, created on first use
     */
    public static CopyHistoryRecorder getInstance() {
        if (instance == null) {
            synchronized (CopyHistoryRecorder.class) {
                if (instance == null) {
                    instance = new CopyHistoryRecorder();
                }
            }
        }
        return instance;
    }

    /**
     * Records one completion event. Called from the event bus; the entry is built here so the
     * volume label is resolved while the volume is still attached.
     *
     * @param event the completion event to record, ignored when {@code null}
     */
    public void onCopyCompleted(CopyCompletedEvent event) {
        if (event == null) {
            return;
        }
        Path destination = event.destinationPath();
        record(new CopyHistoryEntry(
                event.timestamp(),
                resolveVolumeLabel(event.deviceSerial()),
                fileName(event.sourcePath()),
                destination != null ? destination.toString() : "",
                event.fileSize(),
                event.durationMillis(),
                event.result()));
    }

    /**
     * Appends a record, evicting the oldest entry once the buffer is full.
     *
     * @param entry the entry to append, ignored when {@code null}
     */
    public void record(CopyHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (lock) {
            while (entries.size() >= capacity) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
    }

    /**
     * @return an immutable snapshot of the retained records, oldest first
     */
    public List<CopyHistoryEntry> snapshot() {
        synchronized (lock) {
            return List.copyOf(new ArrayList<>(entries));
        }
    }

    /** @return the number of retained records */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** @return the maximum number of records this recorder retains */
    public int capacity() {
        return capacity;
    }

    /** Drops every retained record. */
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
    }

    private static String fileName(Path path) {
        if (path == null) {
            return "";
        }
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    /**
     * Resolves the operator-facing label of a source volume.
     *
     * <p>The volume label is preferred, then the drive letter, then the serial number itself, so
     * the report always identifies <em>something</em> about the device even after the volume was
     * ejected. Resolution is best-effort: a device lookup failure must never lose a copy record.
     */
    private static String resolveVolumeLabel(String serial) {
        if (serial == null || serial.isEmpty()) {
            return "";
        }
        try {
            Volume volume = DeviceManager.getInstance().getVolumeBySerial(serial);
            if (volume != null) {
                String name = volume.getVolumeName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
                String driveLetter = volume.getDriveLetter();
                if (driveLetter != null && !driveLetter.isBlank()) {
                    return driveLetter;
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Could not resolve volume label for serial {}: {}", serial, e.toString());
        }
        return serial;
    }
}
