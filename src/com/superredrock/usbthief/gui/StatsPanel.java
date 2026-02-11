package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.worker.CopyTask;
import com.superredrock.usbthief.worker.TaskScheduler;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class StatsPanel extends JPanel {

    private final I18NManager i18n = I18NManager.getInstance();
    private final JLabel copiedFilesLabel;
    private final JLabel globalSpeedLabel;
    private final JLabel queueSizeLabel;
    private final JLabel activeThreadsLabel;

    private Timer updateTimer;

    public StatsPanel() {
        setLayout(new GridLayout(2, 2, 10, 10));
        setBorder(new TitledBorder(i18n.getMessage("stats.border")));

        copiedFilesLabel = createStatLabel(i18n.getMessage("stats.copiedFiles") + ": 0");
        globalSpeedLabel = createStatLabel(i18n.getMessage("stats.globalSpeed") + ": 0 B/s");
        queueSizeLabel = createStatLabel(i18n.getMessage("stats.queueSize") + ": 0");
        activeThreadsLabel = createStatLabel(i18n.getMessage("stats.activeThreads") + ": 0/8");

        add(copiedFilesLabel);
        add(globalSpeedLabel);
        add(queueSizeLabel);
        add(activeThreadsLabel);

        startUpdateTimer();
    }

    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, e -> updateStats());
        updateTimer.start();
    }

    private void updateStats() {
        SwingUtilities.invokeLater(() -> {
            int fileCount = QueueManager.index.getDigestSize();
            copiedFilesLabel.setText(i18n.getMessage("stats.copiedFiles") + ": " + fileCount);

            // Global speed from CopyTask SpeedProbe
            double speedMBps = CopyTask.getSpeedProbeGroup().getTotalSpeed();
            String formattedSpeed;
            if (speedMBps < 1.0) {
                formattedSpeed = String.format("%.2f KB/s", speedMBps * 1024);
            } else {
                formattedSpeed = String.format("%.2f MB/s", speedMBps);
            }
            globalSpeedLabel.setText(i18n.getMessage("stats.globalSpeed") + ": " + formattedSpeed);

            // Get queue size from QueueManager
            int queueSize = TaskScheduler.getInstance().getQueueDepth();
            queueSizeLabel.setText(i18n.getMessage("stats.queueSize") + ": " + queueSize);

            // Get active thread count
            int activeThreads = QueueManager.getActiveThreadCount();
            int maxThreads = ConfigManager.getInstance().get(ConfigSchema.MAX_POOL_SIZE);
            activeThreadsLabel.setText(i18n.getMessage("stats.activeThreads") + ": " + activeThreads + "/" + maxThreads);
        });
    }

    public void stop() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }
}
