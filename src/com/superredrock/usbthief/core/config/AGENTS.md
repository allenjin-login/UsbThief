# Configuration Package - Type-Safe Settings Management

**Package:** `com.superredrock.usbthief.core.config`
**Generated:** 2026-01-30

## OVERVIEW
Type-safe configuration system using Java Preferences API with import/export functionality.

## STRUCTURE
```
core/config/
├── ConfigManager.java      # Singleton: get/set values, import/export
├── ConfigSchema.java       # Registry of all ConfigEntry constants
├── ConfigEntry.java        # Type-safe config entry definition
├── ConfigType.java         # Enum with serialization logic (INT, LONG, BOOLEAN, STRING, STRING_LIST)
└── package-info.java
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Get/set config values | `ConfigManager.getInstance()` | Singleton access to all settings |
| Register new settings | `ConfigSchema` | Add static ConfigEntry fields |
| Define config entry | `ConfigEntry.of()` | Factory methods for each type |
| Type serialization | `ConfigType` enum | Handles Preferences API get/put |
| Import/Export | `ConfigManager.import*(), export*()` | Properties and JSON formats |
| Device blacklist | `ConfigManager.isDeviceBlacklistedBySerial()` | Special helper methods |

## CONVENTIONS
- **Type safety**: All config values accessed via typed ConfigEntry<T> generics
- **Categories**: Each entry has a category for grouping (e.g., "Thread Pool", "File Watch")
- **Singleton**: ConfigManager uses static initialization-on-demand holder idiom
- **Defaults**: Each ConfigEntry has a default value (used when key not found in Preferences)
- **Persistence**: All changes automatically persisted to java.util.prefs.Preferences

## CONFIGURATION ENTRY EXAMPLE
```java
// In ConfigSchema.java
public static final ConfigEntry<Integer> BUFFER_SIZE =
    intEntry("bufferSize", "Buffer size for file copying", 16384, "File Copy");

// Usage elsewhere
int bufferSize = ConfigManager.getInstance().get(ConfigSchema.BUFFER_SIZE);
ConfigManager.getInstance().set(ConfigSchema.BUFFER_SIZE, 32768);
```

## ANTI-PATTERNS (THIS PACKAGE)
- **DO NOT create ConfigEntry instances outside ConfigSchema** - all entries must be registered centrally
- **DO NOT use raw strings for config keys** - always use ConfigSchema constants
- **DO NOT access Preferences API directly** - use ConfigManager methods
- **DO NOT modify ConfigSchema entries after class initialization** - entries are registered in static block

## UNIQUE STYLES
- **Enum-based serialization**: ConfigType enum handles type-specific get/put logic for Preferences API
- **String list delimiter**: STRING_LIST type uses semicolon (`;`) delimiter for storage
- **Import/export formats**: Supports both Properties (.properties) and JSON formats
- **Category grouping**: Entries grouped by category for organized export
- **Special helper methods**: ConfigManager has convenience methods for device blacklist operations

## NOTES
- Preferences API stores data in OS-specific registry/location (Windows: HKEY_CURRENT_USER\Software\JavaSoft\Prefs...)
- JSON import uses simple regex parsing (no external JSON library dependency)
- Export includes both current values and default values for comparison
- ConfigManager singleton uses lazy initialization via static Holder class
