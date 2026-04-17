# DeviceInfoDialog + Statistics Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign DeviceInfoDialog as a Volume-centric detail view with per-volume stats, and enhance the Statistics class to support per-volume tracking.

**Architecture:** Statistics class gains a `VolumeStats` inner class keyed by volume serial. DeviceInfoDialog is rewritten from Device-centric to Volume-centric cards. StatsPanel is deleted as redundant.

**Tech Stack:** Java 25, Swing (FlatLaf), JNA, java.util.prefs.Preferences for persistence

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/com/superredrock/usbthief/statistics/Statistics.java` | Add VolumeStats, per-volume tracking |
| Rewrite | `src/com/superredrock/usbthief/gui/DeviceInfoDialog.java` | Volume-centric cards with stats |
| Delete | `src/com/superredrock/usbthief/gui/StatsPanel.java` | Redundant with StatisticsPanel |
| Modify | `src/com/superredrock/usbthief/gui/messages.properties` | New i18n keys |
| Modify | `src/com/superredrock/usbthief/gui/messages_en.properties` | English translations |
| Modify | `src/com/superredrock/usbthief/gui/messages_zh.properties` | Chinese translations |
| Modify | `src/com/superredrock/usbthief/gui/messages_ja.properties` | Japanese translations |
| Modify | `src/com/superredrock/usbthief/gui/messages_de.properties` | German translations |

---

### Task 1: Add VolumeStats to Statistics class

**Files:**
- Modify: `src/com/superredrock/usbthief/statistics/Statistics.java`

- [ ] **Step 1: Add VolumeStats inner class and per-volume map**

Add the `VolumeStats` class and the per-volume tracking map. Insert after line 33 (the `copiedDeviceSerials` field):

```java
    public static final class VolumeStats {
        private final AtomicLong filesCopied = new AtomicLong(0);
        private final AtomicLong bytesCopied = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);
        private final ConcurrentHashMap<String, AtomicLong> extensionCounts = new ConcurrentHashMap<>();
        private final long firstSeenTime;

        public VolumeStats() {
            this.firstSeenTime = System.currentTimeMillis();
        }

        public VolumeStats(long firstSeenTime) {
            this.firstSeenTime = firstSeenTime;
        }

        public long getFilesCopied() { return filesCopied.get(); }
        public long getBytesCopied() { return bytesCopied.get(); }
        public long getErrors() { return errors.get(); }
        public long getFirstSeenTime() { return firstSeenTime; }
        public Map<String, Long> getExtensionCounts() {
            var result = new java.util.LinkedHashMap<String, Long>();
            extensionCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
            return result;
        }
    }
```

Add the field after `sessionBytesCopied` (line 36):

```java
    private final ConcurrentHashMap<String, VolumeStats> volumeStatsMap = new ConcurrentHashMap<>();
```

- [ ] **Step 2: Add public accessor methods**

After `getProgressPercentage()` (around line 185), add:

```java
    public VolumeStats getVolumeStats(String serial) {
        return volumeStatsMap.computeIfAbsent(serial, _ -> new VolumeStats());
    }

    public Map<String, VolumeStats> getAllVolumeStats() {
        return new java.util.LinkedHashMap<>(volumeStatsMap);
    }
```

- [ ] **Step 3: Modify onCopyCompleted to update per-volume stats**

In `onCopyCompleted()` (line 67), after the existing logic for file copies (inside the `else` block, after `event.isSuccess()`), add per-volume tracking. After line 82 (`sessionBytesCopied.addAndGet(...)`) add:

```java
                VolumeStats vs = volumeStatsMap.computeIfAbsent(serial, _ -> new VolumeStats());
                vs.filesCopied.incrementAndGet();
                vs.bytesCopied.addAndGet(event.bytesCopied());
                String ext = getFileExtension(fileName);
                if (ext != null) {
                    vs.extensionCounts.computeIfAbsent(ext, _ -> new AtomicLong(0)).incrementAndGet();
                }
```

For failures, after line 94 (`totalErrors.incrementAndGet()`), add:

```java
                VolumeStats vs = volumeStatsMap.computeIfAbsent(serial, _ -> new VolumeStats());
                vs.errors.incrementAndGet();
