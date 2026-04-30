# Cooldown Preservation on Device Eject — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve cooldown timers when USB devices are ejected, so reinserted devices wait for the original timer to finish before starting a new Sniffer.

**Architecture:** Modify `SnifferLifecycleManager` event handlers to stop the Sniffer without cancelling its cooldown ClockThread on eject/remove. A new `stopSnifferOnly()` method separates Sniffer cleanup from timer cancellation. The existing timer callback already handles both the "volume back" and "volume gone" cases correctly.

**Tech Stack:** Java 25, Swing, existing ClockThread timer infrastructure

---

### Task 1: Add `stopSnifferOnly()` method

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java`

- [ ] **Step 1: Add the new private method**

Insert `stopSnifferOnly()` immediately before the existing `stop()` method (after `getRestartDelayMs()`, around line 265):

```java
/**
 * Stops the Sniffer for a volume without cancelling any active cooldown timer.
 */
private void stopSnifferOnly(String serial) {
    SnifferEntry entry = sniffers.remove(serial);
    if (entry != null) {
        entry.sniffer.close();
        logger.debug("Stopped sniffer for: {}", serial);
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java
git commit -m "feat: add stopSnifferOnly() method for cooldown preservation"
```

---

### Task 2: Modify event handlers to preserve cooldown on eject/remove

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java` (lines 81–123 in `registerEventListeners()`)

- [ ] **Step 1: Change VolumeInsertedEvent handler**

Replace lines 84–88:

```java
bus.register(VolumeInsertedEvent.class, event -> {
    Volume volume = event.volume();
    cancelTimer(volume.getSerialNumber());
    logger.info("Volume inserted, scheduling sniffer for: {}", volume.getSerialNumber());
});
```

With:

```java
bus.register(VolumeInsertedEvent.class, event -> {
    logger.info("Volume inserted: {}", event.volume().getSerialNumber());
});
```

The tick loop (line 137) already checks `!timers.containsKey(serial)` before creating a Sniffer, so preserved cooldowns will block immediate Sniffer creation.

- [ ] **Step 2: Change VolumeRemovedEvent handler**

Replace lines 90–94:

```java
bus.register(VolumeRemovedEvent.class, event -> {
    String serial = event.volume().getSerialNumber();
    logger.info("Volume removed, stopping sniffer: {}", serial);
    stop(serial);
});
```

With:

```java
bus.register(VolumeRemovedEvent.class, event -> {
    String serial = event.volume().getSerialNumber();
    logger.info("Volume removed, stopping sniffer: {}", serial);
    stopSnifferOnly(serial);
});
```

- [ ] **Step 3: Change EJECTING case in VolumeStateChangedEvent handler**

Replace lines 110–113:

```java
case EJECTING -> {
    logger.debug("Volume EJECTING, stopping sniffer: {}", serial);
    stop(serial);
}
```

With:

```java
case EJECTING -> {
    logger.debug("Volume EJECTING, stopping sniffer: {}", serial);
    stopSnifferOnly(serial);
    if (!timers.containsKey(serial)) {
        scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
    }
}
```

This stops the Sniffer and starts a cooldown timer if one doesn't already exist (e.g., the Sniffer was still in INITIAL_SCAN/MONITORING when ejected).

- [ ] **Step 4: Change OFFLINE case in VolumeStateChangedEvent handler**

Replace lines 102–105:

```java
case OFFLINE -> {
    logger.debug("Volume OFFLINE, stopping sniffer: {}", serial);
    stop(serial);
}
```

With:

```java
case OFFLINE -> {
    logger.debug("Volume OFFLINE, stopping sniffer: {}", serial);
    stopSnifferOnly(serial);
}
```

- [ ] **Step 5: Compile to verify all changes**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java
git commit -m "feat: preserve cooldown timers on device eject/remove

- EJECTING: stop sniffer, start cooldown if none exists
- OFFLINE/VolumeRemovedEvent: stop sniffer only, keep timer
- VolumeInsertedEvent: don't cancel timer, let tick loop handle
- DISABLED: unchanged (still uses stop() to cancel everything)"
```

---

### Task 3: Verify no regressions in preserved code paths

**Files:**
- Read-only: `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java`

- [ ] **Step 1: Verify DISABLED handler still calls `stop()`**

Confirm that the `case DISABLED` block (around line 107) still calls `stop(serial)` — it should NOT have been changed to `stopSnifferOnly()`. This ensures manual disable still cancels everything.

- [ ] **Step 2: Verify timer callback is untouched**

Confirm that `scheduleRestart()` method (around line 212) still has the original timer callback:

```java
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
```

- [ ] **Step 3: Verify tick loop condition is unchanged**

Confirm that `tick()` (around line 128) still checks `!timers.containsKey(serial)`:

```java
if (volume.getState() == Volume.VolumeState.IDLE &&
    !sniffers.containsKey(serial) &&
    !timers.containsKey(serial)) {
    createSniffer(volume);
}
```

- [ ] **Step 4: Final compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS
