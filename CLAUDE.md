# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tool Preferences

- Prefer LSP over Grep/Glob for code navigation (goToDefinition, findReferences, hover, documentSymbol)

## Build Commands

```bash
# Compile the project
mvn clean compile

# Run tests
mvn test

# Package (builds EXE + ZIP distribution with jlink runtime)
mvn package

# Run from source (development)
java -p target/classes -m UsbThief/com.superredrock.usbthief.Main
```

**Requirements:** Java 25 JDK, Maven 3.9+

No preview features are used or enabled anywhere in the build (see "No Preview Features" under
Decision Records).

## Architecture Overview

UsbThief is a Windows desktop application for USB device monitoring and file copying. Built with Java 25 (modular JPMS), Swing UI (FlatLaf), and JNA for Windows API integration.

### Package Structure

| Package | Responsibility |
|---------|---------------|
| `core` | Device management, event bus, configuration, file filters |
| `core.concurrent` | Dedicated thread pools (`ThreadPools`) and scheduled cooldowns (`CooldownTimer`) |
| `worker` | File scanning, copy tasks, rate limiting, task scheduling, storage management |
| `index` | Checksum deduplication with an in-memory Caffeine cache (no disk store) |
| `platform` | OS-specific implementations behind interfaces (`platform.win`, `platform.stub`) |
| `gui` | Swing UI components, theming, i18n |
| `gui.dailog` | Dialog windows (config, debug, device info, welcome, filters, rate limit). Package name is misspelled in the source tree; kept as-is for source compatibility |
| `gui.components` | Reusable UI components (Toast, EmptyStatePanel) |
| `statistics` | Copy speed and operation metrics |

### Key Components

Services extend the `Service` abstract class and run as daemon threads with tick-based execution:

- **DeviceManager** - USB hotplug detection via Windows API, device state tracking
- **SnifferLifecycleManager** - Per-volume Sniffer creation, restart scheduling, scheduler-based cooldown timers (`CooldownTimer` on the shared `ScheduledExecutorService`)
- **TaskScheduler** - Priority-based task queue
- **RecyclerService** - Storage cleanup when space is low

Not services (do not extend `Service`, do not appear in `ServiceRegistry`):

- **Index** - Singleton wrapping an in-memory Caffeine cache. There is **no** disk persistence: dedup state is lost on restart.
- **StorageController** - Plain utility that monitors work directory disk space (OK/LOW/CRITICAL thresholds, toggleable).
- **Sniffer** (per-device) - `Thread` subclass: initial scan + WatchService for real-time file monitoring, CompletableFuture lifecycle.

### Core Patterns

**EventBus** (`core.event.EventBus`)
- Singleton, thread-safe event dispatch
- Synchronous listeners via `register()`, async via `registerAsync()`
- Dispatch runs listeners in order on the calling thread; the async paths (`dispatchAsync`, `dispatchWithResult`) use the bus's own executor (`setAsyncExecutor`/`getAsyncExecutor`, dedicated pool by default) — never the JVM common pool
- All events are immutable. They are `final` classes extending `AbstractEvent` (not records); a few value types such as `IndexKey`/`CheckSum` are records

**ThreadPools** (`core.concurrent.ThreadPools`)
- Named daemon pools, one per workload: `cooldownScheduler()` (shared `ScheduledExecutorService`), `scanExecutor()` (bounded, 2–4 IO threads), `eventExecutor()`, `recycleExecutor()`
- Keeps long disk scans, async event notification and recycler statistics out of `ForkJoinPool.commonPool()`
- No virtual-thread APIs (Java 11 back-port constraint)

**CooldownTimer** (`core.concurrent.CooldownTimer`)
- Keyed cooldown delays over the shared scheduler; `schedule(key, delayMs, action)` / `cancel(key)` / `remainingMs(key)`
- Used by `SnifferLifecycleManager` for restart cooldowns (no polling thread per volume)

**Service Lifecycle**
```java
// All services extend Service and implement:
protected abstract void tick();              // Called every tick interval
protected abstract long getTickInterval();   // period, interpreted together with getTickUnit()
protected abstract TimeUnit getTickUnit();
public abstract String getServiceName();
public abstract String getDescription();

// State management: STOPPED → STARTING → RUNNING → PAUSED → STOPPING → STOPPED
```

**Singleton Pattern** - Most managers use double-checked locking:
```java
public static Manager getInstance() {
    if (INSTANCE == null) {
        synchronized (Manager.class) {
            if (INSTANCE == null) {
                INSTANCE = new Manager();
            }
        }
    }
    return INSTANCE;
}
```

**Configuration** (`core.config.ConfigSchema`)
- Type-safe config entries with defaults
- Access via `ConfigManager.getInstance().get(ConfigSchema.KEY_NAME)`

### Data Flow

