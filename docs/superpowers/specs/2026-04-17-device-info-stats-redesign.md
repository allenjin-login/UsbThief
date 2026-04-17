# DeviceInfoDialog + Statistics Redesign

## Problem

1. DeviceInfoDialog shows only 4 fields (Serial/VID/PID/Path) - not useful
2. Volume-Device association is unreliable (3 failed attempts) - must not depend on it
3. Two overlapping stats panels (StatsPanel + StatisticsPanel) with redundant timers
4. Statistics class only tracks global totals, no per-volume breakdown

## Design Decisions

- **Volume-centric display**: DeviceInfoDialog shows per-Volume cards since Volume data is reliable (drive letter, storage, FS type, serial)
- **Per-volume stats**: Statistics adds VolumeStats inner class keyed by volume serial
- **Best-effort Device info**: VID/PID shown when DeviceManager can resolve it, omitted otherwise
- **Delete StatsPanel**: Merge into enhanced StatisticsPanel
- **Global overview in DeviceInfoDialog**: Top section shows global stats, bottom section shows per-volume cards

## Changes

### 1. Statistics class - add per-volume tracking

Add `VolumeStats` record/class:
- `filesCopied: AtomicLong`
- `bytesCopied: AtomicLong`
- `errors: AtomicLong`
- `extensionCounts: ConcurrentHashMap<String, AtomicLong>`
- `firstSeenTime: long` (from first CopyCompletedEvent for this serial)

Add to Statistics:
- `ConcurrentHashMap<String, VolumeStats> volumeStatsMap`
- `VolumeStats getVolumeStats(String serial)`
- `Map<String, VolumeStats> getAllVolumeStats()`

Modify `onCopyCompleted()` to also update `volumeStatsMap` keyed by `event.deviceSerial()` (which is already Volume.getSerialNumber() per Sniffer.java:135).

Modify `save()`/`load()` to persist per-volume stats via Preferences.

Modify `resetAll()` to clear volume stats.

### 2. DeviceInfoDialog rewrite

Replace Device (physical device) cards with Volume cards.

Each Volume card shows:
- Drive letter + total capacity + state badge
- Storage progress bar (used/total with percentage)
- FS type, Volume serial
- VID/PID (best-effort via DeviceManager.getDeviceBySerial)
- Connection duration (from VolumeStats.firstSeenTime)
- Per-volume copy stats: files, bytes, errors
- Extension distribution table (top 10)

Top section: global overview (total files/bytes/devices/errors + session progress bar)

Data sources:
- Volume info: DeviceManager.getAllVolumes()
- Storage: Volume.getFileStore()
- Stats: Statistics.getVolumeStats(volume.getSerialNumber())
- Device info: DeviceManager.getDeviceBySerial(volume.getSerialNumber()) (nullable)

### 3. Delete StatsPanel

Remove StatsPanel.java entirely. Its data (copied files, global speed, queue size, active threads) is available in:
- StatisticsPanel (files, size)
- MainFrame compact stats bar (speed, queue, load, total)

### 4. Enhance StatisticsPanel

Keep as separate window accessible from menu. Improve card layout to be more compact and modern.

### 5. i18n

Add new message keys for:
- Volume card labels (storage, connection time, etc.)
- Volume stats labels (files, bytes, errors, extensions)
- Global overview labels

Update all 5 locale files (default, en, zh, de, ja).

## Out of Scope

- Volume-Device association logic
- VolumeListPanel / VolumeCard changes
- Event system architecture changes
- SpeedChartPanel changes
