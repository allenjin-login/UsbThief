# Core Package - Device Management & Infrastructure

**Generated:** 2026-01-30

## OVERVIEW
Central infrastructure for device detection, configuration management, and task queuing.

## STRUCTURE
```
core/
├── QueueManager.java     # Singleton: thread pools, delay queue, device manager
├── DeviceManager.java    # Device collection management (synchronizedSet)
├── Device.java           # Device model with DeviceState state machine
├── DeviceUtils.java      # Windows disk utilities (serial number, paths)
├── DelayedPath.java      # Delayed implementation for retry queue
├── RetryPath.java        # Extends DelayedPath with retry countdown
└── package-info.java
```

**Subpackages:**
- `config/` - Type-safe configuration system (see core/config/AGENTS.md)
- `event/` - Event bus and device/event types (see core/event/AGENTS.md)

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Get/set configuration | `ConfigManager.getInstance()` | Type-safe config access (see core/config/AGENTS.md) |
| Access thread pools | `QueueManager.get*()` | Static singleton access |
| Manage devices | `DeviceManager` | Uses Collections.synchronizedSet() |
| Device state changes | `Device.updateState()` | Modern switch expressions |
| Windows disk serial | `DeviceUtils.getHardDiskSN()` | Creates temp .vbs files |
| Retry failed copies | `RetryPath` | Used in QueueManager.delayQueue |

## CONVENTIONS
- **Singleton pattern**: QueueManager and ConfigManager use static singleton instances
- **Thread-safety**: DeviceManager uses synchronizedSet, QueueManager uses concurrent collections
- **Configuration**: Use ConfigManager for all settings (type-safe via ConfigSchema entries)
- **State machine**: Device has DeviceState enum (Idle/Offline/Unavailable/Working/Disable)
- **Retry logic**: DelayedPath implements Delayed for DelayQueue use

## ANTI-PATTERNS (THIS PACKAGE)
- **DO NOT** create new QueueManager instances - use static methods
- **DO NOT** modify Device.deviceSet directly - use DeviceManager methods
- **DO NOT** skip ThreadLocal.clear() when using buffers in thread pools
- **DO NOT** use DeviceUtils.getHardDiskSN() in tight loops - creates temp files

## NOTES
- DeviceUtils.getHardDiskSN() has performance issues - creates .vbs temp files on each call
- Config changes are automatically persisted to Preferences API (see core/config/AGENTS.md)
- QueueManager uses CallerRunsPolicy for task queue overflow
- Event system conventions are in core/event/AGENTS.md
- Configuration system conventions are in core/config/AGENTS.md
