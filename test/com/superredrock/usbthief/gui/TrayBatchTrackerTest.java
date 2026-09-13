package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.SizeFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch① tray completion summary.
 *
 * <p>There is no system tray on the Linux build ("System tray is not supported"), so the two
 * pieces that actually carry the logic — the busy-period batch detector and the summary text
 * formatting — are covered here instead of through the (unavailable) {@link java.awt.TrayIcon}.</p>
 */
@Timeout(10)
class TrayBatchTrackerTest {

    @BeforeAll
    static void forceEnglish() {
        // The summary text is asserted literally, so pin the bundle before any message is built.
        I18nManager.getInstance().setLocale(Locale.ENGLISH);
    }

    @Test
    void batchOpensOnBusyTickAndClosesWhenThePoolDrains() {
        TrayIconManager.BatchTracker tracker = new TrayIconManager.BatchTracker();
        long t0 = 1_000_000_000L;

        assertNull(tracker.tick(false, t0), "an idle tick must not report a batch");
        assertFalse(tracker.hasOpenBatch());

        assertNull(tracker.tick(true, t0), "the first busy tick opens a batch");
        assertTrue(tracker.hasOpenBatch());

        tracker.recordCopy(true, 1024 * 1024, t0 + 1_000_000L);
        tracker.recordCopy(true, 3L * 1024 * 1024, t0 + 2_000_000L);

        assertNull(tracker.tick(true, t0 + 3_000_000L), "still busy: the batch stays open");

        TrayIconManager.BatchSummary summary = tracker.tick(false, t0 + 72_000_000_000L);
        assertNotNull(summary, "the falling edge closes the batch and reports it once");
        assertEquals(2, summary.files());
        assertEquals(4L * 1024 * 1024, summary.bytes());
        assertEquals(0, summary.failures());
        assertEquals(72_000L, summary.elapsedMillis());
        assertFalse(tracker.hasOpenBatch());

        assertNull(tracker.tick(false, t0 + 80_000_000_000L),
                "a closed batch must not be reported a second time");
    }

    @Test
    void copyEventOpensABatchSoShortBatchesAreNotMissed() {
        TrayIconManager.BatchTracker tracker = new TrayIconManager.BatchTracker();

        // A whole batch that begins and ends between two 1 s polls: no busy tick ever observes it.
        tracker.recordCopy(true, 512, 5_000_000L);
        assertTrue(tracker.hasOpenBatch(), "a copy event alone must open a batch");

        TrayIconManager.BatchSummary summary = tracker.tick(false, 1_005_000_000L);
        assertNotNull(summary);
        assertEquals(1, summary.files());
        assertEquals(512L, summary.bytes());
        assertEquals(1_000L, summary.elapsedMillis());
    }

    @Test
    void busyPeriodThatCopiedNothingStaysSilent() {
        TrayIconManager.BatchTracker tracker = new TrayIconManager.BatchTracker();

        tracker.tick(true, 0L);
        TrayIconManager.BatchSummary summary = tracker.tick(false, 1_000_000_000L);

        assertNotNull(summary, "the tracker still reports the closed period");
        assertEquals(0, summary.files());
        assertFalse(TrayIconManager.shouldNotify(summary),
                "a batch with nothing backed up must not raise a balloon");
    }

    @Test
    void failedCopiesAreCountedSeparatelyAndNeverAsBackedUpFiles() {
        TrayIconManager.BatchTracker tracker = new TrayIconManager.BatchTracker();

        tracker.recordCopy(true, 4_096, 0L);
        tracker.recordCopy(false, 0, 0L);
        tracker.recordCopy(false, 0, 0L);

        TrayIconManager.BatchSummary summary = tracker.tick(false, 1_000_000_000L);
        assertNotNull(summary);
        assertEquals(1, summary.files(), "only successful copies count as backed up");
        assertEquals(2, summary.failures());
        assertEquals(4_096L, summary.bytes(), "failed copies contribute no bytes");
        assertTrue(TrayIconManager.shouldNotify(summary));
    }

    @Test
    void durationIsFormattedAsMinutesAndSeconds() {
        assertEquals("0:00", TrayIconManager.formatDuration(0));
        assertEquals("0:00", TrayIconManager.formatDuration(-5), "negative durations clamp to zero");
        assertEquals("0:59", TrayIconManager.formatDuration(59_999));
        assertEquals("1:12", TrayIconManager.formatDuration(72_000));
        assertEquals("59:59", TrayIconManager.formatDuration(3_599_999));
        assertEquals("1:00:00", TrayIconManager.formatDuration(3_600_000));
        assertEquals("1:02:05", TrayIconManager.formatDuration(3_725_000));
    }

    @Test
    void averageSpeedIsBytesOverTheBatchDuration() {
        // 4 MiB in 2 s
        assertEquals(2.0, new TrayIconManager.BatchSummary(4, 4L * 1024 * 1024, 2_000L, 0).averageMbs(), 1e-9);
        // Instantaneous batch: no division by zero, just no measurable average.
        assertEquals(0.0, new TrayIconManager.BatchSummary(1, 1_024, 0L, 0).averageMbs(), 1e-9);
        assertEquals(0.0, new TrayIconManager.BatchSummary(0, 0, 5_000L, 0).averageMbs(), 1e-9);
    }

    @Test
    void summaryTextCarriesFilesSizeDurationAndSpeed() {
        // 144 MiB in 72 s -> 2.0 MB/s
        TrayIconManager.BatchSummary summary =
                new TrayIconManager.BatchSummary(4, 144L * 1024 * 1024, 72_000L, 0);

        assertEquals(
                "Backed up 4 files · " + SizeFormatter.format(144L * 1024 * 1024)
                        + " · in 1:12 · avg 2.0 MB/s",
                TrayIconManager.buildBatchSummary(summary));
    }

    @Test
    void idleTooltipResolvesToTheLocalisedIdleLabel() {
        assertEquals("UsbThief · Idle", new TrayIconManager().buildTooltip());
    }
}