1. **USB Detection**: `UsbHotplugMonitor` (JNA) → `DeviceManager.onVolumeArrival()` → `Device` + `Volume` created
2. **File Discovery**: `SnifferLifecycleManager` creates `Sniffer` → `Sniffer` scans → `FileDiscoveredEvent` → `CopyTask` submitted to `TaskScheduler`
3. **Deduplication**: `CopyTask` computes `CheckSum` → `Index.checkDuplicate()` → skip if already indexed (in-memory Caffeine cache only; the index is empty after a restart)
4. **Copy Execution**: Rate-limited NIO copy with `RateLimiter` → `CopyCompletedEvent`
5. **Storage Control**: `StorageController` checks disk space → `RecyclerService` cleanup if LOW/CRITICAL
6. **UI Updates**: Components listen to events via `EventBus.register()`

### Volume State Machine

```
OFFLINE → UNAVAILABLE → IDLE ⇄ DISABLED
                       ↓
                    EJECTING (terminal)
```

- **OFFLINE** - Volume not present
- **UNAVAILABLE** - Exists but inaccessible (IOException)
- **IDLE** - Ready, no active operations
- **DISABLED** - Manually disabled by user (requires manual re-enable)
- **EJECTING** - Windows requested eject (`DBT_DEVICEQUERYREMOVE`); terminal state, aborts active copies

On `DBT_DEVICEQUERYREMOVE`, the app blocks the eject and sets EJECTING. Active NIO copies are interrupted, Sniffer is stopped, and the volume never returns to IDLE until removal.

### Thread Safety

- `ConcurrentHashMap` for device/storage maps
- `CopyOnWriteArrayList` for event listeners
- `ReentrantLock` in Service for state transitions
- Thread-local buffers in `CopyTask` for NIO operations
- Caffeine cache in `Index` for thread-safe LRU eviction

### Internationalization

- `I18nManager` manages locale and resource bundles
- Bundle files: `gui/messages_{locale}.properties` (en, zh, ja, de)
- Runtime language switching supported via `setLocale()`

## Windows-Specific

This application is Windows-only due to:
- JNA calls to Windows APIs (device notification, volume serial numbers)
- `UsbHotplugMonitor` uses `RegisterDeviceNotification` via JNA
- Handles `DBT_DEVICEARRIVAL`, `DBT_DEVICEREMOVECOMPLETE`, `DBT_DEVICEQUERYREMOVE`
- Disk serial retrieval via `DeviceIoControl`

## Release Process

`mvn package` produces:
- `target/UsbThief-{version}.exe` - Launch4j Windows executable
- `target/UsbThief-{version}.zip` - Distribution with bundled jlink runtime
- `target/runtime/` - Custom JRE image

### Dual-Version Release (Java 25 + Java 11)

When releasing a new version, **both Java 25 and Java 11 builds must be published simultaneously**. All new features must be migrated to the Java 11 codebase before release.

1. **Update version** — edit `<version>` in `pom.xml` to the new version
2. **Migrate features to Java 11** — port all new features from the Java 25 codebase to the Java 11 codebase, replacing high-version APIs (records, switch expressions, pattern matching, `Stream.toList()`, virtual threads) with compatible alternatives
3. **Update README.md** — reflect any changes (features, screenshots, requirements)
4. **Update CLAUDE.md** — ensure architecture docs and commands stay current
5. **Tag and push** — `git tag v<version> && git push origin master --tags`

The `release.yml` workflow triggers on `v*` tags, builds artifacts on Windows, and creates a GitHub Release with EXE + ZIP.

## Decision Records

Short, append-only notes for decisions that changed documented behaviour. Add a new entry instead
of rewriting history.

- **2026-09-13 · Index has no disk persistence (AR-22).** An earlier design
  (`docs/superpowers/plans/2026-05-01-index-rewrite.md`,
  `docs/superpowers/specs/2026-05-01-index-rewrite-design.md`) planned a binary `IndexDiskStore`
  behind the Caffeine cache, and older versions of this file documented it as shipped. It was
  **deliberately removed** by `docs/superpowers/specs/2026-05-10-index-cache-refactor-design.md` (which lists
  `index/IndexDiskStore.java` under **Delete**), and no `IndexDiskStore`/`DiskStore` class exists in
  the repository. Consequence: deduplication works only for the lifetime of the process — after a
  restart the same file can be copied again. The decision here is to **align the documentation with
  the code** rather than reintroduce the store; persistent dedup would be a separate feature with
  its own design (SQLite/Prefs-backed, bounded, crash-safe) and is not in scope.
- **2026-09-13 · No preview features (AR-18).** `--enable-preview` was removed from the Surefire
  argLine, from the jlink launcher patches and from the Launch4j JVM opts, and from both documented
  run commands. The source has no preview-only syntax: the no-argument `main()` entry point is final
  in JDK 25 (JEP 512, Compact Source Files and Instance Main Methods), and records, switch
  expressions, `instanceof` patterns, sealed types and platform/virtual threads are all standard in
  JDK 25. Compile and the full test suite pass with the flag removed (see the P5-B report). Removing
  it also stops pinning the shipped runtime to one exact JRE build, since preview bytecode is
  rejected by any other JDK version.
