# UsbThief - Project Knowledge Base

**Generated:** 2026-02-03
**Last Updated:** 2026-02-21
**Commit:** 95f2d9b
**Branch:** master
**Java Version:** 24 (modular, with --enable-preview)
**Module:** UsbThief

## OVERVIEW
USB device monitoring and file copying utility for Windows. Detects USB drives, monitors file changes in real-time, and copies files using checksum-based deduplication. **Priority-based task scheduling with adaptive load control and load-aware rate limiting.** **Full internationalization (i18n) support with hot language switching.** **Thread-based service architecture with sleep-based tick scheduling.**

> **Subdirectory AGENTS.md:** `src/com/superredrock/usbthief/core/`, `worker/`, `gui/`

## STRUCTURE
```
UsbThief/
├── src/
│   ├── module-info.java        # Java module definition (requires: java.base, java.logging, java.prefs, java.desktop)
│   ├── com/superredrock/usbthief/
│   │   ├── Main.java           # Application entry point
│   │   ├── core/               # Device management, queue infrastructure (9 files)
│   │   │   ├── config/         # Type-safe configuration system (4 files)
│   │   │   └── event/          # Event bus and device/event types (13 files)
│   │   ├── statistics/         # Statistics tracking and reporting
 │   │   ├── worker/             # Device scanning, file copying, task execution (14 files)
 │   │   ├── index/              # File indexing, checksum verification (4 files)
 │   │   └── gui/                # Swing UI components (10 files + i18n)
│   ├── test/                   # Unit tests (JUnit 5 + Mockito)
│   │   └── com/superredrock/usbthief/
│   │       ├── core/           # Core module tests
│   │       │   └── event/      # EventBus and event tests
│   │       ├── worker/         # Worker module tests
│   │       └── index/          # Index module tests
│   
 ├── pom.xml                     # Maven build config (Java 24, --enable-preview)
 ├── AGENTS.md                   # Root - this file
 ├── CONFIG.md                   # Configuration documentation
 └── TASKSCHEDULER.md            # TaskScheduler system documentation
```

## BUILD & TEST COMMANDS
```bash
# Maven build (recommended)
mvn clean compile              # Clean and compile all sources
mvn compile                    # Compile only (incremental)

# Run tests
mvn test                       # Run all unit tests
mvn test -Dtest=CheckSumTest   # Run specific test class

# Manual modular build
javac -d out --module-source-path src -m UsbThief

# Run application
java -p out -m UsbThief/com.superredrock.usbthief.Main
```


## I18N SYSTEM

### Architecture
- **I18NManager** (Singleton): Central i18n manager with `LocaleChangeListener` support
- **LanguageDiscovery**: Auto-discovers language packs from `messages_*.properties.utf8`
- **LanguageConfig**: User preferences for priorities, display names, and hidden languages
- **LanguageInfo**: Record containing `Locale`, `displayName`, `nativeName`, and `priority`

### Resource Bundles
Located in `src/com/superredrock/usbthief/gui/`:
- `messages.properties` - English (default, 263 keys)
- `messages_en.properties` - English
- `messages_zh_CN.properties` - Chinese Simplified
- `messages_de.properties` - German
- Naming: `messages_<locale>.properties` (auto-discovered)

### Key Naming Conventions
- `main.*` - MainFrame window/menu items
- `menu.*` - Menu bar items
- `tab.*` - Tab titles
- `device.*` - Device list (buttons, borders, cards)
- `device.card.*` - Device card UI (40+ keys)
- `event.*`, `filehistory.*`, `stats.*`, `log.*` - Panel-specific
- `common.*` - Shared strings

### Language Switching
All GUI components implement `refreshLanguage()` method:
```java
public void refreshLanguage() {
    SwingUtilities.invokeLater(() -> {
        componentLabel.setText(i18n.getMessage("key.name"));
        button.setToolTipText(i18n.getMessage("button.tooltip"));
        // Update all language-dependent text
    });
}
```

