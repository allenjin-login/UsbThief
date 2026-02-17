# Core Package

**Parent:** [../../../../../AGENTS.md](../../../../../AGENTS.md)

## OVERVIEW
Service lifecycle management, device detection, event dispatch, and queue infrastructure. Thread-based tick architecture with singleton managers.

## STRUCTURE
```
core/
├── Service.java          # Base Thread subclass with tick() lifecycle
├── ServiceManager.java   # ServiceLoader-based service registry
├── ServiceState.java     # Lifecycle states (STOPPED/RUNNING/PAUSED/FAILED)
├── QueueManager.java     # ThreadPoolExecutor + task queue + scanners
├── DeviceManager.java    # USB detection, ghost devices, scanner lifecycle
├── Device.java           # Device state machine (IDLE/SCANNING/DISABLED/OFFLINE)
├── DeviceUtils.java      # WMI calls for serial/volume info
├── DeviceRecord.java     # Immutable persistence record
├── SizeFormatter.java    # Byte formatting utility
├── RetryPath.java        # Retry logic with config-driven count
├── RejectionAwarePolicy.java  # Custom ThreadPoolExecutor.RejectedExecutionHandler
├── config/               # ConfigManager + ConfigSchema (type-safe config)
└── event/                # EventBus + event types (see event/AGENTS.md)
```

## WHERE TO LOOK
| Task | File | Key Method |
|------|------|------------|
| Add new service | Service.java subclass | `tick()`, `getTickIntervalMs()` |
| Register service | module-info.java | `provides Service with ...` |
| Query device state | Device.java | `getState()`, `setState()` |
| Dispatch events | EventBus.java | `dispatch()`, `dispatchAsync()` |
| Access thread pool | QueueManager.java | `getPool()`, `getQueueSize()` |
| Persist ghost device | DeviceManager.java | `Preferences.userNodeForPackage()` |

## KEY PATTERNS

### Service Tick Architecture
```java
public abstract class Service extends Thread {
    protected abstract void tick();           // Override for periodic work
    protected abstract long getTickIntervalMs(); // Hard-coded interval
    // run() loop: tick() → sleep(interval) → repeat
}
```

### EventBus Dispatch Modes
| Method | Execution | Use Case |
|--------|-----------|----------|
| `dispatch(event)` | parallelStream (blocking) | High throughput, fire-and-forget |
| `dispatchAsync(event)` | CompletableFuture | Non-blocking with completion tracking |
| `dispatchWithResult(event, type)` | CompletableFuture<List<R>> | Collect async results |

### Ghost Device Persistence
- Offline devices stored as `DeviceRecord(serial, totalSpace, lastSeen)` in Java Preferences
- Path: `~/.usbthief/devices/` (via `Preferences.userNodeForPackage(DeviceManager.class)`)
- Reconnect: ghost → active with scanner; Disconnect: active → ghost (if known)

## ANTI-PATTERNS (core-specific)
- **DO NOT call Thread.start() directly** - use `Service.start()` for state tracking
- **DO NOT skip stateLock** - all state transitions require `stateLock.lock()/unlock()`
- **DO NOT create DeviceManager/QueueManager instances** - access via ServiceManager/QueueManager static methods
