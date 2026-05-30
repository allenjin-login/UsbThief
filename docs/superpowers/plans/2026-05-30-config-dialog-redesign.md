# Config Dialog Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JTabbedPane-based ConfigDialog with IntelliJ-style tree navigation + right-side content panel, and refactor ConfigSchema into per-domain config classes.

**Architecture:** ConfigSchema becomes a registry that collects entries from 15 domain-specific config classes in `core/config/configs/`. ConfigDialog uses JSplitPane with a JTree on the left for hierarchical navigation and a dynamic JPanel on the right for settings forms. A search field above the tree filters nodes by entry key/description. Bottom bar has only OK/Cancel buttons.

**Tech Stack:** Java 25, Swing (FlatLaf theming), JTree, JSplitPane, GridBagLayout, ResourceBundle i18n

---

### Task 1: Create per-domain config classes

**Files:**
- Create: `src/com/superredrock/usbthief/core/config/configs/ThreadPoolConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/DeviceScannerConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/IndexConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/FileCopyConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/FileWatchConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/RateLimitConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/PathConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/UIConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/WindowConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/BlacklistConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/FileFilterConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/SuffixFilterConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/StorageConfig.java`
- Create: `src/com/superredrock/usbthief/core/config/configs/StatisticsApiConfig.java`

- [ ] **Step 1: Create the `configs` package directory**

```bash
mkdir -p "src/com/superredrock/usbthief/core/config/configs"
```

- [ ] **Step 2: Create ThreadPoolConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class ThreadPoolConfig {
    public static final String CATEGORY = "Thread Pool";

    public static final ConfigEntry<Integer> CORE_POOL_SIZE =
            intEntry("corePoolSize", "Minimum number of threads in the thread pool", 2, CATEGORY);

    public static final ConfigEntry<Integer> MAX_POOL_SIZE =
            intEntry("maxPoolSize", "Maximum number of threads in the thread pool", Runtime.getRuntime().availableProcessors(), CATEGORY);

    public static final ConfigEntry<Integer> KEEP_ALIVE_TIME_SECONDS =
            intEntry("keepAliveTimeSeconds", "Idle thread keep-alive time in seconds", 60, CATEGORY);

    public static final ConfigEntry<Integer> TASK_QUEUE_CAPACITY =
            intEntry("taskQueueCapacity", "Maximum number of tasks in the queue", 1024, CATEGORY);

    private ThreadPoolConfig() {}
}
```

- [ ] **Step 3: Create DeviceScannerConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class DeviceScannerConfig {
    public static final String CATEGORY = "Device Scanner";

    public static final ConfigEntry<Integer> INITIAL_DELAY_SECONDS =
            intEntry("initialDelaySeconds", "Initial delay before first device scan (seconds)", 10, CATEGORY);

    public static final ConfigEntry<Integer> DELAY_SECONDS =
            intEntry("delaySeconds", "Interval between device scans (seconds)", 500, CATEGORY);

    private DeviceScannerConfig() {}
}
```

- [ ] **Step 4: Create IndexConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class IndexConfig {
    public static final String CATEGORY = "Index Management";

    public static final ConfigEntry<Integer> INDEX_CACHE_SIZE =
            intEntry("indexCacheSize", "Maximum number of entries in the in-memory index cache", 2000, CATEGORY);

    private IndexConfig() {}
}
```

- [ ] **Step 5: Create FileCopyConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileCopyConfig {
    public static final String CATEGORY = "File Copy";

    public static final ConfigEntry<Integer> BUFFER_SIZE =
            intEntry("bufferSize", "Buffer size for file copying (bytes)", 16 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> HASH_BUFFER_SIZE =
            intEntry("hashBufferSize", "Buffer size for hash calculation (bytes)", 1024, CATEGORY);

    public static final ConfigEntry<Integer> MAX_FILE_SIZE =
            intEntry("maxFileSize", "Maximum file size to copy (bytes)", 1000 * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> RETRY_COUNT =
            intEntry("retryCount", "Number of retry attempts for failed operations", 5, CATEGORY);

    public static final ConfigEntry<Long> TIMEOUT_MILLIS =
            longEntry("timeoutMillis", "Timeout for retry queue polling (milliseconds)", 100L, CATEGORY);

    public static final ConfigEntry<Boolean> COPY_VERIFY_ENABLED =
            booleanEntry("copyVerifyEnabled", "Enable pre-copy verification (checksum + dedup before copy)", true, CATEGORY);

    public static final ConfigEntry<String> HASH_ALGORITHM =
            stringEntry("hashAlgorithm", "Hash algorithm: SHA-256, MD5, CRC-8, CRC-16, CRC-32, CRC-64", "SHA-256", CATEGORY);

    private FileCopyConfig() {}
}
```

- [ ] **Step 6: Create FileWatchConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileWatchConfig {
    public static final String CATEGORY = "File Watch";

    public static final ConfigEntry<Boolean> WATCH_ENABLED =
            booleanEntry("watchEnabled", "Enable/disable real-time file monitoring", false, CATEGORY);

    public static final ConfigEntry<Integer> WATCH_THRESHOLD =
            intEntry("watchThreshold", "Number of file changes before triggering copy", 10, CATEGORY);

    public static final ConfigEntry<Integer> WATCH_RESET_INTERVAL_SECONDS =
            intEntry("watchResetIntervalSeconds", "Interval to reset change counter (seconds)", 60, CATEGORY);

    private FileWatchConfig() {}
}
```

- [ ] **Step 7: Create RateLimitConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class RateLimitConfig {
    public static final String CATEGORY = "Rate Limiting";

    public static final ConfigEntry<Long> COPY_READ_RATE_LIMIT =
            longEntry("copyReadRateLimit", "Read rate limit in bytes per second (0 = no limit)", 0L, CATEGORY);

    public static final ConfigEntry<Long> COPY_WRITE_RATE_LIMIT =
            longEntry("copyWriteRateLimit", "Write rate limit in bytes per second (0 = no limit)", 0L, CATEGORY);

    public static final ConfigEntry<Long> COPY_RATE_BURST_SIZE =
            longEntry("copyRateBurstSize", "Copy rate burst size in bytes", 16L * 1024 * 1024, CATEGORY);

    private RateLimitConfig() {}
}
```

- [ ] **Step 8: Create PathConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class PathConfig {
    public static final String CATEGORY = "Paths";

    public static final ConfigEntry<String> WORK_PATH =
            stringEntry("workPath", "Working directory for storing copied files", "devices", CATEGORY);

    private PathConfig() {}
}
```

- [ ] **Step 9: Create UIConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class UIConfig {
    public static final String CATEGORY = "UI";

    public static final ConfigEntry<Integer> FILE_HISTORY_MAX_ENTRIES =
            intEntry("fileHistoryMaxEntries", "Maximum number of file history entries to keep in memory", 10000, CATEGORY);

    private UIConfig() {}
}
```

- [ ] **Step 10: Create WindowConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class WindowConfig {
    public static final String CATEGORY = "Window";

    public static final ConfigEntry<Boolean> AUTO_START_ENABLED =
            booleanEntry("gui.autoStartEnabled", "Start application automatically on Windows login", false, CATEGORY);

    public static final ConfigEntry<Boolean> SHOW_IN_TASKBAR =
            booleanEntry("gui.showInTaskbar", "Show window in taskbar", true, CATEGORY);

    public static final ConfigEntry<String> CLOSE_ACTION =
            stringEntry("gui.closeAction", "Action when closing: ASK, MINIMIZE_TO_TRAY, EXIT", "ASK", CATEGORY);

    public static final ConfigEntry<Boolean> CLOSE_ACTION_REMEMBER =
            booleanEntry("gui.closeActionRemember", "Remember the close action choice", false, CATEGORY);

    private WindowConfig() {}
}
```

- [ ] **Step 11: Create BlacklistConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;
import java.util.List;

public final class BlacklistConfig {
    public static final String CATEGORY = "Blacklist";

    public static final ConfigEntry<List<String>> DEVICE_BLACKLIST =
            listEntry("deviceBlacklist", "Device blacklist by path (deprecated, use deviceBlacklistBySerial)", List.of(), CATEGORY);

    public static final ConfigEntry<List<String>> DEVICE_BLACKLIST_BY_SERIAL =
            listEntry("deviceBlacklistBySerial", "Device blacklist by serial number", List.of(), CATEGORY);

    private BlacklistConfig() {}
}
```

