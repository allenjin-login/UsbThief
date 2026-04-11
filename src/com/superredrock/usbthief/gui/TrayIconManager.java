package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.worker.CopyTask;
import com.superredrock.usbthief.worker.TaskScheduler;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.Timer;

/**
 * Manages dynamic tray icon states and tooltip updates.
 * Generates icons via Java 2D and switches based on app state.
 * All text is English-only (system tray encoding limitations).
 */
public class TrayIconManager {

    private static final Logger logger = Logger.getLogger(TrayIconManager.class.getName());

    public enum TrayState { IDLE, SCANNING, COPYING, ERROR }

    private final Map<TrayState, Image> iconCache = new EnumMap<>(TrayState.class);
    private TrayState currentState = TrayState.IDLE;
    private TrayIcon trayIcon;
    private String lastTooltip = "UsbThief";
    private int scanCount = 0;

    public TrayIconManager() {}

    /**
     * Pre-generate all icon variants and cache them.
     */
    public void initIcons(int size) {
        for (TrayState state : TrayState.values()) {
            iconCache.put(state, generateIcon(state, size));
        }
        logger.fine("Tray icons generated for size: " + size);
    }

    /**
     * Set the TrayIcon to manage.
     */
    public void setTrayIcon(TrayIcon icon) {
        this.trayIcon = icon;
    }

    /**
     * Update tray state based on current application state.
     */
    public void updateState() {
        TrayState newState = determineState();
        if (newState != currentState) {
            currentState = newState;
            applyIcon(currentState);
        }
        updateTooltip();
    }

    private TrayState determineState() {
        int probeCount = CopyTask.getSpeedProbeGroup().getProbeCount();
        if (probeCount > 0) return TrayState.COPYING;
        if (scanCount > 0) return TrayState.SCANNING;
        return TrayState.IDLE;
    }

    public void onScanStarted() {
        scanCount++;
    }

    public void onScanEnded() {
        scanCount = Math.max(0, scanCount - 1);
    }

    public void onError() {
        currentState = TrayState.ERROR;
        applyIcon(TrayState.ERROR);
        Timer recoverTimer = new Timer(5000, _ -> updateState());
        recoverTimer.setRepeats(false);
        recoverTimer.start();
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
        double speed = CopyTask.getSpeedProbeGroup().getTotalSpeed();
        int queue = TaskScheduler.getInstance().getQueueDepth();

        String tooltip;
        if (speed > 0) {
            tooltip = String.format("UsbThief - %.1f MB/s | %d in queue", speed, queue);
        } else if (currentState == TrayState.SCANNING) {
            tooltip = "UsbThief - Scanning...";
        } else {
            tooltip = "UsbThief - Idle";
        }

        if (!tooltip.equals(lastTooltip)) {
            trayIcon.setToolTip(tooltip);
            lastTooltip = tooltip;
        }
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
                case SCANNING -> "\u2315"; // search
                case COPYING -> "\u2193";  // download arrow
                case ERROR -> "!";
                case IDLE -> "";
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
     */
    public void registerEventListeners() {
        EventBus eventBus = EventBus.getInstance();

        eventBus.register(VolumeInsertedEvent.class, _ -> {
            scanCount++;
            javax.swing.SwingUtilities.invokeLater(this::updateState);
        });

        eventBus.register(CopyCompletedEvent.class, _ -> {
            javax.swing.SwingUtilities.invokeLater(this::updateState);
        });
    }
}
