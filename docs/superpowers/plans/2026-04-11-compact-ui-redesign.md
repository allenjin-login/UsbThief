# Compact UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform UsbThief from a 1200x800 standard window to a system-tray-centric compact UI with a real-time speed curve chart.

**Architecture:** The main window shrinks to 500x400. A new `SpeedChartPanel` (custom JPanel with Graphics2D) reads speed data from the existing `CopyTask.getSpeedProbeGroup()`. The existing `SystemTrayIcon` is enhanced with dynamic icon states driven by EventBus events. `VolumeListPanel` device cards are made more compact. `StatisticsPanel` is removed from the main window (already accessible via dialog).

**Tech Stack:** Java 25, Swing, Graphics2D, FlatLaf, existing SpeedProbeGroup/SpeedProbe

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `gui/SpeedChartPanel.java` | Real-time speed curve chart (custom JPanel) |
| Create | `gui/TrayIconManager.java` | Dynamic tray icon states + enhanced popup menu |
| Modify | `gui/MainFrame.java` | Shrink to 500x400, integrate SpeedChartPanel, compact layout |
| Modify | `gui/SystemTrayIcon.java` | Delegate icon management to TrayIconManager |
| Modify | `gui/VolumeListPanel.java` | Compact VolumeCard layout |
| Modify | `gui/theme/ThemeManager.java` | Add chart color constants |
| Modify | `gui/messages_en.properties` | Add new i18n keys |
| Modify | `gui/messages_zh.properties` | Add new i18n keys |
| Modify | `gui/messages_ja.properties` | Add new i18n keys |
| Modify | `gui/messages_de.properties` | Add new i18n keys |

---

### Task 1: Create SpeedChartPanel

**Files:**
- Create: `src/com/superredrock/usbthief/gui/SpeedChartPanel.java`
- Reference: `src/com/superredrock/usbthief/worker/SpeedProbeGroup.java` (getTotalSpeed, getTotalBytes, getProbeCount)
- Reference: `src/com/superredrock/usbthief/worker/CopyTask.java:53-54` (static getSpeedProbeGroup())
- Reference: `src/com/superredrock/usbthief/gui/theme/ThemeManager.java` (color constants)

- [ ] **Step 1: Create SpeedChartPanel with data sampling**

```java
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
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.KEY_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int chartX = CHART_PADDING_LEFT;
        int chartY = CHART_PADDING_TOP;
        int chartW = w - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
        int chartH = h - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;

        // Background
        g2d.setColor(ThemeManager.isDarkTheme() ? new Color(0x11111B) : new Color(0xF8FAFC));
        g2d.fillRoundRect(chartX, chartY, chartW, chartH, 6, 6);

        // Calculate Y scale
        double maxSpeed = peakSpeed * 1.2;
        if (maxSpeed < 1.0) maxSpeed = 1.0;

        // Grid lines and Y labels
        g2d.setFont(g2d.getFont().deriveFont(9f));
        for (int i = 0; i <= NUM_GRID_LINES; i++) {
            int y = chartY + (int) (chartH * (1.0 - (double) i / NUM_GRID_LINES));
            g2d.setColor(ThemeManager.isDarkTheme() ? new Color(0x313244) : new Color(0xE2E8F0));
            g2d.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1.0f, new float[]{4f, 4f}, 0f));
            g2d.drawLine(chartX, y, chartX + chartW, y);

            double val = maxSpeed * i / NUM_GRID_LINES;
            g2d.setColor(ThemeManager.isDarkTheme() ? new Color(0x585B70) : new Color(0x94A3B8));
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

        Color curveColor = ThemeManager.ACCENT_INFO; // Blue

        // Build path
        Path2D curvePath = new Path2D.Double();
        double dx = (double) chartW / (MAX_SAMPLES - 1);

        // Start from rightmost sample, going left
        int offset = MAX_SAMPLES - samples.length;
        double[] xCoords = new double[samples.length];
        double[] yCoords = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            xCoords[i] = chartX + (offset + i) * dx;
            yCoords[i] = chartY + chartH - (samples[i] / maxSpeed) * chartH;
            yCoords[i] = Math.max(chartY, Math.min(chartY + chartH, yCoords[i]));
        }

        // Draw filled area with gradient
        curvePath.moveTo(xCoords[0], yCoords[0]);
        for (int i = 1; i < samples.length; i++) {
            // Bezier smoothing
            double prevX = xCoords[i - 1];
            double prevY = yCoords[i - 1];
            double currX = xCoords[i];
            double currY = yCoords[i];
            double ctrlX = (prevX + currX) / 2;
            curvePath.curveTo(ctrlX, prevY, ctrlX, currY, currX, currY);
        }

        // Fill gradient
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
```

