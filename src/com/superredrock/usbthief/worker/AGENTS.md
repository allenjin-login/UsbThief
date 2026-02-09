# Worker Package - File Monitoring & Copying

**Generated:** 2026-01-30
**Last Updated:** 2026-02-02 (Added TaskScheduler system)

## OVERVIEW
File system monitoring and copy task execution with checksum verification. Now includes priority-based task scheduling with adaptive load control.

## STRUCTURE
```
worker/
├── TaskScheduler.java       # Priority-based task dispatcher (NEW 2026-02-02)
├── PriorityCopyTask.java    # Wrapper with priority metadata (NEW)
├── PriorityRule.java        # Priority calculation engine (NEW)
├── LoadEvaluator.java       # System load calculator (NEW)
├── LoadScore.java           # Load score record (NEW)
├── LoadLevel.java           # Load level enum (NEW)
├── DeviceScanner.java       # WatchService-based file monitoring
├── CopyTask.java            # Callable file copy with ThreadLocal buffers
├── CopyResult.java          # Enum: SUCCESS, FAIL, CANCEL
├── SpeedMonitor.java        # Real-time copy speed tracking
├── RateLimiter.java         # Token bucket rate limiter
├── Recycler.java            # ThreadLocal buffer cleanup on shutdown
└── package-info.java
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Priority-based scheduling | `TaskScheduler` | PriorityQueue with adaptive dispatch (NEW) |
| Priority calculation | `PriorityRule` | Extension + size-based scoring (NEW) |
| Load evaluation | `LoadEvaluator` | Queue/speed/thread metrics (NEW) |
| Real-time file monitoring | `DeviceScanner` | WatchService + threshold triggering |
| File copying | `CopyTask.call()` | Checks Index before copying |
| Copy outcome | `CopyResult` | Returned from CopyTask |
| Submit copy tasks | `TaskScheduler.submit()` | Wrapped before ThreadPoolExecutor (UPDATED) |

## CONVENTIONS
- **Two-phase scanning**: Initial scan followed by WatchService monitoring
- **Threshold triggering**: Files copy after N changes (Config.watchThreshold)
- **ThreadLocal buffers**: CopyTask uses ThreadLocal<ByteBuffer> - always clear() after use
- **Checksum verification**: Check Index.digest before copying to deduplicate
- **Resource cleanup**: Try-with-resources for FileChannel and WatchService
- **Priority-based submission** (NEW): All copy tasks wrapped in PriorityCopyTask via TaskScheduler
- **Adaptive dispatch** (NEW): Batch size varies by load (LOW/MEDIUM/HIGH)

## ANTI-PATTERNS (THIS PACKAGE)
- **DO NOT skip ThreadLocal.clear()** - buffers must be reset for thread-pool reuse
- **DO NOT assume files exist** - check Index.digest and file existence first
- **DO NOT use blocking I/O in event loop** - CopyTask should run in ThreadPoolExecutor
- **DO NOT bypass TaskScheduler** (NEW): Always submit via TaskScheduler.submit(), never direct to QueueManager
- **DO NOT hard-code priorities** (NEW): Use PriorityRule.calculatePriority() for consistency

## UNIQUE STYLES
- **Change counter**: Batches file changes before copy operations
- **Auto-reset**: Prevents counter accumulation (Config.watchResetIntervalSeconds)
- **WatchService monitoring**: Real-time detection of CREATE/MODIFY/DELETE events
- **Priority scoring** (NEW): Extension-based (PDF=10, TMP=1) + size adjustment (+2/-2)
- **Load-aware dispatch** (NEW): Queue depth (40%), copy speed (40%), thread activity (20%)
- **Graceful degradation** (NEW): Falls back to FIFO submission on errors
