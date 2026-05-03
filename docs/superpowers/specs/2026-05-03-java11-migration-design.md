# Java 25 → 11 Migration Design

**Date:** 2026-05-03
**Branch:** `java11` (long-term fork from `master`)
**Goal:** Support Windows 7 by targeting Java 11 with bundled JRE via jlink.

## Dependency Compatibility

All current dependencies support Java 11 — no version changes needed:

| Dependency | Version | Java 11 OK |
|---|---|---|
| FlatLaf | 3.7.1 | ✅ (Java 8+) |
| JNA jna-jpms | 5.18.1 | ✅ (runtime Java 6+) |
| JNA jna-platform-jpms | 5.18.1 | ✅ |
| Log4j2 | 2.25.4 | ✅ (runtime Java 8+) |
| Caffeine | 3.2.3 | ✅ (designed for Java 11+) |
| JUnit 5 | 5.11.4 | ✅ |
| Mockito | 5.15.2 | ✅ (requires Java 11+) |
| Launch4j Maven Plugin | 2.5.1 | ✅ |

## Build System Changes (pom.xml)

1. `maven.compiler.source` and `maven.compiler.target`: `25` → `11`
2. Remove `--enable-preview` from:
   - `maven-surefire-plugin` `<argLine>`
   - jlink launcher `.bat` patch (exec-maven-plugin `patch-launcher-bat`)
   - jlink launcher `.sh` patch (exec-maven-plugin `patch-launcher-sh`)
   - Launch4j `<jre><opts>`
   - `Main` class run command in CLAUDE.md
3. Remove `--enable-native-access=com.sun.jna,com.formdev.flatlaf` from launcher patches and Launch4j opts (Java 16+ flag, not needed on Java 11)
4. `jlink --compress zip-9` → `--compress 2` (Java 11 jlink uses levels 0-2)
5. Launch4j `<minVersion>25</minVersion>` → `<minVersion>11</minVersion>`

## Source Code Conversions

### Records → final classes (16 files)

Each record converts to a `final` class with:
- `private final` fields matching record components
- Constructor accepting all fields
- Accessor methods named after record components (e.g., `name()` not `getName()`)
- `equals()` using `Objects.equals()` for each field
- `hashCode()` using `Objects.hash()` for all fields
- `toString()` for debugging

Files: `CheckSum`, `CopyTask` (nested records), `MetricSnapshot`, `SpeedProbe`, `EventPanel`, `FileHistoryPanel`, `LogPanel`, `StorageController`, `FileHistoryRecord`, `LogBufferAppender`, `DeviceUtils`, `UsbHotplugMonitor`, `SnifferDebugSnapshot`, `EventBus`, `FileSelector`, `LanguageInfo`

### Switch expressions → traditional switch (15 files)

Switch expressions like:
```java
return switch (state) {
    case IDLE -> "idle";
    case RUNNING -> "running";
};
```
Become:
```java
switch (state) {
    case IDLE: return "idle";
    case RUNNING: return "running";
    default: throw new AssertionError();
}
```

Files: `DeviceInfoDialog`, `SnifferLifecycleManager`, `VolumeListPanel`, `EventPanel`, `FileHistoryPanel`, `I18nManager`, `WelcomeDialog`, `LogWindow`, `LogPanel`, `BasicFileFilter`, `CheckSum`, `SnifferDebugDialog`, `PriorityRule`, `FileSelector`, `ThemeManager`

### Pattern matching instanceof → manual cast (9 files)

`if (obj instanceof Device d)` → `if (obj instanceof Device) { Device d = (Device) obj; ... }`

Files: `TaskScheduler`, `StatsEventHandler`, `Volume`, `VolumeListPanel`, `EventPanel`, `FileHistoryPanel`, `LogPanel`, `SnifferDebugDialog`, `Device`

### Unnamed variables `_` → named (20 files)

`catch (IOException _)` → `catch (IOException ignored)`
`(e, _) -> ...` → `(e, ignored) -> ...`

Files: `CopyTask`, `TaskScheduler`, `ExtensionCountCollector`, `SystemTrayIcon`, `MainFrame`, `SpeedProbe`, `VolumeListPanel`, `SystemDirectoryFilter`, `ConfigDialog`, `StorageManagementPanel`, `BlacklistDialog`, `FilterConfigDialog`, `StorageController`, `RecyclerService`, `Toast`, `LogBufferAppender`, `UsbHotplugMonitor`, `PriorityRule`, `SuffixFilter`, `DiskQueryUtil`

### Special case: CheckSum

Uses record pattern matching in switch (`case CheckSum that -> Arrays.equals(...)`) which requires both record → class conversion AND pattern matching in switch → traditional equals rewrite.

## What Stays Unchanged

- `var` (Java 10, works on Java 11)
- `List.of()`, `Set.of()`, `Map.of()` (Java 9)
- `module-info.java` (Java 9)
- JPMS module system and jlink
- All dependency versions

## Branch Strategy

- Branch `java11` from current `master` HEAD
- Long-term maintained fork
- First commit: branch creation + all conversion work
- CLAUDE.md updated to reflect Java 11 build commands