- [ ] **Step 12: Create FileFilterConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileFilterConfig {
    public static final String CATEGORY = "File Filter";

    public static final ConfigEntry<Long> FILE_FILTER_MAX_SIZE =
            longEntry("fileFilter.maxSize", "Maximum file size to copy (bytes)", 100L * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_MAX_SIZE_ENABLED =
            booleanEntry("fileFilter.maxSizeEnabled", "Enable maximum file size filter", true, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_TIME_ENABLED =
            booleanEntry("fileFilter.timeEnabled", "Enable time-based file filtering", false, CATEGORY);

    public static final ConfigEntry<Long> FILE_FILTER_TIME_VALUE =
            longEntry("fileFilter.timeValue", "Time filter value (combined with timeUnit)", 24L, CATEGORY);

    public static final ConfigEntry<String> FILE_FILTER_TIME_UNIT =
            stringEntry("fileFilter.timeUnit", "Time filter unit: HOURS, DAYS, WEEKS, MONTHS, YEARS", "HOURS", CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_INCLUDE_HIDDEN =
            booleanEntry("fileFilter.includeHidden", "Include hidden files in copy", false, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_SKIP_SYMLINKS =
            booleanEntry("fileFilter.skipSymlinks", "Skip symbolic links during copy", true, CATEGORY);

    public static final ConfigEntry<Boolean> FILE_FILTER_ALLOW_NO_EXT =
            booleanEntry("fileFilter.allowNoExtension", "Allow files without extension", true, CATEGORY);

    private FileFilterConfig() {}
}
```

- [ ] **Step 13: Create SuffixFilterConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;
import java.util.List;

public final class SuffixFilterConfig {
    public static final String CATEGORY = "Suffix Filter";

    public static final ConfigEntry<String> SUFFIX_FILTER_MODE =
            stringEntry("suffixFilter.mode", "Suffix filter mode: NONE, WHITELIST, or BLACKLIST", "NONE", CATEGORY);

    public static final ConfigEntry<List<String>> SUFFIX_FILTER_WHITELIST =
            listEntry("suffixFilter.whitelist", "Whitelist of file extensions (without dot)", List.of(), CATEGORY);

    public static final ConfigEntry<List<String>> SUFFIX_FILTER_BLACKLIST =
            listEntry("suffixFilter.blacklist", "Blacklist of file extensions (without dot)", List.of(), CATEGORY);

    public static final ConfigEntry<String> SUFFIX_FILTER_PRESET =
            stringEntry("suffixFilter.preset", "Selected preset name (empty string for none)", "", CATEGORY);

    private SuffixFilterConfig() {}
}
```

- [ ] **Step 14: Create StorageConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class StorageConfig {
    public static final String CATEGORY = "Storage Management";

    public static final ConfigEntry<Long> STORAGE_RESERVED_BYTES =
            longEntry("storage.reservedBytes", "Minimum free space to preserve (bytes)", 10L * 1024 * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Long> STORAGE_MAX_BYTES =
            longEntry("storage.maxBytes", "Maximum space for copied files (bytes)", 100L * 1024 * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> SNIFFER_WAIT_NORMAL_MINUTES =
            intEntry("sniffer.waitNormalMinutes", "Wait time after normal completion (minutes)", 30, CATEGORY);

    public static final ConfigEntry<Integer> SNIFFER_WAIT_ERROR_MINUTES =
            intEntry("sniffer.waitErrorMinutes", "Wait time after error (minutes)", 5, CATEGORY);

    public static final ConfigEntry<String> RECYCLER_STRATEGY =
            stringEntry("recycler.strategy", "Recycler strategy: TIME_FIRST, SIZE_FIRST, or AUTO", "AUTO", CATEGORY);

    public static final ConfigEntry<Integer> RECYCLER_PROTECTED_AGE_HOURS =
            intEntry("recycler.protectedAgeHours", "Protect files newer than X hours from deletion", 1, CATEGORY);

    public static final ConfigEntry<Boolean> STORAGE_WARNING_ENABLED =
            booleanEntry("storage.warningEnabled", "Log warning when storage space is critical", true, CATEGORY);

    public static final ConfigEntry<Boolean> STORAGE_ENABLED =
            booleanEntry("storage.enabled", "Enable storage management (monitoring, recycling, space checks)", true, CATEGORY);

    private StorageConfig() {}
}
```

- [ ] **Step 15: Create StatisticsApiConfig.java**

```java
package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class StatisticsApiConfig {
    public static final String CATEGORY = "Statistics API";

    public static final ConfigEntry<Boolean> STATS_API_ENABLED =
            booleanEntry("stats.api.enabled", "Enable/disable HTTP API for statistics", false, CATEGORY);

    public static final ConfigEntry<Integer> STATS_API_PORT =
            intEntry("stats.api.port", "HTTP API port number", 8421, CATEGORY);

    private StatisticsApiConfig() {}
}
```

- [ ] **Step 16: Commit**

```bash
git add src/com/superredrock/usbthief/core/config/configs/
git commit -m "feat: add per-domain config classes for ConfigSchema refactor"
```

---

### Task 2: Refactor ConfigSchema to registry pattern

**Files:**
- Modify: `src/com/superredrock/usbthief/core/config/ConfigSchema.java`

- [ ] **Step 1: Rewrite ConfigSchema.java as a registry**

Replace the entire file content. The new ConfigSchema collects entries from all config classes using reflection, keeping the public API (`getAllEntries()`, `getEntriesByCategory()`, `getEntry()`) identical.

```java
package com.superredrock.usbthief.core.config;

import com.superredrock.usbthief.core.config.configs.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Schema containing all configuration entries for the application.
 * Acts as a registry that collects entries from per-domain config classes.
 */
public class ConfigSchema {

    private static final Map<String, ConfigEntry<?>> ALL_ENTRIES = new ConcurrentHashMap<>();

    static {
        registerClass(ThreadPoolConfig.class);
        registerClass(DeviceScannerConfig.class);
        registerClass(IndexConfig.class);
        registerClass(FileCopyConfig.class);
        registerClass(FileWatchConfig.class);
        registerClass(RateLimitConfig.class);
        registerClass(PathConfig.class);
        registerClass(UIConfig.class);
        registerClass(WindowConfig.class);
        registerClass(BlacklistConfig.class);
        registerClass(FileFilterConfig.class);
        registerClass(SuffixFilterConfig.class);
        registerClass(StorageConfig.class);
        registerClass(StatisticsApiConfig.class);
    }

    private ConfigSchema() {
        // Static utility class
    }

    /**
     * Register all public static final ConfigEntry fields from the given class.
     */
    private static void registerClass(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isFinal(field.getModifiers())
                    && ConfigEntry.class.isAssignableFrom(field.getType())) {
                try {
                    @SuppressWarnings("unchecked")
                    ConfigEntry<?> entry = (ConfigEntry<?>) field.get(null);
                    if (entry != null) {
                        ALL_ENTRIES.put(entry.key(), entry);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to access config entry: " + field.getName(), e);
                }
            }
        }
    }

    /**
     * Get all registered configuration entries.
     */
    public static Map<String, ConfigEntry<?>> getAllEntries() {
        return Map.copyOf(ALL_ENTRIES);
    }

    /**
     * Get all entries grouped by category.
     */
    public static Map<String, List<ConfigEntry<?>>> getEntriesByCategory() {
        return ALL_ENTRIES.values().stream()
                .collect(Collectors.groupingBy(ConfigEntry::category));
    }

    /**
     * Get entry by key.
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigEntry<T> getEntry(String key) {
        return (ConfigEntry<T>) ALL_ENTRIES.get(key);
    }
}
```

- [ ] **Step 2: Compile to verify no errors**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/core/config/ConfigSchema.java
git commit -m "refactor: convert ConfigSchema to registry pattern using per-domain config classes"
```

---

### Task 3: Update module-info.java to export configs package

**Files:**
- Modify: `src/module-info.java`

- [ ] **Step 1: Add the configs package export**

Add this line after the existing `exports com.superredrock.usbthief.core.config;` line:

```
    exports com.superredrock.usbthief.core.config.configs;
```

The relevant section of `module-info.java` becomes:

```java
    exports com.superredrock.usbthief.core.config;
    exports com.superredrock.usbthief.core.config.configs;
```

- [ ] **Step 2: Commit**

```bash
git add src/module-info.java
git commit -m "feat: export configs package in module-info"
```

---

### Task 4: Migrate all ConfigSchema references to per-domain config classes

**Files:**
- Modify: `src/com/superredrock/usbthief/core/config/ConfigManager.java` (uses DEVICE_BLACKLIST_BY_SERIAL)
- Modify: `src/com/superredrock/usbthief/worker/CopyTask.java` (uses WORK_PATH, COPY_RATE_BURST_SIZE, COPY_READ_RATE_LIMIT, COPY_WRITE_RATE_LIMIT, BUFFER_SIZE)
- Modify: `src/com/superredrock/usbthief/worker/VerifyTask.java` (uses HASH_ALGORITHM, HASH_BUFFER_SIZE)
- Modify: `src/com/superredrock/usbthief/worker/RateLimiter.java` (uses COPY_READ_RATE_LIMIT, COPY_WRITE_RATE_LIMIT, COPY_RATE_BURST_SIZE)
- Modify: `src/com/superredrock/usbthief/index/Index.java` (uses INDEX_CACHE_SIZE)
- Modify: `src/com/superredrock/usbthief/core/QueueManager.java` (uses CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME_SECONDS, TASK_QUEUE_CAPACITY)
- Modify: `src/com/superredrock/usbthief/statistics/collector/SpeedCollector.java` (uses STATS_API_ENABLED, STATS_API_PORT)
- Modify: `src/com/superredrock/usbthief/gui/MainFrame.java` (uses CLOSE_ACTION, CLOSE_ACTION_REMEMBER, SHOW_IN_TASKBAR, WORK_PATH)
- Modify: `src/com/superredrock/usbthief/gui/SystemTrayIcon.java` (if uses any ConfigSchema entries)
- Modify: `src/com/superredrock/usbthief/gui/dailog/HashAlgorithmDialog.java` (uses HASH_ALGORITHM)
- Modify: `src/com/superredrock/usbthief/gui/dailog/BlacklistDialog.java` (uses DEVICE_BLACKLIST_BY_SERIAL)
- Modify: `src/com/superredrock/usbthief/gui/dailog/FilterConfigDialog.java` (uses FILE_FILTER_*, SUFFIX_FILTER_*)
- Modify: `src/com/superredrock/usbthief/worker/Sniffer.java` (uses WATCH_ENABLED, WATCH_THRESHOLD, WATCH_RESET_INTERVAL_SECONDS, COPY_VERIFY_ENABLED)
- Modify: `src/com/superredrock/usbthief/worker/StorageController.java` (uses STORAGE_ENABLED, STORAGE_MAX_BYTES, STORAGE_RESERVED_BYTES, WORK_PATH)
- Modify: `src/com/superredrock/usbthief/worker/RecyclerService.java` (uses RECYCLER_STRATEGY, RECYCLER_PROTECTED_AGE_HOURS, STORAGE_ENABLED, WORK_PATH)
- Modify: `src/com/superredrock/usbthief/worker/SnifferLifecycleManager.java` (uses SNIFFER_WAIT_ERROR_MINUTES, SNIFFER_WAIT_NORMAL_MINUTES)
- Modify: `src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java` (if uses any)
- Modify: `src/com/superredrock/usbthief/core/filter/BasicFileFilter.java` (uses FILE_FILTER_INCLUDE_HIDDEN, FILE_FILTER_MAX_SIZE, etc.)
- Modify: `src/com/superredrock/usbthief/core/filter/SuffixFilter.java` (uses FILE_FILTER_ALLOW_NO_EXT, SUFFIX_FILTER_*)
- Modify: `src/com/superredrock/usbthief/gui/FileHistoryPanel.java` (uses FILE_HISTORY_MAX_ENTRIES)
- Modify: `src/com/superredrock/usbthief/gui/dailog/StorageManagementPanel.java` (uses STORAGE_* entries)
- And any other files referencing ConfigSchema.* constants

For each file, the change is mechanical:
1. Change `import com.superredrock.usbthief.core.config.ConfigSchema;` to `import com.superredrock.usbthief.core.config.configs.XxxConfig;` (and add other config imports as needed)
2. Change `ConfigSchema.SOME_ENTRY` to `XxxConfig.SOME_ENTRY`
3. Remove `import com.superredrock.usbthief.core.config.ConfigSchema;` if no longer needed

Here is the import mapping for each file:

| File | Old import | New import(s) |
|------|-----------|---------------|
| ConfigManager | ConfigSchema | BlacklistConfig |
| CopyTask | ConfigSchema | PathConfig, RateLimitConfig, FileCopyConfig |
| VerifyTask | ConfigSchema | FileCopyConfig |
| RateLimiter | ConfigSchema | RateLimitConfig |
| Index | ConfigSchema | IndexConfig |
| QueueManager | ConfigSchema | ThreadPoolConfig |
| SpeedCollector | ConfigSchema | StatisticsApiConfig |
| MainFrame | ConfigSchema | WindowConfig, PathConfig |
| HashAlgorithmDialog | ConfigSchema | FileCopyConfig |
| BlacklistDialog | ConfigSchema | BlacklistConfig |
| FilterConfigDialog | ConfigSchema | FileFilterConfig, SuffixFilterConfig |
| Sniffer | ConfigSchema | FileWatchConfig, FileCopyConfig |
| StorageController | ConfigSchema | StorageConfig, PathConfig |
| RecyclerService | ConfigSchema | StorageConfig, PathConfig |
| SnifferLifecycleManager | ConfigSchema | StorageConfig |
| BasicFileFilter | ConfigSchema | FileFilterConfig |
| SuffixFilter | ConfigSchema | SuffixFilterConfig, FileFilterConfig |
| FileHistoryPanel | ConfigSchema | UIConfig |
| StorageManagementPanel | ConfigSchema | StorageConfig |

- [ ] **Step 1: Update all source files**

For each file listed above:
1. Read the file to find all `ConfigSchema.FIELD` references
2. Replace `import ...ConfigSchema;` with the appropriate `import ...configs.XxxConfig;`
3. Replace all `ConfigSchema.FIELD` with `XxxConfig.FIELD`

- [ ] **Step 2: Update test files**

Apply the same import migration to:
- `test/com/superredrock/usbthief/core/ConfigManagerTest.java` — references CORE_POOL_SIZE, INDEX_CACHE_SIZE, COPY_READ_RATE_LIMIT, HASH_ALGORITHM, WATCH_ENABLED, DEVICE_BLACKLIST_BY_SERIAL → import ThreadPoolConfig, IndexConfig, RateLimitConfig, FileCopyConfig, FileWatchConfig, BlacklistConfig
- `test/com/superredrock/usbthief/core/ConfigSchemaTest.java` — references CORE_POOL_SIZE, KEEP_ALIVE_TIME_SECONDS, etc. → import ThreadPoolConfig, RateLimitConfig, IndexConfig, FileWatchConfig, StorageConfig, BlacklistConfig
- Any other test files referencing ConfigSchema fields

- [ ] **Step 3: Compile to verify all imports resolved**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run tests to verify nothing broke**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: migrate ConfigSchema references to per-domain config classes"
```

---

### Task 5: Add i18n keys for tree navigation

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/messages.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_en.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_zh.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_ja.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_de.properties`

- [ ] **Step 1: Add keys to messages.properties (default/English)**

Append after the existing `# ConfigDialog` section, replacing the old config keys:

```properties
# ConfigDialog - Tree Navigation
config.title=Preferences
config.search.placeholder=Search settings...
config.breadcrumb.format={0} > {1}
config.button.ok=OK
config.button.cancel=Cancel
config.success=Configuration saved successfully!
config.error.save=Failed to save configuration

# Config tree groups
config.group.general=General
config.group.file=File
config.group.index=Index
config.group.rateLimit=Rate Limiting
config.group.paths=Paths
config.group.ui=UI
config.group.security=Security
config.group.storage=Storage
config.group.advanced=Advanced

# Config tree leaves (display names for config classes)
config.category.threadPool=Thread Pool
config.category.scanner=Device Scanner
config.category.index=Index Management
config.category.fileCopy=File Copy
config.category.fileWatch=File Watch
config.category.rateLimit=Rate Limiting
config.category.paths=Paths
config.category.ui=UI
config.category.window=Window
config.category.blacklist=Blacklist
config.category.fileFilter=File Filter
config.category.suffixFilter=Suffix Filter
config.category.storage=Storage Management
config.category.statisticsApi=Statistics API
```

Note: Remove the old `config.button.import`, `config.button.export`, `config.button.save`, `config.button.reset`, `config.reset.*`, `config.import.*`, `config.export.*` keys since they are no longer used. Keep `config.success` and `config.error.save`.

- [ ] **Step 2: Add keys to messages_en.properties**

Same keys as above (English). Remove old config keys that are no longer used.

- [ ] **Step 3: Add keys to messages_zh.properties**

```properties
# ConfigDialog - 树形导航
config.title=首选项
config.search.placeholder=搜索设置...
config.breadcrumb.format={0} > {1}
config.button.ok=确定
config.button.cancel=取消
config.success=配置保存成功！
config.error.save=保存配置失败

# 配置树分组
config.group.general=常规
config.group.file=文件
config.group.index=索引
config.group.rateLimit=速率限制
config.group.paths=路径
config.group.ui=界面
config.group.security=安全
config.group.storage=存储
config.group.advanced=高级

# 配置树叶节点
config.category.threadPool=线程池
config.category.scanner=设备扫描
config.category.index=索引管理
config.category.fileCopy=文件复制
config.category.fileWatch=文件监控
config.category.rateLimit=速率限制
config.category.paths=路径
config.category.ui=界面
config.category.window=窗口
config.category.blacklist=黑名单
config.category.fileFilter=文件过滤
config.category.suffixFilter=后缀过滤
config.category.storage=存储管理
config.category.statisticsApi=统计 API
```

- [ ] **Step 4: Add keys to messages_ja.properties**

```properties
# ConfigDialog - ツリーナビゲーション
config.title=環境設定
config.search.placeholder=設定を検索...
config.breadcrumb.format={0} > {1}
config.button.ok=OK
config.button.cancel=キャンセル
config.success=設定が正常に保存されました！
config.error.save=設定の保存に失敗しました

# 設定ツリーグループ
config.group.general=一般
config.group.file=ファイル
config.group.index=インデックス
config.group.rateLimit=レート制限
config.group.paths=パス
config.group.ui=UI
config.group.security=セキュリティ
config.group.storage=ストレージ
config.group.advanced=詳細

# 設定ツリーリーフ
config.category.threadPool=スレッドプール
config.category.scanner=デバイススキャン
config.category.index=インデックス管理
config.category.fileCopy=ファイルコピー
config.category.fileWatch=ファイル監視
config.category.rateLimit=レート制限
config.category.paths=パス
config.category.ui=UI
config.category.window=ウィンドウ
config.category.blacklist=ブラックリスト
config.category.fileFilter=ファイルフィルタ
config.category.suffixFilter=サフィックスフィルタ
config.category.storage=ストレージ管理
config.category.statisticsApi=統計 API
```

- [ ] **Step 5: Add keys to messages_de.properties**

```properties
# ConfigDialog - Baum-Navigation
config.title=Einstellungen
config.search.placeholder=Einstellungen suchen...
config.breadcrumb.format={0} > {1}
config.button.ok=OK
config.button.cancel=Abbrechen
config.success=Konfiguration erfolgreich gespeichert!
config.error.save=Konfiguration konnte nicht gespeichert werden

# Konfigurationsbaum-Gruppen
config.group.general=Allgemein
config.group.file=Datei
config.group.index=Index
config.group.rateLimit=Ratenbegrenzung
config.group.paths=Pfade
config.group.ui=UI
config.group.security=Sicherheit
config.group.storage=Speicher
config.group.advanced=Erweitert

# Konfigurationsbaum-Blätter
config.category.threadPool=Thread-Pool
config.category.scanner=Geräte-Scan
config.category.index=Indexverwaltung
config.category.fileCopy=Dateikopie
config.category.fileWatch=Dateiüberwachung
config.category.rateLimit=Ratenbegrenzung
config.category.paths=Pfade
config.category.ui=UI
config.category.window=Fenster
config.category.blacklist=Sperrliste
config.category.fileFilter=Dateifilter
config.category.suffixFilter=Suffix-Filter
config.category.storage=Speicherverwaltung
config.category.statisticsApi=Statistik-API
```

- [ ] **Step 6: Commit**

```bash
git add src/com/superredrock/usbthief/gui/messages*.properties
git commit -m "feat: add i18n keys for tree-based config dialog navigation"
```

---

### Task 6: Rewrite ConfigDialog with tree navigation

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/dailog/ConfigDialog.java`

This is the main UI rewrite. The new ConfigDialog replaces JTabbedPane with JSplitPane (JTree left, content right), search field, breadcrumb, and OK/Cancel buttons.

- [ ] **Step 1: Write the new ConfigDialog.java**

```java
package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.config.ConfigEntry;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.config.ConfigType;
import com.superredrock.usbthief.core.config.configs.*;
import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration dialog with IntelliJ-style tree navigation.
 * Left panel: search box + JTree with hierarchical categories.
 * Right panel: breadcrumb + settings form for selected category.
 * Bottom: OK + Cancel buttons.
 */
public class ConfigDialog extends JDialog {

    private static final I18nManager i18n = I18nManager.getInstance();
    private final ConfigManager configManager;

    // Tree structure: group node → config class nodes
    // Each leaf node stores a String key (i18n category key) used to look up entries.
    private final Map<String, List<ConfigEntry<?>>> entriesByCategory;
    private final Map<String, JComponent> valueComponents = new HashMap<>();

    // UI components
    private JTree tree;
    private JTextField searchField;
    private JLabel breadcrumbLabel;
    private JPanel contentPanel;
    private JScrollPane contentScrollPane;

    // Tree node data: stores the i18n key for the category
    private static class CategoryNode {
        final String categoryKey; // e.g. "config.category.threadPool"
        final String groupKey;    // e.g. "config.group.general" (null for group nodes)

        CategoryNode(String categoryKey, String groupKey) {
            this.categoryKey = categoryKey;
            this.groupKey = groupKey;
        }

        @Override
        public String toString() {
            if (categoryKey != null) {
                return i18n.getMessage(categoryKey);
            }
            return groupKey != null ? i18n.getMessage(groupKey) : "";
        }
    }

    public ConfigDialog(JFrame parent) {
        super(parent, i18n.getMessage("config.title"), true);
        setSize(900, 650);
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(700, 500));

        this.configManager = ConfigManager.getInstance();
        this.entriesByCategory = ConfigSchema.getEntriesByCategory();

        initComponents();
    }

    private void initComponents() {
        // --- Left panel: search + tree ---
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setMinimumSize(new Dimension(150, 0));

        // Search field
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", i18n.getMessage("config.search.placeholder"));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterTree(); }
            @Override public void removeUpdate(DocumentEvent e) { filterTree(); }
            @Override public void changedUpdate(DocumentEvent e) { filterTree(); }
        });
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        searchPanel.add(searchField, BorderLayout.CENTER);
        leftPanel.add(searchPanel, BorderLayout.NORTH);

        // Tree
        DefaultMutableTreeNode rootNode = buildTreeModel();
        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ConfigTreeCellRenderer());
        tree.addTreeSelectionListener(this::onTreeSelectionChanged);
        tree.setSelectionRow(1); // select first leaf

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(treeScroll, BorderLayout.CENTER);

        // --- Right panel: breadcrumb + content ---
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        // Breadcrumb
        breadcrumbLabel = new JLabel(" ");
        breadcrumbLabel.setFont(breadcrumbLabel.getFont().deriveFont(Font.BOLD, breadcrumbLabel.getFont().getSize() + 2f));
        rightPanel.add(breadcrumbLabel, BorderLayout.NORTH);

        // Content scroll
        contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        contentScrollPane = new JScrollPane(contentPanel);
        contentScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
        rightPanel.add(contentScrollPane, BorderLayout.CENTER);

        // --- Split pane ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.0);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // --- Bottom buttons ---
        JButton okButton = new JButton(i18n.getMessage("config.button.ok"));
        okButton.addActionListener(e -> saveConfig());

        JButton cancelButton = new JButton(i18n.getMessage("config.button.cancel"));
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        // --- Layout ---
        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
    }

    /**
     * Build the tree model with group nodes and config class leaf nodes.
     */
    private DefaultMutableTreeNode buildTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // Define the tree structure: group → category keys
        // Each String in the inner list matches a Category constant in the config class
        addGroup(root, "config.group.general", List.of(
                "config.category.threadPool",
                "config.category.scanner"
        ));
        addGroup(root, "config.group.file", List.of(
                "config.category.fileCopy",
                "config.category.fileWatch",
                "config.category.fileFilter",
                "config.category.suffixFilter"
        ));
        addGroup(root, "config.group.index", List.of(
                "config.category.index"
        ));
        addGroup(root, "config.group.rateLimit", List.of(
                "config.category.rateLimit"
        ));
        addGroup(root, "config.group.paths", List.of(
                "config.category.paths"
        ));
        addGroup(root, "config.group.ui", List.of(
                "config.category.ui",
                "config.category.window"
        ));
        addGroup(root, "config.group.security", List.of(
                "config.category.blacklist"
        ));
        addGroup(root, "config.group.storage", List.of(
                "config.category.storage"
        ));
        addGroup(root, "config.group.advanced", List.of(
                "config.category.statisticsApi"
        ));

        return root;
    }

    private void addGroup(DefaultMutableTreeNode root, String groupKey, List<String> categoryKeys) {
        CategoryNode groupData = new CategoryNode(null, groupKey);
        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupData);
        for (String categoryKey : categoryKeys) {
            CategoryNode leafData = new CategoryNode(categoryKey, groupKey);
            groupNode.add(new DefaultMutableTreeNode(leafData));
        }
        root.add(groupNode);
    }

    /**
     * Map i18n category key to the actual category string used in ConfigEntry.category().
     */
    private String resolveCategoryName(String categoryKey) {
        return switch (categoryKey) {
            case "config.category.threadPool" -> ThreadPoolConfig.CATEGORY;
            case "config.category.scanner" -> DeviceScannerConfig.CATEGORY;
            case "config.category.index" -> IndexConfig.CATEGORY;
            case "config.category.fileCopy" -> FileCopyConfig.CATEGORY;
            case "config.category.fileWatch" -> FileWatchConfig.CATEGORY;
            case "config.category.rateLimit" -> RateLimitConfig.CATEGORY;
            case "config.category.paths" -> PathConfig.CATEGORY;
            case "config.category.ui" -> UIConfig.CATEGORY;
            case "config.category.window" -> WindowConfig.CATEGORY;
            case "config.category.blacklist" -> BlacklistConfig.CATEGORY;
            case "config.category.fileFilter" -> FileFilterConfig.CATEGORY;
            case "config.category.suffixFilter" -> SuffixFilterConfig.CATEGORY;
            case "config.category.storage" -> StorageConfig.CATEGORY;
            case "config.category.statisticsApi" -> StatisticsApiConfig.CATEGORY;
            default -> null;
        };
    }

    /**
     * Called when tree selection changes — updates the right panel content.
     */
    private void onTreeSelectionChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (!(userObject instanceof CategoryNode catNode)) return;

        // Only leaf nodes have a categoryKey
        if (catNode.categoryKey == null) return;

        // Update breadcrumb
        String groupName = catNode.groupKey != null ? i18n.getMessage(catNode.groupKey) : "";
        String categoryName = i18n.getMessage(catNode.categoryKey);
        breadcrumbLabel.setText(i18n.getMessage("config.breadcrumb.format", groupName, categoryName));

        // Update content
        String categoryNameInternal = resolveCategoryName(catNode.categoryKey);
        if (categoryNameInternal == null) return;

        List<ConfigEntry<?>> entries = entriesByCategory.get(categoryNameInternal);
        if (entries == null || entries.isEmpty()) return;

        showCategoryPanel(entries);
    }

    /**
     * Build and display the settings panel for the given category entries.
     */
    private void showCategoryPanel(List<ConfigEntry<?>> entries) {
        valueComponents.clear();

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (ConfigEntry<?> entry : entries) {
            JComponent component = createValueComponent(entry);
            valueComponents.put(entry.key(), component);

            // Label
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.0;
            JLabel label = new JLabel(entry.key() + ":");
            label.setToolTipText(entry.description());
            panel.add(label, gbc);

            // Value component
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            panel.add(component, gbc);

            row++;
        }

        // Push content to top
        gbc.gridy = row;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        contentPanel.removeAll();
        contentPanel.add(panel, BorderLayout.NORTH);
        contentPanel.revalidate();
        contentPanel.repaint();
        contentScrollPane.getVerticalScrollBar().setValue(0);
    }

    /**
     * Create appropriate UI component based on configuration entry type.
     */
    @SuppressWarnings("unchecked")
    private JComponent createValueComponent(ConfigEntry<?> entry) {
        Object currentValue = configManager.get(entry);

        if (entry.type() == ConfigType.INT) {
            return createSpinner((Integer) currentValue, entry.description());
        } else if (entry.type() == ConfigType.LONG) {
            return createSpinner((Long) currentValue, entry.description());
        } else if (entry.type() == ConfigType.BOOLEAN) {
            return createCheckBox((Boolean) currentValue, entry.description());
        } else if (entry.type() == ConfigType.STRING) {
            return createTextField((String) currentValue, entry.description());
        } else if (entry.type() == ConfigType.STRING_LIST) {
            return createTextArea((List<String>) currentValue, entry.description());
        }
        return new JLabel("Unknown");
    }

    private JSpinner createSpinner(Number value, String description) {
        JSpinner spinner;
        if (value instanceof Integer) {
            SpinnerNumberModel model = new SpinnerNumberModel((Integer) value, 0, Integer.MAX_VALUE, 1);
            spinner = new JSpinner(model);
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        } else {
            SpinnerNumberModel model = new SpinnerNumberModel((Long) value, 0L, Long.MAX_VALUE, 1L);
            spinner = new JSpinner(model);
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        }
        spinner.setToolTipText(description);
        return spinner;
    }

    private JCheckBox createCheckBox(Boolean value, String description) {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(value);
        checkBox.setToolTipText(description);
        return checkBox;
    }

    private JTextField createTextField(String value, String description) {
        JTextField textField = new JTextField(value != null ? value : "", 30);
        textField.setToolTipText(description);
        return textField;
    }

    private JComponent createTextArea(List<String> values, String description) {
        JTextArea textArea = new JTextArea(values != null ? String.join(";", values) : "", 5, 30);
        textArea.setToolTipText(description + " (" + i18n.getMessage("config.tooltip.separator") + ")");
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    /**
     * Save all configuration values from the current UI components.
     */
    private void saveConfig() {
        try {
            for (Map.Entry<String, List<ConfigEntry<?>>> catEntry : entriesByCategory.entrySet()) {
                for (ConfigEntry<?> entry : catEntry.getValue()) {
                    JComponent component = valueComponents.get(entry.key());
                    if (component == null) continue;

                    @SuppressWarnings("unchecked")
                    ConfigEntry<Object> typedEntry = (ConfigEntry<Object>) entry;

                    Object newValue;
                    if (entry.type() == ConfigType.INT) {
                        newValue = ((Number) ((JSpinner) component).getValue()).intValue();
                    } else if (entry.type() == ConfigType.LONG) {
                        newValue = ((Number) ((JSpinner) component).getValue()).longValue();
                    } else if (entry.type() == ConfigType.BOOLEAN) {
                        newValue = ((JCheckBox) component).isSelected();
                    } else if (entry.type() == ConfigType.STRING) {
                        newValue = ((JTextField) component).getText();
                    } else if (entry.type() == ConfigType.STRING_LIST) {
                        // TextArea is wrapped in JScrollPane
                        JViewport viewport = ((JScrollPane) component).getViewport();
                        JTextArea textArea = (JTextArea) viewport.getView();
                        String text = textArea.getText();
                        String[] parts = text.split(";");
                        List<String> list = new ArrayList<>();
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                list.add(trimmed);
                            }
                        }
                        newValue = list;
                    } else {
                        continue;
                    }

                    configManager.set(typedEntry, newValue);
                }
            }

            // Also save entries that are not currently displayed (other categories)
            // Since valueComponents only has the current category, we need to iterate all
            // Actually we saved only current panel. We need to re-read ALL categories from their panels.
            // Fix: iterate all categories and all their entries, reading from valueComponents map.
            // But valueComponents is cleared on each panel switch...
            // We need to accumulate components across panel switches.

            // This is handled by the new approach below.

            JOptionPane.showMessageDialog(this, i18n.getMessage("config.success"),
                    i18n.getMessage("common.success"), JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, i18n.getMessage("config.error.save") + ": " + e.getMessage(),
                    i18n.getMessage("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Filter tree nodes based on search text.
     */
    private void filterTree() {
        String text = searchField.getText().trim().toLowerCase();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        if (text.isEmpty()) {
            // Show all nodes
            for (int i = 0; i < root.getChildCount(); i++) {
                DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(i);
                group.setAllowsChildren(true);
                for (int j = 0; j < group.getChildCount(); j++) {
                    DefaultMutableTreeNode leaf = (DefaultMutableTreeNode) group.getChildAt(j);
                    ((CategoryNode) leaf.getUserObject()).toString(); // ensure displayable
                }
            }
            model.reload();
            // Expand all
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
            return;
        }

        // Filter: hide nodes that don't match
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(i);
            boolean groupHasMatch = false;

            for (int j = group.getChildCount() - 1; j >= 0; j--) {
                DefaultMutableTreeNode leaf = (DefaultMutableTreeNode) group.getChildAt(j);
                CategoryNode data = (CategoryNode) leaf.getUserObject();
                String catName = resolveCategoryName(data.categoryKey);
                boolean matches = matchesSearch(catName, text);
                if (matches) {
                    groupHasMatch = true;
                }
            }

            // Show/hide group based on whether any child matches
            // (Using model reload approach since JTree doesn't natively hide nodes)
        }

        // Simpler approach: expand all and select first match
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }

        // Select first matching leaf
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < group.getChildCount(); j++) {
                DefaultMutableTreeNode leaf = (DefaultMutableTreeNode) group.getChildAt(j);
                CategoryNode data = (CategoryNode) leaf.getUserObject();
                String catName = resolveCategoryName(data.categoryKey);
                if (matchesSearch(catName, text)) {
                    TreePath path = new TreePath(leaf.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    return;
                }
            }
        }
    }

    private boolean matchesSearch(String categoryName, String searchText) {
        if (categoryName == null) return false;
        // Match against category display name and all entry keys/descriptions in that category
        if (categoryName.toLowerCase().contains(searchText)) return true;

        List<ConfigEntry<?>> entries = entriesByCategory.get(categoryName);
        if (entries == null) return false;

        for (ConfigEntry<?> entry : entries) {
            if (entry.key().toLowerCase().contains(searchText)) return true;
            if (entry.description().toLowerCase().contains(searchText)) return true;
        }
        return false;
    }

    /**
     * Custom tree cell renderer for IntelliJ-style full-width selection highlight.
     */
    private static class ConfigTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                       boolean expanded, boolean leaf, int row,
                                                       boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            return this;
        }
    }
}
```

**Important design note:** The `saveConfig()` method above has a bug — `valueComponents` is cleared each time the user switches categories, so only the currently-displayed category gets saved. This must be fixed by accumulating components. See Task 7 for the fix.

- [ ] **Step 2: Compile to check for errors**

Run: `mvn compile -q`
Expected: May have compile errors from ConfigSchema import removal — fix any remaining references.

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/dailog/ConfigDialog.java
git commit -m "feat: rewrite ConfigDialog with IntelliJ-style tree navigation"
```