### Adding New Languages
1. Create `messages_<locale>.properties` in gui package
2. Add translations for all keys (UTF-8 encoding, no Unicode escapes needed)
3. Restart application - language auto-appears in menu
4. Optional: Configure in `~/.usbthief/languages.properties`

### Configuration File
`~/.usbthief/languages.properties`:
```properties
# Set language priority (higher = first in menu)
zh_CN.priority=10
en.priority=5

# Custom display names
zh_CN.displayName=Simplified Chinese
zh_CN.nativeName=简体中文

# Hide languages
fr.hidden=true

# Set default language
default.language=zh_CN
```

### UTF-8 Properties Support
- Uses `.utf8` extension (Java 9+ standard)
- Compatible with Java module system (no custom ResourceBundle.Control needed)
- Direct UTF-8 encoding - no `\uXXXX` escapes required
- Maven pom.xml includes both `.properties` and `.properties.utf8`

### Important Implementation Notes
- **Table headers**: Use dynamic `getColumnName()` that fetches from I18NManager each call
- **Search components**: Store `searchLabel`, `searchButton`, `clearButton` as fields for updates
- **Device cards**: Implement `refreshLanguage()` to update all buttons and labels
- **Menu items**: Language menu auto-generated from discovered languages
- **Event listeners**: All panels implement `LocaleChangeListener` via `refreshLanguage()`


## CODE STYLE GUIDELINES

### Import Ordering
Project imports first (grouped by subpackage), then stdlib grouped by category:
```java
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.worker.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

import static com.superredrock.usbthief.core.DeviceUtils.getHardDiskSN;
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `TaskScheduler`, `LoadEvaluator`)
- **Methods**: camelCase (e.g., `evaluateLoad()`, `checkDuplicate()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `LOG_INTERVAL_MS`, `BUFFER_SIZE`)
- **Private fields**: camelCase (e.g., `priorityQueue`, `loadEvaluator`)
- **Records**: PascalCase (e.g., `CheckSum`, `LoadScore`)
- **Enums**: PascalCase with UPPERCASE values (e.g., `DeviceState.IDLE`, `LoadLevel.HIGH`)

### Field Declaration Order
```java
public static final Logger logger = Logger.getLogger(ClassName.class.getName());

private static volatile Singleton INSTANCE;

private final Type finalField;
private Type mutableField;
```

### Logging Pattern
```java
private static final Logger logger = Logger.getLogger(ClassName.class.getName());

// Usage
logger.fine("Detailed debug info");
logger.info("Normal operation info");
logger.warning("Warning message");
logger.severe("Error message");
logger.throwing("ClassName", "methodName", exception);
```

### Error Handling
- Use specific exception types (NoSuchFileException, AccessDeniedException)
- Switch expressions with pattern matching for exception handling:
```java
switch (state) {
    case IDLE -> { /* action */ }
    case SCANNING -> { /* action */ }
    default -> {} // No action
}

// Exception handling
try {
    // operation
} catch (IOException | InterruptedException e) {
    result = CopyResult.FAIL;
} finally {
    buffer.clear(); // Always cleanup
}
```

### Switch Expressions
Use arrow notation with pattern matching:
```java
return switch (obj) {
    case CheckSum cs -> Arrays.equals(context, cs.context);
    case null, default -> false;
};

switch (loadLevel) {
    case LOW -> executor.submit(task);
    case MEDIUM -> priorityQueue.offer(task);
    case HIGH -> submitBatch(batchSize);
}
```

### Thread-Safety Patterns
- `Collections.synchronizedSet()` for device collections
- `ConcurrentHashMap.newKeySet()` for O(1) index lookups
- `CopyOnWriteArrayList` for event listener lists
- `volatile` for singleton instance flags and state fields
- `AtomicLong` with `compareAndSet()` for thread-safe counters
- `synchronized` methods for queue depth queries
- **ThreadLocal buffers**: Always call `clear()` in finally block for thread-pool reuse
- **Encapsulated static fields**: Use private static fields with getter methods (e.g., `QueueManager.getIndex()`)

