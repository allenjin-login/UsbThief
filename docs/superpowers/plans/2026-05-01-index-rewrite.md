# Index System Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current `CopyOnWriteArraySet<CheckSum>` index with a Caffeine-backed LRU cache using path-keyed dedup and a custom binary disk format.

**Architecture:** `Index` holds a `Caffeine<Path, CheckSum>` cache (max 10k entries). On eviction, entries are appended to a binary disk file via `IndexDiskStore`. Cache misses fall through to disk lookup. Periodic compaction rewrites the disk file to remove stale entries.

**Tech Stack:** Caffeine 3.2.3, Java NIO, Log4j2

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `pom.xml` | Modify | Add Caffeine dependency |
| `src/module-info.java` | Modify | Add `requires com.github.benmanes.caffeine` |
| `src/.../core/config/ConfigSchema.java` | Modify | Add `INDEX_CACHE_SIZE` entry |
| `src/.../index/CheckSum.java` | Modify | Remove `Serializable` |
| `src/.../index/IndexDiskStore.java` | Create | Binary file I/O: load, lookup, append, compact |
| `src/.../index/Index.java` | Modify | Rewrite: Caffeine cache + IndexDiskStore integration |
| `test/.../index/IndexDiskStoreTest.java` | Create | Tests for binary format I/O |
| `test/.../index/IndexTest.java` | Create | Tests for Index checkDuplicate/addFile logic |

---

### Task 1: Add Caffeine Dependency

**Files:**
- Modify: `pom.xml:41-93` (dependencies section)
- Modify: `src/module-info.java:1-24`

- [ ] **Step 1: Add Caffeine to pom.xml dependencies**

Add after the Log4j2 dependencies block (after line 69):

```xml
        <!-- Caffeine - High-performance in-memory cache -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
            <version>3.2.3</version>
        </dependency>
```

- [ ] **Step 2: Add Caffeine to module-info.java requires**

Add after the `requires org.apache.logging.log4j.core;` line:

```java
    requires com.github.benmanes.caffeine;
```

- [ ] **Step 3: Add Caffeine module to jlink --add-modules**

In `pom.xml`, find the `<argument>--add-modules</argument>` section and append `,com.github.benmanes.caffeine` to the module list. The line becomes:

```xml
<argument>UsbThief,com.formdev.flatlaf,com.sun.jna,com.sun.jna.platform,org.apache.logging.log4j,org.apache.logging.log4j.core,com.github.benmanes.caffeine</argument>
```

- [ ] **Step 4: Verify compilation**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/module-info.java
git commit -m "build: add Caffeine 3.2.3 dependency for index cache"
```

---

### Task 2: Add ConfigSchema Entry for Cache Size

**Files:**
- Modify: `src/.../core/config/ConfigSchema.java:34-42` (Index Management section)

- [ ] **Step 1: Add INDEX_CACHE_SIZE entry**

After the existing `INDEX_PATH` entry (line 42), add:

```java
    public static final ConfigEntry<Integer> INDEX_CACHE_SIZE =
            intEntry("indexCacheSize", "Maximum number of entries in the in-memory index cache", 10_000, "Index Management");
```

- [ ] **Step 2: Register the entry**

In the `static {}` block, after `registerEntry(INDEX_PATH);` (line 186), add:

```java
        registerEntry(INDEX_CACHE_SIZE);
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/core/config/ConfigSchema.java
git commit -m "feat: add INDEX_CACHE_SIZE config entry for Caffeine cache"
```

---

### Task 3: Simplify CheckSum Record

**Files:**
- Modify: `src/.../index/CheckSum.java`

- [ ] **Step 1: Remove Serializable**

Rewrite `CheckSum.java` to:

```java
package com.superredrock.usbthief.index;

import java.util.Arrays;

public record CheckSum(byte[] context) {
    @Override
    public boolean equals(Object obj) {
        return switch (obj) {
            case CheckSum that -> Arrays.equals(context, that.context);
            case null, default -> false;
        };
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(context);
    }
}
```

Changes: removed `implements Serializable`, renamed `characteristics` → `that` in pattern match (cleaner).

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/index/CheckSum.java
git commit -m "refactor: remove Serializable from CheckSum record"
```

---

### Task 4: Create IndexDiskStore — Binary File I/O

