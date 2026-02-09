# Event System - Project Knowledge Base

**Package:** `com.superredrock.usbthief.core.event`

**Generated:** 2026-01-30

## OVERVIEW
Thread-safe event bus for device lifecycle and file indexing notifications.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Event dispatching | `EventBus.java` | Singleton bus with thread-safe listener management |
| Event registration | `EventListener.java` | Functional interface for handler registration |
| Device events | `DeviceEvent.java` hierarchy | Base class for device lifecycle events |
| Index events | `IndexEvent.java` hierarchy | Base class for file indexing events |

## CONVENTIONS
**Event Naming:** `{Domain}{Action}Event` (e.g., `DeviceInsertedEvent`, `IndexLoadedEvent`)

**Event Creation:**
- Events are immutable - use constructor injection for all required fields
- Extend appropriate base class (`DeviceEvent`, `IndexEvent`, or implement `Event`)

**Event Dispatching:**
```java
EventBus.getInstance().dispatch(new DeviceInsertedEvent(device));
```

**Listener Registration:**
- Register early during initialization
- Unregister on shutdown
- Exceptions in listeners are logged but don't stop dispatch

**Event Types:**
- `DeviceInsertedEvent` - Fired when USB device is detected
- `DeviceRemovedEvent` - Fired when device goes offline
- `DeviceStateChangedEvent` - Fired when device state changes
- `FileIndexedEvent` - Fired when file is added to index
- `DuplicateDetectedEvent` - Fired when duplicate checksum found
- `IndexLoadedEvent` - Fired when index is loaded from disk
- `IndexSavedEvent` - Fired when index is persisted

## ANTI-PATTERNS
- **DO NOT mutate events after creation** - all events are immutable
- **DO NOT throw exceptions from listeners** - they suppress dispatch to other listeners
- **DO NOT assume event order** - listeners are called in registration order but this is not guaranteed
