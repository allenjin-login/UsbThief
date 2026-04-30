# Global Debug Dashboard Design

## Background

Replace the current `SnifferDebugDialog` (420x320 fixed, single-purpose) with a full-featured global Debug Dashboard. The new dialog provides a tabbed interface with 4 views: Sniffer status, Service status, Event stream, and Thread info. Uses the card-based dashboard visual style with the existing ThemeManager color palette.

## Window

- **Type**: Non-modal `JDialog` (does not block MainFrame)
- **Title**: "UsbThief Debug"
- **Size**: 640x480, resizable
- **Location**: Relative to owner (MainFrame)
- **Close**: DISPOSE_ON_CLOSE
- **File**: Replace `src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java`

## Tab Bar

Horizontal tab strip at the top using pill-shaped buttons:
- **Sniffers** (default active)
- **Services**
- **Events**
- **Threads**

Active tab: `ThemeManager.ACCENT_PRIMARY` background, white text.
Inactive tab: white background, `TEXT_MUTED` text, thin border.
Single listener switches content panels.

## Tab 1: Sniffers

Data source: `SnifferLifecycleManager.getInstance().getDebugSnapshots()`

Each `SnifferDebugSnapshot` renders as a card:

**Card layout:**
- Header row: drive letter icon (32x32 rounded square, `ACCENT_PRIMARY` bg) + volume name + serial number (muted) | status badge (right-aligned pill)
- Stats row (below header): 2-3 stat items in a horizontal flex layout

**Status badges:**
- `INITIAL_SCAN`: blue pill ("Scanning"), `ACCENT_INFO` bg
- `MONITORING`: green pill ("Monitoring"), `ACCENT_SUCCESS` bg
- `FINISHED` + cooldown > 0: red pill ("Cooldown"), `ACCENT_ERROR` bg
- `FINISHED` + no cooldown: gray pill ("Finished"), `TEXT_MUTED` bg

**Stats shown by phase:**
- MONITORING: Changes (progress bar), Reset In, Watched Dirs
- FINISHED + cooldown: Remaining time, Reason
- INITIAL_SCAN: Watched Dirs only

**Cooldown remaining format:** `Xm Ys` (same as current `formatDuration`)

**Empty state:** centered text "No active sniffers" in `TEXT_MUTED`

Cards scroll vertically, use `BoxLayout.Y_AXIS` with 10px gaps.

## Tab 2: Services

Data source: iterate known Service singletons via `QueueManager` and direct `getInstance()` calls.

Known services (all extend `Service`):
- `DeviceManager`
- `SnifferLifecycleManager`
- `TaskScheduler`
- `Index`
- `StorageController`
- `RecyclerService`

Each service queried via `getServiceState()`, `getTickInterval()`, `getTickUnit()`, `getServiceName()`, `getDescription()`.

**Summary bar** at top: pill badges showing count of RUNNING (green), PAUSED (amber), FAILED (red) services.

**Service cards** in 2-column grid:
- Service name (bold) + status dot (colored, 8px circle)
- Tick interval + description in muted text

**Status dot colors:**
- RUNNING: `ACCENT_SUCCESS`
- PAUSED: `ACCENT_WARNING`
- FAILED: `ACCENT_ERROR`
- STOPPED/STARTING/STOPPING/SUSPENDED: `TEXT_MUTED`

Failed service cards get a red-tinted border (`ACCENT_ERROR` at 30% opacity).

## Tab 3: Events

Data source: `EventBus` listener. The dashboard registers an async listener on `Event.class` that captures events into a bounded ring buffer (last 200 events).

**Event categories** (derived from event package):
- **All** (default active)
- **Device**: `DeviceEvent`, `VolumeEvent` subclasses
- **Copy**: `FileDiscoveredEvent`, `CopyCompletedEvent`, `DuplicateDetectedEvent`
- **Index**: `IndexEvent`, `IndexSavedEvent`, `IndexLoadedEvent`
- **Storage**: `StorageLowEvent`, `StorageRecoveredEvent`, `FilesRecycledEvent`, `EmptyFoldersDeletedEvent`

**Filter bar:** horizontal pill buttons for category filtering.

**Event list:** scrollable panel, each event is a row:
- Timestamp (HH:mm:ss, muted)
- Category badge (colored pill)
- `event.description()` text

**Category badge colors:**
- Device: `ACCENT_INFO` (blue)
- Copy: `ACCENT_SUCCESS` (green)
- Index: `ACCENT_PRIMARY` (indigo)
- Storage: `ACCENT_WARNING` (amber)

**Auto-scroll:** always scrolls to bottom when new events arrive.

**Event rate:** show events/sec counter in filter bar (updated each refresh).

## Tab 4: Threads

Data source: `Thread.getAllStackTraces().keySet()` filtered to show non-system threads (exclude threads starting with "Reference Handler", "Finalizer", "Signal Dispatcher", "Attach Listener", "Common-Cleaner").

**Thread table** (single table, not cards):
| # | Name | State | Daemon | Priority |
|---|------|-------|--------|----------|
| 1 | main | RUNNABLE | No | 5 (NORM) |

**State colors:**
- RUNNABLE: `ACCENT_SUCCESS` green
- WAITING / TIMED_WAITING: `ACCENT_WARNING` amber
- BLOCKED / TERMINATED: `ACCENT_ERROR` red
- NEW: `TEXT_MUTED` gray

**Summary bar:** total threads count + daemon count.

## Refresh

All tabs share a single `javax.swing.Timer` at 500ms. On each tick:
1. Sniffers: call `getDebugSnapshots()`, rebuild cards
2. Services: query all service states, rebuild cards
3. Events: read from ring buffer, rebuild visible events
4. Threads: query `Thread.getAllStackTraces()`, rebuild table

Only the active tab's content is rebuilt (others skip).

## Event Capture

The dashboard registers an EventBus listener when constructed:
```java
EventBus.getInstance().registerAsync(Event.class, event -> {
    eventBuffer.add(new CapturedEvent(event));
});
```

`CapturedEvent` wraps the event with a capture timestamp.
`eventBuffer` is a bounded `LinkedList<CapturedEvent>` (max 200) — oldest evicted when full.

Listener unregistered on `windowClosed`.

## Integration Points

- **MainFrame menu**: existing "Debug" menu item opens this dialog instead of old `SnifferDebugDialog`
- **No new files**: entirely rewrite `SnifferDebugDialog.java`
- **No new dependencies**: uses existing ThemeManager, EventBus, Service, SnifferDebugSnapshot

## What Gets Removed

- Old `SnifferDebugDialog` implementation (fully replaced)
- No other files deleted

## What Stays Unchanged

- SnifferDebugSnapshot record
- EventBus dispatch and listener infrastructure
- Service abstract class and ServiceState enum
- ThemeManager colors and theming
- MainFrame (only the menu action target changes)
