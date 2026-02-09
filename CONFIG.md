# UsbThief Configuration Guide

## Overview

UsbThief uses Java's `Preferences` API to store configuration. All settings are persisted in the user's preferences and can be modified programmatically through the `Config` class.

### New Configuration System (v2.0)

The configuration system has been refactored to be more extensible and maintainable:

- **Type-safe configuration**: Uses `ConfigEntry` with strongly-typed access
- **Import/Export support**: Export configuration to Properties or JSON files
- **Organized schema**: Configuration entries grouped by category in `ConfigSchema`
- **Backward compatible**: All existing `Config` static methods continue to work

## Configuration Parameters

### Thread Pool Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `corePoolSize` | 2 | Minimum number of threads in the thread pool |
| `maxPoolSize` | 8 | Maximum number of threads in the thread pool |
| `keepAliveTimeSeconds` | 60 | Idle thread keep-alive time in seconds |
| `taskQueueCapacity` | 1024 | Maximum number of tasks in the queue |

### Device Scanner Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `initialDelaySeconds` | 10 | Initial delay before first device scan (seconds) |
| `delaySeconds` | 5 | Interval between device scans (seconds) |

### Index Management Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `saveInitialDelaySeconds` | 30 | Initial delay before first index save (seconds) |
| `saveDelaySeconds` | 60 | Interval between index saves (seconds) |
| `indexPath` | "index.obj" | Path to the index file (relative or absolute) |

### File Copy Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `bufferSize` | 16384 (16KB) | Buffer size for file copying (bytes) |
| `hashBufferSize` | 1024 (1KB) | Buffer size for hash calculation (bytes) |
| `maxFileSize` | 104857600 (100MB) | Maximum file size to copy (bytes) |
| `retryCount` | 5 | Number of retry attempts for failed operations |
| `timeoutMillis` | 100 | Timeout for retry queue polling (milliseconds) |

### File Watch Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `watchEnabled` | true | Enable/disable real-time file monitoring |
| `watchThreshold` | 10 | Number of file changes before triggering copy |
| `watchResetIntervalSeconds` | 60 | Interval to reset change counter (seconds) |

### Path Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `workPath` | "" (current directory) | Working directory for storing copied files |

## How to Modify Configuration

### Method 1: Using Config Class (Programmatic) - Recommended

```java
import com.superredrock.usbthief.core.Config;

// Set configuration values
Config.setCorePoolSize(4);
Config.setMaxPoolSize(16);
Config.setBufferSize(32 * 1024);  // 32KB buffer
Config.setMaxFileSize(200 * 1024 * 1024);  // 200MB limit

// Get configuration values
int poolSize = Config.getMaxPoolSize();
```

### Method 2: Using ConfigManager for Advanced Operations

```java
import com.superredrock.usbthief.core.Config;
import com.superredrock.usbthief.core.config.ConfigManager;
import java.nio.file.Path;

// Get manager instance
ConfigManager manager = Config.getManager();

// Export configuration to properties file
manager.exportToProperties(Path.of("config.properties"));

// Export configuration to JSON file
manager.exportToJson(Path.of("config.json"));

// Import from properties file
manager.importFromProperties(Path.of("config.properties"));

// Import from JSON file
manager.importFromJson(Path.of("config.json"));

// Reset to defaults
manager.resetToDefaults();
```

### Method 3: Using Preferences API Directly - Legacy

```java
import java.util.prefs.Preferences;

Preferences prefs = Preferences.userNodeForPackage(com.superredrock.usbthief.core.Config.class);
prefs.putInt("maxPoolSize", 16);
prefs.putString("workPath", "/path/to/storage");
```

### Reset to Defaults

```java
Config.resetToDefaults();
// or
Config.getManager().resetToDefaults();
```

## Performance Tuning Guide

### High Performance Setup
For systems with high-performance SSDs and many small files:

