# Extensible Statistics System Design

**Date:** 2026-05-02
**Status:** Approved
**Scope:** Replace monolithic `Statistics` class with a pluggable metric framework + local HTTP API

## Problem

The current `statistics` package has three extension blockers:

1. **Hardcoded metrics** — every `AtomicLong` field requires changes in `onCopyCompleted`, `load`, `save`, and `resetAll` (four touch points per metric)
2. **Three overlapping speed classes** — `SpeedProbe`, `SpeedProbeGroup`, `SpeedStatistics` all implement sliding-window speed tracking independently
3. **Persistence coupled to business logic** — `Preferences` read/write accounts for ~50% of `Statistics` code

Adding a new metric (e.g., average file size, hourly distribution) requires modifying the singleton class itself.

## Design

### Core Abstractions

#### MetricCollector interface

Each metric implements this interface:

```java
public interface MetricCollector {
    String getId();                    // unique identifier, e.g. "files.copied"
    void onEvent(Object event);        // receives typed EventBus events, collector checks instanceof internally
    MetricSnapshot snapshot();         // immutable current-value snapshot
    boolean isPersistent();            // whether to persist across restarts
    void load(MetricStore store);      // restore from storage
    void save(MetricStore store);      // persist to storage
    void reset();                      // reset to initial state
}
```

Each collector registers its own EventBus listeners during construction and checks `event instanceof` in `onEvent()`.

#### MetricSnapshot (immutable record)

```java
public record MetricSnapshot(
    String metricId,
    long longValue,
    double doubleValue,
    Map<String, Object> details
) {}
```

#### MetricRegistry (central registry)

```java
public final class MetricRegistry {
    private final ConcurrentHashMap<String, MetricCollector> collectors;

    public void register(MetricCollector collector);
    public MetricSnapshot getSnapshot(String metricId);
    public Map<String, MetricSnapshot> getAllSnapshots();
    public void loadAll(MetricStore store);
    public void saveAll(MetricStore store);
    public void addListener(Consumer<MetricSnapshot> listener);  // for SSE push
}
```

#### MetricStore interface (storage abstraction)

```java
public interface MetricStore {
    void put(String key, long value);
    void put(String key, double value);
    void put(String key, String value);
    OptionalLong getLong(String key);
    OptionalDouble getDouble(String key);
    Optional<String> getString(String key);
    void remove(String key);
    void flush();
}
```

### Collector Implementations

10 collectors, each self-contained:

| Collector | Persistent | Event Sources | Notes |
|-----------|-----------|---------------|-------|
| `TotalFilesCopiedCollector` | yes | `CopyCompletedEvent` | AtomicLong counter |
| `TotalBytesCopiedCollector` | yes | `CopyCompletedEvent` | AtomicLong counter |
| `TotalErrorsCollector` | yes | `CopyCompletedEvent` | Count when result != SUCCESS |
| `TotalFoldersCopiedCollector` | yes | `CopyCompletedEvent` | Count folder copies |
| `TotalDevicesCopiedCollector` | yes | `CopyCompletedEvent` | Track unique device serials via ConcurrentHashMap KeySet |
| `ExtensionCountCollector` | yes | `CopyCompletedEvent` | ConcurrentHashMap<String, AtomicLong> |
| `SessionProgressCollector` | no | `FileDiscoveredEvent` + `CopyCompletedEvent` | Tracks discovered vs copied bytes, session file/folder counts |
| `SpeedCollector` | no | `CopyCompletedEvent` | Wraps SpeedProbeGroup internally, exposes speed via details |
| `VolumeStatsCollector` | yes | `CopyCompletedEvent` | VolumeStats domain object, serialized to MetricStore |
| `DeviceHistoryCollector` | yes | `DeviceArrivalEvent` + `DeviceRemovalEvent` | DeviceHistoryEntry domain object, serialized to MetricStore |

### Speed Module Unification

`SpeedStatistics` is deleted (redundant alternative implementation). `SpeedProbe` and `SpeedProbeGroup` are kept as-is — `SpeedProbe` provides per-copy thread-local recording, `SpeedProbeGroup` aggregates probes.

`SpeedCollector` wraps a `SpeedProbeGroup` internally:

- Exposes `currentSpeed`, `averageSpeed`, `totalBytes`, `probeCount` via `MetricSnapshot.details`
- Non-persistent (`isPersistent()` returns false)
- Provides `createProbe()` method that creates a `SpeedProbe` and adds it to the internal `SpeedProbeGroup` — `CopyTask` calls this instead of creating probes directly
- `SpeedChartPanel` queries `registry.getSnapshot("speed.global").details()` instead of directly accessing `CopyTask.getSpeedProbeGroup()`

### Domain Objects

`VolumeStats` and `DeviceHistoryEntry` move from inner classes of `Statistics` to top-level classes in the `collector` package. Their collectors serialize/deserialize them through `MetricStore` using key prefixes:

- VolumeStats: `"volumeStats.{serial}.filesCopied"`, `"volumeStats.{serial}.bytesCopied"`, etc.
- DeviceHistoryEntry: `"deviceHistory.{id}.serial"`, `"deviceHistory.{id}.timeline"`, etc.

### MetricStore Implementations

**PreferencesMetricStore** (default, backward-compatible):
- Wraps `java.util.prefs.Preferences`
- Data format matches existing keys for seamless migration

