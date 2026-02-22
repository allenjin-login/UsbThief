# Index Package

**Parent:** [../../../../../AGENTS.md](../../../../../AGENTS.md)

## OVERVIEW
File indexing and checksum-based deduplication. Index Service persists checksums for duplicate detection across sessions.

## STRUCTURE
```
index/
├── Index.java             # Service subclass, manages CheckSum digest
├── CheckSum.java          # SHA-256 checksum record (byte[] context)
├── FileHistoryRecord.java # Failed copy tracking record
└── package-info.java
```

## WHERE TO LOOK
| Task | File | Key Method |
|------|------|------------|
| Check for duplicates | Index.java | `contains(CheckSum)`, `add(CheckSum)` |
| Compute file hash | CheckSum.java | `CheckSum.verify(Path)` |
| Persist index | Index.java | `save()`, `load()` |
| Track failed copy | FileHistoryRecord.java | Constructor |

## KEY PATTERNS

### CheckSum Computation
```java
// SHA-256 hash with ThreadLocal buffer for thread-pool safety
public static CheckSum verify(Path path) throws IOException {
    ByteBuffer buffer = bufferThreadLocal.get();
    try (FileChannel readChannel = FileChannel.open(path, StandardOpenOption.READ)) {
        while (readChannel.read(buffer) != -1) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
    } finally {
        buffer.clear(); // MUST clear for reuse
    }
    return new CheckSum(digest.digest());
}
```

### Index Persistence
- **Storage**: Serialized objects to `index.obj` (path from ConfigSchema.INDEX_PATH)
- **Format**: ObjectInputStream/ObjectOutputStream for CheckSum objects
- **Dirty flag**: Only saves when `dirty=true` (set on add/remove)
- **Tick interval**: 60000ms (saves periodically if dirty)

### Deduplication Flow
```
1. CopyTask computes CheckSum via CheckSum.verify(file)
2. QueueManager.checkDuplicate(checksum) queries Index
3. If contains() → skip copy, dispatch DuplicateDetectedEvent
4. If new → add to digest, mark dirty, proceed with copy
```

## CONCURRENCY
- **digest**: `ConcurrentHashMap.newKeySet()` for O(1) add/contains
- **ThreadLocal buffer**: CheckSum uses ThreadLocal ByteBuffer, MUST clear() in finally
- **dirty flag**: volatile, set on modification

## ANTI-PATTERNS (index-specific)
- **DO NOT forget buffer.clear()** - ThreadLocal buffers must be reset for reuse
- **DO NOT mutate CheckSum records** - immutable after creation
- **DO NOT skip dirty check** - save() is no-op if not dirty, safe to call frequently