```java
Config.setCorePoolSize(8);
Config.setMaxPoolSize(32);
Config.setBufferSize(64 * 1024);  // 64KB buffer
Config.setHashBufferSize(4 * 1024);  // 4KB hash buffer
```

### Low Resource Setup
For systems with limited memory:

```java
Config.setCorePoolSize(1);
Config.setMaxPoolSize(4);
Config.setBufferSize(8 * 1024);  // 8KB buffer
Config.setTaskQueueCapacity(256);
```

### Large File Handling
For copying large files:

```java
Config.setMaxFileSize(1024 * 1024 * 1024);  // 1GB limit
Config.setBufferSize(1024 * 1024);  // 1MB buffer
Config.setRetryCount(3);  // Fewer retries for large files
```

## Common Use Cases

### 1. Change Storage Location
```java
Config.setWorkPath("D:/UsbThiefStorage");
```

### 2. Adjust Scan Frequency for Faster Detection
```java
Config.setInitialDelaySeconds(2);
Config.setDelaySeconds(1);
```

### 3. Reduce Index Save Frequency for Better Performance
```java
Config.setSaveInitialDelaySeconds(60);
Config.setSaveDelaySeconds(300);  // Save every 5 minutes
```

### 4. Increase Thread Count for Multi-Core Systems
```java
Config.setMaxPoolSize(16);  // Adjust based on CPU cores
```

### 5. Enable File Monitoring with Custom Threshold
```java
Config.setWatchEnabled(true);
Config.setWatchThreshold(5);  // Copy after 5 file changes
Config.setWatchResetIntervalSeconds(120);  // Reset counter every 2 minutes
```

### 6. Disable File Monitoring for Manual Scan Only
```java
Config.setWatchEnabled(false);
```

## Configuration Persistence