---

### Task 7: Fix save logic — accumulate value components across panel switches

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/dailog/ConfigDialog.java`

The current design clears `valueComponents` on each panel switch. To save all categories at once, we need a persistent map that accumulates components across switches. When the user navigates to a category that was already visited, we restore its panel instead of recreating it.

- [ ] **Step 1: Add a cache for already-built category panels**

Add a field and modify the `showCategoryPanel` method in ConfigDialog:

```java
// Add field:
private final Map<String, JPanel> categoryPanelCache = new HashMap<>();
private String currentCategory = null;

// Replace showCategoryPanel:
private void showCategoryPanel(List<ConfigEntry<?>> entries) {
    if (entries.isEmpty()) return;
    String categoryName = entries.get(0).category();

    // If we already built this panel, restore it
    if (categoryPanelCache.containsKey(categoryName)) {
        // First, save any modified values from current panel back to valueComponents
        captureCurrentValues();

        contentPanel.removeAll();
        contentPanel.add(categoryPanelCache.get(categoryName), BorderLayout.NORTH);
        contentPanel.revalidate();
        contentPanel.repaint();
        contentScrollPane.getVerticalScrollBar().setValue(0);

        // Restore valueComponents for this category
        valueComponents.clear();
        restoreComponentsForCategory(categoryName);
        currentCategory = categoryName;
        return;
    }

    // Build new panel
    valueComponents.clear();
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 4, 4, 4);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    int row = 0;
    for (ConfigEntry<?> entry : entries) {
        JComponent component = createValueComponent(entry);
        valueComponents.put(entry.key(), component);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JLabel label = new JLabel(entry.key() + ":");
        label.setToolTipText(entry.description());
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(component, gbc);
        row++;
    }

    gbc.gridy = row;
    gbc.weighty = 1.0;
    panel.add(Box.createVerticalGlue(), gbc);

    categoryPanelCache.put(categoryName, panel);

    // Save valueComponents mapping for this category
    saveComponentsForCategory(categoryName);

    contentPanel.removeAll();
    contentPanel.add(panel, BorderLayout.NORTH);
    contentPanel.revalidate();
    contentPanel.repaint();
    contentScrollPane.getVerticalScrollBar().setValue(0);
    currentCategory = categoryName;
}
```

- [ ] **Step 2: Add helper methods for component persistence**

```java
// Store all value components by category for later retrieval during save
private final Map<String, Map<String, JComponent>> allCategoryComponents = new HashMap<>();

