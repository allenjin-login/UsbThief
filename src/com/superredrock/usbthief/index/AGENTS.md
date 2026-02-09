# Index Package - File Deduplication & Persistence

**Generated:** 2026-01-30

## OVERVIEW
Checksum-based file index for deduplication and persistence.

## STRUCTURE
```
index/
├── Index.java               # Index management with ConcurrentHashMap.newKeySet()
├── CheckSum.java            # Record with ThreadLocal buffer
├── IndexSavesService.java   # Index persistence scheduling
└── package-info.java
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Add/check file checksum | `Index.digest()` | Returns CheckSum if exists |
| Index persistence | `IndexSavesService` | Scheduled saves (Config.saveDelaySeconds) |
| Load/save index | `Index.load()`, `Index.save()` | Uses ObjectOutputStream |

## CONVENTIONS
- **Deduplication**: Checksum-based - same checksum = same content
- **Concurrent access**: Index.digest uses ConcurrentHashMap.newKeySet() for O(1) add
- **ThreadLocal buffers**: CheckSum.verify() uses ThreadLocal<ByteBuffer> - always clear()
- **Persistence**: Index saved periodically via IndexSavesService scheduler

## ANTI-PATTERNS (THIS PACKAGE)
- **DO NOT skip ThreadLocal.clear()** - buffers must be reset for thread-pool reuse
- **DO NOT modify Index.indexSet directly** - use Index.digest() method

## NOTES
- Index uses ObjectOutputStream serialization - see CONFIG.md for persistence configuration
- IndexSavesService runs on QueueManager.ScheduledExecutor
- Module exports this package (see module-info.java)
