# Index Cache Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove index persistence, switch to LFU cache, and include disk serial number in index keys.

**Architecture:** The Index class becomes a plain singleton (no longer extends Service) with a Caffeine LFU cache keyed by `IndexKey(serialNumber, filePath)`. All persistence code (`IndexDiskStore`, save/load tick, related events) is deleted. Callers (`CopyTask`, `VerifyTask`) construct `IndexKey` with their existing `deviceSerial` field.

**Tech Stack:** Java 25, Caffeine cache, JUnit 5

---

## File Structure

| File | Responsibility | Action |
|------|---------------|--------|
| `src/com/superredrock/usbthief/index/IndexKey.java` | Composite cache key (serial + path) | **Create** |
| `src/com/superredrock/usbthief/index/Index.java` | Pure in-memory LFU cache singleton | **Rewrite** |
| `src/com/superredrock/usbthief/index/IndexDiskStore.java` | Disk persistence (being removed) | **Delete** |
| `src/com/superredrock/usbthief/core/event/index/IndexSavedEvent.java` | Persistence event (being removed) | **Delete** |
| `src/com/superredrock/usbthief/core/event/index/IndexLoadedEvent.java` | Persistence event (being removed) | **Delete** |
| `src/com/superredrock/usbthief/core/config/ConfigSchema.java` | Config entries registry | **Edit** |
| `src/com/superredrock/usbthief/worker/CopyTask.java` | File copy task | **Edit** |
| `src/com/superredrock/usbthief/worker/VerifyTask.java` | Pre-copy verification task | **Edit** |
| `src/com/superredrock/usbthief/gui/LogPanel.java` | Event log panel | **Edit** |
| `src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java` | Debug dialog | **Edit** |
| `src/com/superredrock/usbthief/gui/dailog/HashAlgorithmDialog.java` | Hash algo dialog | **Edit** |
| `src/com/superredrock/usbthief/gui/MainFrame.java` | Main window (remove save index menu) | **Edit** |
| `src/com/superredrock/usbthief/Main.java` | App entry point | **Edit** |
| `src/com/superredrock/usbthief/core/QueueManager.java` | Service manager | **Edit** |
| `src/com/superredrock/usbthief/gui/messages.properties` | i18n (remove save-related keys) | **Edit** |
| `src/com/superredrock/usbthief/gui/messages_en.properties` | i18n | **Edit** |
| `src/com/superredrock/usbthief/gui/messages_zh.properties` | i18n | **Edit** |
| `src/com/superredrock/usbthief/gui/messages_ja.properties` | i18n | **Edit** |
| `src/com/superredrock/usbthief/gui/messages_de.properties` | i18n | **Edit** |
| `test/com/superredrock/usbthief/index/IndexTest.java` | Index unit tests | **Edit** |
| `test/com/superredrock/usbthief/index/IndexDiskStoreTest.java` | Disk store tests (being removed) | **Delete** |
| `test/com/superredrock/usbthief/core/ConfigManagerTest.java` | Config tests | **Edit** |
| `test/com/superredrock/usbthief/core/ConfigSchemaTest.java` | Schema tests | **Edit** |

---

### Task 1: Create `IndexKey` record and rewrite `Index` class

**Files:**
- Create: `src/com/superredrock/usbthief/index/IndexKey.java`
- Rewrite: `src/com/superredrock/usbthief/index/Index.java`

- [ ] **Step 1: Create `IndexKey.java`**

```java
package com.superredrock.usbthief.index;

import java.nio.file.Path;

public record IndexKey(String serialNumber, Path filePath) {}
```

- [ ] **Step 2: Rewrite `Index.java`**

