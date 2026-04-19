# Sniffer Debug Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a separate debug dialog that shows live Sniffer state per device — phase, change counter, reset countdown, cooldown timers, watched directory count.

**Architecture:** Add minimal getters and phase tracking to Sniffer. SnifferLifecycleManager exposes a method to collect debug snapshots for all active/cooldown sniffers. A new SnifferDebugDialog polls via Swing Timer and renders one card per device.

**Tech Stack:** Java 25, Swing, FlatLaf

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/com/superredrock/usbthief/worker/SnifferPhase.java` | Create | Enum: INITIAL_SCAN, MONITORING, FINISHED |
| `src/com/superredrock/usbthief/worker/SnifferDebugSnapshot.java` | Create | Immutable record with all debug state |
| `src/com/superredrock/usbthief/worker/Sniffer.java` | Modify | Add phase field, lastResetTime, getters, snapshot method |
| `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java` | Modify | Add `getDebugSnapshots()` method |
| `src/com/superredrock/usbthief/gui/SnifferDebugDialog.java` | Create | Debug dialog with polling timer |
| `src/com/superredrock/usbthief/gui/MainFrame.java` | Modify | Add "Debug Monitor" menu item in action menu |

---

### Task 1: Create SnifferPhase enum

**Files:**
- Create: `src/com/superredrock/usbthief/worker/SnifferPhase.java`

- [ ] **Step 1: Create the enum**

```java
package com.superredrock.usbthief.worker;

public enum SnifferPhase {
    INITIAL_SCAN("Scanning"),
    MONITORING("Monitoring"),
    FINISHED("Finished");

    private final String display;

