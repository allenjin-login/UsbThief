# Sniffer Debug Panel Design

## Summary

A separate debug dialog window for monitoring Sniffer lifecycle state per device — change counter progress, reset cooldown, wait timers, and scan phase.

## Approach

Add minimal getters + a snapshot record to Sniffer. The dialog polls each active sniffer via `DeviceManager` and renders a card per device.

## Components

### 1. SnifferDebugSnapshot (record in `worker`)

Immutable point-in-time snapshot:

| Field | Type | Description |
|-------|------|-------------|
| `driveLetter` | `String` | Volume drive letter |
| `root` | `Path` | Root path being monitored |
| `phase` | `SnifferPhase` | INITIAL_SCAN / MONITORING / WAITING |
| `changeCount` | `int` | Current change counter value |
| `threshold` | `int` | Configured WATCH_THRESHOLD |
| `secondsUntilReset` | `int` | Countdown to next counter reset |
| `resetIntervalSec` | `int` | Configured reset interval |
| `watchedDirCount` | `int` | Number of watched directories |
| `waitRemainingSeconds` | `long` | Remaining cooldown if in WAITING state, else 0 |
| `waitReason` | `String` | "normal" / "error" / empty |

### 2. SnifferPhase (enum in `worker`)

```
INITIAL_SCAN → MONITORING → WAITING (on complete/error) → back to MONITORING
```

### 3. Sniffer changes

- Add `Instant lastResetTime` field, updated when reset thread resets the counter
- Add `volatile SnifferPhase phase` field, set at each transition point
- Add `getDebugSnapshot()` method that reads all state atomically
- Add getters: `getChangeCount()`, `getWatchedDirCount()`

### 4. SnifferDebugDialog (in `gui`)

- Opens from a "Debug Monitor" menu item in MainFrame's menu bar
- `JDialog`, non-modal, resizable
- One card per active sniffer device, updating via `javax.swing.Timer` at 500ms
- Per-card layout:
  - Phase badge (colored label)
  - Change counter: progress bar (current / threshold)
  - Next reset: countdown label
  - Wait cooldown: countdown label (only shown if in WAITING phase)
  - Watched dirs: count label
- Follows existing ThemeManager/FlatLaf theming

### 5. DeviceManager integration

- Add `List<Sniffer> getActiveSniffers()` or equivalent accessor
- If no such accessor exists, add a mapping from device to sniffer

## Data Flow

```
Swing Timer (500ms)
  → DeviceManager.getActiveSniffers()
  → sniffer.getDebugSnapshot()
  → UI update per card
```

## Files to Create/Modify

| File | Action |
|------|--------|
| `worker/SnifferPhase.java` | Create — enum |
| `worker/SnifferDebugSnapshot.java` | Create — record |
| `worker/Sniffer.java` | Modify — add phase tracking, getters, snapshot method |
| `gui/SnifferDebugDialog.java` | Create — debug dialog |
| `gui/MainFrame.java` | Modify — add menu item to open dialog |
| `core/DeviceManager.java` | Modify — add sniffer accessor if needed |
