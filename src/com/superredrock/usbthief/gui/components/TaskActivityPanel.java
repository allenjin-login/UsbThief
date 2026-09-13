package com.superredrock.usbthief.gui.components;

import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;
import com.superredrock.usbthief.worker.TaskScheduler;

import javax.swing.*;
import java.awt.*;

/**
 * "Multi-thread driver's license" (design v2 / batch 1): a slim live task strip
 * showing what the copy engine is doing right now - active worker threads, queue
 * depth and current throughput. Makes the multi-threaded core *visible*: the
 * key differentiator over single-threaded USBCopyer.
 *
 * <p>Hidden entirely while idle (the "quietly fast" identity); appears only while
 * tasks are executing or queued, and disappears again when the engine rests.</p>
 */
public class TaskActivityPanel extends JPanel {

    private final I18nManager i18n = I18nManager.getInstance();
    private final JLabel activityLabel;
    private final JLabel speedLabel;
    private final Timer refreshTimer;

    /** Smoothed speeds so the strip does not flicker between samples. */
    private double smoothRead = 0;
    private double smoothWrite = 0;

    public TaskActivityPanel() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        setOpaque(true);
        setBackground(ThemeManager.getCardBackground());
        setVisible(false);

        activityLabel = new JLabel();
        activityLabel.setFont(ThemeManager.FONT_SMALL_BOLD);
        speedLabel = new JLabel();
        speedLabel.setFont(ThemeManager.FONT_SMALL);
        speedLabel.setForeground(ThemeManager.TEXT_SECONDARY);

        add(activityLabel, BorderLayout.WEST);
        add(speedLabel, BorderLayout.EAST);

        refreshTimer = new Timer(500, e -> refresh());
        refreshTimer.start();
    }

    private void refresh() {
        TaskScheduler scheduler = TaskScheduler.getInstance();
        int active = scheduler.getPool() != null ? scheduler.getPool().getActiveCount() : 0;
        int queued = scheduler.getQueueDepth();

        boolean busy = active > 0 || queued > 0;
        if (busy != isVisible()) {
            setVisible(busy);
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }
        if (!busy) {
            return;
        }

        double read = Statistics.getInstance().getSpeedCollector().getReadProbeGroup().getTotalSpeed();
        double write = Statistics.getInstance().getSpeedCollector().getWriteProbeGroup().getTotalSpeed();
        smoothRead = smoothRead * 0.6 + read * 0.4;
        smoothWrite = smoothWrite * 0.6 + write * 0.4;

        activityLabel.setText(i18n.getMessage("task.activity.threads", active, queued));
        speedLabel.setText(i18n.getMessage("task.activity.speed",
                String.format("%.1f", smoothRead), String.format("%.1f", smoothWrite)));
    }

    /** Stops the refresh timer on application shutdown. */
    public void stop() {
        refreshTimer.stop();
    }
}