**Files:**
- Create: `src/.../index/IndexDiskStore.java`
- Create: `test/.../index/IndexDiskStoreTest.java`

- [ ] **Step 1: Write IndexDiskStoreTest with failing tests**

Create `test/com/superredrock/usbthief/index/IndexDiskStoreTest.java`:

```java
package com.superredrock.usbthief.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndexDiskStoreTest {

    private static final byte[] HASH_A = new byte[32]; // all zeros
    private static final byte[] HASH_B = new byte[32];
    static { HASH_B[0] = 1; }

    @Test
    void loadReturnsEmptyMapForNonexistentFile(@TempDir Path dir) {
        IndexDiskStore store = new IndexDiskStore(dir.resolve("no-such-file.idx"));
        Map<Path, CheckSum> entries = store.load();
        assertTrue(entries.isEmpty());
    }

    @Test
    void appendAndLoadRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("test.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\test\\file.txt");
        CheckSum checksumA = new CheckSum(HASH_A);
        store.append(pathA, checksumA);

        Map<Path, CheckSum> entries = store.load();
        assertEquals(1, entries.size());
        assertEquals(checksumA, entries.get(pathA));
    }

    @Test
    void appendMultipleEntries(@TempDir Path dir) {
        Path file = dir.resolve("test.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\docs\\a.pdf");
        Path pathB = Path.of("E:\\docs\\b.pdf");
        CheckSum csA = new CheckSum(HASH_A);
        CheckSum csB = new CheckSum(HASH_B);

        store.append(pathA, csA);
        store.append(pathB, csB);

        Map<Path, CheckSum> entries = store.load();
        assertEquals(2, entries.size());
        assertEquals(csA, entries.get(pathA));
        assertEquals(csB, entries.get(pathB));
    }

    @Test
    void compactDeduplicatesKeepingLatest(@TempDir Path dir) {
        Path file = dir.resolve("test.idx");
        IndexDiskStore store = new IndexDiskStore(file);

        Path pathA = Path.of("E:\\test\\file.txt");
        CheckSum csOld = new CheckSum(HASH_A);
        CheckSum csNew = new CheckSum(HASH_B);

        store.append(pathA, csOld);
        store.append(pathA, csNew); // same path, different hash

        // Compact with the latest map
        store.compact(Map.of(pathA, csNew));

        Map<Path, CheckSum> entries = store.load();
        assertEquals(1, entries.size());
        assertEquals(csNew, entries.get(pathA));
    }

    @Test
    void loadHandlesCorruptFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("corrupt.idx");
        // Write garbage
        java.nio.file.Files.writeString(file, "not a valid index file");
        IndexDiskStore store = new IndexDiskStore(file);

        Map<Path, CheckSum> entries = store.load();
        assertTrue(entries.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=IndexDiskStoreTest -DfailIfNoTests=false 2>&1 | tail -5`
Expected: Compilation error or test failure (class not found)

- [ ] **Step 3: Implement IndexDiskStore**

Create `src/com/superredrock/usbthief/index/IndexDiskStore.java`:

