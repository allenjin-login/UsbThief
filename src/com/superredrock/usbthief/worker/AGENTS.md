# Worker Package

**Parent:** [../../../../../AGENTS.md](../../../../../AGENTS.md)

## OVERVIEW
Priority-based task scheduling with adaptive load control. Rate-limited copy execution with lock-free speed tracking.

## STRUCTURE
```
worker/
├── TaskScheduler.java     # Tick-based priority scheduler (500ms)
├── PriorityTask.java      # Wrapper with 0-100 priority, Comparable
├── PriorityRule.java      # Extensible priority computation
├── LoadEvaluator.java     # Queue depth + thread ratio → LoadScore
├── LoadLevel.java         # Enum: LOW/MEDIUM/HIGH
├── LoadScore.java         # Record: (score, level)
├── RateLimiter.java       # Token bucket with load-aware adjustment
├── CopyTask.java          # Runnable copy operation
├── SpeedProbe.java        # Per-thread speed measurement (ThreadLocal)
├── SpeedProbeGroup.java   # Lock-free probe collection (ConcurrentLinkedQueue)
├── Recycler.java          # Object pooling for frequent allocations
├── Sniffer.java           # USB change detection (WMI events)
└── package-info.java
```

## WHERE TO LOOK
| Task | File | Key Method |
|------|------|------------|
| Adjust scheduling | TaskScheduler.java | `tick()`, `handleHighLoad()` |
| Change priority logic | PriorityRule.java | `calculatePriority()` |
| Tune load thresholds | LoadEvaluator.java | `lowThreshold`, `highThreshold` |
| Modify rate limiting | RateLimiter.java | `adjustRateLimit(LoadLevel)` |
| Track copy speed | SpeedProbeGroup.java | `getTotalBytesPerSecond()` |

## KEY PATTERNS

### Priority Scheduling Flow
```
1. PriorityRule.calculatePriority(task) → int (0-100)
2. PriorityTask wrapper created, enqueued to PriorityQueue
3. TaskScheduler.tick():
   - LoadEvaluator.evaluate() → LoadScore → LoadLevel
   - If HIGH: batch dequeue (50), submit to executor
   - If MEDIUM: batch dequeue (20)
   - If LOW: single dequeue
4. RateLimiter.adjustRateLimit(level) adjusts throughput
```

### Load Evaluation Algorithm
```java
// LoadScore = queueDepth% * 0.35 + threadRatio% * 0.35 + activity% * 0.15 + rejection% * 0.15
score = (queueRatio * 0.35) + (threadRatio * 0.35) 
      + (activityRatio * 0.15) + (rejectionRatio * 0.15);
level = score < lowThreshold ? LOW : score > highThreshold ? HIGH : MEDIUM;
```

### Rate Limiter Adaptation
| LoadLevel | Rate Multiplier | Batch Size |
|-----------|-----------------|------------|
| LOW | 100% | 10 |
| MEDIUM | 70% | 20 |
| HIGH | 40% | 50 |

## CONCURRENCY TECHNIQUES
- **PriorityQueue access**: `synchronized (priorityQueue)` for enqueue/dequeue
- **SpeedProbe**: ThreadLocal byte accumulation, AtomicLong for totals
- **SpeedProbeGroup**: ConcurrentLinkedQueue + WeakReference + ReferenceQueue for lock-free cleanup
- **Recycler**: Object pooling to reduce GC pressure during task bursts

## ANTI-PATTERNS (worker-specific)
- **DO NOT bypass RateLimiter** - always check `acquire(bytes)` before copy
- **DO NOT create PriorityTask directly** - use TaskScheduler submission methods
- **DO NOT modify LoadLevel thresholds at runtime** - they're config-driven, restart required
