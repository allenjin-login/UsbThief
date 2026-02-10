# Index Package - File Deduplication

**Generated:** 2026-01-30
**Updated:** 2026-02-09 (Removed FileHistory functionality)

## OVERVIEW
Checksum-based file index for deduplication and persistence.

## STRUCTURE
```
index/
├── Index.java               # Index management with ConcurrentHashMap.newKeySet()
├── CheckSum.java            # Record with ThreadLocal buffer
├── FileHistoryRecord.java   # Failed copy record (used by FileHistoryPanel)
└── package-info.java
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Add/check file checksum | `Index.digest()` | Returns CheckSum if exists |
| Add file to index | `Index.addFile()` | Dispatches FileIndexedEvent |
| Load/save index | `Index.load()`, `Index.save()` | Uses ObjectOutputStream |

## CONVENTIONS
- **Deduplication**: Checksum-based - same checksum = same content
- **Concurrent access**: Index.digest uses ConcurrentHashMap.newKeySet() for O(1) add
- **ThreadLocal buffers**: CheckSum.verify() uses ThreadLocal<ByteBuffer> - always clear()
- **Persistence**: Index saved periodically via Service scheduler
- **No history tracking**: Index only tracks checksums, not copy history

## ANTI-PATTERNS (THIS PACKAGE)
- **DO NOT skip ThreadLocal.clear()** - buffers must be reset for thread-pool reuse
- **DO NOT use Index for history** - history is now tracked separately by FileHistoryPanel

## NOTES
- Index uses ObjectOutputStream serialization - see CONFIG.md for persistence configuration
- Failed copy records are displayed in FileHistoryPanel, not stored in Index
- Module exports this package (see module-info.java)