```java
package com.superredrock.usbthief.index;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Index {

    private static final Logger logger = LogManager.getLogger(Index.class);

    private static volatile Index INSTANCE;

    private final Cache<IndexKey, CheckSum> cache;
    private final AtomicInteger totalEntries = new AtomicInteger(0);

    private Index() {
        int maxSize = ConfigManager.getInstance().get(ConfigSchema.INDEX_CACHE_SIZE);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .recordStats()
                .build();
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

    public boolean checkDuplicate(IndexKey key, CheckSum checksum) {
        CheckSum cached = cache.getIfPresent(key);
        if (cached != null) {
            boolean isDuplicate = cached.equals(checksum);
            if (isDuplicate) {
                EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, key.filePath(), 1));
            }
            return isDuplicate;
        }
        return false;
    }

    public boolean addFile(CheckSum checksum, IndexKey key, long fileSize) {
        CheckSum existing = cache.getIfPresent(key);
        boolean isNew = existing == null || !existing.equals(checksum);

        cache.put(key, checksum);

        if (isNew) {
            totalEntries.incrementAndGet();
            EventBus.getInstance().dispatch(new FileIndexedEvent(checksum, key.filePath(), fileSize, totalEntries.get()));
        }
        return isNew;
    }

    public void clear() {
        int oldSize = totalEntries.getAndSet(0);
        cache.invalidateAll();
        logger.info("Index cleared: {} entries removed", oldSize);
    }

    public int getDigestSize() {
        return (int) cache.estimatedSize();
    }

    public String getStatus() {
        return String.format("Index - Cache: %d", cache.estimatedSize());
    }
}
```

- [ ] **Step 3: Compile to verify no syntax errors**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS (there will be compile errors in other files referencing removed methods — we fix those in subsequent tasks)

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/index/IndexKey.java src/com/superredrock/usbthief/index/Index.java
git commit -m "refactor: rewrite Index as pure in-memory LFU cache with IndexKey"
```

---

### Task 2: Delete `IndexDiskStore`, `IndexSavedEvent`, `IndexLoadedEvent`

**Files:**
- Delete: `src/com/superredrock/usbthief/index/IndexDiskStore.java`
- Delete: `src/com/superredrock/usbthief/core/event/index/IndexSavedEvent.java`
- Delete: `src/com/superredrock/usbthief/core/event/index/IndexLoadedEvent.java`
- Delete: `test/com/superredrock/usbthief/index/IndexDiskStoreTest.java`

- [ ] **Step 1: Delete the files**

```bash
rm src/com/superredrock/usbthief/index/IndexDiskStore.java
rm src/com/superredrock/usbthief/core/event/index/IndexSavedEvent.java
rm src/com/superredrock/usbthief/core/event/index/IndexLoadedEvent.java
rm test/com/superredrock/usbthief/index/IndexDiskStoreTest.java
```

- [ ] **Step 2: Commit**

```bash
git add -u
git commit -m "refactor: remove IndexDiskStore, IndexSavedEvent, IndexLoadedEvent"
```

---

### Task 3: Update callers — CopyTask and VerifyTask

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/CopyTask.java` (line 206)
- Modify: `src/com/superredrock/usbthief/worker/VerifyTask.java` (line 94)

- [ ] **Step 1: Update `CopyTask.java`**

Add import at top of file:
```java
import com.superredrock.usbthief.index.IndexKey;
```

Change line 206 from:
```java
            QueueManager.getIndex().addFile(hash, source, size);
```
to:
```java
            QueueManager.getIndex().addFile(hash, new IndexKey(deviceSerial, source), size);
```

- [ ] **Step 2: Update `VerifyTask.java`**

Add import at top of file:
```java
import com.superredrock.usbthief.index.IndexKey;
```

Change line 94 from:
```java
            if (QueueManager.getIndex().checkDuplicate(processingPath, hash)) {
```
to:
```java
            if (QueueManager.getIndex().checkDuplicate(new IndexKey(deviceSerial, processingPath), hash)) {
```

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/worker/CopyTask.java src/com/superredrock/usbthief/worker/VerifyTask.java
git commit -m "refactor: use IndexKey with deviceSerial in CopyTask and VerifyTask"
```

---

### Task 4: Update service lifecycle — Main.java and QueueManager.java

**Files:**
- Modify: `src/com/superredrock/usbthief/Main.java` (lines 9, 47-48, 59, 95)
- Modify: `src/com/superredrock/usbthief/core/QueueManager.java` (lines 59-65)

- [ ] **Step 1: Update `Main.java`**

Remove the import:
```java
import com.superredrock.usbthief.index.Index;
```

Remove lines 47-48:
```java
        // Load index
        Index.getInstance().load();
