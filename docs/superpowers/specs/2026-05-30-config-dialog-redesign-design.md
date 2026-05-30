# Config Dialog Redesign — IntelliJ-style Settings UI

Date: 2026-05-30

## Goal

Replace the current `JTabbedPane`-based ConfigDialog with an IntelliJ IDEA-style settings dialog: left-side tree navigation + right-side content panel. Also refactor `ConfigSchema` to split ~40 config entries into per-domain config classes under `core/config/configs/`.

## 1. ConfigSchema Refactor

### Current State

All ~40 `ConfigEntry` fields are static members of a single `ConfigSchema` class. This makes the class large and hard to navigate.

### New Structure

Each functional domain gets its own config class in `core/config/configs/`:

```
core/config/
├── ConfigEntry.java          (unchanged)
├── ConfigType.java           (unchanged)
├── ConfigManager.java        (unchanged)
├── ConfigSchema.java         (registry — collects entries from all config classes)
└── configs/
    ├── ThreadPoolConfig.java       → corePoolSize, maxPoolSize, keepAliveTimeSeconds, taskQueueCapacity
    ├── DeviceScannerConfig.java    → initialDelaySeconds, delaySeconds
    ├── IndexConfig.java            → indexCacheSize
    ├── FileCopyConfig.java         → bufferSize, hashBufferSize, maxFileSize, retryCount, timeoutMillis, copyVerifyEnabled, hashAlgorithm
    ├── FileWatchConfig.java        → watchEnabled, watchThreshold, watchResetIntervalSeconds
    ├── RateLimitConfig.java        → copyReadRateLimit, copyWriteRateLimit, copyRateBurstSize
    ├── PathConfig.java             → workPath
    ├── UIConfig.java               → fileHistoryMaxEntries
    ├── WindowConfig.java           → autoStartEnabled, showInTaskbar, closeAction, closeActionRemember
    ├── BlacklistConfig.java        → deviceBlacklist, deviceBlacklistBySerial
    ├── FileFilterConfig.java       → maxSize, maxSizeEnabled, timeEnabled, timeValue, timeUnit, includeHidden, skipSymlinks, allowNoExtension
    ├── SuffixFilterConfig.java     → mode, whitelist, blacklist, preset
    ├── StorageConfig.java          → reservedBytes, maxBytes, waitNormalMinutes, waitErrorMinutes, strategy, protectedAgeHours, warningEnabled, enabled
    └── StatisticsApiConfig.java    → enabled, port
```

### Config Class Pattern

