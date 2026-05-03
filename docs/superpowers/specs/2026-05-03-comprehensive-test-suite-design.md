# Comprehensive Test Suite Design

**Date:** 2026-05-03
**Scope:** Easy + Medium level testable components (25 test classes)
**Stack:** JUnit 5 + Mockito
**Depth:** Full coverage — normal paths, edge cases, error handling, concurrency

## Organization

Package-by-package, mirroring source structure:

```
test/com/superredrock/usbthief/
├── core/
│   ├── EventBusTest.java
│   ├── ConfigSchemaTest.java
│   ├── ConfigEntryTest.java
│   ├── ConfigManagerTest.java
│   ├── ClockThreadTest.java
│   ├── ServiceTest.java
│   └── event/
│       └── EventRecordsTest.java
├── worker/
│   ├── RateLimiterTest.java
│   ├── FileSelectorTest.java
│   ├── CopyTaskTest.java
│   └── TaskSchedulerTest.java
├── index/
│   ├── CheckSumTest.java
│   ├── IndexTest.java          (expand existing)
│   └── IndexDiskStoreTest.java (expand existing)
└── statistics/
    ├── SpeedCollectorTest.java
    ├── TotalBytesCopiedCollectorTest.java
    ├── TotalFilesCopiedCollectorTest.java
    ├── TotalErrorsCollectorTest.java
    ├── TotalDevicesCopiedCollectorTest.java
    ├── VolumeStatsCollectorTest.java
    └── SessionProgressCollectorTest.java
```

## Test Infrastructure

### Shared Test Utilities

- **`@TempDir`** for IndexDiskStore and CopyTask file IO tests
- **`@Mock` / `@InjectMocks`** for Medium-level tests
- **`CountDownLatch`** for verifying async/ConcurrentFuture completions
- **`@Timeout(5)`** on all tests involving threading to prevent hangs
- Reflection helper to reset singletons between tests (already used in existing IndexTest)

### Mock Strategy by Dependency

| Dependency | Mock Approach |
|-----------|---------------|
| Preferences | `@Mock Preferences` — ConfigManagerTest |
| IndexDiskStore | `@Mock` — IndexTest |
| EventBus | `@Mock` with `any()` matchers — verify dispatch calls |
| ConfigManager | `@Mock` with `when(get(...)).thenReturn(...)` — CopyTask, TaskScheduler |
| StorageController | `@Mock` — CopyTask |
| RateLimiter | `@Mock` or real instance — CopyTask |
| MetricStore | `@Mock` — all MetricCollector tests |
| FileStore/Path | `@Mock` — VolumeTest |
| DeviceUtils | `@Mock` static or wrapper — CopyTask |

---

## Easy Level Tests (Pure Logic)

### 1. EventBusTest

| Test Case | Verification |
|-----------|-------------|
| register + dispatch | Listener receives correct event |
| register null eventClass | IllegalArgumentException |
| register null listener | IllegalArgumentException |
| register duplicate | Second registration ignored, listenerCount unchanged |
| unregister + dispatch | Listener no longer fires |
| unregister null | IllegalArgumentException |
| unregister not registered | Silent no-op |
| dispatch null event | IllegalArgumentException |
| dispatch exception isolation | One listener throws → others still execute |
| dispatchAsync | Returns CompletableFuture that completes normally |
| dispatchWithResult | Collects results from async listeners |
| dispatchWithResultMap | Returns listener→result map |
| clearAll | listenerCount() == 0 |
| listenerCount | Accurate before/after register/unregister |
| concurrent register/dispatch | Multi-threaded, no race conditions |

### 2. ConfigSchemaTest

| Test Case | Verification |
|-----------|-------------|
| getAllEntries count | Returns ~45+ entries |
| getAllEntries immutable | UnsupportedOperationException on modification |
| getEntriesByCategory | Each category non-empty, category names correct |
| getEntry known key | Returns correct entry |
| getEntry unknown key | Returns null |
| entry integrity (sampled) | key/description/defaultValue/type/category correct for critical entries |

### 3. ConfigEntryTest

| Test Case | Verification |
|-----------|-------------|
| intEntry factory | type=INTEGER, all fields correct |
| longEntry factory | type=LONG |
| booleanEntry factory | type=BOOLEAN |
| stringEntry factory | type=STRING |
| listEntry factory | type=LIST, default empty list |

### 4. RateLimiterTest

