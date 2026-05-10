# Index Cache Refactor Design

Remove index persistence, switch to explicit LFU eviction, and include disk serial number in index keys.

## Motivation

1. **Persistence adds complexity without strong benefit** — the index rebuilds on each scan anyway, and the disk store introduces fragility (file corruption, migration on algorithm change, periodic save I/O).
2. **Drive letter reuse causes false deduplication** — if USB-A is assigned `E:` and later USB-B gets `E:`, the index incorrectly matches `E:\foo.txt` from the old disk. Including the volume serial number in the key solves this.
3. **LFU is already Caffeine's default** (Window TinyLFU), but the code comments and `getDescription()` say "LRU". Making LFU explicit avoids confusion.

## Changes

### 1. New `IndexKey` record

```java
// com.superredrock.usbthief.index.IndexKey
public record IndexKey(String serialNumber, Path filePath) {}
```

- `serialNumber` — volume serial from `Volume.getSerialNumber()` (8-char hex from Windows API)
- `filePath` — source file absolute path
- Record provides correct `equals`/`hashCode` automatically

### 2. `Index` class refactored

**Remove:**
- `IndexDiskStore diskStore` field
- `dirty` flag and `markDirty()`/`isDirty()` methods
- `resolveIndexPath()` and `ensureDirectories()` methods
- `load()` method (no persistence to load)
- `save()` method (no persistence to save)
- `getIndexPath()` method
- `removalListener` that writes evicted entries to disk
- `cleanup()` override that called `save()`
- `tick()`/`getTickInterval()`/`getTickUnit()` — Index no longer needs to be a timed service
- `extends Service` — Index no longer needs background thread execution; becomes a plain singleton

**Modify:**
- Cache type: `Cache<Path, CheckSum>` → `Cache<IndexKey, CheckSum>`
- Add `.recordStats()` to Caffeine builder for frequency tracking visibility
- `checkDuplicate(Path, CheckSum)` → `checkDuplicate(IndexKey, CheckSum)` — checks in-memory cache only (no disk fallback)
- `addFile(CheckSum, Path, long)` → `addFile(CheckSum, IndexKey, long)` — stores in cache only
- Constructor simplified: takes only `maxSize`, no `indexPath`
- `getDescription()` updated to "File index with LFU cache"
- `getStatus()` updated: remove dirty/clean state

**Keep:**
- `clear()` — invalidates all cache entries (no disk clear)
- `getDigestSize()` — returns `cache.estimatedSize()`
- Singleton pattern
- Algorithm change detection: move into `HashAlgorithmDialog` only (clear cache + update config)
- `DuplicateDetectedEvent` and `FileIndexedEvent` dispatch

### 3. Callers updated

**`CopyTask`** (`worker/CopyTask.java`):
```java
// Before
QueueManager.getIndex().addFile(hash, source, size);

// After
IndexKey key = new IndexKey(deviceSerial, source);
QueueManager.getIndex().addFile(hash, key, size);
```

**`VerifyTask`** (`worker/VerifyTask.java`):
```java
// Before
QueueManager.getIndex().checkDuplicate(processingPath, hash);

// After
IndexKey key = new IndexKey(deviceSerial, processingPath);
QueueManager.getIndex().checkDuplicate(key, hash);
```

Both classes already have `deviceSerial` as a field.

### 4. Service lifecycle changes

**`Main.java`:**
- Remove `Index.getInstance().load()` call (line 48)
- Remove `Index.getInstance().start()` call (line 59) — no background thread needed
- Remove `Index.getInstance().stopService()` call (line 95) — no service to stop

**`QueueManager.java`:**
- Remove `index.stopService()` (line 60) and `index.save()` (line 64) from shutdown sequence
- Remove comment "Stop index periodic save service" and "Save index"

### 5. Remove persistence events

Delete:
- `IndexSavedEvent` (`core/event/index/IndexSavedEvent.java`)
- `IndexLoadedEvent` (`core/event/index/IndexLoadedEvent.java`)

Update listeners in:
- `LogPanel` — remove `onIndexSaved()` and `onIndexLoaded()` handlers and registrations
- `SnifferDebugDialog` — remove `IndexSavedEvent`/`IndexLoadedEvent` from filter cases

### 6. Remove persistence config entries

Remove from `ConfigSchema`:
- `SAVE_INITIAL_DELAY_SECONDS` — no periodic save needed
- `SAVE_DELAY_SECONDS` — no periodic save needed
- `INDEX_PATH` — no disk file needed
- `HASH_ALGORITHM_LAST` — only used by `Index.load()` to detect algorithm changes; without persistence this is unnecessary

Remove `HASH_ALGORITHM_LAST` from `HashAlgorithmDialog` — the dialog already calls `QueueManager.getIndex().clear()` on algorithm change, which is sufficient.

### 7. Delete `IndexDiskStore`

Delete `index/IndexDiskStore.java` and `test/.../IndexDiskStoreTest.java` entirely.

### 8. Update tests

- `IndexTest` — update all `checkDuplicate`/`addFile` calls to use `IndexKey`; remove `@TempDir` from `setUp` (no disk path needed); update `createForTest` to match new constructor
- `IndexDiskStoreTest` — deleted
- `CopyTaskTest` — update any `checkDuplicate`/`addFile` calls to use `IndexKey`

## Files Changed

| File | Action |
|------|--------|
| `index/IndexKey.java` | **New** — record |
| `index/Index.java` | **Rewrite** — remove persistence, LFU cache, IndexKey |
| `index/IndexDiskStore.java` | **Delete** |
| `core/event/index/IndexSavedEvent.java` | **Delete** |
| `core/event/index/IndexLoadedEvent.java` | **Delete** |
| `core/config/ConfigSchema.java` | **Edit** — remove 4 entries |
| `worker/CopyTask.java` | **Edit** — use IndexKey |
| `worker/VerifyTask.java` | **Edit** — use IndexKey |
| `gui/LogPanel.java` | **Edit** — remove event handlers |
| `gui/dailog/SnifferDebugDialog.java` | **Edit** — remove event filter cases |
| `gui/dailog/HashAlgorithmDialog.java` | **Edit** — remove HASH_ALGORITHM_LAST |
| `Main.java` | **Edit** — remove Index.load()/start()/stopService() |
| `core/QueueManager.java` | **Edit** — remove Index stopService/save from shutdown |
| `test/.../IndexTest.java` | **Edit** — use IndexKey |
| `test/.../IndexDiskStoreTest.java` | **Delete** |
| `test/.../CopyTaskTest.java` | **Edit** — use IndexKey if applicable |

## Out of Scope

- Changing the hash algorithm system itself
- Changing how volume serial numbers are obtained
- UI changes (other than removing persistence event handlers)
- Any changes to the copy/verify pipeline beyond the IndexKey integration