```

Remove line 59:
```java
        Index.getInstance().start();
```

Remove line 95:
```java
        Index.getInstance().stopService();
```

- [ ] **Step 2: Update `QueueManager.java`**

Replace lines 59-65:
```java
            // 2. Stop index periodic save service
            index.stopService();
            logger.info("Index ticker stopped");

            // 3. Save index
            index.save();
            logger.info("Index saved");
```

With:
```java
            // 2. Interrupt all disk scanner threads
```

Then renumber the remaining comments (4 → 3, 5 → 4):
- `// 4. Interrupt all disk scanner threads` → `// 3. Interrupt all disk scanner threads`
- `// 5. Gracefully shutdown thread pool` → `// 4. Gracefully shutdown thread pool`

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/Main.java src/com/superredrock/usbthief/core/QueueManager.java
git commit -m "refactor: remove Index service lifecycle from Main and QueueManager"
```

---

### Task 5: Remove persistence event listeners — LogPanel and SnifferDebugDialog

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/LogPanel.java` (lines 10-11, 158-159, 181-189)
- Modify: `src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java` (lines 13-14, 538, 563)

- [ ] **Step 1: Update `LogPanel.java`**

Remove imports:
```java
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;
```

Remove lines 158-159:
```java
        eventBus.register(IndexLoadedEvent.class, this::onIndexLoaded);
        eventBus.register(IndexSavedEvent.class, this::onIndexSaved);
```

Remove methods (lines 181-189):
```java
    private void onIndexLoaded(IndexLoadedEvent event) {
        String message = i18n.getMessage("log.message.indexLoaded", event.loadedCount());
        log(message, LogLevel.INFO);
    }

    private void onIndexSaved(IndexSavedEvent event) {
        String message = i18n.getMessage("log.message.indexSaved", event.savedCount());
        log(message, LogLevel.INFO);
    }
```

- [ ] **Step 2: Update `SnifferDebugDialog.java`**

Remove imports:
```java
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
```

Change line 538 from:
```java
            case "Index" -> event instanceof IndexEvent || event instanceof IndexSavedEvent || event instanceof IndexLoadedEvent;
```
to:
```java
            case "Index" -> event instanceof IndexEvent;
```

Change line 563 from:
```java
        } else if (event instanceof IndexEvent || event instanceof IndexSavedEvent || event instanceof IndexLoadedEvent) {
```
to:
```java
        } else if (event instanceof IndexEvent) {
```

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/LogPanel.java src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java
git commit -m "refactor: remove IndexSavedEvent/IndexLoadedEvent listeners"
```

---

### Task 6: Remove "Save Index" menu from MainFrame and HashAlgorithmDialog HASH_ALGORITHM_LAST

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/MainFrame.java` (lines 270-272, 474-489)
- Modify: `src/com/superredrock/usbthief/gui/dailog/HashAlgorithmDialog.java` (line 138)

- [ ] **Step 1: Update `MainFrame.java`**

Remove lines 270-272 (save index menu item):
```java
        JMenuItem saveIndexItem = new JMenuItem(i18n.getMessage("menu.action.saveIndex"));
        saveIndexItem.addActionListener(_ -> saveIndex());
        actionMenu.add(saveIndexItem);
```

Remove the entire `saveIndex()` method (lines 474-489):
```java
    private void saveIndex() {
        updateStatusBar(i18n.getMessage("status.savingIndex"));

        SwingUtilities.invokeLater(() -> {
            try {
                QueueManager.getIndex().save();
                updateStatusBar(i18n.getMessage("status.indexSaved"));
            } catch (Exception e) {
                updateStatusBar(i18n.getMessage("status.indexSaveFailed"));
                JOptionPane.showMessageDialog(this,
                        i18n.getMessage("message.saveIndexFailed", e.getMessage()),
                        i18n.getMessage("common.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
```

- [ ] **Step 2: Update `HashAlgorithmDialog.java`**