```

- [ ] **Step 4: Add save/load for per-volume stats**

In `save()` method (after the existing extension save block, around line 162), add:

```java
        // Save per-volume stats
        prefs.putInt("volumeStats.count", volumeStatsMap.size());
        int idx = 0;
        for (var entry : volumeStatsMap.entrySet()) {
            String prefix = "vs." + idx + ".";
            prefs.put(prefix + "serial", entry.getKey());
            prefs.putLong(prefix + "files", entry.getValue().getFilesCopied());
            prefs.putLong(prefix + "bytes", entry.getValue().getBytesCopied());
            prefs.putLong(prefix + "errors", entry.getValue().getErrors());
            prefs.putLong(prefix + "firstSeen", entry.getValue().getFirstSeenTime());
            for (var extEntry : entry.getValue().extensionCounts.entrySet()) {
                prefs.putLong(prefix + "ext." + extEntry.getKey(), extEntry.getValue().get());
            }
            idx++;
        }
```

In `load()` method (after the existing extension load block, around line 146), add:

```java
        // Load per-volume stats
        int vsCount = prefs.getInt("volumeStats.count", 0);
        for (int i = 0; i < vsCount; i++) {
            String prefix = "vs." + i + ".";
            String serial = prefs.get(prefix + "serial", null);
            if (serial == null || serial.isEmpty()) continue;
            VolumeStats vs = new VolumeStats(prefs.getLong(prefix + "firstSeen", System.currentTimeMillis()));
            vs.filesCopied.set(prefs.getLong(prefix + "files", 0));
            vs.bytesCopied.set(prefs.getLong(prefix + "bytes", 0));
            vs.errors.set(prefs.getLong(prefix + "errors", 0));
            // Load extensions for this volume
            for (String key : prefs.keys()) {
                if (key.startsWith(prefix + "ext.")) {
                    String ext = key.substring((prefix + "ext.").length());
                    long count = prefs.getLong(key, 0);
                    if (count > 0) {
                        vs.extensionCounts.put(ext, new AtomicLong(count));
                    }
                }
            }
            volumeStatsMap.put(serial, vs);
        }
