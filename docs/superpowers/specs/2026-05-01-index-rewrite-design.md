# Index System Rewrite Design

**Date**: 2026-05-01
**Status**: Approved

## Problem

The current `Index` uses `CopyOnWriteArraySet<CheckSum>` storing only SHA-256 hashes. This means:

- Content-based dedup: files with identical content across different USBs are treated as duplicates
- No path tracking: impossible to distinguish "same file seen again" from "different file with same hash"
- Hash collision vulnerability: if two different files produce the same SHA-256, one is silently skipped
- `CopyOnWriteArraySet` copies the entire set on every write — expensive at scale

## Requirements

1. Path-keyed dedup: each unique source path is tracked independently
2. LRU eviction: bounded in-memory cache with overflow to disk
3. Disk-backed persistence: custom binary format with append + compaction
4. Same content on different USBs = not a duplicate (path-based identity)

## Architecture

### Overview

```
┌─────────────────────────────────────┐
│           Index (Service)            │
│                                      │
│  Caffeine<Path, CheckSum>            │  ← In-memory LRU (10,000 entries)
│    .maximumSize(10_000)              │
│    .removalListener → write to disk  │
│                                      │
│  IndexDiskStore                      │  ← Persistent binary file
│    Header: magic + version + count   │
│    Entries: path + hash pairs        │
└─────────────────────────────────────┘
```

### Data Flow

1. **Lookup** (`checkDuplicate`): Caffeine cache → disk store → not found
2. **Add** (`addFile`): `cache.put(path, checksum)` → Caffeine evicts LRU → `RemovalListener` writes to disk
3. **Cache miss**: falls through to `IndexDiskStore.lookup()` → if found, promote to cache
4. **Persistence**: evicted entries appended to disk file; periodic compaction rewrites the file

### checkDuplicate Logic

```java
boolean checkDuplicate(Path filePath, CheckSum checksum) {
    // 1. Check in-memory cache (Caffeine)
    CheckSum cached = cache.getIfPresent(filePath);
    if (cached != null) {
        return cached.equals(checksum); // same content = duplicate
    }

    // 2. Cache miss → check disk store
    CheckSum fromDisk = diskStore.lookup(filePath);
    if (fromDisk != null) {
        cache.put(filePath, fromDisk); // promote to cache
        return fromDisk.equals(checksum);
    }

    // 3. Not found → new file
    return false;
}
```

Edge cases:
- Same path, same content → duplicate (skip)
- Same path, different content → NOT duplicate (path reused, e.g. file overwritten). `addFile` updates the entry.
- New path → not duplicate

## Binary Disk Format

```
Header (16 bytes):
  magic:     4 bytes  "IDXF"
  version:   4 bytes  (int = 1)
  count:     4 bytes  (int, total entries)
  reserved:  4 bytes  (0x00000000)

Entry (variable length, repeated):
  path_len:  2 bytes  (unsigned short, max 65535)
  path_utf8: path_len bytes
  hash_len:  1 byte   (always 32 for SHA-256)
  hash:      hash_len bytes
```

- **Append-only writes**: evicted entries are appended to the file (fast, no rewrite)
- **Compaction**: periodic full rewrite that deduplicates entries (latest path→hash wins) and updates the header count
- **Load**: read all entries into a `HashMap<Path, CheckSum>` for O(1) disk lookup during cold starts

## Components

### Index.java (rewrite)

- Replaces `CopyOnWriteArraySet<CheckSum>` with `Caffeine<Path, CheckSum>`
- `RemovalListener` delegates evicted entries to `IndexDiskStore.append()`
- `addFile()`: calls `cache.put(path, checksum)`
- `checkDuplicate()`: cache → disk → not found
- Periodic `tick()` runs compaction if dirty
- `load()`: loads disk entries, pre-populates cache (up to max size)
- `save()`: compacts disk store

### IndexDiskStore.java (new)

- Manages the binary index file
- `load()`: reads all entries into `HashMap<Path, CheckSum>`
- `lookup(Path)`: checks the in-memory map
- `append(Path, CheckSum)`: appends a single entry to the file
- `compact(Map<Path, CheckSum>)`: rewrites the file with deduplicated entries
- Thread-safe via `synchronized` on write operations

### ConfigSchema Changes

- New entry: `INDEX_CACHE_SIZE` (int, default 10_000) — maximum Caffeine cache entries

## Dependency Changes

### pom.xml

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.2.3</version>
</dependency>
```

### module-info.java

```java
requires com.github.benmanes.caffeine;
```

## Migration

- Old `index.obj` (Java Serialization) is **not compatible** with new format
- On first launch with new code, old `index.obj` is ignored (or deleted)
- Index starts fresh — all files will be treated as new on first scan
- This is acceptable because the dedup strategy has changed (content-based → path-based)

## Files Changed

| File | Change |
|------|--------|
| `Index.java` | Rewrite: Caffeine cache + IndexDiskStore |
| `IndexDiskStore.java` | New: binary file I/O |
| `CheckSum.java` | Remove `Serializable`, keep as plain record |
| `pom.xml` | Add Caffeine dependency |
| `module-info.java` | Add `requires com.github.benmanes.caffeine` |
| `ConfigSchema.java` | Add `INDEX_CACHE_SIZE` entry |
| `CopyTask.java` | No change (calls `Index.addFile()`) |
| `VerifyTask.java` | No change (calls `Index.checkDuplicate()`) |