```java
package com.superredrock.usbthief.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

class IndexDiskStore {

    private static final Logger logger = LogManager.getLogger(IndexDiskStore.class);
    private static final int MAGIC = 0x49445846; // "IDXF"
    private static final int VERSION = 1;

    private final Path filePath;

    IndexDiskStore(Path filePath) {
        this.filePath = filePath;
    }

    Map<Path, CheckSum> load() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }

        try (DataInputStream in = new DataInputStream(
                Files.newInputStream(filePath, StandardOpenOption.READ))) {

            int magic = in.readInt();
            if (magic != MAGIC) {
                logger.warn("Invalid index file magic: {}, expected {}", magic, MAGIC);
                return new HashMap<>();
            }

            int version = in.readInt();
            if (version != VERSION) {
                logger.warn("Unsupported index version: {}, expected {}", version, VERSION);
                return new HashMap<>();
            }

            int count = in.readInt();
            in.readInt(); // reserved

            Map<Path, CheckSum> entries = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                try {
                    Path path = readPath(in);
                    CheckSum checksum = readCheckSum(in);
                    if (path != null && checksum != null) {
                        entries.put(path, checksum);
                    }
                } catch (IOException e) {
                    logger.warn("Error reading entry {} in index file", i, e);
                    break;
                }
            }
            return entries;
        } catch (IOException e) {
            logger.warn("Failed to load index file", e);
            return new HashMap<>();
        }
    }

    synchronized void append(Path path, CheckSum checksum) {
        boolean fileExists = Files.exists(filePath);
        try (DataOutputStream out = new DataOutputStream(
                Files.newOutputStream(filePath,
                        fileExists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE))) {

            if (!fileExists) {
                writeHeader(out, 0);
            }

            writeEntry(out, path, checksum);
        } catch (IOException e) {
            logger.warn("Failed to append to index file", e);
        }
    }

    synchronized void compact(Map<Path, CheckSum> entries) {
        try {
            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");

            try (DataOutputStream out = new DataOutputStream(
                    Files.newOutputStream(tempFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                writeHeader(out, entries.size());
                for (var entry : entries.entrySet()) {
                    writeEntry(out, entry.getKey(), entry.getValue());
                }
            }

            Files.move(tempFile, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            logger.info("Index compacted: {} entries", entries.size());
        } catch (IOException e) {
            logger.warn("Failed to compact index file", e);
        }
    }

    private void writeHeader(DataOutputStream out, int count) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(count);
        out.writeInt(0); // reserved
    }

    private void writeEntry(DataOutputStream out, Path path, CheckSum checksum) throws IOException {
        byte[] pathBytes = path.toString().getBytes(StandardCharsets.UTF_8);
        out.writeShort(pathBytes.length);
        out.write(pathBytes);
        out.writeByte(checksum.context().length);
        out.write(checksum.context());
    }

    private Path readPath(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return Path.of(new String(bytes, StandardCharsets.UTF_8));
    }

    private CheckSum readCheckSum(DataInputStream in) throws IOException {
        int len = in.readUnsignedByte();
        byte[] hash = new byte[len];
        in.readFully(hash);
        return new CheckSum(hash);
    }

    Path getFilePath() {
        return filePath;
    }
}
```

