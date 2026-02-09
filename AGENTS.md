# UsbThief - Project Knowledge Base

**Generated:** 2026-02-03
**Last Updated:** 2026-02-03
**Java Version:** 17+ (modular)
**Module:** UsbThief

## OVERVIEW
USB device monitoring and file copying utility for Windows. Detects USB drives, monitors file changes in real-time, and copies files using checksum-based deduplication. Features an extensible event system for device lifecycle notifications. **Priority-based task scheduling with adaptive load control.**

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
│   └── com.superredrock.usbthief.test/    # Manual test stubs (9 files)
├── AGENTS.md                   # Root - this file
├── CONFIG.md                   # Configuration documentation
├── TASKSCHEDULER.md            # TaskScheduler system documentation
└── out/production/UsbThief/    # Compiled output directory
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Application startup | `Main.java` | Initializes Index, QueueManager, installs ticker |
| Configuration | `core/config/ConfigManager.java` | Type-safe config via Preferences API |
| Device detection | `core/DeviceManager.java` | Polls FileStores, manages Device instances, dispatches events |
| Thread pools | `core/QueueManager.java` | Singleton with CallerRunsPolicy |
| Priority scheduling | `worker/TaskScheduler.java` | Priority-based dispatcher with adaptive load control |
| Retry logic | `core/DelayedPath.java` | DelayQueue for failed operations |
| Event system | `core/event/EventBus.java` | Thread-safe event bus |
| Device scanning | `worker/DeviceScanner.java` | WatchService-based monitoring |
| File copying | `worker/CopyTask.java` | ThreadPoolExecutor + ThreadLocal buffers |
| Speed monitoring | `worker/SpeedMonitor.java` | Real-time copy speed tracking |
| Rate limiting | `worker/RateLimiter.java` | Token bucket algorithm |
| Index management | `index/Index.java` | ConcurrentHashMap.newKeySet for deduplication |
| Main window | `gui/MainFrame.java` | Swing UI container |
| Tests | `com.superredrock.usbthief.test/*.java` | Manual test stubs (main() methods only) |

## CODE MAP
| Symbol | Type | Location | Role |
|--------|------|----------|------|
| ConfigManager | class | core/config/ConfigManager.java | Type-safe configuration singleton |
| ConfigSchema | class | core/config/ConfigSchema.java | Registry of ConfigEntry definitions |
| QueueManager | class | core/QueueManager.java | Singleton: manages thread pools, delay queue, device manager |
| DeviceManager | class | core/DeviceManager.java | Manages device collection (synchronizedSet), dispatches device events |
| Device | class | core/Device.java | Device model with state machine (DeviceState enum) |
| TaskScheduler | class | worker/TaskScheduler.java | Priority-based task dispatcher with adaptive load control |
| PriorityCopyTask | class | worker/PriorityCopyTask.java | Wrapper adding priority metadata to CopyTask |
| PriorityRule | class | worker/PriorityRule.java | Calculates task priority from file characteristics |
| LoadEvaluator | class | worker/LoadEvaluator.java | Evaluates system load from queue/speed/threads |
| Index | class | index/Index.java | File index with checksum deduplication |
| CheckSum | record | index/CheckSum.java | Immutable checksum data holder |
| CopyTask | class | worker/CopyTask.java | Callable file copy task with checksum verification |
| DeviceScanner | class | worker/DeviceScanner.java | WatchService-based file change monitoring |
| EventBus | class | core/event/EventBus.java | Thread-safe event bus (singleton) |
| EventListener | interface | core/event/EventListener.java | Functional interface for event handlers |
| MainFrame | class | gui/MainFrame.java | Main Swing window |

## CONVENTIONS
**Java-Specific Patterns:**
- **Package structure**: Flat under `src/` (not `src/main/java/`), tests in `src/com.superredrock.usbthief.test/`
- **Import ordering**: Project imports first, then stdlib grouped by category
- **Logging**: `protected static final Logger logger = Logger.getLogger(ClassName.class.getName());`
- **Field order**: Public static constants → Protected static constants → Private/protected instance fields
- **Switch expressions**: Arrow notation with pattern matching: `case NoSuchFileException _ -> ...`
- **ThreadLocal buffers**: Always call `clear()` after use (thread-pool environment)
- **Configuration**: ALWAYS use `Config` class - no hard-coded constants

## ANTI-PATTERNS (THIS PROJECT)
- **DO NOT hard-code constants** - always use `Config` class for all settings
- **DO NOT skip ThreadLocal.clear()** - buffers must be reset after use in thread pools
- **DO NOT use JUnit/TestNG** - no testing framework configured (manual test stubs only)

## UNIQUE STYLES
- **Priority-based scheduling**: Extension-based priority (PDF=10, TMP=1) + size adjustment (+2/-2), adaptive dispatch by load level
- **Adaptive load control**: Queue depth (40%) + copy speed (40%) + thread activity (20%) → batch size (LOW/MEDIUM/HIGH)
- **Two-phase device scanning**: Initial scan followed by WatchService-based monitoring with threshold-based triggering
- **Checksum deduplication**: `ConcurrentHashMap.newKeySet()` in Index.digest for O(1) add operations
- **ThreadLocal pattern**: Reusable `ByteBuffer` in `CopyTask` and `CheckSum.verify()` to reduce allocation overhead
- **Event-driven architecture**: DeviceManager publishes events via EventBus; listeners subscribe to specific event types
- **WatchService threshold**: Batches file changes before triggering copy (configurable via `watchThreshold`)
- **Token bucket rate limiting**: `RateLimiter` class implements token bucket algorithm for copy speed control

## COMMANDS
```bash
# Build with Maven
mvn clean compile              # Clean and compile all sources
mvn compile                    # Compile only (incremental)

# Build with IntelliJ IDEA
# Open project in IDE and use Build > Build Project

# Compile Java files manually (modular build)
javac -d out --module-source-path src -m UsbThief

# Run the application
java -p out -m UsbThief/com.superredrock.usbthief.Main

# Run test stubs (manual execution - no testing framework)
# Compile single test
javac -d out src/com.superredrock.usbthief.test/FileSystemTest.java --module-source-path src
# Run single test
java -p out -m com.superredrock.usbthief.test/com.superredrock.usbthief.test.FileSystemTest
```

## NOTES
- **Build system**: Maven + IntelliJ IDEA (pom.xml present)
- **Java version**: 17+ (modular)
- **Testing**: Manual test stubs in `src/com.superredrock.usbthief.test/` with main() methods - no JUnit/TestNG
- **Linting**: No formal linting configured - manual code review
- **Thread-safety**: `DeviceManager` uses `Collections.synchronizedSet()`, `Index` uses `ConcurrentHashMap.newKeySet()`
- **Module exports**: Exports `index`, `core`, `core.config`, `core.event`, `gui` packages
