# Overwrite Strategy Design

## Summary

Replace the current checksum-based index deduplication with a simpler overwrite strategy system. The checksum code is preserved but not activated. Three strategies are offered as an enum with per-strategy decision logic.

## Requirements

| Item | Decision |
|------|----------|
| Trigger | When a file already exists at the target path |
| Scope | Checksum code preserved but not enabled; overwrite strategy becomes default |
| Strategy 1 — RENAME | Append timestamp to filename (e.g. `photo_20260531_143022.jpg`) |
| Strategy 2 — TIME_COMPARE | Compare source `creationTime` vs target `lastModifiedTime` |
| Strategy 3 — ALWAYS_OVERWRITE | Overwrite unconditionally; **default** |
| Design | Enum implementing strategy interface |
| UI | Dropdown in ConfigDialog |

## Architecture

### OverwriteStrategy Enum

**File**: `src/com/superredrock/usbthief/worker/OverwriteStrategy.java`

```java
public enum OverwriteStrategy {
    RENAME {
        @Override
        public boolean shouldOverwrite(Path source, Path target) {
            return false; // Never overwrite — resolveTarget renames instead
        }
        @Override
        public Path resolveTarget(Path target) {
            // Append _yyyyMMdd_HHmmss before extension
            // e.g. photo.jpg → photo_20260531_143022.jpg
        }
    },
    TIME_COMPARE {
        @Override
        public boolean shouldOverwrite(Path source, Path target) {
            // Read source.basic:creationTime and target.basic:lastModifiedTime
            // Return true if source creationTime > target lastModifiedTime
        }
    },
    ALWAYS_OVERWRITE {
        @Override
        public boolean shouldOverwrite(Path source, Path target) {
            return true;
        }
    };

    public abstract boolean shouldOverwrite(Path source, Path target);

    /** Returns the actual target path (may differ for RENAME strategy). */
    public Path resolveTarget(Path target) { return target; }
}
```

### OverwriteConfig

**File**: `src/com/superredrock/usbthief/core/config/configs/OverwriteConfig.java`

```java
public final class OverwriteConfig {
    public static final String CATEGORY = "Overwrite Strategy";
    public static final ConfigEntry<String> OVERWRITE_STRATEGY =
        stringEntry("overwriteStrategy",
            "Strategy when target exists: RENAME, TIME_COMPARE, ALWAYS_OVERWRITE",
            "ALWAYS_OVERWRITE", CATEGORY);
}
```

## CopyTask Integration

In `CopyTask.doCopy()`, after `Files.createDirectories(dest.getParent())` and before opening file channels:

```java
if (Files.exists(dest)) {
    OverwriteStrategy strategy = OverwriteStrategy.valueOf(
        ConfigManager.getInstance().get(OverwriteConfig.OVERWRITE_STRATEGY));

    if (strategy.shouldOverwrite(source, dest)) {
        // overwrite — use REPLACE_EXISTING via delete + create
        Files.deleteIfExists(dest);
    } else {
        // RENAME or TIME_COMPARE decided not to overwrite
        dest = strategy.resolveTarget(dest);
        // If the resolved target also exists (extremely unlikely with timestamp),
        // fall through to normal copy which will overwrite
    }
}
```

The `dest` local variable needs to become effectively non-final so it can be reassigned. The rest of `doCopy` uses `dest` unchanged.

**Special case — TIME_COMPARE skip**: When TIME_COMPARE returns `shouldOverwrite() == false`, the source file is older — we skip the copy entirely. Add a `shouldSkip()` method or return a tri-state result (OVERWRITE / RENAME / SKIP) to distinguish "rename the target" from "skip the copy".

## ConfigDialog Integration

Add `OverwriteConfig.CATEGORY` mapping in the category resolution block of `ConfigDialog`. The `OVERWRITE_STRATEGY` string entry will render as a text field or dropdown showing the three enum value names.

For a proper dropdown, add enum-dropdown support in ConfigDialog's component factory: when a ConfigEntry<String> has known enum values (detected by convention or annotation), render a `JComboBox<String>` instead of a text field. Alternatively, the enum values are documented in the description and the user types one of the valid values.

## Files Changed

| File | Action | Description |
|------|--------|-------------|
| `worker/OverwriteStrategy.java` | NEW | Enum with shouldOverwrite/resolveTarget |
| `core/config/configs/OverwriteConfig.java` | NEW | Config entry for strategy selection |
| `worker/CopyTask.java` | MODIFY | Insert overwrite check before copy |
| `gui/dailog/ConfigDialog.java` | MODIFY | Add OverwriteConfig category mapping |
| `core/config/ConfigSchema.java` | MODIFY | Register OverwriteConfig class |

## Preserved Code

The following are **not deleted**, just not activated in the default flow:
- `index/Index.java`
- `index/CheckSum.java`
- `index/IndexKey.java`
- `index/HashAlgorithm.java`
- `worker/VerifyTask.java`

The `Index.addFile()` call in `CopyTask.doCopy()` line 209 remains gated on `hash != null`, which is always null now since Sniffer never passes a pre-verified hash. No dead code path is introduced — it's already inactive.

## Error Handling

- If `OverwriteStrategy.valueOf()` fails on the config value (corrupt config), fall back to `ALWAYS_OVERWRITE` with a log warning.
- If `Files.readAttributes()` fails in `TIME_COMPARE`, fall back to overwrite (same as ALWAYS_OVERWRITE) and log a warning.
- If `resolveTarget()` generates a path that already exists, proceed with normal copy (it will overwrite — acceptable for a timestamp-based name that would only collide under extreme conditions).

## Testing

- Unit test `OverwriteStrategy.shouldOverwrite()` with mock file attributes
- Unit test `resolveTarget()` naming format
- Manual test in ConfigDialog: switch between strategies, verify behavior
