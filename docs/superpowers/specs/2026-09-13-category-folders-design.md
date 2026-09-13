# Category Folders Design (batch 2-B)

## Summary

Optional "category folder" layer for copied files: instead of always writing
`workPath/(storeName_serial)/relative/path/file.ext`, USB copies can be filed by file
type (images / videos / music / documents / archives / programs / other) or by copy date.
The feature is **off by default** and mirrors the {@code OverwriteStrategy} shape: an enum
whose constants own their decision logic, a config entry, a dialog page and i18n text.

## Requirements

| Item | Decision |
|------|----------|
| Modes | `OFF` (default), `BY_TYPE`, `BY_DATE` (`yyyy-MM-dd`) |
| Path shape | `workPath/(storeName_serial)/<Category>/<fileName>` — file-level, relative sub-tree dropped |
| Classification | extension-only table, case-insensitive, in its own class (`FileCategory`) |
| Fallback | unknown / missing extension, dot-files → `Other` |
| Default | `OFF`, so an existing installation keeps the previous layout byte-for-byte |
| Folders | never categorised (folder tasks keep the plain destination) |
| Scope | files copied by `CopyTask` only; no change to `Sniffer` or the main window |

## Architecture

### CategoryMode

`src/com/superredrock/usbthief/worker/CategoryMode.java`

```java
public enum CategoryMode {
    OFF     { String categoryDirectory(Path f) { return null; } },
    BY_TYPE { String categoryDirectory(Path f) { return FileCategory.classify(f).directoryName(); } },
    BY_DATE { String categoryDirectory(Path f) { return DATE_FORMAT.format(LocalDate.now()); } };

    public abstract String categoryDirectory(Path sourceFile);
    public static CategoryMode safeValueOf(String name);   // invalid -> OFF + warning
}
```

The mode only answers *which single folder name to insert*; it does not know about
`workPath`, volumes or the relative sub-tree. An extra mode therefore needs no change
in the copy path.

### FileCategory

`src/com/superredrock/usbthief/worker/FileCategory.java`

| Constant | Folder | Extensions |
|----------|--------|------------|
| `IMAGE` | `Images` | jpg jpeg png gif bmp webp heic heif tif tiff svg ico |
| `VIDEO` | `Videos` | mp4 mov avi mkv wmv flv webm m4v mpg mpeg 3gp ts |
| `AUDIO` | `Music` | mp3 flac wav m4a aac ogg wma opus aiff mid midi |
| `DOCUMENT` | `Documents` | doc docx pdf xls xlsx ppt pptx txt md rtf csv odt ods odp epub |
| `ARCHIVE` | `Archives` | zip rar 7z tar gz tgz bz2 xz zst iso cab |
| `PROGRAM` | `Programs` | exe msi msix apk dmg app bat cmd com jar deb rpm |
| `OTHER` | `Other` | *(fallback)* |

The table is a `HashMap` built once at class initialisation; duplicates would throw at
startup. Folder names are deliberately stable and locale-independent (same reasoning as
the literal `UsbThiefData` default work folder): translating on-disk folder names would
split one library into `Images/` and `图片/` trees after a language switch.

### Destination path

`DeviceUtils.getPath(workPath, target, volume)` keeps its signature and behaviour. A new
overload adds one folder below the volume folder:

```java
public static Path getPath(Path workPath, Path target, Volume volume, String categoryDir)
// categoryDir == null/"" -> workPath/(storeName_serial)/relative      (unchanged)
// otherwise              -> workPath/(storeName_serial)/<categoryDir>/<fileName>
```

`CopyTask` resolves the folder from the mode and passes it in:

```java
String categoryDir = attributes.isDirectory()
        ? null : settings.categoryMode.categoryDirectory(processingPath);
destinationPath = getPath(processingPath, categoryDir);
```

The mode is part of the per-file `CopySettings` snapshot (PF-02), so the hot loop still
reads `Preferences` once per file.

## Trade-offs

* **Relative sub-tree is dropped.** `F:\DCIM\2024\photo.jpg` becomes
  `(KINGSTON_ABC)/Images/photo.jpg`, matching USBCopyer and keeping the destination
  shallow. Two same-named files from different folders therefore collide in the category
  folder and are resolved by the configured `OverwriteStrategy` (default: overwrite).
  Keeping the sub-tree (`(store)/Images/DCIM/2024/photo.jpg`) remains a one-line change in
  `DeviceUtils.getPath` if collisions become a problem.
* **Byte-for-byte compatibility.** With the default `OFF` the destination is identical to
  the pre-feature output, and `DeviceUtils.getPath(workPath, target, volume)` is literally
  the same call with a `null` category.
* **Extension-only classification.** Content sniffing and a user-editable table are out of
  scope; unknown extensions are safe (`Other`), never skipped.

## Testing

* `FileCategoryTest` — every documented extension, case-insensitivity, unknown / missing
  extension, dot-files, `null`, per-category resolution and distinct folder names.
* `CategoryModeTest` — `OFF` produces no folder, `BY_TYPE` uses the table, `BY_DATE` uses
  `yyyy-MM-dd`, `safeValueOf` fallback, config default is `OFF`.
* `DeviceUtilsCategoryPathTest` — the new overload inserts the folder below the volume
  folder, drops the relative sub-tree, and the 3-argument overload is unchanged.
* `CopyTaskTest` — `BY_TYPE` / `BY_DATE` end-to-end copies, default `OFF` layout, folder
  tasks stay uncategorised.
* `ConfigSchemaTest`, `ConfigCategoryPanelTest` (existing) — the new entry is registered,
  reachable from the preferences tree and round-trips.
* `CategoryConfigI18nTest` — label/hint/category/options exist in all five bundles.