- [ ] **Step 2: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/SpeedChartPanel.java
git commit -m "feat: add SpeedChartPanel for real-time speed curve display"
```

---

### Task 2: Create TrayIconManager with Dynamic Icons

**Files:**
- Create: `src/com/superredrock/usbthief/gui/TrayIconManager.java`
- Reference: `src/com/superredrock/usbthief/gui/SystemTrayIcon.java` (existing tray integration)
- Reference: `src/com/superredrock/usbthief/core/event/EventBus.java` (event dispatch)
- Reference: `src/com/superredrock/usbthief/core/event/worker/CopyCompletedEvent.java`
- Reference: `src/com/superredrock/usbthief/core/event/device/VolumeInsertedEvent.java`
- Reference: `src/com/superredrock/usbthief/core/event/device/VolumeStateChangedEvent.java`

- [ ] **Step 1: Create TrayIconManager**

```java
package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.worker.CopyTask;
import com.superredrock.usbthief.worker.TaskScheduler;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

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
    private int copyCount = 0;

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
        if (copyCount > 0) return TrayState.SCANNING;
        return TrayState.IDLE;
    }

    public void onCopyStarted() {
        copyCount++;
    }

    public void onCopyEnded() {
        copyCount = Math.max(0, copyCount - 1);
    }

    public void onError() {
        currentState = TrayState.ERROR;
        applyIcon(TrayState.ERROR);
        // Auto-recover after 5 seconds
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
    Image generateIcon(TrayState state, int size) {
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
        int overlaySize = size / 3;
        int overlayX = size - overlaySize - 1;
        int overlayY = size - overlaySize - 1;

        g2d.setColor(stateColor);
        g2d.fillOval(overlayX, overlayY, overlaySize, overlaySize);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, overlaySize - 2));
        FontMetrics fm = g2d.getFontMetrics();
        String symbol = switch (state) {
            case SCANNING -> "\u2315"; // ⌕ search
            case COPYING -> "\u2193";  // ↓ download
            case ERROR -> "!";
            case IDLE -> "";
        };
        if (!symbol.isEmpty()) {
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
            copyCount++;
            updateState();
        });

        eventBus.register(CopyCompletedEvent.class, _ -> {
            javax.swing.SwingUtilities.invokeLater(this::updateState);
        });
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/TrayIconManager.java
git commit -m "feat: add TrayIconManager with dynamic state icons"
```

---

### Task 3: Enhance SystemTrayIcon with TrayIconManager

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/SystemTrayIcon.java`

This task integrates TrayIconManager into the existing SystemTrayIcon, replacing the simple static icon with dynamic state icons, and adds speed/status info to the popup menu.

- [ ] **Step 1: Modify SystemTrayIcon to use TrayIconManager**

Key changes to `SystemTrayIcon.java`:
1. Add `TrayIconManager trayIconManager` field
2. In `initialize()`: create TrayIconManager, call `initIcons(iconSize)`, register event listeners
3. Replace `createDefaultIcon()` with `trayIconManager.generateIcon(TrayState.IDLE, size)`
4. Add a Timer (1000ms) to call `trayIconManager.updateState()` periodically
5. Add speed display MenuItem to popup menu (updated in timer)
6. In `dispose()`: stop the timer

The popup menu should become:
```
Show/Hide Window
──────────────
Speed: 12.5 MB/s
Copied: 1.2 GB (47 files)
──────────────
Pause/Start Scanning
──────────────
Exit
```

Add these fields:
```java
private TrayIconManager trayIconManager;
private MenuItem speedItem;
private MenuItem copiedItem;
private Timer stateTimer;
```

In `initialize()`, before creating the TrayIcon, add:
```java
trayIconManager = new TrayIconManager();
trayIconManager.initIcons(iconSize);
```

Replace the `createDefaultIcon()` fallback with:
```java
trayImage = trayIconManager.generateIcon(TrayIconManager.TrayState.IDLE, iconSize);
```

After `systemTray.add(trayIcon)`, add:
```java
trayIconManager.setTrayIcon(trayIcon);
trayIconManager.registerEventListeners();

// Periodic state update
stateTimer = new Timer(1000, _ -> {
    trayIconManager.updateState();
    updateDynamicMenuItems();
});
stateTimer.start();
```

Add new menu items between the separator and scan item:
```java
speedItem = new MenuItem("Speed: 0.0 MB/s");
speedItem.setEnabled(false);
popup.add(speedItem);

copiedItem = new MenuItem("Copied: 0 B (0 files)");
copiedItem.setEnabled(false);
popup.add(copiedItem);
```