Remove line 138:
```java
            ConfigManager.getInstance().set(ConfigSchema.HASH_ALGORITHM_LAST, newAlgo);
```

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/MainFrame.java src/com/superredrock/usbthief/gui/dailog/HashAlgorithmDialog.java
git commit -m "refactor: remove Save Index menu and HASH_ALGORITHM_LAST usage"
```

---

### Task 7: Remove persistence config entries from ConfigSchema

**Files:**
- Modify: `src/com/superredrock/usbthief/core/config/ConfigSchema.java` (lines 35-42, 69-70, 201-203, 212)

- [ ] **Step 1: Remove field declarations**

Remove these 4 entries from ConfigSchema:
```java
    public static final ConfigEntry<Integer> SAVE_INITIAL_DELAY_SECONDS =
            intEntry("saveInitialDelaySeconds", "Initial delay before first index save (seconds)", 30, "Index Management");

    public static final ConfigEntry<Integer> SAVE_DELAY_SECONDS =
            intEntry("saveDelaySeconds", "Interval between index saves (seconds)", 60, "Index Management");

    public static final ConfigEntry<String> INDEX_PATH =
            stringEntry("indexPath", "Path to the index file (relative or absolute)", "index.obj", "Index Management");
```

```java
    public static final ConfigEntry<String> HASH_ALGORITHM_LAST =
            stringEntry("hashAlgorithmLast", "Previous hash algorithm (internal, for detecting changes)", "SHA-256", "File Copy");
```

- [ ] **Step 2: Remove `registerEntry` calls from static block**

Remove:
```java
        registerEntry(SAVE_INITIAL_DELAY_SECONDS);
        registerEntry(SAVE_DELAY_SECONDS);
        registerEntry(INDEX_PATH);
```

```java
        registerEntry(HASH_ALGORITHM_LAST);
```

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/core/config/ConfigSchema.java
git commit -m "refactor: remove persistence config entries from ConfigSchema"
```

---

### Task 8: Remove i18n keys for save/load index

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/messages.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_en.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_zh.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_ja.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_de.properties`

- [ ] **Step 1: Remove keys from all 5 properties files**

Remove these keys from each file (exact line numbers vary per file):

```
status.savingIndex
status.indexSaved
status.indexSaveFailed
menu.action.saveIndex
message.saveIndexFailed
log.message.indexLoaded
log.message.indexSaved
toast.indexSaved
```

Note: Some files may not have all keys (e.g. `messages_zh.properties` lacks `log.message.indexLoaded`/`log.message.indexSaved`). Only remove keys that exist in each file.

- [ ] **Step 2: Commit**

```bash
git add src/com/superredrock/usbthief/gui/messages*.properties
git commit -m "refactor: remove persistence-related i18n keys"
```

---

### Task 9: Update tests

**Files:**
- Edit: `test/com/superredrock/usbthief/index/IndexTest.java`
- Edit: `test/com/superredrock/usbthief/core/ConfigManagerTest.java`
- Edit: `test/com/superredrock/usbthief/core/ConfigSchemaTest.java`

- [ ] **Step 1: Rewrite `IndexTest.java`**

```java
package com.superredrock.usbthief.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private static final byte[] HASH_A = new byte[32];
    private static final byte[] HASH_B = new byte[32];
    static { HASH_B[0] = 1; }

    private Index index;

    @BeforeEach
    void setUp() {
        try {
            var field = Index.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        index = Index.getInstance();
    }

    @Test
    void newFileIsNotDuplicate() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\newfile.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        assertFalse(index.checkDuplicate(key, cs));
    }

    @Test
    void addedFileIsDuplicate() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, key, 100);
        assertTrue(index.checkDuplicate(key, cs));
    }

    @Test
    void samePathDifferentContentIsNotDuplicate() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum csA = new CheckSum(HASH_A);
        CheckSum csB = new CheckSum(HASH_B);
        index.addFile(csA, key, 100);
        assertFalse(index.checkDuplicate(key, csB));
    }

    @Test
    void differentPathSameContentIsNotDuplicate() {
        IndexKey keyA = new IndexKey("SERIAL1", Path.of("E:\\fileA.txt"));
        IndexKey keyB = new IndexKey("SERIAL1", Path.of("E:\\fileB.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, keyA, 100);
        assertFalse(index.checkDuplicate(keyB, cs));
    }

    @Test
    void differentSerialSamePathIsNotDuplicate() {
        IndexKey keyA = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        IndexKey keyB = new IndexKey("SERIAL2", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, keyA, 100);
        assertFalse(index.checkDuplicate(keyB, cs));
    }

    @Test
    void addFileReturnsTrueForNewEntry() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        assertTrue(index.addFile(cs, key, 100));
        assertFalse(index.addFile(cs, key, 100));
    }

    @Test
    void clearRemovesAllEntries() {
        IndexKey key = new IndexKey("SERIAL1", Path.of("E:\\file.txt"));
        CheckSum cs = new CheckSum(HASH_A);
        index.addFile(cs, key, 100);
        index.clear();
        assertFalse(index.checkDuplicate(key, cs));
        assertEquals(0, index.getDigestSize());
    }

    @Test
    void getDigestSizeReturnsCorrectCount() {
        assertEquals(0, index.getDigestSize());
        index.addFile(new CheckSum(HASH_A), new IndexKey("SERIAL1", Path.of("E:\\a.txt")), 10);
        assertEquals(1, index.getDigestSize());
        index.addFile(new CheckSum(HASH_B), new IndexKey("SERIAL1", Path.of("E:\\b.txt")), 20);
        assertEquals(2, index.getDigestSize());
    }
}
```

- [ ] **Step 2: Update `ConfigManagerTest.java`**

Change line 39-40 from:
```java
        assertEquals(60, manager.get(ConfigSchema.SAVE_DELAY_SECONDS));
        assertEquals("index.obj", manager.get(ConfigSchema.INDEX_PATH));
