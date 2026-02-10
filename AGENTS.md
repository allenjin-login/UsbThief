# UsbThief - Project Knowledge Base

**Generated:** 2026-02-03
**Last Updated:** 2026-02-09
**Java Version:** 24 (modular, with --enable-preview)
**Module:** UsbThief

## OVERVIEW
USB device monitoring and file copying utility for Windows. Detects USB drives, monitors file changes in real-time, and copies files using checksum-based deduplication. Features an extensible event system for device lifecycle notifications. **Priority-based task scheduling with adaptive load control and load-aware rate limiting.**

## STRUCTURE
```
UsbThief/
├── src/
│   ├── module-info.java        # Java module definition (requires: java.base, java.logging, java.prefs, java.desktop)
│   ├── com/superredrock/usbthief/
│   │   ├── Main.java           # Application entry point
│   │   ├── core/               # Device management, queue infrastructure (7 files)
│   │   │   ├── config/         # Type-safe configuration system (4 files)
│   │   │   └── event/          # Event bus and device/event types (12 files)
│   │   ├── worker/             # Device scanning, file copying, task execution (14 files)
│   │   ├── index/              # File indexing, checksum verification (4 files)
│   │   └── gui/                # Swing UI components (6 files)
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

# Manual modular build
javac -d out --module-source-path src -m UsbThief

# Run application
java -p out -m UsbThief/com.superredrock.usbthief.Main


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
protected static final Logger logger = Logger.getLogger(ClassName.class.getName());

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
- `volatile` for singleton instance flags
- `synchronized` methods for queue depth queries
- **ThreadLocal buffers**: Always call `clear()` in finally block for thread-pool reuse

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

## ANTI-PATTERNS
- **DO NOT hard-code constants** - always use ConfigManager
- **DO NOT skip ThreadLocal.clear()** - buffers must be reset after use in thread pools
- **DO NOT use JUnit/TestNG** - manual test stubs only (main() methods)
- **DO NOT block EDT** - all Swing updates use SwingUtilities.invokeLater()
- **DO NOT mutate events after creation** - all events are immutable
- **DO NOT throw exceptions from event listeners** - suppresses dispatch to other listeners

## UNIQUE STYLES
- **Priority scheduling**: Extension-based (PDF=10, TMP=1) + size adjustment (+2/-2)
- **Adaptive load control**: Queue depth (35%) + copy speed (35%) + thread activity (15%) + rejection rate (15%)
- **Load-aware rate limiting**: RateLimiter automatically adjusts based on LoadLevel (LOW 100%, MEDIUM 70%, HIGH 40%)
- **Two-phase scanning**: Initial scan → WatchService with threshold triggering
- **Checksum deduplication**: ConcurrentHashMap.newKeySet() for O(1) add operations
- **Graceful degradation**: TaskScheduler falls back to FIFO on errors
- **Token bucket rate limiting**: RateLimiter for copy speed control

## NOTES
- **Build**: Maven + IntelliJ IDEA
- **Linting**: No formal linting - manual code review
- **Module exports**: `index`, `core`, `core.config`, `core.event`, `gui`
