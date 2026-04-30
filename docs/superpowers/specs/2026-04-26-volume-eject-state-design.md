# Volume Eject State Design

## Goal

Detect Windows USB eject requests (`DBT_DEVICEQUERYREMOVE`), stop all tasks on the affected volume, and mark it as ejecting.

## Changes

### 1. Volume.java — Add `EJECTING` state

- Add `EJECTING` to `VolumeState` enum
- Add `setEjecting()` method: sets state to `EJECTING`, dispatches `VolumeStateChangedEvent`
- `updateState()` skips `EJECTING` (same as it skips `DISABLED`)
- `isAccessible()` already returns false for `EJECTING` (only true for `IDLE`)

### 2. UsbHotplugMonitor.java — Handle `DBT_DEVICEQUERYREMOVE`

- `windowProc`: handle wParam `0x8001` (`DBT_DEVICEQUERYREMOVE`)
  - Extract drive letter from `DEV_BROADCAST_VOLUME`
  - Find corresponding Volume via `DeviceManager`
  - Call `volume.setEjecting()`
  - Return `BROADCAST_QUERY_DENY` (`0x424D5144`) as `LRESULT` to block the eject
- For all other `WM_DEVICECHANGE` events, keep returning `new LRESULT(1)`

### 3. CopyTask.java — Interrupt active copy on EJECTING

- In the NIO copy loop (`while (readChannel.read(buffer) != -1)`), check `volume.isAccessible()` after each buffer read
- If not accessible, break out of the loop and return `CopyResult.FAIL`

### 4. VerifyTask.java — Check accessibility

- After the `Files.isRegularFile` check, also check `volume.isAccessible()`
- If not accessible, return `CopyResult.SKIPPED` (no point verifying a volume about to eject)

## Data Flow

```
User clicks "Eject"
  → WM_DEVICECHANGE (DBT_DEVICEQUERYREMOVE)
  → UsbHotplugMonitor.windowProc
    → volume.setEjecting()
    → return BROADCAST_QUERY_DENY (block eject)
  → Volume enters EJECTING state
    → Queued tasks: isAccessible() = false → FAIL/SKIPPED
    → Running copy tasks: NIO loop check → interrupted
  → User physically removes or system retries
  → DBT_DEVICEREMOVECOMPLETE → normal removal flow
  → Re-insert → new Volume created in IDLE state
```

## State Transitions

```
IDLE → EJECTING (on DBT_DEVICEQUERYREMOVE)
EJECTING → removed (on DBT_DEVICEREMOVECOMPLETE, Volume removed from DeviceManager)
Re-insert → new Volume in IDLE
```

EJECTING is a terminal state for a Volume instance — it never transitions back to IDLE.