```

- [ ] **Step 5: Update resetAll to clear volume stats**

In `resetAll()` (before `resetSession()` call, around line 218), add:

```java
        volumeStatsMap.clear();
        try {
            for (String key : prefs.keys()) {
                if (key.startsWith("vs.") || key.equals("volumeStats.count")) {
                    prefs.remove(key);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to clear volume stats: " + e.getMessage());
        }
```

- [ ] **Step 6: Build and verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/com/superredrock/usbthief/statistics/Statistics.java
git commit -m "feat: add per-volume stats tracking to Statistics class"
```

---

### Task 2: Rewrite DeviceInfoDialog as Volume-centric view

**Files:**
- Rewrite: `src/com/superredrock/usbthief/gui/DeviceInfoDialog.java`

- [ ] **Step 1: Rewrite the entire DeviceInfoDialog class**

Replace the full content of `DeviceInfoDialog.java` with the Volume-centric implementation. The new design:
- Top: global overview bar (total files/size/devices/errors)
- Bottom: scrollable list of Volume cards, each showing drive letter, storage bar, FS type, connection time, VID/PID (best-effort), per-volume stats, extension table

```java
package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.Device;
import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.gui.components.EmptyStatePanel;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.statistics.Statistics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class DeviceInfoDialog extends JDialog implements I18NManager.LocaleChangeListener {

    private final I18NManager i18n = I18NManager.getInstance();
    private final DeviceManager deviceManager = DeviceManager.getInstance();
    private final Statistics stats = Statistics.getInstance();

    private final JPanel volumeCardsPanel;
    private final JPanel globalBar;
    private final JLabel globalFilesLabel;
    private final JLabel globalSizeLabel;
    private final JLabel globalErrorsLabel;
    private final JLabel globalDevicesLabel;
    private EmptyStatePanel emptyStatePanel;
    private JScrollPane scrollPane;

    private final Timer updateTimer;

    public DeviceInfoDialog(JFrame owner) {
        super(owner, i18n.getMessage("deviceinfo.title"), false);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setSize(560, 620);
        setLocationRelativeTo(owner);

        // Global overview bar
        globalFilesLabel = new JLabel("0", SwingConstants.CENTER);
        globalSizeLabel = new JLabel("0 B", SwingConstants.CENTER);
        globalErrorsLabel = new JLabel("0", SwingConstants.CENTER);
        globalDevicesLabel = new JLabel("0", SwingConstants.CENTER);

        globalBar = createGlobalBar();

        // Volume cards
        volumeCardsPanel = new JPanel();
        volumeCardsPanel.setLayout(new BoxLayout(volumeCardsPanel, BoxLayout.Y_AXIS));

        refreshVolumes();
        registerListeners();

        setLayout(new BorderLayout());
        add(globalBar, BorderLayout.NORTH);
        scrollPane = new JScrollPane(volumeCardsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        add(scrollPane, BorderLayout.CENTER);

        updateTimer = new Timer(2000, _ -> updateDisplay());
        updateTimer.start();

        i18n.addLocaleChangeListener(this);
    }

    private JPanel createGlobalBar() {
        JPanel bar = new JPanel(new GridLayout(1, 4, 2, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_COLOR),
            new EmptyBorder(6, 8, 6, 8)
        ));

        Font valFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        Font lblFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);

        for (var entry : new Object[][]{
            {i18n.getMessage("deviceinfo.global.files"), globalFilesLabel},
            {i18n.getMessage("deviceinfo.global.size"), globalSizeLabel},
            {i18n.getMessage("deviceinfo.global.errors"), globalErrorsLabel},
            {i18n.getMessage("deviceinfo.global.devices"), globalDevicesLabel}
        }) {
            JPanel cell = new JPanel(new BorderLayout(2, 0));
            cell.setOpaque(false);
            JLabel lbl = new JLabel((String) entry[0], SwingConstants.CENTER);
            lbl.setFont(lblFont);
            lbl.setForeground(ThemeManager.TEXT_MUTED);
            JLabel val = (JLabel) entry[1];
            val.setFont(valFont);
            cell.add(lbl, BorderLayout.NORTH);
            cell.add(val, BorderLayout.CENTER);
            bar.add(cell);
        }

        return bar;
    }

    private void registerListeners() {
        EventBus bus = EventBus.getInstance();
        bus.register(VolumeInsertedEvent.class, e -> SwingUtilities.invokeLater(this::refreshVolumes));
        bus.register(VolumeRemovedEvent.class, e -> SwingUtilities.invokeLater(this::refreshVolumes));
        bus.register(VolumeStateChangedEvent.class, e -> SwingUtilities.invokeLater(this::refreshVolumes));
    }

    private void refreshVolumes() {
        volumeCardsPanel.removeAll();
        Collection<Volume> volumes = deviceManager.getAllVolumes();

        if (volumes.isEmpty()) {
            emptyStatePanel = new EmptyStatePanel(
                "\uD83D\uDD0C",
                i18n.getMessage("deviceinfo.empty.title"),
                i18n.getMessage("deviceinfo.empty.description")
            );
            volumeCardsPanel.add(emptyStatePanel);
        } else {
            for (Volume volume : volumes) {
                volumeCardsPanel.add(createVolumeCard(volume));
                volumeCardsPanel.add(Box.createVerticalStrut(6));
            }
        }

        volumeCardsPanel.revalidate();
        volumeCardsPanel.repaint();
    }

    private void updateDisplay() {
        SwingUtilities.invokeLater(() -> {
            globalFilesLabel.setText(String.valueOf(stats.getTotalFilesCopied()));
            globalSizeLabel.setText(SizeFormatter.format(stats.getTotalBytesCopied()));
            globalErrorsLabel.setText(String.valueOf(stats.getTotalErrors()));
            globalDevicesLabel.setText(String.valueOf(stats.getCopiedDeviceCount()));
            refreshVolumes();
        });
    }

    private JPanel createVolumeCard(Volume volume) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        card.setBackground(ThemeManager.CARD_BACKGROUND);
        card.setOpaque(true);

        // Row 1: Drive letter + total size + state badge
        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setOpaque(false);

        String driveLabel = volume.getDriveLetter().isEmpty()
            ? volume.getRootPath().toString()
            : volume.getDriveLetter();
        String totalStr = formatTotalSpace(volume);
        JLabel driveLabelComp = new JLabel(driveLabel + "  " + totalStr);
        driveLabelComp.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        driveLabelComp.setForeground(ThemeManager.TEXT_PRIMARY);

        JPanel stateBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        stateBadge.setOpaque(true);
        stateBadge.setBackground(ThemeManager.getStateColor(volume.getState()));
        JLabel stateText = new JLabel(getLocalizedState(volume.getState()));
        stateText.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        stateText.setForeground(Color.WHITE);
        stateBadge.add(stateText);
        stateBadge.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));

        headerRow.add(driveLabelComp, BorderLayout.WEST);
        headerRow.add(stateBadge, BorderLayout.EAST);
        card.add(headerRow);

        // Row 2: Storage progress bar
        JProgressBar storageBar = createStorageBar(volume);
        if (storageBar != null) {
            JPanel barPanel = new JPanel(new BorderLayout(4, 0));
            barPanel.setOpaque(false);
            barPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
            barPanel.add(storageBar, BorderLayout.CENTER);
            JLabel storageText = new JLabel(getStorageDetail(volume));
            storageText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            storageText.setForeground(ThemeManager.TEXT_SECONDARY);
            barPanel.add(storageText, BorderLayout.EAST);
            card.add(barPanel);
        }

        // Row 3: Info grid (FS type, Serial, VID/PID, connection time)
        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 12, 2));
        infoGrid.setOpaque(false);
        infoGrid.setBorder(new EmptyBorder(6, 0, 0, 0));

        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.fs"), getFsType(volume));
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.serial"), volume.getSerialNumber());

        Device dev = deviceManager.getDeviceBySerial(volume.getSerialNumber());
        if (dev != null) {
            addInfoRow(infoGrid, "VID", dev.getVid() != null ? dev.getVid() : "-");
            addInfoRow(infoGrid, "PID", dev.getPid() != null ? dev.getPid() : "-");
        }

        Statistics.VolumeStats vs = stats.getVolumeStats(volume.getSerialNumber());
        long elapsed = System.currentTimeMillis() - vs.getFirstSeenTime();
        addInfoRow(infoGrid, i18n.getMessage("deviceinfo.card.connected"), formatDuration(elapsed));

        card.add(infoGrid);

        // Row 4: Per-volume stats (if any activity)
        if (vs.getFilesCopied() > 0 || vs.getErrors() > 0) {
            JPanel statsRow = new JPanel(new GridLayout(1, 4, 8, 0));
            statsRow.setOpaque(false);
            statsRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.BORDER_COLOR),
                new EmptyBorder(6, 0, 0, 0)
            ));

            addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.files"), String.valueOf(vs.getFilesCopied()));
            addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.bytes"), SizeFormatter.format(vs.getBytesCopied()));
            addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.errors"), String.valueOf(vs.getErrors()));

            // Extensions (top 3 inline)
            Map<String, Long> exts = vs.getExtensionCounts();
            if (!exts.isEmpty()) {
                String topExts = exts.entrySet().stream()
                    .limit(3)
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
                addStatCell(statsRow, i18n.getMessage("deviceinfo.stats.types"), topExts);
            } else {
                addStatCell(statsRow, "", "");
            }

            card.add(statsRow);
        }

        return card;
    }

    private void addInfoRow(JPanel grid, String label, String value) {
        JLabel lbl = new JLabel(label + ": ");
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        grid.add(lbl);

        JLabel val = new JLabel(value);
        val.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        val.setForeground(ThemeManager.TEXT_SECONDARY);
        grid.add(val);
    }

    private void addStatCell(JPanel panel, String label, String value) {
        JPanel cell = new JPanel(new BorderLayout(2, 0));
        cell.setOpaque(false);
        JLabel lbl = new JLabel(label, SwingConstants.CENTER);
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        cell.add(lbl, BorderLayout.NORTH);
        cell.add(val, BorderLayout.CENTER);
        panel.add(cell);
    }

    private JProgressBar createStorageBar(Volume volume) {
        if (volume.getFileStore() == null) return null;
        try {
            long total = volume.getFileStore().getTotalSpace();
            long usable = volume.getFileStore().getUsableSpace();
            long used = total - usable;
            int pct = total > 0 ? (int) (used * 100 / total) : 0;

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(pct);
            bar.setStringPainted(false);
            bar.setPreferredSize(new Dimension(200, 12));
            bar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));

            if (pct > 90) bar.setForeground(ThemeManager.ACCENT_ERROR);
            else if (pct > 75) bar.setForeground(ThemeManager.ACCENT_WARNING);
            else bar.setForeground(ThemeManager.ACCENT_SUCCESS);

            return bar;
        } catch (IOException e) {
            return null;
        }
    }

    private String getStorageDetail(Volume volume) {
        if (volume.getFileStore() == null) return i18n.getMessage("deviceinfo.card.unknown");
        try {
            long total = volume.getFileStore().getTotalSpace();
            long usable = volume.getFileStore().getUsableSpace();
            long used = total - usable;
            return SizeFormatter.format(used) + " / " + SizeFormatter.format(total);
        } catch (IOException e) {
            return i18n.getMessage("deviceinfo.card.unknown");
        }
    }

    private String formatTotalSpace(Volume volume) {
        if (volume.getFileStore() == null) return "";
        try {
            return "(" + SizeFormatter.format(volume.getFileStore().getTotalSpace()) + ")";
        } catch (IOException e) {
            return "";
        }
    }

    private String getFsType(Volume volume) {
        if (volume.getFileStore() == null) return i18n.getMessage("deviceinfo.card.unknown");
        return volume.getFileStore().type();
    }

    private String getLocalizedState(Volume.VolumeState state) {
        return switch (state) {
            case OFFLINE -> i18n.getMessage("device.state.offline");
            case UNAVAILABLE -> i18n.getMessage("device.state.unavailable");
            case IDLE -> i18n.getMessage("device.state.idle");
            case DISABLED -> i18n.getMessage("device.state.disabled");
        };
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        SwingUtilities.invokeLater(this::refreshLanguage);
    }

    public void refreshLanguage() {
        setTitle(i18n.getMessage("deviceinfo.title"));
        refreshVolumes();
    }
}
```

- [ ] **Step 2: Build and verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/DeviceInfoDialog.java
git commit -m "feat: rewrite DeviceInfoDialog as volume-centric view with per-volume stats"
```

---

### Task 3: Add i18n keys for new UI elements

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/messages.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_en.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_zh.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_ja.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_de.properties`

- [ ] **Step 1: Add new keys to messages.properties**

Replace the existing `deviceinfo.*` block (lines 470-477) with:

```properties
# DeviceInfoDialog
deviceinfo.title=USB Devices
deviceinfo.empty.title=No Devices
deviceinfo.empty.description=No USB devices have been detected yet
deviceinfo.global.files=Files
deviceinfo.global.size=Total Size
deviceinfo.global.errors=Errors
deviceinfo.global.devices=Devices
deviceinfo.card.fs=FS Type
deviceinfo.card.serial=Serial
deviceinfo.card.connected=Connected
deviceinfo.card.unknown=Unknown
deviceinfo.stats.files=Files
deviceinfo.stats.bytes=Bytes
deviceinfo.stats.errors=Errors
deviceinfo.stats.types=Top Types
```

- [ ] **Step 2: Add to messages_en.properties**

Replace the existing `deviceinfo.*` block with the same keys as Step 1 (English is identical to default).

- [ ] **Step 3: Add to messages_zh.properties**

Replace the existing `deviceinfo.*` block with:

```properties
deviceinfo.title=USB \u8bbe\u5907
deviceinfo.empty.title=\u6682\u65e0\u8bbe\u5907
deviceinfo.empty.description=\u5c1a\u672a\u68c0\u6d4b\u5230 USB \u8bbe\u5907
deviceinfo.global.files=\u6587\u4ef6
deviceinfo.global.size=\u603b\u5927\u5c0f
deviceinfo.global.errors=\u9519\u8bef
deviceinfo.global.devices=\u8bbe\u5907
deviceinfo.card.fs=\u6587\u4ef6\u7cfb\u7edf
deviceinfo.card.serial=\u5e8f\u5217\u53f7
deviceinfo.card.connected=\u5df2\u8fde\u63a5
deviceinfo.card.unknown=\u672a\u77e5
deviceinfo.stats.files=\u6587\u4ef6
deviceinfo.stats.bytes=\u5b57\u8282
deviceinfo.stats.errors=\u9519\u8bef
deviceinfo.stats.types=\u7c7b\u578b
```

- [ ] **Step 4: Add to messages_ja.properties**

Replace the existing `deviceinfo.*` block with:

```properties
deviceinfo.title=USB \u30c7\u30d0\u30a4\u30b9
deviceinfo.empty.title=\u30c7\u30d0\u30a4\u30b9\u306a\u3057
deviceinfo.empty.description=USB \u30c7\u30d0\u30a4\u30b9\u306f\u307e\u3060\u691c\u51fa\u3055\u308c\u3066\u3044\u307e\u305b\u3093
deviceinfo.global.files=\u30d5\u30a1\u30a4\u30eb
deviceinfo.global.size=\u5408\u8a08\u30b5\u30a4\u30ba
deviceinfo.global.errors=\u30a8\u30e9\u30fc
deviceinfo.global.devices=\u30c7\u30d0\u30a4\u30b9
deviceinfo.card.fs=FS\u30bf\u30a4\u30d7
deviceinfo.card.serial=\u30b7\u30ea\u30a2\u30eb
deviceinfo.card.connected=\u63a5\u7d9a\u6642\u9593
deviceinfo.card.unknown=\u4e0d\u660e
deviceinfo.stats.files=\u30d5\u30a1\u30a4\u30eb
deviceinfo.stats.bytes=\u30d0\u30a4\u30c8
deviceinfo.stats.errors=\u30a8\u30e9\u30fc
deviceinfo.stats.types=\u30bf\u30a4\u30d7
```

- [ ] **Step 5: Add to messages_de.properties**

Replace the existing `deviceinfo.*` block with:

```properties
deviceinfo.title=USB-Geraete
deviceinfo.empty.title=Keine Geraete
deviceinfo.empty.description=Es wurden noch keine USB-Geraete erkannt
deviceinfo.global.files=Dateien
deviceinfo.global.size=Groesse
deviceinfo.global.errors=Fehler
deviceinfo.global.devices=Geraete
deviceinfo.card.fs=Dateisystem
deviceinfo.card.serial=Seriennummer
deviceinfo.card.connected=Verbunden
deviceinfo.card.unknown=Unbekannt
deviceinfo.stats.files=Dateien
deviceinfo.stats.bytes=Bytes
deviceinfo.stats.errors=Fehler
deviceinfo.stats.types=Typen
```

- [ ] **Step 6: Build and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/com/superredrock/usbthief/gui/messages*.properties
git commit -m "feat: add i18n keys for volume-centric DeviceInfoDialog"
```

---

### Task 4: Delete StatsPanel

**Files:**
- Delete: `src/com/superredrock/usbthief/gui/StatsPanel.java`

- [ ] **Step 1: Verify StatsPanel is unused**

Run: `grep -r "StatsPanel" src/ --include="*.java" | grep -v "StatsPanel.java"`
Expected: No results (no Java file references it)

- [ ] **Step 2: Delete the file**

```bash
git rm src/com/superredrock/usbthief/gui/StatsPanel.java
```

- [ ] **Step 3: Build and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: remove redundant StatsPanel (superseded by StatisticsPanel)"
```

---

### Task 5: Final integration test

- [ ] **Step 1: Full build**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run tests**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 3: Final commit (if any remaining changes)**

```bash
git add -A
git commit -m "chore: finalize DeviceInfoDialog + Statistics redesign"
```