Add method:
```java
private void updateDynamicMenuItems() {
    double speed = CopyTask.getSpeedProbeGroup().getTotalSpeed();
    speedItem.setLabel(String.format("Speed: %.1f MB/s", speed));

    long bytes = CopyTask.getSpeedProbeGroup().getTotalBytes();
    com.superredrock.usbthief.statistics.Statistics stats = com.superredrock.usbthief.statistics.Statistics.getInstance();
    copiedItem.setLabel(String.format("Copied: %s (%d files)",
            com.superredrock.usbthief.core.SizeFormatter.format(bytes),
            stats.getTotalFilesCopied()));
}
```

In `dispose()`, add timer stop:
```java
if (stateTimer != null) stateTimer.stop();
```

Add import for `CopyTask` and `SizeFormatter`.

- [ ] **Step 2: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/SystemTrayIcon.java
git commit -m "feat: integrate TrayIconManager into SystemTrayIcon with dynamic icons and speed display"
```

---

### Task 4: Add Chart Colors to ThemeManager

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/theme/ThemeManager.java`

- [ ] **Step 1: Add chart-specific color constants**

After the existing toast color constants (line ~59), add:

```java
// Chart colors
public static final Color CHART_CURVE = new Color(0x89B4FA);          // Blue curve
public static final Color CHART_CURVE_FILL = new Color(0x89B4FA, true); // Blue fill (with alpha)
public static final Color CHART_GRID_LIGHT = new Color(0xE2E8F0);     // Grid lines (light)
public static final Color CHART_GRID_DARK = new Color(0x313244);      // Grid lines (dark)
public static final Color CHART_BG_LIGHT = new Color(0xF8FAFC);       // Chart background (light)
public static final Color CHART_BG_DARK = new Color(0x11111B);        // Chart background (dark)
public static final Color CHART_TEXT_LIGHT = new Color(0x94A3B8);     // Chart text (light)
public static final Color CHART_TEXT_DARK = new Color(0x585B70);      // Chart text (dark)
```

- [ ] **Step 2: Update SpeedChartPanel to use ThemeManager constants**

Update `paintComponent()` in `SpeedChartPanel.java` to use the new constants:

Replace the inline color constructors:
```java
// Background
g2d.setColor(ThemeManager.isDarkTheme() ? ThemeManager.CHART_BG_DARK : ThemeManager.CHART_BG_LIGHT);

// Grid lines
g2d.setColor(ThemeManager.isDarkTheme() ? ThemeManager.CHART_GRID_DARK : ThemeManager.CHART_GRID_LIGHT);

// Labels
g2d.setColor(ThemeManager.isDarkTheme() ? ThemeManager.CHART_TEXT_DARK : ThemeManager.CHART_TEXT_LIGHT);

// Curve color
Color curveColor = ThemeManager.CHART_CURVE;
```

- [ ] **Step 3: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/gui/theme/ThemeManager.java src/com/superredrock/usbthief/gui/SpeedChartPanel.java
git commit -m "feat: add chart color constants to ThemeManager, update SpeedChartPanel"
```

---

### Task 5: Redesign MainFrame Layout

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/MainFrame.java`

This is the core layout change. MainFrame shrinks from 1200x800 to 500x400 with the new structure.

- [ ] **Step 1: Modify MainFrame constructor and layout**

Key changes to `MainFrame.java`:

1. **Size**: Change `setSize(1200, 800)` → `setSize(500, 400)`

2. **Add SpeedChartPanel field**:
```java
private final SpeedChartPanel speedChartPanel;
```

3. **Replace layout** — remove the old layout section (lines ~63-81 where volumeListPanel goes CENTER) with:

```java
speedChartPanel = new SpeedChartPanel();

// Stats bar with compact cards
JPanel statsBar = createCompactStatsBar();

// Center panel: speed chart + stats + device list
JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
centerPanel.add(speedChartPanel, BorderLayout.NORTH);
centerPanel.add(statsBar, BorderLayout.CENTER);

setLayout(new BorderLayout());
add(menuBar, BorderLayout.NORTH);
add(centerPanel, BorderLayout.CENTER);
add(statusBar, BorderLayout.SOUTH);
```