### Singleton Pattern
```java
private static volatile Singleton INSTANCE;

public static synchronized Singleton getInstance() {
    if (INSTANCE == null) {
        INSTANCE = new Singleton();
    }
    return INSTANCE;
}
```

### Configuration Access
ALWAYS use ConfigManager - never hard-code constants:
```java
ConfigManager config = ConfigManager.getInstance();
int bufferSize = config.get(ConfigSchema.BUFFER_SIZE);
String workPath = config.get(ConfigSchema.WORK_PATH);
```

### Try-with-Resources
Required for FileChannel, WatchService, and any AutoCloseable:
```java
try (FileChannel readChannel = FileChannel.open(path, StandardOpenOption.READ);
     FileChannel writeChannel = FileChannel.open(dest, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
    // operations
} // Auto-closed
```

### JavaDoc Style
```java
/**
 * Brief description.
 *
 * <p>Additional details with {@code inlineCode}.
 *
 * @param param description
 * @return description
 * @throws Exception description
 */
```

## EVENT SYSTEM

### Architecture
- **EventBus** (Singleton): Central event dispatcher with parallel listener invocation
- **EventListener<T>**: Synchronous listener interface (`void onEvent(T event)`)
- **AsyncEventListener<T, R>**: Asynchronous listener returning `CompletableFuture<R>`

### Dispatch Methods
| Method | Execution | Return Type | Use Case |
|--------|-----------|-------------|----------|
| `dispatch(event)` | Parallel (parallelStream) | void | Fire-and-forget, high throughput |
| `dispatchAsync(event)` | Async (thread pool) | `CompletableFuture<Void>` | Non-blocking with completion tracking |
| `dispatchWithResult(event, type)` | Async | `CompletableFuture<List<R>>` | Collect results from async listeners |

### Parallel Dispatch
`dispatch()` uses `parallelStream()` for multi-threaded listener invocation:
- Uses ForkJoinPool.commonPool() for parallel execution
- Method blocks until all listeners complete
- Exception in one listener does not affect others (logged but not rethrown)
- **Order not guaranteed** - listeners execute in parallel

### Event Types
| Package | Events | Description |
|---------|--------|-------------|
| `core.event.device` | DeviceInsertedEvent, DeviceRemovedEvent, DeviceStateChangedEvent, NewDeviceJoinedEvent | Device lifecycle |
| `core.event.worker` | CopyCompletedEvent, FileDiscoveredEvent | File operations |
| `core.event.index` | FileIndexedEvent, DuplicateDetectedEvent, IndexLoadedEvent, IndexSavedEvent | Index operations |
| `core.event.misc` | TestEvent | Testing/demonstration |

### Thread Safety
- `CopyOnWriteArrayList` for listener storage (safe iteration during modification)
- EventBus singleton is thread-safe
- Events should be immutable after creation

## SERVICE ARCHITECTURE

### Design Pattern
- **Service extends Thread**: Thread-based execution instead of ScheduledThreadPoolExecutor
- **Tick-based lifecycle**: Subclasses implement `tick()` method for periodic execution
- **Hard-coded intervals**: Subclasses override `getTickIntervalMs()` to specify tick timing
- **No user-configurable delays**: Tick intervals are set in code, not via configuration

### Abstract Methods
```java
private abstract void tick();              // Core tick logic (replaces run())
protected abstract long getTickIntervalMs(); // Hard-coded tick interval in milliseconds
public abstract String getServiceName();        // Service name (renamed to avoid Thread.getName() conflict)
public abstract String getDescription();        // Service descripti
```

### Service Lifecycle
```
STOPPED → STARTING → RUNNING → STOPPING → STOPPED
                    ↕
                 PAUSED
```

### Method Renaming (Thread Conflicts)
- `getName()` → `getServiceName()` (Thread.getName() is final)
- `getState()` → `getServiceState()` (Thread.getState() is final)
- `stop()` → `stopService()` (Thread.stop() is deprecated)

