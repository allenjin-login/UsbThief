# Cooldown Preservation on Device Eject

## Background

Currently, when a USB device is ejected or removed, `SnifferLifecycleManager.stop()` cancels both the active Sniffer and any cooldown ClockThread. This means cooldown state is lost on eject.

The user wants cooldown timers to survive device ejection so that:
- If the cooldown expires and the device hasn't been reinserted, the entry is auto-destroyed
- If the device is reinserted before the cooldown expires, the original timer finishes before a new Sniffer starts

## Behavior

### Eject Flow (NEW)

1. Volume goes EJECTING → stop Sniffer, start cooldown timer if none exists
2. Volume physically removed (OFFLINE / VolumeRemovedEvent) → stop Sniffer only, keep timer
3. Timer fires:
   - Volume is back (IDLE) → start new Sniffer
   - Volume not found → clean up entry from `timers`
4. Volume reinserted during cooldown → VolumeInsertedEvent does NOT cancel timer → tick loop sees timer, skips Sniffer creation → timer fires → Sniffer starts

### Behavior Matrix

| Event | Current Behavior | New Behavior |
|-------|-----------------|--------------|
| EJECTING | `stop()` cancels timer + stops Sniffer | Stop Sniffer only; start cooldown timer if none exists |
| OFFLINE / VolumeRemovedEvent | `stop()` cancels timer + stops Sniffer | Stop Sniffer only; timer preserved |
| VolumeInsertedEvent | `cancelTimer()` → tick creates Sniffer | Don't touch timer → tick sees timer, skips → timer fires → Sniffer starts |
| DISABLED | `stop()` cancels everything | Same (user action, cancel timer) |
| Timer fires, volume exists | Create Sniffer | Same |
| Timer fires, volume gone | "skip restart" log | Same; entry removed from `timers` |

## Changes to SnifferLifecycleManager

### 1. New method: `stopSnifferOnly(String serial)`

Extracts the Sniffer-removal logic from `stop()` without touching timers:

```java
private void stopSnifferOnly(String serial) {
    SnifferEntry entry = sniffers.remove(serial);
    if (entry != null) {
        entry.sniffer.close();
        logger.debug("Stopped sniffer for: {}", serial);
    }
}
```

### 2. EJECTING handler change

In `registerEventListeners()`, `VolumeStateChangedEvent` case EJECTING:

```java
case EJECTING -> {
    logger.debug("Volume EJECTING, stopping sniffer: {}", serial);
    stopSnifferOnly(serial);
    if (!timers.containsKey(serial)) {
        scheduleRestart(serial, RestartReason.NORMAL_COMPLETION);
    }
}
```

### 3. OFFLINE handler change

In `registerEventListeners()`, `VolumeStateChangedEvent` case OFFLINE:

```java
case OFFLINE -> {
    logger.debug("Volume OFFLINE, stopping sniffer: {}", serial);
    stopSnifferOnly(serial);
}
```

### 4. VolumeRemovedEvent handler change

In `registerEventListeners()`:

```java
bus.register(VolumeRemovedEvent.class, event -> {
    String serial = event.volume().getSerialNumber();
    logger.info("Volume removed, stopping sniffer: {}", serial);
    stopSnifferOnly(serial);
});
```

### 5. VolumeInsertedEvent handler change

Remove the `cancelTimer()` call:

```java
bus.register(VolumeInsertedEvent.class, event -> {
    logger.info("Volume inserted: {}", event.volume().getSerialNumber());
});
```

The tick loop already checks `!timers.containsKey(serial)` before creating a Sniffer, so if a cooldown is in progress it will skip Sniffer creation. When the timer fires and finds the volume is IDLE, it creates the Sniffer.

### 6. Timer callback — no change needed

Existing callback already works correctly:

```java
ClockThread timer = new ClockThread(delayMs)
    .thenRun(() -> {
        timers.remove(serial);
        Volume vol = getVolumeBySerial(serial);
        if (vol != null && vol.getState() == Volume.VolumeState.IDLE && !sniffers.containsKey(serial)) {
            createSniffer(vol);
        }
    });
```

### 7. Existing `stop()` method — no change needed

Kept for DISABLED case and public API. Still cancels timer + stops Sniffer.

### 8. getDebugSnapshots() — no change needed

Timer entries are already rendered with cooldown data. When timer fires and volume is gone, the entry disappears from `timers` → `getDebugSnapshots()` stops returning it → dashboard cleans up on next refresh.

## Files Modified

- `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java` — all changes in this single file

## Edge Cases

- **DISABLED volume**: Still uses `stop()` which cancels timer (deliberate user action)
- **App shutdown with preserved timers**: `cleanup()` cancels all timers (existing behavior)
- **Rapid eject-removal-reinsert**: EJECTING sets timer → OFFLINE preserves it → reinsert doesn't cancel → tick skips → timer fires → Sniffer starts
- **Sniffer was running (not FINISHED) when ejected**: `scheduleRestart()` is called with NORMAL_COMPLETION, creating a fresh cooldown timer as requested

## Dashboard Impact

No changes needed to `SnifferDebugDialog`. The ejected cache from the previous session and `getDebugSnapshots()` timer entries already handle the display correctly:
- Ejected cards with preserved cooldown show the cooldown countdown
- When timer expires and volume is gone, the entry vanishes from snapshots → dashboard removes the card