```
to:
```java
        assertEquals(10_000, manager.get(ConfigSchema.INDEX_CACHE_SIZE));
```

Change lines 65-67 from:
```java
        assertEquals("index.obj", manager.get(ConfigSchema.INDEX_PATH));
        manager.set(ConfigSchema.INDEX_PATH, "/custom/path");
        assertEquals("/custom/path", manager.get(ConfigSchema.INDEX_PATH));
```
to:
```java
        assertEquals("SHA-256", manager.get(ConfigSchema.HASH_ALGORITHM));
        manager.set(ConfigSchema.HASH_ALGORITHM, "MD5");
        assertEquals("MD5", manager.get(ConfigSchema.HASH_ALGORITHM));
```

- [ ] **Step 3: Update `ConfigSchemaTest.java`**

Change lines 75-78 from:
```java
        assertEquals(60, ConfigSchema.SAVE_DELAY_SECONDS.defaultValue());
        assertEquals("index.obj", ConfigSchema.INDEX_PATH.defaultValue());
        assertEquals(10_000, ConfigSchema.INDEX_CACHE_SIZE.defaultValue());
        assertEquals(ConfigType.STRING, ConfigSchema.INDEX_PATH.type());
```
to:
```java
        assertEquals(10_000, ConfigSchema.INDEX_CACHE_SIZE.defaultValue());
        assertEquals(ConfigType.INT, ConfigSchema.INDEX_CACHE_SIZE.type());
```

- [ ] **Step 4: Run tests**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add test/com/superredrock/usbthief/index/IndexTest.java test/com/superredrock/usbthief/core/ConfigManagerTest.java test/com/superredrock/usbthief/core/ConfigSchemaTest.java
git commit -m "test: update tests for IndexKey and removed config entries"
```

---

### Task 10: Compile and run full test suite

**Files:** None (verification only)

- [ ] **Step 1: Full compile**

Run: `mvn clean compile`
Expected: BUILD SUCCESS, no compile errors

- [ ] **Step 2: Full test run**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 3: Verify no references to deleted code remain**

Run: `grep -r "IndexDiskStore\|IndexSavedEvent\|IndexLoadedEvent\|HASH_ALGORITHM_LAST\|SAVE_DELAY_SECONDS\|SAVE_INITIAL_DELAY_SECONDS\|INDEX_PATH\b" src/`
Expected: No matches (empty output)