### Subclass Implementations
| Service | Tick Interval | Description |
|---------|---------------|-------------|
| DeviceManager | 2000ms | Device detection and monitoring |
| TaskScheduler | 500ms | Task dispatch with load-based batching |
| Index | 60000ms | Periodic index save when dirty |

### Thread Safety
- `volatile boolean running` - Controls main tick loop
- `volatile boolean paused` - Pause/resume functionality
- `ReentrantLock stateLock` - Protects state transitions
- `TimeUnit.MILLISECONDS.sleep()` - More precise than Thread.sleep()

### Pause/Resume Support
```java
// Pause: Sets paused flag, state changes to PAUSED
public void pause();

// Resume: Clears paused flag, state changes to RUNNING
public void resume();

// Tick loop checks paused flag before calling tick()
if (!paused) {
    tick();
}
```

### Ghost Device Handling
Ghost devices represent known devices that are currently offline, stored as `DeviceRecord` in Java Preferences:
- **DeviceRecord**: Immutable record for persistence (serial, totalSpace, lastSeen)
- **DeviceManager**: Manages ghost device lifecycle and persistence
- **Persistence**: Uses Java Preferences API (`~/.usbthief/devices/`)
- When device reconnects: Ghost → Active device with scanner
- When device disconnects: Active device → Ghost (if previously known)

## DEVICE ARCHITECTURE

### Responsibility Separation
- **Device**: State management only (IDLE, SCANNING, DISABLED, UNAVAILABLE, OFFLINE)
- **DeviceManager**: Scanner lifecycle (`Map<Device, DeviceScanner>`), ghost handling
- **DeviceRecord**: Immutable persistence record (serial, totalSpace, lastSeen)
- **DeviceScanner**: File scanning and copy task creation

### Device State Transitions
```
OFFLINE ←→ IDLE ←→ SCANNING
    ↓         ↓
DISABLED   DISABLED
```

### SizeFormatter Utility
Unified byte formatting utility (`SizeFormatter.format()`) used across:
- Device cards, FileHistoryPanel, LogPanel, StatisticsPanel, Statistics
```

## ANTI-PATTERNS
- **DO NOT hard-code constants** - always use ConfigManager
- **DO NOT skip ThreadLocal.clear()** - buffers must be reset after use in thread pools
- **DO NOT block EDT** - all Swing updates use SwingUtilities.invokeLater()
- **DO NOT mutate events after creation** - all events are immutable
- **DO NOT throw exceptions from event listeners** - suppresses dispatch to other listeners
- **DO NOT hard-code UI strings** - always use I18NManager for user-facing text
- **DO NOT use public static mutable fields** - use private fields with getter methods

## UNIQUE STYLES
- **Priority scheduling**: Extension-based (PDF=10, TMP=1) + size adjustment (+2/-2)
- **Adaptive load control**: Queue depth (35%) + copy speed (35%) + thread activity (15%) + rejection rate (15%)
- **Load-aware rate limiting**: RateLimiter automatically adjusts based on LoadLevel (LOW 100%, MEDIUM 70%, HIGH 40%)
- **Two-phase scanning**: Initial scan → WatchService with threshold triggering
- **Checksum deduplication**: ConcurrentHashMap.newKeySet() for O(1) add operations
- **Graceful degradation**: TaskScheduler falls back to FIFO on errors
- **Token bucket rate limiting**: RateLimiter for copy speed control
- **Thread-based services**: Each Service runs in its own Thread with tick-based execution

## NOTES
- **Build**: Maven + IntelliJ IDEA
- **Testing**: JUnit 5.11.4 + Mockito 5.15.2
- **Linting**: No formal linting - manual code review
- **Module exports**: `index`, `core`, `core.config`, `core.event`, `gui`
- **I18N**: Hot-switching enabled via I18NManager singleton with locale change listeners
- **Supported Languages**: English (en), Chinese Simplified (zh_CN), German (de)