**InMemoryMetricStore** (no-op):
- Used by non-persistent collectors (speed, session progress)
- All `put`/`get` methods are no-ops

Future: `JsonFileMetricStore`, `SqliteMetricStore` — implement the same interface.

### Statistics Facade

`Statistics` becomes a thin facade that delegates to `MetricRegistry`:

```java
public final class Statistics {
    private final MetricRegistry registry;
    private final PreferencesMetricStore store;

    private void initCollectors() {
        registry.register(new TotalFilesCopiedCollector());
        registry.register(new TotalBytesCopiedCollector());
        registry.register(new TotalErrorsCollector());
        registry.register(new TotalFoldersCopiedCollector());
        registry.register(new TotalDevicesCopiedCollector());
        registry.register(new ExtensionCountCollector());
        registry.register(new SessionProgressCollector());
        registry.register(new SpeedCollector());
        registry.register(new VolumeStatsCollector());
        registry.register(new DeviceHistoryCollector());
    }

    // Existing public API unchanged — GUI components need zero changes
    public long getTotalFilesCopied() {
        return registry.getSnapshot("files.copied").longValue();
    }

    public double getCurrentSpeed() {
        return registry.getSnapshot("speed.global").doubleValue();
    }

    // SpeedChartPanel queries this instead of CopyTask.getSpeedProbeGroup()
    public MetricSnapshot getSpeedSnapshot() {
        return registry.getSnapshot("speed.global");
    }
}
```

Lifecycle: constructor creates registry + store → registers all collectors → `registry.loadAll(store)` on startup → `registry.saveAll(store)` on shutdown.

No GUI changes required except `SpeedChartPanel` (updated to query facade instead of `CopyTask`).

### HTTP API (Phase 2)

JDK `com.sun.net.httpserver` — no external dependencies. JSON via manual string building.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stats` | JSON snapshot of all metrics |
| GET | `/api/stats/{metricId}` | Single metric detail |
| GET | `/api/stats/stream` | SSE real-time push |
| POST | `/api/stats/reset` | Reset all or specified metrics |

**SSE push flow:**
1. `MetricCollector.onEvent()` updates value, notifies `MetricRegistry`
2. `MetricRegistry` fires to all registered `Consumer<MetricSnapshot>` listeners
3. `StatsEventHandler` writes SSE frames to connected clients

**JSON output example:**
```json
{
  "files.copied": {"longValue": 1234, "doubleValue": 0.0},
  "speed.global": {
    "longValue": 0,
    "doubleValue": 45.6,
    "details": {
      "currentSpeed": 45.6,
      "averageSpeed": 38.2,
      "totalBytes": 1073741824
    }
  }
}
```

### Package Structure

```
statistics/
├── Statistics.java                  (facade)
├── MetricRegistry.java
├── SpeedProbe.java                  (kept as-is)
├── SpeedProbeGroup.java             (kept as-is)
├── collector/
│   ├── MetricCollector.java
│   ├── MetricSnapshot.java
│   ├── TotalFilesCopiedCollector.java
│   ├── TotalBytesCopiedCollector.java
│   ├── TotalErrorsCollector.java
│   ├── TotalFoldersCopiedCollector.java
│   ├── TotalDevicesCopiedCollector.java
│   ├── ExtensionCountCollector.java
│   ├── SessionProgressCollector.java
│   ├── SpeedCollector.java
│   ├── VolumeStatsCollector.java
│   ├── VolumeStats.java             (domain object, moved from inner class)
│   ├── DeviceHistoryCollector.java
│   └── DeviceHistoryEntry.java      (domain object, moved from inner class)
├── store/
│   ├── MetricStore.java
│   ├── PreferencesMetricStore.java
│   └── InMemoryMetricStore.java
└── api/                             (Phase 2)
    ├── StatsHttpServer.java
    └── StatsEventHandler.java
```

`SpeedStatistics.java` is deleted.

### Configuration

New `ConfigSchema` entries:

| Key | Default | Description |
|-----|---------|-------------|
| `stats.api.enabled` | `true` | Enable/disable HTTP API |
| `stats.api.port` | `8421` | HTTP API port number |

### Adding a New Metric

1. Create a class implementing `MetricCollector`
2. Register it: `registry.register(new MyCollector())`
3. Done — no changes to `MetricRegistry`, `Statistics`, or any other file

Zero modification to the framework per new metric.

## Migration Phases

1. **Phase 1:** Core framework (`MetricCollector`, `MetricSnapshot`, `MetricRegistry`, `MetricStore` implementations) + all 10 collectors + facade refactoring + `SpeedStatistics` deletion
2. **Phase 2:** HTTP API + SSE endpoint

Phase 2 depends only on `MetricRegistry.getAllSnapshots()` and `addListener()` — clean seam.

## Constraints

- **No external dependencies** — uses JDK `com.sun.net.httpserver` for HTTP, no third-party libraries
- **Backward-compatible persistence** — `PreferencesMetricStore` reads existing registry keys
- **Thread safety** — all collectors must handle concurrent `onEvent` calls (same pattern as current code)
- **GUI compatibility** — `Statistics` facade preserves existing public method signatures
- **Speed probe preservation** — `SpeedProbe` and `SpeedProbeGroup` remain unchanged; only `SpeedStatistics` is removed