All configuration changes are automatically persisted to the system's preferences:
- **Windows**: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\superredrock\usbthief\core`
- **Linux**: `~/.java/.userPrefs/com/superredrock/usbthief/core`
- **macOS**: `~/Library/Preferences/com.superredrock.usbthief.core.plist`

## Notes

- Changes to thread pool settings (`corePoolSize`, `maxPoolSize`, `keepAliveTimeSeconds`) require application restart to take effect
- Some settings (like `bufferSize`) affect newly created buffers only
- `maxFileSize` applies to newly discovered files; previously queued files are not affected
- `workPath` must be an absolute path or empty string (for current directory)
- File monitoring (`watchEnabled`) can be enabled/disabled at runtime, but changes take effect on new USB device connections
- `watchThreshold` controls when file changes trigger copy operations; higher values reduce frequency of copy operations
- `watchResetIntervalSeconds` prevents the change counter from accumulating indefinitely; set to 0 to disable auto-reset

## Troubleshooting

### Configuration Not Applied
- Ensure you're using the correct package name when accessing preferences
- Call `Config.resetToDefaults()` to clear corrupted settings
- Check file system permissions if `indexPath` fails

### Performance Issues
- Increase `bufferSize` and `hashBufferSize` for faster copying
- Adjust thread pool size based on available CPU cores
- Reduce `maxFileSize` to exclude large files that slow down operations

### Memory Issues
- Decrease `bufferSize` and `hashBufferSize`
- Reduce `maxPoolSize` and `taskQueueCapacity`
- Lower `maxFileSize` to prevent memory overload

### File Monitoring Not Working
- Verify `watchEnabled` is set to `true`
- Check file system supports `WatchService` (most modern file systems do)
- Increase `watchThreshold` if too many files trigger copy operations
- Review logs for `OVERFLOW` warnings indicating events were missed

---

## New Configuration System Architecture

### Components

#### ConfigEntry<T>
Represents a single configuration entry with:
- `key`: Configuration key name
- `type`: Configuration value type (INT, LONG, BOOLEAN, STRING, STRING_LIST)
- `defaultValue`: Default value if not set
- `description`: Human-readable description
- `category`: Category for grouping (e.g., "Thread Pool", "File Copy")

#### ConfigSchema
Central registry of all configuration entries:
- Defines all configuration constants (e.g., `CORE_POOL_SIZE`, `MAX_POOL_SIZE`)
- Provides methods to access all entries or group by category
- Thread-safe using `ConcurrentHashMap`

#### ConfigManager
Runtime configuration manager:
- Provides type-safe `get()` and `set()` methods
- Handles import/export to Properties and JSON formats
- Manages reset to defaults

#### Config (Backward Compatible)
Static facade maintaining original API:
- All original static methods (`getCorePoolSize()`, `setCorePoolSize()`, etc.)
- Internally delegates to `ConfigManager`
- Exposes `getManager()` for advanced operations

### Configuration Categories

| Category | Entries |
|----------|----------|
| Thread Pool | `CORE_POOL_SIZE`, `MAX_POOL_SIZE`, `KEEP_ALIVE_TIME_SECONDS`, `TASK_QUEUE_CAPACITY` |
| Device Scanner | `INITIAL_DELAY_SECONDS`, `DELAY_SECONDS` |
| Index Management | `SAVE_INITIAL_DELAY_SECONDS`, `SAVE_DELAY_SECONDS`, `INDEX_PATH` |
| File Copy | `BUFFER_SIZE`, `HASH_BUFFER_SIZE`, `MAX_FILE_SIZE`, `RETRY_COUNT`, `TIMEOUT_MILLIS` |
| File Watch | `WATCH_ENABLED`, `WATCH_THRESHOLD`, `WATCH_RESET_INTERVAL_SECONDS` |
| Rate Limiting | `COPY_RATE_LIMIT`, `COPY_RATE_BURST_SIZE` |
| Paths | `WORK_PATH` |
| Blacklist | `DEVICE_BLACKLIST` (deprecated), `DEVICE_BLACKLIST_BY_SERIAL` |

### Import/Export Formats

#### Properties Format
```
Thread Pool.corePoolSize=4
Thread Pool.corePoolSize.description=Minimum number of threads in the thread pool
Paths.workPath=D:/Storage
Paths.workPath.description=Working directory for storing copied files (empty = current directory)
Blacklist.deviceBlacklistBySerial=SERIAL1;SERIAL2;SERIAL3
Blacklist.deviceBlacklistBySerial.description=Device blacklist by serial number
```

#### JSON Format
```json
{
  "version": "1.0",
  "exportDate": "2026-01-28T10:00:00Z",
  "categories": {
    "Thread Pool": {
      "corePoolSize": {
        "description": "Minimum number of threads in the thread pool",
        "value": 4,
        "default": 2
      },
      "maxPoolSize": {
        "description": "Maximum number of threads in the thread pool",
        "value": 16,
        "default": 8
      }
    }
  }
}
```

### Adding New Configuration Entries

To add a new configuration parameter:

1. **Define entry in ConfigSchema**:
```java
public static final ConfigEntry<Integer> NEW_CONFIG =
    intEntry("newConfig", "Description", 42, "Category Name");
```

1. **Register entry** (add to static block in ConfigSchema):
```java
registerEntry(NEW_CONFIG);
```

1. **Add backward-compatible methods to Config** (optional):
```java
public static int getNewConfig() {
    return manager.get(ConfigSchema.NEW_CONFIG);
}

public static void setNewConfig(int value) {
    manager.set(ConfigSchema.NEW_CONFIG, value);
}
```

1. **No other changes needed!** The entry will automatically be:
   - Included in `getAllEntries()`
   - Grouped in `getEntriesByCategory()`
   - Exported/imported via ConfigManager
   - Reset by `resetToDefaults()`

### Testing the Configuration System

Run the test class to verify import/export functionality:

```bash
javac src/test/ConfigManagerTest.java -cp out
java -cp out com.superredrock.usbthief.test.ConfigManagerTest
```

The test will:
1. Record current configuration values
2. Modify various settings
3. Export to Properties and JSON files
4. Import from both formats
5. Reset to defaults
6. Verify schema entries