Each config class:

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileCopyConfig {
    public static final String CATEGORY = "File Copy";

    public static final ConfigEntry<Integer> BUFFER_SIZE =
        intEntry("bufferSize", "Buffer size for file copying (bytes)", 16 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> HASH_BUFFER_SIZE =
        intEntry("hashBufferSize", "Buffer size for hash calculation (bytes)", 1024, CATEGORY);

    // ... remaining entries

    private FileCopyConfig() {}
}
```

### ConfigSchema as Registry

`ConfigSchema` keeps its public API (`getAllEntries()`, `getEntriesByCategory()`, `getEntry()`) but delegates registration:

```java
static {
    registerClass(ThreadPoolConfig.class);
    registerClass(DeviceScannerConfig.class);
    registerClass(IndexConfig.class);
    registerClass(FileCopyConfig.class);
    // ... all config classes
}
```

`registerClass()` uses reflection to collect all `public static final ConfigEntry<?>` fields from the given class and registers them in the internal `ALL_ENTRIES` map.

### Migration Impact

All code referencing `ConfigSchema.BUFFER_SIZE` etc. must update imports to `FileCopyConfig.BUFFER_SIZE`. This is a find-and-replace refactoring across `worker/`, `index/`, `gui/`, and `statistics/` packages.

## 2. ConfigDialog UI Redesign

### Overall Layout

```
┌──────────────────────────────────────────────────┐
│  Preferences (dialog title)                       │
├──────────────┬───────────────────────────────────┤
│  🔍 Search…  │  General > Thread Pool             │  breadcrumb
│              │───────────────────────────────────│
│ ▼ General    │                                   │
│   Thread Pool│  corePoolSize:        [2      ▾]  │
│   Scanner    │  maxPoolSize:         [8      ▾]  │
│ ▶ File       │  keepAliveTime:       [60     ▾]  │
│ ▶ Index      │  taskQueueCapacity:   [1024   ▾]  │
│ ▶ Rate Limit │                                   │
│ ▶ Paths      │                                   │
│ ▶ UI         │                                   │
│ ▶ Security   │                                   │
│ ▶ Storage    │                                   │
│ ▶ Advanced   │                                   │
├──────────────┴───────────────────────────────────┤
│                              [ OK ]  [ Cancel ]  │
└──────────────────────────────────────────────────┘
```

### Tree Navigation Groups

| Tree Level 1 (Parent) | Tree Level 2 (Config Class) |
|---|---|
| General | Thread Pool, Scanner |
| File | File Copy, File Watch, File Filter, Suffix Filter |
| Index | Index Management |
| Rate Limiting | Rate Limiting |
| Paths | Paths |
| UI | UI, Window |
| Security | Blacklist |
| Storage | Storage Management |
| Advanced | Statistics API |

### Component Details

**Left Panel — JTree Navigation**
- `JTree` inside `JScrollPane`, fixed width ~200px
- Two-level tree: parent group nodes (expandable) and child config class nodes (leaf)
- Custom `TreeCellRenderer`: full-width blue highlight for selected row, transparent for unselected
- Parent nodes show expand/collapse icon; click on leaf node switches right panel content
- FlatLaf styling handles font, colors, and row height automatically

**Search Box**
- `JTextField` with placeholder text above the JTree
- On text input: filter tree nodes by matching ConfigEntry key or description
- Auto-expand parent node and select first matching child
- Clear search restores full tree

**Right Panel — Content Area**
- Top: breadcrumb `JLabel` showing "Parent > Child" path (e.g., "General > Thread Pool")
- Below: `JScrollPane` wrapping `JPanel` with `GridBagLayout`
- Reuses existing `createSpinner`, `createCheckBox`, `createTextField`, `createTextArea` logic
- Labels use i18n translated text instead of raw config keys

**Bottom Buttons**
- Only OK (save) and Cancel (close)
- OK button: primary blue style
- Cancel button: gray/default style
- Import/Export/Reset buttons are removed

**Layout Container — JSplitPane**
- `JSplitPane` with horizontal split
- Left pane: search box + JTree (fixed ~200px initial width, user-resizable)
- Right pane: breadcrumb + content scroll

### Dialog Properties
- Size: ~900×650 (wider than current 800×700 to accommodate side panel)
- Modal dialog
- Title from i18n: `config.title`
- `setLocationRelativeTo(parent)`

## 3. i18n Changes

Add new keys to all 4 locale files (en, zh, ja, de):

- Tree group names: `config.group.general`, `config.group.file`, `config.group.index`, etc.
- Search placeholder: `config.search.placeholder`
- Breadcrumb format: `config.breadcrumb.format` (e.g., "{0} > {1}")
- Config class display names: `config.category.threadPool`, `config.category.scanner`, etc.

These replace the current category-as-tab-name approach with proper i18n keys.

## 4. Files to Create/Modify

### New Files
- `core/config/configs/ThreadPoolConfig.java`
- `core/config/configs/DeviceScannerConfig.java`
- `core/config/configs/IndexConfig.java`
- `core/config/configs/FileCopyConfig.java`
- `core/config/configs/FileWatchConfig.java`
- `core/config/configs/RateLimitConfig.java`
- `core/config/configs/PathConfig.java`
- `core/config/configs/UIConfig.java`
- `core/config/configs/WindowConfig.java`
- `core/config/configs/BlacklistConfig.java`
- `core/config/configs/FileFilterConfig.java`
- `core/config/configs/SuffixFilterConfig.java`
- `core/config/configs/StorageConfig.java`
- `core/config/configs/StatisticsApiConfig.java`

### Modified Files
- `core/config/ConfigSchema.java` — remove inline entries, become registry only
- `gui/dailog/ConfigDialog.java` — full rewrite (JTree + JSplitPane + search)
- `gui/messages.properties` — add tree/search/breadcrumb keys
- `gui/messages_en.properties` — add tree/search/breadcrumb keys
- `gui/messages_zh.properties` — add tree/search/breadcrumb keys
- `gui/messages_ja.properties` — add tree/search/breadcrumb keys
- `gui/messages_de.properties` — add tree/search/breadcrumb keys

### Files with Import Updates
All files referencing `ConfigSchema.SOME_ENTRY` must update to `SomeConfig.SOME_ENTRY`:
- `worker/CopyTask.java`
- `worker/VerifyTask.java`
- `worker/RateLimiter.java`
- `index/Index.java`
- `statistics/collector/SpeedCollector.java`
- `core/QueueManager.java`
- `gui/dailog/*.java` (other dialogs)
- `test/` files

## 5. Out of Scope

- ConfigEntry/ConfigType/ConfigManager internals unchanged
- Import/Export XML functionality removed from dialog (no replacement in this iteration)
- Reset to defaults removed from dialog (no replacement in this iteration)
- No persistence of tree expand/collapse state
- No keyboard shortcuts for tree navigation beyond standard JTree behavior
