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

    private final JLabel copiedFilesLabel;
    private final JLabel globalSpeedLabel;
    private final JLabel queueSizeLabel;
    private final JLabel activeThreadsLabel;

    private Timer updateTimer;

    public StatsPanel() {
        setLayout(new GridLayout(2, 2, 10, 10));
        setBorder(new TitledBorder("统计信息"));

        copiedFilesLabel = createStatLabel("已复制文件: 0");
        globalSpeedLabel = createStatLabel("全局速度: 0 B/s");
        queueSizeLabel = createStatLabel("任务队列: 0");
        activeThreadsLabel = createStatLabel("活动线程: 0/8");

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
            copiedFilesLabel.setText("已复制文件: " + fileCount);

            // Global speed from CopyTask SpeedProbe
            double speedMBps = CopyTask.getSpeedProbeGroup().getTotalSpeed();
            String formattedSpeed;
            if (speedMBps < 1.0) {
                formattedSpeed = String.format("%.2f KB/s", speedMBps * 1024);
            } else {
                formattedSpeed = String.format("%.2f MB/s", speedMBps);
            }
            globalSpeedLabel.setText("全局速度: " + formattedSpeed);

            // Get queue size from QueueManager
            int queueSize = TaskScheduler.getInstance().getQueueDepth();
            queueSizeLabel.setText("任务队列: " + queueSize);

            // Get active thread count
            int activeThreads = QueueManager.getActiveThreadCount();
            int maxThreads = ConfigManager.getInstance().get(ConfigSchema.MAX_POOL_SIZE);
            activeThreadsLabel.setText("活动线程: " + activeThreads + "/" + maxThreads);
        });
    }

    public void stop() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }
}