| Test Case | Verification |
|-----------|-------------|
| acquire unlimited (rate=0) | Any byte count returns immediately |
| acquire within burst | bytes ≤ burstSize → no blocking |
| acquire exceeds burst | Blocks until tokens refill |
| token refill over time | Wait + acquire → succeeds without blocking |
| setRateLimit dynamic | New rate takes effect immediately |
| burst size cap | Tokens never exceed burstSize |
| interrupt during acquire | InterruptedException or interrupted status |
| exact rate verification | Multiple acquires, total time matches rate ±10% |
| constructor edge cases | rateLimit=0 OK, burstSize=0 OK |

### 5. FileSelectorTest

| Test Case | Verification |
|-----------|-------------|
| selectByTime basic | Oldest files selected first |
| selectByTime protected | protected=true files skipped |
| selectByTime null files | Returns empty list |
| selectByTime bytesNeeded ≤ 0 | Returns empty list |
| selectByTime insufficient | Returns all non-protected files |
| selectBySize basic | Largest files selected first |
| selectBySize protected | Same as time-based |
| selectAuto CRITICAL | Equivalent to selectBySize |
| selectAuto OK/LOW | Equivalent to selectByTime |
| empty list input | Returns empty list |
| single file exceeds target | Returns that single file |

### 6. ClockThreadTest

| Test Case | Verification |
|-----------|-------------|
| normal countdown (100ms) | onCountdown() future completes |
| thenRun | Action executes after countdown |
| thenRun chaining | Multiple actions execute in order |
| cancel mid-countdown | isCancelled=true, future cancelled |
| cancel idempotent | Multiple calls → no exception |
| pause + resume | Pauses, resumes, eventually completes |
| pause when done | No-op |
| resume when not paused | No-op |
| getRemaining mid-countdown | Approximate remaining time |
| getRemaining after done | Returns 0 |
| isDone/isPaused/isCancelled | Correct for each state |
| constructor null unit | IllegalArgumentException |
| constructor negative delay | IllegalArgumentException |
| onCountdown already done | Returns already-completed future |

### 7. CheckSumTest

| Test Case | Verification |
|-----------|-------------|
| equals same content | true, same hashCode |
| equals different content | false |
| equals null | false |
| constructor | Preserves byte[] content |

### 8. EventRecordsTest

| Test Case | Verification |
|-----------|-------------|
| CopyCompletedEvent isSuccess/isFailure/isCancelled | Each result variant |
| CopyCompletedEvent progressPercentage | Calculated correctly |
| VolumeStateChangedEvent fields | oldState, newState, timestamp |
| DuplicateDetectedEvent fields | checksum, filePath, duplicateCount |
| FileDiscoveredEvent fields | filePath, fileSize, deviceSerial |
| All events description() | Non-null, non-empty |
| All events timestamp() | Matches constructor value |

---

## Medium Level Tests (With Mocks)

### 9. ConfigManagerTest (mock Preferences)

| Test Case | Verification |
|-----------|-------------|
| get default | Returns ConfigEntry.defaultValue() |
| get after set | Returns set value |
| get type safety | int→Integer, string→String |
| set + clear | Returns to default |
| resetToDefaults | All values reset |
| isDeviceBlacklistedBySerial true | Found in blacklist |
| isDeviceBlacklistedBySerial false | Not in blacklist |
| isDeviceBlacklistedBySerial null/empty | Returns false |
| addToDeviceBlacklistBySerial | Added successfully |
| addToDeviceBlacklistBySerial duplicate | No duplicates |
| setDeviceBlacklistBySerial | Replaces entire list |
| setDeviceBlacklistBySerial null | Clears blacklist |
| removeFromDeviceBlacklistBySerial | Removed successfully |
| exportToXml + importFromXml | Round-trip preserves values |
| exportToXml error | IOException for invalid path |
| importFromXml error | Exception for malformed file |
| singleton | getInstance() returns same instance |

### 10. IndexTest (expand existing, mock IndexDiskStore + EventBus + ConfigManager)

| Test Case | Verification |
|-----------|-------------|
| checkDuplicate new | Returns false |
| checkDuplicate existing | Returns true after addFile |
| addFile | Subsequent checkDuplicate returns true |
| addFile dispatches event | FileIndexedEvent dispatched |
| checkDuplicate dispatches event | DuplicateDetectedEvent dispatched |
| load | Cache populated from disk store |
| save dirty | markDirty → save → compact called |
| save not dirty | No disk write |
| clear | All checkDuplicate return false |
| cache eviction | Add > cacheSize entries → LRU eviction |
| getDigestSize | Reflects entry count |
| createForTest | Uses specified path, no singleton side effects |
| concurrent access | Multi-threaded addFile/checkDuplicate |

### 11. IndexDiskStoreTest (expand existing, @TempDir real IO)

