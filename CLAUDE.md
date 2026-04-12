# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Compile the project
mvn clean compile

# Run tests
mvn test

# Package (builds EXE + ZIP distribution with jlink runtime)
mvn package

# Run from source (development)
java -p target/classes -m UsbThief/com.superredrock.usbthief.Main --enable-preview
```

**Requirements:** Java 25 JDK (uses preview features), Maven 3.9+

## Architecture Overview

UsbThief is a Windows desktop application for USB device monitoring and file copying. Built with Java 25 (modular JPMS), Swing UI (FlatLaf), and JNA for Windows API integration.

### Package Structure

| Package | Responsibility |
|---------|---------------|
| `core` | Device management, event bus, configuration, file filters |
| `worker` | File scanning, copy tasks, rate limiting, task scheduling |
| `index` | MD5 checksum deduplication with persistent storage |
| `gui` | Swing UI components, theming, i18n |
| `statistics` | Copy speed and operation metrics |

### Key Services (all extend `Service` abstract class)

Services run as daemon threads with tick-based execution:

- **DeviceManager** - USB hotplug detection via Windows API, device state tracking
- **TaskScheduler** - Priority-based task queue with adaptive load control (LOW/MEDIUM/HIGH)
- **Index** - Periodic checksum index persistence
- **Sniffer** (per-device) - Initial scan + WatchService for real-time file monitoring

### Core Patterns

**EventBus** (`core.event.EventBus`)
- Singleton, thread-safe event dispatch
- Synchronous listeners via `register()`, async via `registerAsync()`
- Dispatch uses `parallelStream()` for concurrent listener notification
- All events are immutable records

**Service Lifecycle**
```java
// All services extend Service and implement:
protected abstract void tick();           // Called every tick interval
protected abstract long getTickIntervalMs();
public abstract String getServiceName();

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

1. **USB Detection**: `UsbHotplugMonitor` (JNA) → `DeviceManager.onVolumeArrival()` → `Device` created
2. **File Discovery**: `Sniffer` scans → `FileDiscoveredEvent` → `CopyTask` submitted to `TaskScheduler`
3. **Deduplication**: `CopyTask` computes `CheckSum` → `Index.checkDuplicate()` → skip if exists
4. **Copy Execution**: Rate-limited NIO copy with `RateLimiter` → `CopyCompletedEvent`
5. **UI Updates**: Components listen to events via `EventBus.register()`

### Thread Safety

- `ConcurrentHashMap` for device/storage maps
- `CopyOnWriteArrayList` for event listeners
- `ReentrantLock` in Service for state transitions
- Thread-local buffers in `CopyTask` for NIO operations

### Internationalization

- `I18NManager` manages locale and resource bundles
- Bundle files: `gui/messages_{locale}.properties`
- Runtime language switching supported via `setLocale()`

## Windows-Specific

This application is Windows-only due to:
- JNA calls to Windows APIs (device notification, volume serial numbers)
- `UsbHotplugMonitor` uses `RegisterDeviceNotification` via JNA
- Disk serial retrieval via `DeviceIoControl`

## Release Process

The `mvn package` command produces:
- `target/UsbThief-{version}.exe` - Launch4j Windows executable
- `target/UsbThief-{version}.zip` - Distribution with bundled jlink runtime
- `target/runtime/` - Custom JRE image