private void saveComponentsForCategory(String categoryName) {
    Map<String, JComponent> components = new HashMap<>(valueComponents);
    allCategoryComponents.put(categoryName, components);
}

private void restoreComponentsForCategory(String categoryName) {
    Map<String, JComponent> saved = allCategoryComponents.get(categoryName);
    if (saved != null) {
        valueComponents.putAll(saved);
    }
}

private void captureCurrentValues() {
    if (currentCategory != null) {
        saveComponentsForCategory(currentCategory);
    }
}
```

- [ ] **Step 3: Fix saveConfig to iterate all categories**

Replace the `saveConfig()` method:

```java
private void saveConfig() {
    try {
        // Capture current panel values first
        captureCurrentValues();

        // Now iterate all accumulated components across all visited categories
        for (Map.Entry<String, Map<String, JComponent>> catEntry : allCategoryComponents.entrySet()) {
            String categoryName = catEntry.getKey();
            List<ConfigEntry<?>> entries = entriesByCategory.get(categoryName);
            if (entries == null) continue;

            for (ConfigEntry<?> entry : entries) {
                JComponent component = catEntry.getValue().get(entry.key());
                if (component == null) continue;

                @SuppressWarnings("unchecked")
                ConfigEntry<Object> typedEntry = (ConfigEntry<Object>) entry;
                Object newValue = readComponentValue(entry, component);
                if (newValue != null) {
                    configManager.set(typedEntry, newValue);
                }
            }
        }

        JOptionPane.showMessageDialog(this, i18n.getMessage("config.success"),
                i18n.getMessage("common.success"), JOptionPane.INFORMATION_MESSAGE);
        dispose();
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, i18n.getMessage("config.error.save") + ": " + e.getMessage(),
                i18n.getMessage("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}

private Object readComponentValue(ConfigEntry<?> entry, JComponent component) {
    if (entry.type() == ConfigType.INT) {
        return ((Number) ((JSpinner) component).getValue()).intValue();
    } else if (entry.type() == ConfigType.LONG) {
        return ((Number) ((JSpinner) component).getValue()).longValue();
    } else if (entry.type() == ConfigType.BOOLEAN) {
        return ((JCheckBox) component).isSelected();
    } else if (entry.type() == ConfigType.STRING) {
        return ((JTextField) component).getText();
    } else if (entry.type() == ConfigType.STRING_LIST) {
        JViewport viewport = ((JScrollPane) component).getViewport();
        JTextArea textArea = (JTextArea) viewport.getView();
        String text = textArea.getText();
        String[] parts = text.split(";");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }
    return null;
}
```

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/com/superredrock/usbthief/gui/dailog/ConfigDialog.java
git commit -m "fix: accumulate value components across panel switches for correct save"
```

---

### Task 8: Update tests for new config class imports

**Files:**
- Modify: `test/com/superredrock/usbthief/core/ConfigSchemaTest.java`
- Modify: `test/com/superredrock/usbthief/core/ConfigManagerTest.java`

- [ ] **Step 1: Update ConfigSchemaTest.java imports and references**

Change imports from `ConfigSchema` to the new config classes and update all `ConfigSchema.FIELD` to `XxxConfig.FIELD`:

```java
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.config.configs.IndexConfig;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import com.superredrock.usbthief.core.config.configs.StorageConfig;
import com.superredrock.usbthief.core.config.configs.BlacklistConfig;
```

Then replace in test bodies:
- `ConfigSchema.CORE_POOL_SIZE` → `ThreadPoolConfig.CORE_POOL_SIZE`
- `ConfigSchema.KEEP_ALIVE_TIME_SECONDS` → `ThreadPoolConfig.KEEP_ALIVE_TIME_SECONDS`
- `ConfigSchema.TASK_QUEUE_CAPACITY` → `ThreadPoolConfig.TASK_QUEUE_CAPACITY`
- `ConfigSchema.COPY_READ_RATE_LIMIT` → `RateLimitConfig.COPY_READ_RATE_LIMIT`
- `ConfigSchema.COPY_WRITE_RATE_LIMIT` → `RateLimitConfig.COPY_WRITE_RATE_LIMIT`
- `ConfigSchema.COPY_RATE_BURST_SIZE` → `RateLimitConfig.COPY_RATE_BURST_SIZE`
- `ConfigSchema.INDEX_CACHE_SIZE` → `IndexConfig.INDEX_CACHE_SIZE`
- `ConfigSchema.WATCH_ENABLED` → `FileWatchConfig.WATCH_ENABLED`
- `ConfigSchema.STORAGE_ENABLED` → `StorageConfig.STORAGE_ENABLED`
- `ConfigSchema.DEVICE_BLACKLIST_BY_SERIAL` → `BlacklistConfig.DEVICE_BLACKLIST_BY_SERIAL`

- [ ] **Step 2: Update ConfigManagerTest.java imports and references**

Same pattern:
```java
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import com.superredrock.usbthief.core.config.configs.IndexConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import com.superredrock.usbthief.core.config.configs.BlacklistConfig;
```

Then replace all `ConfigSchema.FIELD` references accordingly.

- [ ] **Step 3: Run tests**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add test/
git commit -m "test: update tests to use per-domain config class imports"
```

---

### Task 9: Final compilation and smoke test

- [ ] **Step 1: Full clean compile**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 3: Manual smoke test**

Run: `java -p target/classes -m UsbThief/com.superredrock.usbthief.Main --enable-preview`

Verify:
1. Application starts without errors
2. Config → Preferences menu opens the new dialog
3. Tree navigation shows all 9 groups with correct sub-items
4. Clicking a leaf node shows the correct settings on the right
5. Breadcrumb updates correctly
6. Search field filters and selects matching nodes
7. OK saves, Cancel closes without saving
8. No exceptions in console

- [ ] **Step 4: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: address smoke test findings"
```
