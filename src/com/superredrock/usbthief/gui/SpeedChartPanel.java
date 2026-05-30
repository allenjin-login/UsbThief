package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Real-time scrolling speed curve chart panel with read and write curves.
 * Samples speed from Statistics.getSpeedCollector() every 500ms,
 * displays the last 60 samples (30 seconds) as smooth curves with gradient fill.
 */
public class SpeedChartPanel extends JPanel {

    private static final int MAX_SAMPLES = 60;
    private static final int SAMPLE_INTERVAL_MS = 500;
    private static final int CHART_PADDING_LEFT = 36;
    private static final int CHART_PADDING_RIGHT = 8;
    private static final int CHART_PADDING_TOP = 8;
    private static final int CHART_PADDING_BOTTOM = 16;
    private static final int NUM_GRID_LINES = 4;

    private final ArrayDeque<Double> readHistory = new ArrayDeque<>(MAX_SAMPLES);
    private final ArrayDeque<Double> writeHistory = new ArrayDeque<>(MAX_SAMPLES);
    private final Timer sampleTimer;
    private double currentReadSpeed = 0;
    private double currentWriteSpeed = 0;
    private double peakSpeed = 0;

    public SpeedChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(0, 120));
        setMinimumSize(new Dimension(200, 100));

        sampleTimer = new Timer(SAMPLE_INTERVAL_MS, _ -> sample());
        sampleTimer.start();
    }

    private synchronized void sample() {
        var collector = Statistics.getInstance().getSpeedCollector();
        currentReadSpeed = collector.getReadProbeGroup().getTotalSpeed();
        currentWriteSpeed = collector.getWriteProbeGroup().getTotalSpeed();

        readHistory.addLast(currentReadSpeed);
        writeHistory.addLast(currentWriteSpeed);
        if (readHistory.size() > MAX_SAMPLES) readHistory.removeFirst();
        if (writeHistory.size() > MAX_SAMPLES) writeHistory.removeFirst();

        double maxSample = Math.max(currentReadSpeed, currentWriteSpeed);
        if (maxSample > peakSpeed) peakSpeed = maxSample;
        repaint();
    }

    public synchronized double getCurrentReadSpeed() { return currentReadSpeed; }
    public synchronized double getCurrentWriteSpeed() { return currentWriteSpeed; }

    public synchronized long getTotalBytes() {
        return Statistics.getInstance().getSpeedCollector().getWriteProbeGroup().getTotalBytes();
    }

    public synchronized int getProbeCount() {
        return Statistics.getInstance().getSpeedCollector().getWriteProbeGroup().getProbeCount();
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

        g2d.setColor(ThemeManager.getInstance().isDarkTheme() ? ThemeManager.CHART_BG_DARK : ThemeManager.CHART_BG_LIGHT);
        g2d.fillRoundRect(chartX, chartY, chartW, chartH, 6, 6);

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

        Double[] readSamples, writeSamples;
        synchronized (this) {
            readSamples = readHistory.toArray(Double[]::new);
            writeSamples = writeHistory.toArray(Double[]::new);
        }

        if (readSamples.length < 2) {
            g2d.dispose();
            return;
        }

        double dx = (double) chartW / (MAX_SAMPLES - 1);
        int offset = MAX_SAMPLES - readSamples.length;

        // Draw read curve (green, behind write)
        drawCurve(g2d, readSamples, offset, dx, chartX, chartY, chartW, chartH, maxSpeed, ThemeManager.CHART_CURVE_READ);

        // Draw write curve (blue, in front)
        drawCurve(g2d, writeSamples, offset, dx, chartX, chartY, chartW, chartH, maxSpeed, ThemeManager.CHART_CURVE);

        g2d.dispose();
    }

    private void drawCurve(Graphics2D g2d, Double[] samples, int offset, double dx,
                           int chartX, int chartY, int chartW, int chartH, double maxSpeed, Color curveColor) {
        double[] xCoords = new double[samples.length];
        double[] yCoords = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            xCoords[i] = chartX + (offset + i) * dx;
            yCoords[i] = chartY + chartH - (samples[i] / maxSpeed) * chartH;
            yCoords[i] = Math.max(chartY, Math.min(chartY + chartH, yCoords[i]));
        }

        // Gradient fill
        Path2D fillPath = new Path2D.Double();
        fillPath.moveTo(xCoords[0], chartY + chartH);
        fillPath.lineTo(xCoords[0], yCoords[0]);
        for (int i = 1; i < samples.length; i++) {
            double ctrlX = (xCoords[i - 1] + xCoords[i]) / 2;
            fillPath.curveTo(ctrlX, yCoords[i - 1], ctrlX, yCoords[i], xCoords[i], yCoords[i]);
        }
        fillPath.lineTo(xCoords[samples.length - 1], chartY + chartH);
        fillPath.closePath();

        GradientPaint gradient = new GradientPaint(
                0, chartY, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 80),
                0, chartY + chartH, new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 5));
        g2d.setPaint(gradient);
        g2d.fill(fillPath);

        // Curve line
        Path2D curvePath = new Path2D.Double();
        curvePath.moveTo(xCoords[0], yCoords[0]);
        for (int i = 1; i < samples.length; i++) {
            double ctrlX = (xCoords[i - 1] + xCoords[i]) / 2;
            curvePath.curveTo(ctrlX, yCoords[i - 1], ctrlX, yCoords[i], xCoords[i], yCoords[i]);
        }
        g2d.setColor(curveColor);
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(curvePath);

        // Current point with glow
        double lastX = xCoords[samples.length - 1];
        double lastY = yCoords[samples.length - 1];
        g2d.setColor(new Color(curveColor.getRed(), curveColor.getGreen(), curveColor.getBlue(), 80));
        g2d.fillOval((int) lastX - 6, (int) lastY - 6, 12, 12);
        g2d.setColor(curveColor);
        g2d.fillOval((int) lastX - 3, (int) lastY - 3, 6, 6);
    }
}