4. **Add `createCompactStatsBar()` method**:
```java
private JPanel createCompactStatsBar() {
    JPanel panel = new JPanel(new BorderLayout(0, 2));

    // Stats cards row
    JPanel statsRow = new JPanel(new GridLayout(1, 4, 4, 0));
    statsRow.setOpaque(false);

    JLabel totalLabel = new JLabel("0 B", SwingConstants.CENTER);
    JLabel filesLabel = new JLabel("0", SwingConstants.CENTER);
    JLabel queueLabel = new JLabel("0", SwingConstants.CENTER);
    JLabel loadLabel = new JLabel("LOW", SwingConstants.CENTER);

    Font statFont = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 8);

    for (var entry : new Object[][]{
            {"Total", totalLabel}, {"Files", filesLabel},
            {"Queue", queueLabel}, {"Load", loadLabel}
    }) {
        JPanel card = new JPanel(new BorderLayout(2, 0));
        card.setOpaque(false);
        JLabel lbl = new JLabel((String) entry[0], SwingConstants.CENTER);
        lbl.setFont(labelFont);
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        JLabel val = (JLabel) entry[1];
        val.setFont(statFont);
        card.add(lbl, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);
        statsRow.add(card);
    }

    // Device list (compact)
    JScrollPane deviceScroll = new JScrollPane(volumeListPanel);
    deviceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    deviceScroll.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

    panel.add(statsRow, BorderLayout.NORTH);
    panel.add(deviceScroll, BorderLayout.CENTER);

    // Timer to update stats
    Timer statsTimer = new Timer(1000, _ -> {
        com.superredrock.usbthief.statistics.Statistics stats = com.superredrock.usbthief.statistics.Statistics.getInstance();
        totalLabel.setText(com.superredrock.usbthief.core.SizeFormatter.format(stats.getTotalBytesCopied()));
        filesLabel.setText(String.valueOf(stats.getTotalFilesCopied()));
        queueLabel.setText(String.valueOf(TaskScheduler.getInstance().getQueueDepth()));

        com.superredrock.usbthief.worker.LoadScore score = com.superredrock.usbthief.worker.LoadEvaluator.getInstance().evaluateLoad();
        if (score != null) {
            loadLabel.setText(score.level().name());
            loadLabel.setForeground(switch (score.level()) {
                case LOW -> ThemeManager.ACCENT_SUCCESS;
                case MEDIUM -> ThemeManager.ACCENT_WARNING;
                case HIGH -> ThemeManager.ACCENT_ERROR;
            });
        }
    });
    statsTimer.start();

    return panel;
}
```

5. **In `performShutdown()`**, add `speedChartPanel.stop()` before `volumeListPanel.stop()`.

6. **Keep `statisticsPanel` field** but remove from main layout — it's still used by `showStatisticsWindow()`.

7. **Simplify status bar** — make it more compact:
Change the EmptyBorder padding from `new EmptyBorder(8, 12, 8, 12)` to `new EmptyBorder(4, 8, 4, 8)` and font size from 12 to 10.

- [ ] **Step 2: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/MainFrame.java
git commit -m "feat: redesign MainFrame to compact 500x400 layout with speed chart"
```

---

### Task 6: Compact VolumeListPanel Cards

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/VolumeListPanel.java`

- [ ] **Step 1: Make VolumeCard more compact**

Changes to `VolumeCard` inner class (starting around line 362):

1. Reduce max card height: `setMaximumSize(new Dimension(Integer.MAX_VALUE, 150)` → `setMaximumSize(new Dimension(Integer.MAX_VALUE, 80))`

2. Reduce padding: Change `new EmptyBorder(12, 16, 12, 16)` → `new EmptyBorder(6, 10, 6, 10)`

3. Reduce icon font: `new Font(Font.SANS_SERIF, Font.PLAIN, 32)` → `new Font(Font.SANS_SERIF, Font.PLAIN, 20)`

4. Remove the GridLayout info panel — switch to a single-line horizontal layout:
```java
// Replace the GridLayout(0, 1, 0, 4) info panel with horizontal layout
JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
infoPanel.setOpaque(false);

pathLabel = new JLabel(volume.getRootPath().toString());
pathLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

storageLabel = new JLabel(getStorageInfoCompact());
storageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
storageLabel.setForeground(ThemeManager.TEXT_SECONDARY);

stateBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
stateBadge.setOpaque(false);
stateLabel = new JLabel(getLocalizedState(volume.getState()));
stateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
stateBadge.add(stateLabel);

infoPanel.add(pathLabel);
infoPanel.add(storageLabel);
infoPanel.add(stateBadge);
```

5. Remove `volumeLabel`, `fsTypeLabel`, `activeTaskLabel` fields and their creation code (they are no longer shown in compact mode).