| Test Case | Verification |
|-----------|-------------|
| load empty file | Returns empty map |
| load non-existent | Returns empty map |
| load corrupted magic | Returns empty map |
| load corrupted version | Returns empty map |
| append + load | All entries present |
| append duplicate path | Latest checksum wins |
| compact + load | Data intact, file compacted |
| compact atomicity | Temp file + atomic move |
| clear + load | Returns empty map |
| large dataset (1000+) | All entries load correctly |
| concurrent append | No data loss (synchronized) |

### 12. CopyTaskTest (mock StorageController, ConfigManager, RateLimiter, EventBus, DeviceUtils)

| Test Case | Verification |
|-----------|-------------|
| normal copy | SUCCESS result, file at destination |
| copy attributes | Timestamps and DOS attributes preserved |
| source not found | FAILURE result |
| destination exists | Skip or overwrite per implementation |
| storage critical | SKIPPED, no copy |
| file too large (>90% space) | SKIPPED |
| rate limiting | RateLimiter.acquire called during copy |
| interruption | CANCEL result on thread interrupt |
| null deviceSerial | Stored as empty string |
| preVerifiedHash | Skips verification step |
| dispatch on success | CopyCompletedEvent with SUCCESS |
| dispatch on skip | CopyCompletedEvent with SKIPPED |

### 13. TaskSchedulerTest (mock ConfigManager)

| Test Case | Verification |
|-----------|-------------|
| submit task | Task executes |
| submit priority | Higher priority executes first |
| cancelBySerial | Only matching serial cancelled |
| cancelBySerial not found | No-op |
| getActiveRatio empty | 0.0 |
| getActiveRatio active | Correct ratio |
| adaptive dispatch budget | Halved on rejection, restored on success |
| stop | No new tasks accepted |
| concurrent submit | No race conditions |

### 14. ServiceTest (concrete subclass of abstract Service)

| Test Case | Verification |
|-----------|-------------|
| lifecycle STOPPED→RUNNING→STOPPED | State transitions correct |
| pause/resume | RUNNING→PAUSED→RUNNING |
| stop from paused | PAUSED→STOPPED |
| double start | State unchanged |
| double stop | State unchanged |
| tick called periodically | tick() invoked at interval |
| tick paused | tick() not invoked |
| tick exception → FAILED | State becomes FAILED |
| close | Equivalent to stopService() |
| interrupt | Stops correctly |

### 15. VolumeTest (mock FileStore, Path)

| Test Case | Verification |
|-----------|-------------|
| valid transitions | OFFLINE→IDLE, IDLE→DISABLED, etc. |
| invalid transitions | OFFLINE→EJECTING → rejected |
| enable/disable | DISABLED↔IDLE |
| setEjecting | IDLE→EJECTING |
| updateState accessible | UNAVAILABLE→IDLE |
| updateState inaccessible | IDLE→UNAVAILABLE |
| isConnected | True for IDLE, false for OFFLINE |
| VolumeState.isPresent | False only for OFFLINE |
| initial state | OFFLINE |

### 16-22. MetricCollector Tests (mock MetricStore)

**SpeedCollectorTest:**
- createProbe stores by name
- snapshot returns current speed data
- event handling updates speed
- isPersistent → false

**TotalBytesCopiedCollectorTest / TotalFilesCopiedCollectorTest / TotalErrorsCollectorTest:**
- Event counting matches event count
- load/save via MetricStore
- reset clears counter
- isPersistent → true
- Wrong event type → count unchanged

**TotalDevicesCopiedCollectorTest:**
- Unique serials counted once
- Different serials increment
- load/save serializes device list

**VolumeStatsCollectorTest:**
- Per-volume tracking
- File/byte/error/extension counting
- load/save with prefix serialization
- reset clears all volumes

**SessionProgressCollectorTest:**
- Progress = copied / discovered * 100
- FileDiscoveredEvent → discovered count up
- CopyCompletedEvent → copied count up
- isPersistent → false
- Zero discovered → progress = 0 (no div-by-zero)
- reset clears all

---

## Out of Scope (Hard Level — future work)

These require heavy JNA/filesystem mocking and are excluded from this phase:
- DeviceManager (JNA Windows API)
- Sniffer (WatchService, real filesystem)
- StorageController / RecyclerService (real disk IO)
- SnifferLifecycleManager (orchestrates multiple hard dependencies)

## Estimated Test Count

| Category | Classes | ~Test Cases |
|----------|---------|-------------|
| Easy (core) | 5 | ~55 |
| Easy (events) | 1 | ~12 |
| Easy (worker) | 2 | ~25 |
| Easy (index) | 1 | ~4 |
| Medium (core) | 3 | ~45 |
| Medium (worker) | 2 | ~25 |
| Medium (index) | 2 | ~25 |
| Medium (statistics) | 7 | ~40 |
| **Total** | **23** | **~231** |
