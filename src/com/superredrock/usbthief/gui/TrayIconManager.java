package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.statistics.Statistics;
import com.superredrock.usbthief.statistics.collector.SpeedCollector;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.TaskScheduler;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Manages dynamic tray icon states, the hover tooltip and the end-of-batch summary bubble.
 *
 * <h2>State wiring (batch①)</h2>
 * The tray state is derived from live engine counters, not from app-lifetime flags:
 * <ul>
 *   <li>{@link TrayState#COPYING} — the {@link TaskScheduler} pool has running tasks or queued work.</li>
 *   <li>{@link TrayState#SCANNING} — {@link SnifferLifecycleManager} reports at least one live
 *       {@code Sniffer} ({@code getActiveCount() > 0}).</li>
 *   <li>{@link TrayState#ERROR} — a {@link CopyCompletedEvent} reported {@code FAIL}; the icon is
 *       pinned for {@link #ERROR_RECOVERY_MILLIS} so the periodic refresh cannot immediately erase it.</li>
 *   <li>{@link TrayState#IDLE} — none of the above.</li>
 * </ul>
 *
 * <p>An earlier revision derived COPYING from {@code SpeedProbeGroup.getProbeCount()} and SCANNING
 * from {@code SnifferLifecycleManager.isAlive()}. Both were wrong: the write probe group keeps one
 * long-lived probe per worker thread (so the count never returns to zero once a copy has happened),
 * and {@code isAlive()} is {@link Thread#isAlive()} on the manager's own service thread — true for
 * the whole process lifetime. The icon therefore never went back to IDLE, and ERROR was never
 * wired at all.</p>
 *
 * <h2>Batch detection</h2>
 * A "batch" is one busy period of the copy pool. {@link BatchTracker} opens a batch on the rising
 * edge (idle → busy), accumulates per-file results, and closes it on the falling edge (busy →
 * idle), at which point one silent INFO balloon is shown. Batch accounting is self-tracked rather
 * than diffed from {@link Statistics} because session counters are cumulative and cannot express
 * "this batch"; the tracker also opens a batch from the first copy event, so a batch that starts
 * and finishes between two 1 s polls is still reported.
 *
 * <p>All mutable state is confined to the EDT: {@link #updateState()} is called from a Swing
 * {@link Timer} and the event-bus listeners marshal onto the EDT before touching the tracker.</p>
 */
public class TrayIconManager {

    private static final Logger logger = LogManager.getLogger(TrayIconManager.class);

    public enum TrayState { IDLE, SCANNING, COPYING, ERROR }

    /** How long the ERROR icon stays pinned before the live state takes over again. */
    static final int ERROR_RECOVERY_MILLIS = 5000;

    /** Minimum gap between two completion balloons, so batches cannot stack notifications. */
    static final long NOTIFICATION_MIN_GAP_MILLIS = 1500;

    private final Map<TrayState, Image> iconCache = new EnumMap<>(TrayState.class);
    private final BatchTracker batchTracker = new BatchTracker();

    private TrayState currentState = TrayState.IDLE;
    private TrayIcon trayIcon;
    private String lastTooltip = "";
    private Timer errorRecoveryTimer;
    private boolean errorPinned = false;
    private long lastNotificationAtMillis = Long.MIN_VALUE;

    public TrayIconManager() {}

    /**
     * Pre-generate all icon variants and cache them.
     */
    public void initIcons(int size) {
        for (TrayState state : TrayState.values()) {
            iconCache.put(state, generateIcon(state, size));
        }
        logger.debug("Tray icons generated for size: {}", size);
    }

    /**
     * Set the TrayIcon to manage.
     */
    public void setTrayIcon(TrayIcon icon) {
        this.trayIcon = icon;
    }

    /**
     * Recompute the tray state, close a finished batch and refresh the tooltip.
     * Must be called on the EDT.
     */
    public void updateState() {
        if (!errorPinned) {
            TrayState newState = determineState();
            if (newState != currentState) {
                currentState = newState;
                applyIcon(currentState);
            }
        }
        updateBatch();
        updateTooltip();
    }

    /**
     * @return the state implied by the current engine counters (ERROR is not covered here, it is
     *         a transient pin raised by {@link #onError()})
     */
    private TrayState determineState() {
        if (isCopyBusy()) return TrayState.COPYING;
        if (SnifferLifecycleManager.getInstance().getActiveCount() > 0) return TrayState.SCANNING;
        return TrayState.IDLE;
    }

    /**
     * @return true while the copy pool is working or still has tasks waiting for a thread
     */
    static boolean isCopyBusy() {
        TaskScheduler scheduler = TaskScheduler.getInstance();
        return scheduler.getPool().getActiveCount() > 0 || scheduler.getQueueDepth() > 0;
    }

    /**
     * Pin the icon to ERROR for a short while. Called when a copy reports {@code FAIL}.
     * Runs on the EDT.
     */
    public void onError() {
        errorPinned = true;
        currentState = TrayState.ERROR;
        applyIcon(TrayState.ERROR);
        updateTooltip();

        if (errorRecoveryTimer != null) {
            errorRecoveryTimer.stop();
        }
        errorRecoveryTimer = new Timer(ERROR_RECOVERY_MILLIS, _ -> {
            errorPinned = false;
            updateState();
        });
        errorRecoveryTimer.setRepeats(false);
        errorRecoveryTimer.start();
    }

    private void updateBatch() {
        BatchSummary summary = batchTracker.tick(isCopyBusy(), System.nanoTime());
        if (summary != null) {
            notifyBatchComplete(summary);
        }
    }

    /**
     * Show the "n files · size · duration · average speed" balloon for a finished batch.
     * Silent INFO message: it never steals focus and only one is shown per batch.
     */
    private void notifyBatchComplete(BatchSummary summary) {
        logger.info("Copy batch finished: {} file(s), {} bytes, {} failure(s), {} ms (avg {} MB/s)",
                summary.files(), summary.bytes(), summary.failures(),
                summary.elapsedMillis(), formatSpeed(summary.averageMbs()));

        if (!shouldNotify(summary)) {
            return;
        }
        if (trayIcon == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastNotificationAtMillis != Long.MIN_VALUE
                && now - lastNotificationAtMillis < NOTIFICATION_MIN_GAP_MILLIS) {
            logger.debug("Completion balloon suppressed (another was shown {} ms ago)",
                    now - lastNotificationAtMillis);
            return;
        }
        lastNotificationAtMillis = now;

        trayIcon.displayMessage(
                I18nManager.getInstance().getMessage("tray.batch.title"),
                buildBatchSummary(summary),
                TrayIcon.MessageType.INFO);
    }

    private void applyIcon(TrayState state) {
        if (trayIcon == null) return;
        Image icon = iconCache.get(state);
        if (icon != null) {
            trayIcon.setImage(icon);
        }
    }

    private void updateTooltip() {
        if (trayIcon == null) return;
        String tooltip = buildTooltip();
        if (!tooltip.equals(lastTooltip)) {
            trayIcon.setToolTip(tooltip);
            lastTooltip = tooltip;
        }
    }

    /**
     * Builds the persistent hover text: threads in flight plus read/write throughput while busy,
     * a plain idle label otherwise.
     */
    String buildTooltip() {
        I18nManager i18n = I18nManager.getInstance();
        if (currentState == TrayState.IDLE) {
            return i18n.getMessage("tray.tooltip.idle");
        }
        SpeedCollector collector = Statistics.getInstance().getSpeedCollector();
        return i18n.getMessage("tray.tooltip.busy",
                TaskScheduler.getInstance().getPool().getActiveCount(),
                formatSpeed(collector.getReadProbeGroup().getTotalSpeed()),
                formatSpeed(collector.getWriteProbeGroup().getTotalSpeed()));
    }

    /**
     * Decides whether a finished batch deserves a balloon.
     *
     * <p>A batch that backed up nothing (failures only, or work that was cancelled) stays silent:
     * the failure path already pins the ERROR icon, and a "0 files" balloon would be pure noise.</p>
     */
    static boolean shouldNotify(BatchSummary summary) {
        return summary.files() > 0;
    }

    /**
     * Formats a MB/s value with one decimal.
     */
    static String formatSpeed(double megabytesPerSecond) {
        return String.format("%.1f", megabytesPerSecond);
    }

    /**
     * Formats a duration as {@code mm:ss}, growing to {@code h:mm:ss} past the hour.
     */
    static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Renders the batch summary through the active locale.
     */
    static String buildBatchSummary(BatchSummary summary) {
        return I18nManager.getInstance().getMessage("tray.batch.summary",
                summary.files(),
                SizeFormatter.format(summary.bytes()),
                formatDuration(summary.elapsedMillis()),
                formatSpeed(summary.averageMbs()));
    }

    /**
     * Generate a tray icon for the given state.
     */
    public Image generateIcon(TrayState state, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color stateColor = switch (state) {
            case IDLE -> new Color(0x6C7086);      // Gray
            case SCANNING -> new Color(0xA6E3A1);   // Green
            case COPYING -> new Color(0x89B4FA);     // Blue
            case ERROR -> new Color(0xF38BA8);       // Red
        };

        // Background circle
        int pad = Math.max(1, size / 8);
        int diameter = size - pad * 2;
        g2d.setColor(stateColor);
        g2d.fillOval(pad, pad, diameter, diameter);

        // USB symbol (simplified rectangle)
        int cx = size / 2;
        int cy = size / 2;
        int rw = size / 3;
        int rh = size / 4;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(cx - rw / 2, cy - rh / 2, rw, rh);

        // Inner detail
        int iw = rw / 2;
        int ih = rh / 3;
        g2d.setColor(stateColor);
        g2d.fillRect(cx - iw / 2, cy - ih / 2, iw, ih);

        // State overlay icon (bottom-right corner)
        if (state != TrayState.IDLE) {
            int overlaySize = size / 3;
            int overlayX = size - overlaySize - 1;
            int overlayY = size - overlaySize - 1;

            g2d.setColor(stateColor);
            g2d.fillOval(overlayX, overlayY, overlaySize, overlaySize);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, overlaySize - 2));
            FontMetrics fm = g2d.getFontMetrics();
            String symbol = switch (state) {
                case SCANNING -> "⌕"; // search
                case COPYING -> "↓";  // download arrow
                case ERROR -> "!";
                default -> "";   // IDLE excluded by enclosing if (Qodana unreachable)
            };
            int sx = overlayX + (overlaySize - fm.stringWidth(symbol)) / 2;
            int sy = overlayY + fm.getAscent() + (overlaySize - fm.getHeight()) / 2;
            g2d.drawString(symbol, sx, sy);
        }

        g2d.dispose();
        return img;
    }

    /**
     * Register EventBus listeners for automatic state updates.
     *
     * <p>Only copy completion is subscribed. Scanner activity is sampled straight from
     * {@link SnifferLifecycleManager} instead of being tracked from volume insert/remove events,
     * which described device presence rather than scanning and never went back to zero while a
     * stick stayed plugged in.</p>
     */
    public void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        eventBus.register(CopyCompletedEvent.class, event -> {
            boolean success = event.isSuccess();
            boolean failure = event.isFailure();
            long bytesCopied = event.bytesCopied();
            long nowNanos = System.nanoTime();
            SwingUtilities.invokeLater(() -> {
                batchTracker.recordCopy(success, bytesCopied, nowNanos);
                if (failure) {
                    onError();
                }
                updateState();
            });
        });
    }

    /**
     * Detects one busy period of the copy pool and accumulates what it copied.
     *
     * <p>Not thread-safe by design: it is only touched from the EDT.</p>
     */
    static final class BatchTracker {

        private Batch batch;

        /**
         * Advances the busy/idle edge.
         *
         * @param busy      whether the copy pool currently has work in flight or queued
         * @param nowNanos  monotonic timestamp (see {@link System#nanoTime()})
         * @return the summary of the batch that just ended, or {@code null} when no batch ended
         */
        BatchSummary tick(boolean busy, long nowNanos) {
            if (busy) {
                if (batch == null) {
                    batch = new Batch(nowNanos);
                }
                return null;
            }
            if (batch == null) {
                return null;
            }
            Batch finished = batch;
            batch = null;
            return finished.summary(nowNanos);
        }

        /**
         * Records one finished file copy. Opens a batch if none is open, so a batch that is fully
         * contained between two {@link #tick} calls is still accounted for.
         */
        void recordCopy(boolean success, long bytesCopied, long nowNanos) {
            if (batch == null) {
                batch = new Batch(nowNanos);
            }
            if (success) {
                batch.files++;
                batch.bytes += Math.max(0L, bytesCopied);
            } else {
                batch.failures++;
            }
        }

        /** @return true while a batch is being accumulated */
        boolean hasOpenBatch() {
            return batch != null;
        }
    }

    /** Mutable accumulator for the batch currently in flight. */
    private static final class Batch {
        private final long startedNanos;
        private long files;
        private long bytes;
        private long failures;

        Batch(long startedNanos) {
            this.startedNanos = startedNanos;
        }

        BatchSummary summary(long nowNanos) {
            long elapsedNanos = Math.max(0L, nowNanos - startedNanos);
            return new BatchSummary(files, bytes, elapsedNanos / 1_000_000L, failures);
        }
    }

    /**
     * Immutable result of one finished batch.
     *
     * @param files          files copied successfully in the batch
     * @param bytes          bytes copied successfully in the batch
     * @param elapsedMillis  wall-clock duration of the batch
     * @param failures       copies that ended in {@code FAIL}
     */
    record BatchSummary(long files, long bytes, long elapsedMillis, long failures) {

        /**
         * @return average throughput over the whole batch, in MB/s (0 when the batch was
         *         instantaneous, which can happen when every copy event lands in the same tick)
         */
        double averageMbs() {
            if (elapsedMillis <= 0L || bytes <= 0L) {
                return 0.0;
            }
            return (bytes / (1024.0 * 1024.0)) / (elapsedMillis / 1000.0);
        }
    }
}
