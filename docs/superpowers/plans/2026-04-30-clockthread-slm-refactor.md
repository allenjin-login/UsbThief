# ClockThread Component + SLM Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend ClockThread into a reusable timer with CompletableFuture + lifecycle control, then refactor SnifferLifecycleManager to use it instead of tick-based cooldown polling.

**Architecture:** ClockThread replaces the polling pattern (cooldowns map + pendingRestarts set checked every 3s) with direct timer threads. Each cooldown becomes a ClockThread that fires a callback when elapsed. SLM's tick() simplifies to only volume detection and sniffer cleanup.

**Tech Stack:** Java 25, CompletableFuture, synchronized wait/notify, ConcurrentHashMap

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/com/superredrock/usbthief/core/ClockThread.java` | **Rewrite** | Timer thread with CF, pause/resume/cancel, chain API |
| `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java` | **Modify** | Replace cooldowns/pendingRestarts with ClockThread timers |

No new files created. No test files (project has no test infrastructure).

---

### Task 1: Rewrite ClockThread

**Files:**
- Rewrite: `src/com/superredrock/usbthief/core/ClockThread.java`

- [ ] **Step 1: Write the full ClockThread implementation**

Replace the entire file content with:

```java
package com.superredrock.usbthief.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClockThread extends Thread {

    private final TimeUnit unit;
    private final long initialDelay;
    private volatile long remaining;
    private volatile boolean paused = false;
    private volatile boolean done = false;
    private volatile boolean cancelled = false;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    public ClockThread(TimeUnit unit, long delay) {
        this.unit = unit;
        this.initialDelay = delay;
        this.remaining = delay;
        setDaemon(true);
        setName("ClockThread");
    }

    public ClockThread(long delayMillis) {
        this(TimeUnit.MILLISECONDS, delayMillis);
    }

    public CompletableFuture<Void> future() {
        return future;
    }

    public ClockThread thenRun(Runnable action) {
        future.thenRun(action);
        return this;
    }

    @Override
    public void run() {
        while (remaining > 0 && !cancelled) {
            synchronized (this) {
                if (paused) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        if (!cancelled) {
                            future.completeExceptionally(e);
                        }
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
            }

            try {
                unit.sleep(1);
            } catch (InterruptedException e) {
                if (!cancelled) {
                    future.completeExceptionally(e);
                }
                Thread.currentThread().interrupt();
                return;
            }

            remaining--;
        }

        if (remaining <= 0 && !cancelled) {
            done = true;
            future.complete(null);
        }
    }

    public void cancel() {
        cancelled = true;
        future.cancel(true);
        synchronized (this) {
            notifyAll();
        }
        interrupt();
    }

    public void pause() {
        synchronized (this) {
            paused = true;
        }
    }

    public void resume() {
        synchronized (this) {
            paused = false;
            notifyAll();
        }
    }

    public void restart() {
        synchronized (this) {
            remaining = initialDelay;
            paused = false;
            notifyAll();
        }
    }

    public long getRemaining(TimeUnit targetUnit) {
        return targetUnit.convert(remaining, unit);
    }

    public boolean isDone() {
        return done;
    }

    public boolean isPaused() {
        return paused;
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/core/ClockThread.java
git commit -m "feat: rewrite ClockThread with CompletableFuture, lifecycle control, and chain API"
```

---

### Task 2: Refactor SnifferLifecycleManager — add timers, remove cooldowns/pendingRestarts

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java`

- [ ] **Step 1: Replace cooldowns and pendingRestarts fields with timers map**

In `SnifferLifecycleManager.java`, find the field declarations (around lines 37-39):

```java
    /** Cooldown tracking: serial → timestamp when cooldown expires */
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    /** Volumes pending restart after cooldown */
    private final Set<String> pendingRestarts = ConcurrentHashMap.newKeySet();
```

Replace both with:

```java
    /** Active cooldown timers keyed by volume serial number */
    private final ConcurrentHashMap<String, ClockThread> timers = new ConcurrentHashMap<>();
```

- [ ] **Step 2: Add import for ClockThread**

In the import section (around line 4), add:

```java
import com.superredrock.usbthief.core.ClockThread;
```

Remove the unused imports if any (`Set` can stay — it may be used elsewhere).

- [ ] **Step 3: Update VolumeInsertedEvent handler to cancel timer**

In `registerEventListeners()`, find the `VolumeInsertedEvent` handler (around line 86):

```java
        bus.register(VolumeInsertedEvent.class, event -> {
            Volume volume = event.volume();
            pendingRestarts.remove(volume.getSerialNumber());
            logger.info("Volume inserted, scheduling sniffer for: {}", volume.getSerialNumber());
            // Don't create sniffer immediately — tick() will pick it up
        });
```

Replace with:

```java
        bus.register(VolumeInsertedEvent.class, event -> {
            Volume volume = event.volume();
            cancelTimer(volume.getSerialNumber());
            logger.info("Volume inserted, scheduling sniffer for: {}", volume.getSerialNumber());
        });
```

- [ ] **Step 4: Update VolumeRemovedEvent handler**

Find the `VolumeRemovedEvent` handler (around line 93):

```java
        bus.register(VolumeRemovedEvent.class, event -> {
            String serial = event.volume().getSerialNumber();
            logger.info("Volume removed, stopping sniffer: {}", serial);
            stop(serial);
            // Keep cooldowns and pendingRestarts to prevent immediate restart on re-insert after error
        });
```

Replace with:

```java
        bus.register(VolumeRemovedEvent.class, event -> {
            String serial = event.volume().getSerialNumber();
            logger.info("Volume removed, stopping sniffer: {}", serial);
            stop(serial);
        });
```

- [ ] **Step 5: Rewrite tick() — remove pendingRestarts polling**

Replace the entire `tick()` method (lines 131-172) with:

```java
    @Override
    protected void tick() {
        if (!initialized) {
            if (QueueManager.getDeviceManager() == null) return;
            initialized = true;
        }

        Collection<Volume> volumes = QueueManager.getDeviceManager().getAllVolumes();
        for (Volume volume : volumes) {
            String serial = volume.getSerialNumber();
            if (volume.getState() == Volume.VolumeState.IDLE &&
                !sniffers.containsKey(serial) &&
                !timers.containsKey(serial)) {
                createSniffer(volume);
            }
        }

        sniffers.entrySet().removeIf(entry -> {
            SnifferEntry se = entry.getValue();
            if (!se.sniffer.isAlive()) {
                logger.debug("Cleaned up finished sniffer for: {}", se.serialNumber);
                return true;
            }
            return false;
        });
    }
```

- [ ] **Step 6: Rewrite scheduleRestart()**

Replace the `scheduleRestart()` method (lines 232-244) with:

```java
    private void scheduleRestart(String serial, RestartReason reason) {
        long delayMs = getRestartDelayMs(reason);

        if (delayMs <= 0) {
            Volume vol = getVolumeBySerial(serial);
            if (vol != null && vol.getState() == Volume.VolumeState.IDLE && !sniffers.containsKey(serial)) {
                logger.info("No delay, restarting sniffer for: {}", serial);
                createSniffer(vol);
            }
            return;
        }

        ClockThread timer = new ClockThread(delayMs)
            .thenRun(() -> {
                timers.remove(serial);
                Volume vol = getVolumeBySerial(serial);
                if (vol != null && vol.getState() == Volume.VolumeState.IDLE && !sniffers.containsKey(serial)) {
                    logger.info("Cooldown elapsed, restarting sniffer for: {}", serial);
                    createSniffer(vol);
                } else {
                    logger.debug("Skipping restart for {}: volume not IDLE or sniffer already active", serial);
                }
            });
        timers.put(serial, timer);
        timer.start();
        logger.info("Scheduled restart for {} in {} min (reason: {})", serial, TimeUnit.MILLISECONDS.toMinutes(delayMs), reason);
    }
```

- [ ] **Step 7: Add helper methods cancelTimer() and getVolumeBySerial()**

Add these two helper methods after the `scheduleRestart` method:

```java
    private void cancelTimer(String serial) {
        ClockThread timer = timers.remove(serial);
        if (timer != null) {
            timer.cancel();
        }
    }

    private Volume getVolumeBySerial(String serial) {
        DeviceManager dm = QueueManager.getDeviceManager();
        return dm != null ? dm.getVolumeBySerial(serial) : null;
    }
```

- [ ] **Step 8: Remove isInCooldown() method**

Delete the entire `isInCooldown()` method (around lines 249-257):

```java
    private boolean isInCooldown(String serial) {
        Long endTime = cooldowns.get(serial);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            cooldowns.remove(serial);
            return false;
        }
        return true;
    }
```

- [ ] **Step 9: Update stop() to cancel timers**

Replace the `stop()` method (lines 278-284) with:

```java
    public void stop(String serialNumber) {
        cancelTimer(serialNumber);
        SnifferEntry entry = sniffers.remove(serialNumber);
        if (entry != null) {
            entry.sniffer.close();
            logger.debug("Stopped scanner for: {}", serialNumber);
        }
    }
```

- [ ] **Step 10: Update restart() to cancel timers**

Replace the `restart()` method (lines 289-295) with:

```java
    public void restart(Volume volume) {
        String serial = volume.getSerialNumber();
        cancelTimer(serial);
        SnifferEntry entry = sniffers.remove(serial);
        if (entry != null) {
            entry.sniffer.close();
        }
        createSniffer(volume);
    }
```

- [ ] **Step 11: Update pause() — no change needed but verify**

The `pause()` method should remain as-is (it calls `stop` + `scheduleRestart`), both of which are now updated. No code change needed.

- [ ] **Step 12: Update isRestartPending()**

Replace `isRestartPending()` (lines 324-326) with:

```java
    public boolean isRestartPending(String serialNumber) {
        return timers.containsKey(serialNumber);
    }
```

- [ ] **Step 13: Update getRemainingCooldownMs()**

Replace `getRemainingCooldownMs()` (lines 331-336) with:

```java
    public long getRemainingCooldownMs(String serialNumber) {
        ClockThread timer = timers.get(serialNumber);
        return timer != null ? timer.getRemaining(TimeUnit.MILLISECONDS) : 0;
    }
```

- [ ] **Step 14: Update getDebugSnapshots()**

Replace the `getDebugSnapshots()` method (lines 341-383) with:

```java
    public List<SnifferDebugSnapshot> getDebugSnapshots() {
        List<SnifferDebugSnapshot> snapshots = new ArrayList<>();

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

        for (var entry : timers.entrySet()) {
            String serial = entry.getKey();
            boolean hasActive = snapshots.stream().anyMatch(s -> s.serialNumber().equals(serial));
            if (!hasActive) {
                long remaining = entry.getValue().getRemaining(TimeUnit.MILLISECONDS);
                String reason = remaining > 0 ? "restart" : "";
                Volume vol = getVolumeBySerial(serial);
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

- [ ] **Step 15: Update cleanup()**

Replace `cleanup()` (lines 388-401) with:

```java
    @Override
    protected void cleanup() {
        for (ClockThread timer : timers.values()) {
            timer.cancel();
        }
        timers.clear();

        for (SnifferEntry entry : sniffers.values()) {
            try {
                entry.sniffer.close();
            } catch (Exception e) {
                logger.warn("Error closing sniffer for {}: {}", entry.serialNumber, e);
            }
        }
        sniffers.clear();
        logger.info("All sniffers stopped and cleaned up");
    }
```

- [ ] **Step 16: Remove unused imports**

Remove `import java.util.Set;` if it's no longer used anywhere in the file. Verify with compile.

- [ ] **Step 17: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 18: Commit**

```bash
git add src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java
git commit -m "refactor: replace SLM cooldown polling with ClockThread timers"
```