    SnifferPhase(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/com/superredrock/usbthief/worker/SnifferPhase.java
git commit -m "feat(debug): add SnifferPhase enum"
```

---

### Task 2: Create SnifferDebugSnapshot record

**Files:**
- Create: `src/com/superredrock/usbthief/worker/SnifferDebugSnapshot.java`

- [ ] **Step 1: Create the record**

```java
package com.superredrock.usbthief.worker;

public record SnifferDebugSnapshot(
    String driveLetter,
    String serialNumber,
    SnifferPhase phase,
    int changeCount,
    int threshold,
    int secondsUntilReset,
    int resetIntervalSec,
    int watchedDirCount,
    long cooldownRemainingMs,
    String cooldownReason
) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/com/superredrock/usbthief/worker/SnifferDebugSnapshot.java
git commit -m "feat(debug): add SnifferDebugSnapshot record"
```

---

### Task 3: Add phase tracking and debug getters to Sniffer

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/Sniffer.java`

Sniffer currently at line 34 has `volatile boolean running = true`. We add a `phase` field and a `lastResetTime` field, update phase at each transition point, and expose a `getDebugSnapshot()` method.

- [ ] **Step 1: Add imports and new fields**

After line 18 (`import java.util.Map;`), add:
```java
import java.time.Instant;
```

After line 34 (`private volatile boolean running = true;`), add:
```java
private volatile SnifferPhase phase = SnifferPhase.INITIAL_SCAN;
private volatile Instant lastResetTime = Instant.now();
```

- [ ] **Step 2: Update phase in run()**

In the `run()` method (line 67), after `performInitialScan();` (line 68), before the interrupt check, add phase transition:

Change:
```java
    @Override
    public void run() {
        performInitialScan();
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
```

To:
```java
    @Override
    public void run() {
        performInitialScan();
        phase = SnifferPhase.MONITORING;
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
```

- [ ] **Step 3: Set FINISHED phase in startMonitoring finally block**

In `startMonitoring()` (line 181), in the `finally` block (line 218), before `running = false;`, add:

Change:
```java
        } finally {
            running = false;
```

To:
```java
        } finally {
            phase = SnifferPhase.FINISHED;
            running = false;
```

- [ ] **Step 4: Update lastResetTime in reset thread**

In `getResetThread()` (line 228), inside the if block where `count > 0` (line 234), after `logger.debug("Reset change count: {}");`, update the timestamp:

Change:
```java
                    if (count > 0) {
                        logger.debug("Reset change count: {}", count);
                    }
```

To:
```java
                    if (count > 0) {
                        logger.debug("Reset change count: {}", count);
                    }
                    lastResetTime = Instant.now();
```

Move `lastResetTime = Instant.now();` to execute on every tick regardless of count — so we always know when the reset thread last woke up:

Change:
```java
                    int count = changeCount.getAndSet(0);
                    if (count > 0) {
                        logger.debug("Reset change count: {}", count);
                    }
```

To:
```java
                    int count = changeCount.getAndSet(0);
                    lastResetTime = Instant.now();
                    if (count > 0) {
                        logger.debug("Reset change count: {}", count);
                    }
```

- [ ] **Step 5: Add getter methods and getDebugSnapshot()**

Before the `stopMonitoring()` method (line 296), add:

```java
    public int getChangeCount() {
        return changeCount.get();
    }

    public int getWatchedDirCount() {
        return watchKeys.size();
    }

    public SnifferPhase getPhase() {
        return phase;
    }

    public Instant getLastResetTime() {
        return lastResetTime;
    }

    public SnifferDebugSnapshot getDebugSnapshot() {
        ConfigManager config = ConfigManager.getInstance();
        Instant resetTime = this.lastResetTime;
        int intervalSec = config.get(ConfigSchema.WATCH_RESET_INTERVAL_SECONDS);
        long elapsedSec = java.time.Duration.between(resetTime, Instant.now()).getSeconds();
        int untilReset = Math.max(0, intervalSec - (int) elapsedSec);

        return new SnifferDebugSnapshot(
            volume.getDriveLetter(),
            volume.getSerialNumber(),
            phase,
            changeCount.get(),
            config.get(ConfigSchema.WATCH_THRESHOLD),
            untilReset,
            intervalSec,
            watchKeys.size(),
            0L,
            ""
        );
    }
```

Note: `cooldownRemainingMs` and `cooldownReason` are set to 0/"" here because cooldown is managed by SnifferLifecycleManager, which fills these in its own method (Task 4).

- [ ] **Step 6: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/com/superredrock/usbthief/worker/Sniffer.java
git commit -m "feat(debug): add phase tracking and debug snapshot to Sniffer"
```

---

### Task 4: Add getDebugSnapshots() to SnifferLifecycleManager

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java`

- [ ] **Step 1: Add import and method**

After the existing imports (line 16), add:
```java
import java.util.stream.Collectors;
```

After the `getRemainingCooldownMs()` method (line 336), add:

```java
    /**
     * Returns debug snapshots for all tracked sniffers (active and in-cooldown).
     */
    public java.util.List<SnifferDebugSnapshot> getDebugSnapshots() {
        java.util.List<SnifferDebugSnapshot> snapshots = new java.util.ArrayList<>();

        for (SnifferEntry entry : sniffers.values()) {
            if (entry.sniffer.isAlive()) {
                SnifferDebugSnapshot raw = entry.sniffer.getDebugSnapshot();
                snapshots.add(new SnifferDebugSnapshot(
                    raw.driveLetter(),
                    raw.serialNumber(),
                    raw.phase(),
                    raw.changeCount(),
                    raw.threshold(),
                    raw.secondsUntilReset(),
                    raw.resetIntervalSec(),
                    raw.watchedDirCount(),
                    0L,
                    ""
                ));
            }
        }

        // Add entries for volumes in cooldown (no active sniffer)
        for (String serial : pendingRestarts) {
            boolean hasActive = snapshots.stream().anyMatch(s -> s.serialNumber().equals(serial));
            if (!hasActive) {
                long remaining = getRemainingCooldownMs(serial);
                String reason = "";
                if (remaining > 0) {
                    reason = "restart";
                }
                Volume vol = QueueManager.getDeviceManager() != null
                    ? QueueManager.getDeviceManager().getVolumeBySerial(serial)
                    : null;
                snapshots.add(new SnifferDebugSnapshot(
                    vol != null ? vol.getDriveLetter() : serial,
                    serial,
                    SnifferPhase.FINISHED,
                    0, 0, 0, 0, 0,
                    remaining,
                    reason
                ));
            }
        }

        return snapshots;
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java
git commit -m "feat(debug): add getDebugSnapshots() to SnifferLifecycleManager"
```

---

### Task 5: Create SnifferDebugDialog

**Files:**
- Create: `src/com/superredrock/usbthief/gui/SnifferDebugDialog.java`

- [ ] **Step 1: Create the dialog**

This is a non-modal JDialog. Each device gets a card showing: phase badge, change counter progress bar, reset countdown, cooldown timer, watched dirs count. A Swing Timer polls every 500ms.

```java
package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.worker.SnifferDebugSnapshot;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.SnifferPhase;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SnifferDebugDialog extends JDialog {

    private static final int POLL_MS = 500;

    private final Timer timer;
    private final JPanel cardsPanel;

    public SnifferDebugDialog(Frame owner) {
        super(owner, "Sniffer Debug Monitor", false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(420, 320);
        setMinimumSize(new Dimension(350, 200));
        setLocationRelativeTo(owner);

        cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(cardsPanel);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);

        timer = new Timer(POLL_MS, _ -> refresh());
        timer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
            }
        });
    }

    private void refresh() {
        List<SnifferDebugSnapshot> snapshots = SnifferLifecycleManager.getInstance().getDebugSnapshots();

        SwingUtilities.invokeLater(() -> {
            cardsPanel.removeAll();

            if (snapshots.isEmpty()) {
                JLabel empty = new JLabel("No active sniffers", SwingConstants.CENTER);
                empty.setForeground(ThemeManager.TEXT_MUTED);
                cardsPanel.add(empty);
            } else {
                for (SnifferDebugSnapshot s : snapshots) {
                    cardsPanel.add(buildCard(s));
                }
            }

            cardsPanel.revalidate();
            cardsPanel.repaint();
        });
    }

    private JPanel buildCard(SnifferDebugSnapshot s) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.BLACK, 1),
            s.driveLetter() + " (" + s.serialNumber() + ")"
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;

        // Phase badge
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel phaseLabel = new JLabel(s.phase().getDisplay());
        phaseLabel.setOpaque(true);
        phaseLabel.setFont(phaseLabel.getFont().deriveFont(Font.BOLD, 11f));
        phaseLabel.setForeground(Color.WHITE);
        phaseLabel.setBackground(phaseColor(s.phase()));
        phaseLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        card.add(phaseLabel, gbc);

        // Change counter
        if (s.phase() == SnifferPhase.MONITORING) {
            row++;
            gbc.gridwidth = 1;
            gbc.gridx = 0; gbc.gridy = row;
            JLabel counterLabel = new JLabel("Changes: " + s.changeCount() + " / " + s.threshold());
            counterLabel.setFont(counterLabel.getFont().deriveFont(10f));
            card.add(counterLabel, gbc);

            gbc.gridx = 1;
            JProgressBar counterBar = new JProgressBar(0, Math.max(1, s.threshold()));
            counterBar.setValue(Math.min(s.changeCount(), s.threshold()));
            counterBar.setStringPainted(true);
            counterBar.setPreferredSize(new Dimension(120, 16));
            card.add(counterBar, gbc);
        }

        // Reset countdown
        if (s.phase() == SnifferPhase.MONITORING && s.resetIntervalSec() > 0) {
            row++;
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            JLabel resetLabel = new JLabel("Next reset in: " + formatDuration(s.secondsUntilReset()) + " (interval: " + s.resetIntervalSec() + "s)");
            resetLabel.setFont(resetLabel.getFont().deriveFont(10f));
            card.add(resetLabel, gbc);
        }

        // Watched directories
        if (s.phase() != SnifferPhase.FINISHED || s.watchedDirCount() > 0) {
            row++;
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            JLabel dirLabel = new JLabel("Watched dirs: " + s.watchedDirCount());
            dirLabel.setFont(dirLabel.getFont().deriveFont(10f));
            card.add(dirLabel, gbc);
        }

        // Cooldown
        if (s.cooldownRemainingMs() > 0) {
            row++;
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            long sec = TimeUnit.MILLISECONDS.toSeconds(s.cooldownRemainingMs());
            JLabel cooldownLabel = new JLabel("Cooldown: " + formatDuration(sec) + " (" + s.cooldownReason() + ")");
            cooldownLabel.setFont(cooldownLabel.getFont().deriveFont(10f));
            cooldownLabel.setForeground(new Color(220, 80, 60));
            card.add(cooldownLabel, gbc);
        }

        return card;
    }

    private static Color phaseColor(SnifferPhase phase) {
        return switch (phase) {
            case INITIAL_SCAN -> new Color(52, 152, 219);   // blue
            case MONITORING -> new Color(46, 204, 113);     // green
            case FINISHED -> new Color(149, 165, 166);      // gray
        };
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min > 0) {
            return min + "m " + sec + "s";
        }
        return sec + "s";
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/SnifferDebugDialog.java
git commit -m "feat(debug): add SnifferDebugDialog with polling-based live updates"
```

---

### Task 6: Add menu item to MainFrame

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/MainFrame.java`

Add a "Debug Monitor" menu item to the action menu, after the Log Window item.

- [ ] **Step 1: Add menu item**

In `createMenus()`, after the `logWindowItem` block (around line 249), and before the second `actionMenu.addSeparator();` (line 250), add:

After:
```java
        JMenuItem logWindowItem = new JMenuItem(i18n.getMessage("menu.view.logwindow"));
        logWindowItem.addActionListener(_ -> showLogWindow());
        actionMenu.add(logWindowItem);
```

Add:
```java
        actionMenu.addSeparator();

        JMenuItem debugMonitorItem = new JMenuItem("Debug Monitor");
        debugMonitorItem.addActionListener(_ -> showDebugMonitor());
        actionMenu.add(debugMonitorItem);
```

- [ ] **Step 2: Add showDebugMonitor() method**

After the `showLogWindow()` method (around line 388), add:

```java
    private SnifferDebugDialog debugDialog;

    private void showDebugMonitor() {
        if (debugDialog == null) {
            debugDialog = new SnifferDebugDialog(this);
        }
        debugDialog.setVisible(true);
    }
```

Also add the field at the top of the class, near the other dialog fields (around line 39):

```java
    private SnifferDebugDialog debugDialog;
```

Then remove the local declaration from the method — it should just use the field.

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/gui/MainFrame.java
git commit -m "feat(debug): add Debug Monitor menu item to MainFrame"
```

---

## Self-Review

**Spec coverage:**
- SnifferPhase enum: Task 1 ✓
- SnifferDebugSnapshot record: Task 2 ✓
- Sniffer phase tracking + getters: Task 3 ✓
- SnifferLifecycleManager snapshot accessor: Task 4 ✓
- SnifferDebugDialog with polling: Task 5 ✓
- MainFrame menu integration: Task 6 ✓

**Placeholder scan:** No TBDs, TODOs, or vague steps. All code shown inline.

**Type consistency:** `SnifferDebugSnapshot` record fields match across all tasks. `SnifferPhase` enum values used consistently. `getDebugSnapshots()` returns `List<SnifferDebugSnapshot>` matching what the dialog expects.