6. Add `getStorageInfoCompact()` method:
```java
private String getStorageInfoCompact() {
    if (volume.getFileStore() == null) return "?";
    try {
        long total = volume.getFileStore().getTotalSpace();
        return SizeFormatter.format(total);
    } catch (IOException e) {
        return "?";
    }
}
```

7. Update `refreshVolumeInfo()` to only update the compact fields.

- [ ] **Step 2: Reduce overall panel padding**

In the `VolumeListPanel` constructor, change the scrollPane border from:
```java
BorderFactory.createCompoundBorder(
    new EmptyBorder(10, 10, 10, 10),
    new TitledBorder(i18n.getMessage("device.list.border")))
```
to:
```java
BorderFactory.createEmptyBorder(2, 2, 2, 2)
```

Remove the TitledBorder for compact mode.

- [ ] **Step 3: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/gui/VolumeListPanel.java
git commit -m "feat: compact VolumeListPanel cards to single-line layout"
```

---

### Task 7: Update i18n Messages

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/messages_en.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_zh.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_ja.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_de.properties`

- [ ] **Step 1: Add new i18n keys to all locale files**

Add to `messages_en.properties`:
```properties
# Speed Chart
chart.speed.title=TRANSFER SPEED
chart.speed.live=LIVE
chart.speed.unit=MB/s
chart.stats.total=Total
chart.stats.files=Files
chart.stats.queue=Queue
chart.stats.load=Load
```

Add to `messages_zh.properties`:
```properties
# Speed Chart
chart.speed.title=\u4F20\u8F93\u901F\u5EA6
chart.speed.live=\u5B9E\u65F6
chart.speed.unit=MB/s
chart.stats.total=\u603B\u8BA1
chart.stats.files=\u6587\u4EF6
chart.stats.queue=\u961F\u5217
chart.stats.load=\u8D1F\u8F7D
```

Add to `messages_ja.properties`:
```properties
# Speed Chart
chart.speed.title=\u8EE2\u9001\u901F\u5EA6
chart.speed.live=\u30EA\u30A2\u30EB\u30BF\u30A4\u30E0
chart.speed.unit=MB/s
chart.stats.total=\u5408\u8A08
chart.stats.files=\u30D5\u30A1\u30A4\u30EB
chart.stats.queue=\u30AD\u30E5\u30FC
chart.stats.load=\u8CA0\u8377
```

Add to `messages_de.properties`:
```properties
# Speed Chart
chart.speed.title=\u00DCBERTRAGUNGSRATE
chart.speed.live=LIVE
chart.speed.unit=MB/s
chart.stats.total=Gesamt
chart.stats.files=Dateien
chart.stats.queue=Warteschlange
chart.stats.load=Last
```

- [ ] **Step 2: Verify compilation**

Run: `cd E:\IdeaProjects\UsbThief && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/messages_en.properties src/com/superredrock/usbthief/gui/messages_zh.properties src/com/superredrock/usbthief/gui/messages_ja.properties src/com/superredrock/usbthief/gui/messages_de.properties
git commit -m "feat: add i18n keys for speed chart and compact stats"
```

---

### Task 8: Final Integration Test and Polish

**Files:**
- All modified files

- [ ] **Step 1: Full compile check**

Run: `cd E:\IdeaProjects\UsbThief && mvn clean compile 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 2: Verify no unused imports or fields**

Check that the removed fields (`volumeLabel`, `fsTypeLabel`, `activeTaskLabel`) are not referenced anywhere else. If they are, update those references.

Run: `cd E:\IdeaProjects\UsbThief && grep -rn "volumeLabel\|fsTypeLabel\|activeTaskLabel" src/com/superredrock/usbthief/gui/`
Expected: No results (they were removed from VolumeCard)

- [ ] **Step 3: Run tests**

Run: `cd E:\IdeaProjects\UsbThief && mvn test 2>&1 | tail -10`
Expected: Tests pass

- [ ] **Step 4: Final commit with any fixes**

```bash
git add -A
git commit -m "fix: integration fixes for compact UI redesign"
```

---

## Self-Review Checklist

- **Spec coverage**: SpeedChartPanel (Task 1), TrayIconManager (Task 2), SystemTrayIcon enhancement (Task 3), ThemeManager colors (Task 4), MainFrame layout (Task 5), compact VolumeListPanel (Task 6), i18n (Task 7), integration (Task 8) — all spec requirements covered.
- **Placeholder scan**: No TBD/TODO/placeholders. All code blocks contain complete implementation.
- **Type consistency**: `TrayState` enum used consistently across TrayIconManager and SystemTrayIcon. `SpeedProbeGroup.getTotalSpeed()` returns `double`, consumed as `double` everywhere. `SizeFormatter.format()` used consistently.
