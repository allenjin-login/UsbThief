package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.worker.CopyTask;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Real-time scrolling speed curve chart panel.
 * Samples speed from CopyTask.getSpeedProbeGroup() every 500ms,
 * displays the last 60 samples (30 seconds) as a smooth curve with gradient fill.
 */
public class SpeedChartPanel extends JPanel {

    private static final int MAX_SAMPLES = 60;
    private static final int SAMPLE_INTERVAL_MS = 500;
    private static final int CHART_PADDING_LEFT = 36;
    private static final int CHART_PADDING_RIGHT = 8;
    private static final int CHART_PADDING_TOP = 8;
    private static final int CHART_PADDING_BOTTOM = 16;
    private static final int NUM_GRID_LINES = 4;

    private final ArrayDeque<Double> speedHistory = new ArrayDeque<>(MAX_SAMPLES);
    private final Timer sampleTimer;
    private double currentSpeed = 0;
    private double peakSpeed = 0;

    public SpeedChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(0, 120));
        setMinimumSize(new Dimension(200, 100));

        sampleTimer = new Timer(SAMPLE_INTERVAL_MS, _ -> sample());
        sampleTimer.start();
    }

    private synchronized void sample() {
        currentSpeed = CopyTask.getSpeedProbeGroup().getTotalSpeed();
        speedHistory.addLast(currentSpeed);
        if (speedHistory.size() > MAX_SAMPLES) {
            speedHistory.removeFirst();
        }
        if (currentSpeed > peakSpeed) {
            peakSpeed = currentSpeed;
        }
        repaint();
    }

    public synchronized double getCurrentSpeed() {
        return currentSpeed;
    }

    public synchronized long getTotalBytes() {
        return CopyTask.getSpeedProbeGroup().getTotalBytes();
    }

    public synchronized int getProbeCount() {
        return CopyTask.getSpeedProbeGroup().getProbeCount();
    }

    public void stop() {
        if (sampleTimer != null) sampleTimer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int chartX = CHART_PADDING_LEFT;
        int chartY = CHART_PADDING_TOP;
        int chartW = w - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
        int chartH = h - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;

        // Background
        g2d.setColor(ThemeManager.getInstance().isDarkTheme() ? ThemeManager.CHART_BG_DARK : ThemeManager.CHART_BG_LIGHT);
        g2d.fillRoundRect(chartX, chartY, chartW, chartH, 6, 6);

        // Calculate Y scale
        double maxSpeed = peakSpeed * 1.2;
        if (maxSpeed < 1.0) maxSpeed = 1.0;

        // Grid lines and Y labels
        g2d.setFont(g2d.getFont().deriveFont(9f));
        for (int i = 0; i <= NUM_GRID_LINES; i++) {
            int y = chartY + (int) (chartH * (1.0 - (double) i / NUM_GRID_LINES));
            g2d.setColor(ThemeManager.getInstance().isDarkTheme() ? ThemeManager.CHART_GRID_DARK : ThemeManager.CHART_GRID_LIGHT);
            g2d.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1.0f, new float[]{4f, 4f}, 0f));
            g2d.drawLine(chartX, y, chartX + chartW, y);

            double val = maxSpeed * i / NUM_GRID_LINES;
            g2d.setColor(ThemeManager.getInstance().isDarkTheme() ? ThemeManager.CHART_TEXT_DARK : ThemeManager.CHART_TEXT_LIGHT);
            g2d.drawString(String.format(Locale.ROOT, "%.1f", val), 2, y + 3);
        }

        // Draw curve
        Double[] samples;
        synchronized (this) {
            samples = speedHistory.toArray(Double[]::new);
        }

        if (samples.length < 2) {
            g2d.dispose();
            return;
        }

        Color curveColor = ThemeManager.CHART_CURVE;

        // Build path
        double dx = (double) chartW / (MAX_SAMPLES - 1);

        int offset = MAX_SAMPLES - samples.length;
        double[] xCoords = new double[samples.length];
        double[] yCoords = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            xCoords[i] = chartX + (offset + i) * dx;
            yCoords[i] = chartY + chartH - (samples[i] / maxSpeed) * chartH;
            yCoords[i] = Math.max(chartY, Math.min(chartY + chartH, yCoords[i]));
        }

        // Draw filled area with gradient
        Path2D fillPath = new Path2D.Double();
        fillPath.moveTo(xCoords[0], chartY + chartH);
        fillPath.lineTo(xCoords[0], yCoords[0]);
        for (int i = 1; i < samples.length; i++) {
            double prevX = xCoords[i - 1];
            double prevY = yCoords[i - 1];
            double currX = xCoords[i];
            double currY = yCoords[i];
            double ctrlX = (prevX + currX) / 2;
            fillPath.curveTo(ctrlX, prevY, ctrlX, currY, currX, currY);
        }
        fillPath.lineTo(xCoords[samples.length - 1], chartY + chartH);
        fillPath.closePath();

        GradientPaint gradient = new GradientPaint(
                0, chartY, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 100),
                0, chartY + chartH, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 5));
        g2d.setPaint(gradient);
        g2d.fill(fillPath);

        // Draw curve line
        Path2D curvePath = new Path2D.Double();
        curvePath.moveTo(xCoords[0], yCoords[0]);
        for (int i = 1; i < samples.length; i++) {
            double prevX = xCoords[i - 1];
            double prevY = yCoords[i - 1];
            double currX = xCoords[i];
            double currY = yCoords[i];
            double ctrlX = (prevX + currX) / 2;
            curvePath.curveTo(ctrlX, prevY, ctrlX, currY, currX, currY);
        }
        g2d.setColor(curveColor);
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(curvePath);

        // Draw current point with glow
        double lastX = xCoords[samples.length - 1];
        double lastY = yCoords[samples.length - 1];
        g2d.setColor(new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 80));
        g2d.fillOval((int) lastX - 6, (int) lastY - 6, 12, 12);
        g2d.setColor(curveColor);
        g2d.fillOval((int) lastX - 3, (int) lastY - 3, 6, 6);

        g2d.dispose();
    }
}