Note: `import java.nio.file.StandardCopyOption` is used for `Files.move()` in `compact()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=IndexDiskStoreTest -q`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/com/superredrock/usbthief/index/IndexDiskStore.java test/com/superredrock/usbthief/index/IndexDiskStoreTest.java
git commit -m "feat: add IndexDiskStore with custom binary format"
```

---

### Task 5: Rewrite Index.java with Caffeine Cache

**Files:**
- Modify: `src/.../index/Index.java` (full rewrite)
- Create: `test/.../index/IndexTest.java`

- [ ] **Step 1: Write IndexTest with failing tests**

Create `test/com/superredrock/usbthief/index/IndexTest.java`:

```java
package com.superredrock.usbthief.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private static final byte[] HASH_A = new byte[32];
    private static final byte[] HASH_B = new byte[32];
    static { HASH_B[0] = 1; }

    private Index index;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        // Create a fresh Index with a temp directory for testing
        // Reset singleton via reflection for test isolation
        try {
            var field = Index.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // We need to set the INDEX_PATH config before Index constructor runs
        // For now, use a package-private test constructor
        index = Index.createForTest(dir.resolve("test.idx"));
    }

    @Test
    void newFileIsNotDuplicate() {
        Path file = Path.of("E:\\newfile.txt");
        CheckSum cs = new CheckSum(HASH_A);

        assertFalse(index.checkDuplicate(file, cs));
    }

    @Test
    void addedFileIsDuplicate() {
        Path file = Path.of("E:\\file.txt");
        CheckSum cs = new CheckSum(HASH_A);

        index.addFile(cs, file, 100);
        assertTrue(index.checkDuplicate(file, cs));
    }

    @Test
    void samePathDifferentContentIsNotDuplicate() {
        Path file = Path.of("E:\\file.txt");
        CheckSum csA = new CheckSum(HASH_A);
        CheckSum csB = new CheckSum(HASH_B);

        index.addFile(csA, file, 100);
        // Same path, different content → not a duplicate
        assertFalse(index.checkDuplicate(file, csB));
    }

    @Test
    void differentPathSameContentIsNotDuplicate() {
        Path fileA = Path.of("E:\\fileA.txt");
        Path fileB = Path.of("E:\\fileB.txt");
        CheckSum cs = new CheckSum(HASH_A);

        index.addFile(cs, fileA, 100);
        // Different path, same content → NOT duplicate (path-based dedup)
        assertFalse(index.checkDuplicate(fileB, cs));
    }

    @Test
    void addFileReturnsTrueForNewEntry() {
        Path file = Path.of("E:\\file.txt");
        CheckSum cs = new CheckSum(HASH_A);

        assertTrue(index.addFile(cs, file, 100));
        // Adding same path again returns false (already indexed)
        assertFalse(index.addFile(cs, file, 100));
    }

    @Test
    void clearRemovesAllEntries() {
        Path file = Path.of("E:\\file.txt");
        CheckSum cs = new CheckSum(HASH_A);

        index.addFile(cs, file, 100);
        index.clear();

        assertFalse(index.checkDuplicate(file, cs));
        assertEquals(0, index.getDigestSize());
    }

    @Test
    void getDigestSizeReturnsCorrectCount() {
        assertEquals(0, index.getDigestSize());

        index.addFile(new CheckSum(HASH_A), Path.of("E:\\a.txt"), 10);
        assertEquals(1, index.getDigestSize());

        index.addFile(new CheckSum(HASH_B), Path.of("E:\\b.txt"), 20);
        assertEquals(2, index.getDigestSize());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=IndexTest -q 2>&1 | tail -5`
Expected: Compilation error (Index doesn't have createForTest method)

- [ ] **Step 3: Rewrite Index.java**

Rewrite `src/com/superredrock/usbthief/index/Index.java`:

```java
package com.superredrock.usbthief.index;

import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Index extends Service {
    private static final Logger logger = LogManager.getLogger(Index.class);

    private static volatile Index INSTANCE;

    private final Cache<Path, CheckSum> cache;
    private final IndexDiskStore diskStore;
    private volatile boolean dirty;
    private final AtomicInteger totalEntries = new AtomicInteger(0);

    private Index() {
        this(resolveIndexPath());
    }

    private Index(Path indexPath) {
        int maxSize = ConfigManager.getInstance().get(ConfigSchema.INDEX_CACHE_SIZE);
        this.diskStore = new IndexDiskStore(indexPath);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .removalListener((Path key, CheckSum value, RemovalCause cause) -> {
                    if (cause.wasEvicted() && value != null) {
                        diskStore.append(key, value);
                        logger.debug("Evicted entry to disk: {}", key);
                    }
                })
                .build();
        this.dirty = false;
        ensureDirectories(indexPath);
    }

    static Index createForTest(Path indexPath) {
        return new Index(indexPath);
    }

    private static Path resolveIndexPath() {
        Path path = Path.of(ConfigManager.getInstance().get(ConfigSchema.INDEX_PATH));
        Path base = path.getParent() != null ? path.getParent() : Paths.get(".");
        return base.resolve("index.dat");
    }

    private void ensureDirectories(Path indexPath) {
        try {
            Path parent = indexPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create index directory", e);
        }
    }

    public static Index getInstance() {
        if (INSTANCE == null) {
            synchronized (Index.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Index();
                }
            }
        }
        return INSTANCE;
    }

    public void load() {
        Map<Path, CheckSum> diskEntries = diskStore.load();
        totalEntries.set(diskEntries.size());

        int maxSize = ConfigManager.getInstance().get(ConfigSchema.INDEX_CACHE_SIZE);
        int toCache = Math.min(diskEntries.size(), maxSize);
        int count = 0;
        for (var entry : diskEntries.entrySet()) {
            if (count >= toCache) break;
            cache.put(entry.getKey(), entry.getValue());
            count++;
        }

        dirty = false;
        logger.info("Index loaded: {} entries ({} in cache)", diskEntries.size(), toCache);

        EventBus.getInstance().dispatch(new IndexLoadedEvent(diskEntries.size()));
    }

    public void save() {
        if (!dirty) {
            logger.debug("Index not dirty, skipping save");
            return;
        }

        Map<Path, CheckSum> diskEntries = diskStore.load();
        diskEntries.putAll(cache.asMap());
        diskStore.compact(diskEntries);

        dirty = false;
        totalEntries.set(diskEntries.size());
        logger.info("Index saved: {} entries", diskEntries.size());

        EventBus.getInstance().dispatch(new IndexSavedEvent(diskEntries.size()));
    }

    public boolean checkDuplicate(Path filePath, CheckSum checksum) {
        CheckSum cached = cache.getIfPresent(filePath);
        if (cached != null) {
            boolean isDuplicate = cached.equals(checksum);
            if (isDuplicate) {
                EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, filePath, 1));
            }
            return isDuplicate;
        }

        Map<Path, CheckSum> diskEntries = diskStore.load();
        CheckSum fromDisk = diskEntries.get(filePath);
        if (fromDisk != null) {
            cache.put(filePath, fromDisk);
            boolean isDuplicate = fromDisk.equals(checksum);
            if (isDuplicate) {
                EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, filePath, 1));
            }
            return isDuplicate;
        }

        return false;
    }

    public boolean addFile(CheckSum checksum, Path filePath, long fileSize) {
        CheckSum existing = cache.getIfPresent(filePath);
        boolean isNew = existing == null || !existing.equals(checksum);

        cache.put(filePath, checksum);
        markDirty();

        if (isNew) {
            totalEntries.incrementAndGet();
            EventBus.getInstance().dispatch(new FileIndexedEvent(checksum, filePath, fileSize, totalEntries.get()));
        }
        return isNew;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clear() {
        int oldSize = totalEntries.getAndSet(0);
        cache.invalidateAll();
        markDirty();
        logger.info("Index cleared: {} entries removed", oldSize);
    }

    public int getDigestSize() {
        return (int) cache.estimatedSize();
    }

    public Path getIndexPath() {
        return diskStore.getFilePath();
    }

    @Override
    protected void tick() {
        try {
            if (dirty) {
                logger.debug("Executing periodic index save");
                save();
            }
        } catch (Exception e) {
            logger.error("Index save failed: ", e);
            state = ServiceState.FAILED;
        }
    }

    @Override
    protected long getTickInterval() {
        return ConfigManager.getInstance().get(ConfigSchema.SAVE_DELAY_SECONDS);
    }

    @Override
    protected TimeUnit getTickUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    public String getServiceName() {
        return "Index";
    }

    @Override
    public String getDescription() {
        return "File index with LRU cache and disk persistence";
    }

    @Override
    protected void cleanup() {
        save();
    }

    @Override
    public String getStatus() {
        return String.format("Index[%s] - Cache: %d, State: %s",
                state, cache.estimatedSize(), dirty ? "dirty" : "clean");
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=IndexTest,IndexDiskStoreTest -q`
Expected: All tests PASS

- [ ] **Step 5: Verify full compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/com/superredrock/usbthief/index/Index.java src/com/superredrock/usbthief/index/IndexDiskStore.java test/com/superredrock/usbthief/index/IndexTest.java test/com/superredrock/usbthief/index/IndexDiskStoreTest.java
git commit -m "feat: rewrite Index with Caffeine LRU cache and binary disk store"
```

---

### Task 6: Verify End-to-End Integration

**Files:**
- No changes, verification only

- [ ] **Step 1: Full compile**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `mvn test -q`
Expected: All tests PASS

- [ ] **Step 3: Verify no broken references to old Index API**

Search for any remaining references to `getDigest()` (which was removed) or `addChecksum()` (which was removed):

Run: `grep -rn "getDigest\|addChecksum" src/`
Expected: No matches (these methods were removed in the rewrite)

- [ ] **Step 4: Verify old index.obj is handled gracefully**

The old `index.obj` uses Java Serialization format. The new code reads `index.dat` (different file). The old file is simply ignored. Verify the new file extension:

Run: `grep -n "index.dat" src/com/superredrock/usbthief/index/Index.java`
Expected: Match on the `resolveIndexPath()` method

- [ ] **Step 5: Commit any fixes if needed**

If any issues were found, fix and commit with appropriate message.

---

## Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| Caffeine dependency | Task 1 |
| INDEX_CACHE_SIZE config | Task 2 |
| Remove Serializable from CheckSum | Task 3 |
| Custom binary format (IDXF header + entries) | Task 4 |
| Append-only disk writes | Task 4 (IndexDiskStore.append) |
| Compaction (dedup + rewrite) | Task 4 (IndexDiskStore.compact) |
| Caffeine cache with RemovalListener | Task 5 |
| checkDuplicate: cache → disk → not found | Task 5 |
| addFile with event dispatch | Task 5 |
| Path-keyed dedup (same content, different path = NOT dup) | Task 5 (IndexTest verifies) |
| Service lifecycle (tick/save/cleanup) | Task 5 |
| End-to-end verification | Task 6 |
