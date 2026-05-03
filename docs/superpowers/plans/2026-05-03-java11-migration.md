# Java 25 → 11 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the UsbThief project from Java 25 to Java 11 to support Windows 7, maintaining JPMS modules and bundled JRE via jlink.

**Architecture:** Mechanical conversion of all post-Java-11 language features (records, switch expressions, pattern matching, unnamed variables) and API calls (Math.clamp, List.removeFirst, Stream.toList) to Java 11 compatible equivalents. No behavior changes — pure syntax downgrades.

**Tech Stack:** Java 11, Maven, JPMS/jlink, JNA, FlatLaf, Caffeine 3.x, Log4j2

---

### Task 1: Create Branch + Update Build System

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Create the java11 branch from master**

```bash
git checkout -b java11 master
```

- [ ] **Step 2: Update pom.xml compiler target**

In `pom.xml`, change lines 22-23:

```xml
<maven.compiler.source>11</maven.compiler.source>
<maven.compiler.target>11</maven.compiler.target>
```

- [ ] **Step 3: Remove --enable-preview from surefire plugin**

In `pom.xml`, line 189, change `<argLine>`:

```xml
<argLine>-Dnet.bytebuddy.experimental=true</argLine>
```

- [ ] **Step 4: Change jlink --compress from zip-9 to 2**

In `pom.xml`, line 224, change:

```xml
<argument>2</argument>
```

(replace `<argument>zip-9</argument>`)

- [ ] **Step 5: Remove --enable-preview and --enable-native-access from .bat launcher patch**

In `pom.xml`, lines 242-243, change the PowerShell replacement string:

```xml
<argument>(Get-Content runtime.bat) -replace 'set JLINK_VM_OPTIONS=', 'set JLINK_VM_OPTIONS=-Dsun.java2d.d3d=false' | Set-Content runtime.bat</argument>
```

- [ ] **Step 6: Remove --enable-preview and --enable-native-access from .sh launcher patch**

In `pom.xml`, lines 258-259, change:

```xml
<argument>(Get-Content runtime) -replace 'JLINK_VM_OPTIONS=', 'JLINK_VM_OPTIONS=-Dsun.java2d.d3d=false' | Set-Content runtime</argument>
```

- [ ] **Step 7: Update Launch4j minVersion and remove --enable-preview**

In `pom.xml`, the `<jre>` section (lines 287-294):

```xml
<jre>
    <path>${jlink.image.name}</path>
    <minVersion>11</minVersion>
    <opts>
        <opt>-Dsun.java2d.d3d=false</opt>
        <opt>-Dlaunch4j.exefile="%EXEFILE%"</opt>
    </opts>
</jre>
```

(Remove the `--enable-preview` and `--enable-native-access` opts, change minVersion from 25 to 11.)

- [ ] **Step 8: Commit build system changes**

```bash
git add pom.xml
git commit -m "build: target Java 11, remove --enable-preview and --enable-native-access"
```

---

### Task 2: Convert Post-Java-11 API Calls

**Files:**
- `src/com/superredrock/usbthief/worker/PriorityRule.java:52`
- `src/com/superredrock/usbthief/worker/PriorityTask.java:16`
- `src/com/superredrock/usbthief/gui/EventPanel.java:262`
- `src/com/superredrock/usbthief/gui/FileHistoryPanel.java:193`
- `src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java:96`
- `src/com/superredrock/usbthief/gui/LogPanel.java:305`
- `src/com/superredrock/usbthief/gui/SpeedChartPanel.java:45`
- `src/com/superredrock/usbthief/worker/RecyclerService.java:163`
- `src/com/superredrock/usbthief/worker/FileSelector.java:55,90`
- `src/com/superredrock/usbthief/gui/dailog/DeviceInfoDialog.java:368`
- `src/com/superredrock/usbthief/gui/dailog/SnifferDebugDialog.java:601`
- `src/com/superredrock/usbthief/gui/StatisticsPanel.java:216`

- [ ] **Step 1: Replace Math.clamp() with manual clamp**

`Math.clamp(val, min, max)` is Java 21+. Replace all 2 usages:

Pattern:
```java
// BEFORE (Java 21+):
Math.clamp(value, min, max)

// AFTER (Java 11):
Math.max(min, Math.min(max, value))
```

Files:
- `PriorityRule.java:52`: `Math.clamp(basePriority + sizeAdjustment, 0, 100)` → `Math.max(0, Math.min(100, basePriority + sizeAdjustment))`
- `PriorityTask.java:16`: `Math.clamp(priority, 0, 100)` → `Math.max(0, Math.min(100, priority))`

- [ ] **Step 2: Replace List.removeFirst() with remove(0)**

`List.removeFirst()` is Java 21+. Replace all 5 usages:

Pattern:
```java
// BEFORE (Java 21+):
list.removeFirst()

// AFTER (Java 11):
list.remove(0)
```

Files:
- `EventPanel.java:262`: `eventEntries.removeFirst()` → `eventEntries.remove(0)`
- `FileHistoryPanel.java:193`: `records.removeFirst()` → `records.remove(0)`
- `SnifferDebugDialog.java:96`: `eventBuffer.removeFirst()` → `eventBuffer.remove(0)`
- `LogPanel.java:305`: `logEntries.removeFirst()` → `logEntries.remove(0)`
- `SpeedChartPanel.java:45`: `speedHistory.removeFirst()` → `speedHistory.remove(0)`

- [ ] **Step 3: Replace Stream.toList() with .collect(Collectors.toList())**

`Stream.toList()` is Java 16+. Replace all 7 usages. Add `import java.util.stream.Collectors` if not present.

Pattern:
```java
// BEFORE (Java 16+):
stream.toList()

// AFTER (Java 11):
stream.collect(Collectors.toList())
```

Files:
- `RecyclerService.java:163`
- `FileSelector.java:55`
- `FileSelector.java:90`
- `DeviceInfoDialog.java:368`
- `SnifferDebugDialog.java:601`
- `StatisticsPanel.java:216`

- [ ] **Step 4: Commit API call conversions**

```bash
git add -A
git commit -m "refactor: replace Math.clamp, List.removeFirst, Stream.toList with Java 11 equivalents"
```

---

### Task 3: Convert Unnamed Variables `_` to Named Variables

**Pattern:**
```java
// BEFORE (Java 22+): lambda unused parameter
_ -> doSomething()
// AFTER:
ignored -> doSomething()

// BEFORE (Java 22+): catch unused
catch (IOException _) { ... }
// AFTER:
catch (IOException ignored) { ... }

// BEFORE (Java 22+): lambda unused in multi-param
(key, _) -> ...
// AFTER:
(key, ignored) -> ...
```

**Files with `_ ->` in lambdas (replace `_` with `ignored`):**

- `TaskScheduler.java:168`: `_ -> new CopyOnWriteArrayList<>()`
- `DeviceManager.java:185`: `_ -> {`
- `ExtensionCountCollector.java:24`: `_ -> new AtomicLong(0)`
- `DeviceHistoryCollector.java:27`: `_ ->`
- `VolumeStatsCollector.java:28,35,118`: `_ -> new VolumeStats()` / `_ -> new AtomicLong(0)`
- `FileHistoryPanel.java:75`: `_ -> applyFilter()`
- `FileHistoryPanel.java:78`: `_ -> {`
- `DeviceInfoDialog.java:74`: `_ -> {`
- `DeviceInfoDialog.java:129-133`: `_ -> SwingUtilities.invokeLater(...)`
- `DeviceInfoDialog.java:356`: `_ -> {`
- `RateLimitConfigDialog.java:158,229,232,235`: `_ -> resetStats()` etc.
- `RateLimitConfigDialog.java:248`: `_ -> updateStats()`
- `LogWindow.java:109`: `_ -> setVisible(false)`
- `SnifferDebugDialog.java:105`: `_ -> refresh()`
- `SnifferDebugDialog.java:133`: `_ -> switchTab(tabIndex)`
- `SnifferDebugDialog.java:494`: `_ -> {`
- `MainFrame.java:170,229,235,239,245,249,255,259,265,271,279,283,287,293,297`: all `_ ->` lambdas
- `StatisticsPanel.java:164`: `_ -> updateDisplay()`
- `SystemTrayIcon.java:116`: `_ -> {`
- `SpeedChartPanel.java:37`: `_ -> sample()`
- `VolumeListPanel.java:94,97`: `_ -> setSelectAll(true/false)`
- `VolumeListPanel.java:528`: `catch (IOException _)` → `catch (IOException ignored)`

**Files with `catch (ExceptionType _)`:**

- `UsbTesting.java:59,80,156`
- `Sniffer.java:107`
- `Volume.java:97`
- `SystemTrayIcon.java:152`

- [ ] **Step 1: Replace all `_ ->` lambda patterns with `ignored ->`**

Use global search-and-replace per file. For each file listed above, replace `_ ->` with `ignored ->`. For multi-line lambdas like `_ -> {`, the replacement is `ignored -> {`.

- [ ] **Step 2: Replace all `catch (ExceptionType _)` with `catch (ExceptionType ignored)`**

In each file listed above, replace the catch pattern. Example: `catch (IOException _)` → `catch (IOException ignored)`.

- [ ] **Step 3: Check for remaining `_` usages**

```bash
grep -rn "\b_\s*->\|catch\s*(\s*\w\+\s+_\s*)" src/ --include="*.java"
```

Expected: no matches.

- [ ] **Step 4: Commit unnamed variable conversions**

```bash
git add -A
git commit -m "refactor: replace unnamed variables (_) with named equivalents for Java 11"
```

---

### Task 4: Convert Pattern Matching instanceof to Manual Casts

**Pattern:**
```java
// BEFORE (Java 16+):
if (obj instanceof Type t) { /* use t */ }
if (!(obj instanceof Type t)) return false;

// AFTER (Java 11):
if (obj instanceof Type) { Type t = (Type) obj; /* use t */ }
if (!(obj instanceof Type)) return false; Type t = (Type) obj;
```

**Special case — record pattern matching in instanceof (Java 21+):**
```java
// BEFORE (Java 21+):
if (!(o instanceof EventListenerWrapper<?>(Class<?> aClass, EventListener<?> listener1))) return false;

// AFTER (Java 11):
if (!(o instanceof EventListenerWrapper<?>)) return false;
EventListenerWrapper<?> other = (EventListenerWrapper<?>) o;
// Then access fields via other.eventClass(), other.listener()
```

- [ ] **Step 1: Convert EventBus.java record pattern matching**

In `EventBus.java:411-416`, replace the equals method:

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EventListenerWrapper)) return false;
    EventListenerWrapper<?> other = (EventListenerWrapper<?>) o;
    return eventClass.equals(other.eventClass()) && listener.equals(other.listener());
}
```

- [ ] **Step 2: Convert all instanceof pattern matching**

Files and specific conversions:

- `TaskScheduler.java:165`: `if (delegate instanceof CopyTask ct) serial = ct.getDeviceSerial();` → `if (delegate instanceof CopyTask) { serial = ((CopyTask) delegate).getDeviceSerial(); }`
- `TaskScheduler.java:166`: `else if (delegate instanceof VerifyTask vt) serial = vt.getDeviceSerial();` → `else if (delegate instanceof VerifyTask) { serial = ((VerifyTask) delegate).getDeviceSerial(); }`
- `TaskScheduler.java:189-190`: Same pattern as above.
- `FileHistoryPanel.java:260`: `if (value instanceof Long fileSize) {` → `if (value instanceof Long) { long fileSize = (Long) value;`
- `EventPanel.java:319`: `if (value instanceof String eventType) {` → `if (value instanceof String) { String eventType = (String) value;`
- `LogPanel.java:357`: `if (value instanceof LogLevel level) {` → `if (value instanceof LogLevel) { LogLevel level = (LogLevel) value;`
- `StatsEventHandler.java:135-136`: `if (v instanceof Boolean b) return b.toString();` → `if (v instanceof Boolean) return v.toString();` (can inline since `v` is already in scope)
- `VolumeListPanel.java:556`: `if (c instanceof VolumeCard vc) cards.add(vc);` → `if (c instanceof VolumeCard) { cards.add((VolumeCard) c); }`
- `SnifferDebugDialog.java:99`: `if (event instanceof VolumeRemovedEvent vre) {` → `if (event instanceof VolumeRemovedEvent) { VolumeRemovedEvent vre = (VolumeRemovedEvent) event;`
- `Volume.java:183`: `if (!(o instanceof Volume volume)) return false;` → `if (!(o instanceof Volume)) return false; Volume volume = (Volume) o;`
- `Device.java:62`: `if (!(o instanceof Device device)) return false;` → `if (!(o instanceof Device)) return false; Device device = (Device) o;`

- [ ] **Step 3: Verify no remaining pattern matching instanceof**

```bash
grep -rn "instanceof\s\+\w\+\s\+\w\+" src/ --include="*.java"
```

Expected: no matches (all pattern matching instanceof should be gone).

- [ ] **Step 4: Commit pattern matching conversions**

```bash
git add -A
git commit -m "refactor: replace pattern matching instanceof with manual casts for Java 11"
```

---

### Task 5: Convert Switch Expressions to Traditional Switch

**Pattern:**
```java
// BEFORE (Java 14+):
return switch (value) {
    case A -> resultA;
    case B -> resultB;
    default -> resultDefault;
};

// AFTER (Java 11):
switch (value) {
    case A: return resultA;
    case B: return resultB;
    default: return resultDefault;
}
```

For void/non-return contexts:
```java
// BEFORE:
switch (value) {
    case A -> doA();
    case B -> doB();
}

// AFTER:
switch (value) {
    case A: doA(); break;
    case B: doB(); break;
}
```

- [ ] **Step 1: Convert CheckSum.java (record + switch with pattern matching)**

This file combines record AND switch expression with pattern matching. Convert switch in equals:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CheckSum)) return false;
    CheckSum that = (CheckSum) obj;
    return Arrays.equals(context, that.context);
}
```

- [ ] **Step 2: Convert simple return-switch expressions (enum/int switch)**

These files use `return switch (...) { case X -> ...; }` with simple constants:

- `EventPanel.java:287-292,303-308` (getColumnName, getValueAt — int switch)
- `LogPanel.java:341-346` (getValueAt — int switch)
- `LogPanel.java:367-372` (getLevelColor — enum switch)
- `EventPanel.java:319` — already handled in Task 4 (pattern matching)
- `BasicFileFilter.java` — check for switch expressions
- `I18nManager.java` — check for switch expressions
- `ThemeManager.java` — check for switch expressions
- `PriorityRule.java:86-91` — type pattern switch: convert to if/else-if chain:

```java
public int calculatePriority(Callable<?> task) {
    if (task instanceof CopyTask) {
        return calculatePriority(((CopyTask) task).getProcessingPath());
    } else if (task instanceof VerifyTask) {
        return calculatePriority(((VerifyTask) task).getProcessingPath());
    }
    return DEFAULT_PRIORITY;
}
```

- `FileSelector.java` — check for switch expressions
- `VolumeListPanel.java` — check for switch expressions
- `DeviceInfoDialog.java` — check for switch expressions
- `WelcomeDialog.java` — check for switch expressions
- `LogWindow.java` — check for switch expressions
- `SnifferDebugDialog.java` — check for switch expressions
- `SnifferLifecycleManager.java` — check for switch expressions

For each file: read the file, identify switch expressions, and convert to traditional switch/if-else.

- [ ] **Step 3: Verify no remaining switch expressions**

```bash
grep -rn "case\s.\+->" src/ --include="*.java"
```

Expected: no matches (all arrow-case syntax should be gone).

- [ ] **Step 4: Commit switch expression conversions**

```bash
git add -A
git commit -m "refactor: convert switch expressions to traditional switch statements for Java 11"
```

---

### Task 6: Convert Records to Final Classes

**Pattern:**
```java
// BEFORE (Java 16+):
public record Point(int x, int y) {
    // optional compact constructor, additional methods
}

// AFTER (Java 11):
public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Point[x=" + x + ", y=" + y + "]";
    }
}
```

**Key rules for conversion:**
- Add `import java.util.Objects` if using `Objects.hash()` / `Objects.equals()`
- Accessor methods keep the same name as record components (e.g., `x()` not `getX()`)
- Compact constructors become regular constructors with field assignments
- Additional constructors chain to the canonical constructor
- For records with reference-type fields: use `Objects.equals()` in `equals()`
- For records with array fields: use `Arrays.equals()` / `Arrays.hashCode()` in `equals()`/`hashCode()`

- [ ] **Step 1: Convert simple records (no custom constructors)**

These records only have the auto-generated constructor — no compact constructor or additional methods.

- `SnifferDebugSnapshot.java` — 8 fields, all simple types/Strings
- `gui/LanguageInfo.java` — has compact constructor (validation), additional constructors, and methods. Keep all methods, expand compact constructor into the canonical constructor.
- `core/event/EventBus.java:404` — nested `EventListenerWrapper<T>` record. Also has custom `equals()` with record pattern matching (already handled in Task 4). Convert to static nested final class.
- `gui/EventPanel.java:345` — nested `EventEntry` record (3 String fields)
- `gui/LogPanel.java:376` — nested `LogEntry` record (String, LogLevel, String)
- `gui/LogBufferAppender.java` — check for nested record
- `gui/FileHistoryPanel.java` — check for nested record

For each: read the file, identify the record, and replace with a `final` class using the pattern above. Ensure accessor method names match (so callers don't break).

- [ ] **Step 2: Convert records with custom equals/hashCode/toString**

Some records already override `equals()`/`hashCode()` — carry those over as-is (they were already converted in Task 4/5 for pattern matching and switch expressions).

- `index/CheckSum.java` — byte[] field, already has custom equals/hashCode. Just change `record` to `final class` with field + constructor + accessors.
- `index/FileHistoryRecord.java` — deprecated record with factory methods. Read the file to understand its structure.
- `statistics/collector/MetricSnapshot.java` — has compact constructor (wraps details in unmodifiable map), multiple additional constructors. Convert carefully.
- `worker/StorageController.java` — check for nested record classes
- `core/DeviceUtils.java:230` — nested `DeviceIdentity` record with a `getIdentifier()` method

- [ ] **Step 3: Convert records in remaining files**

- `worker/CopyTask.java` — check for nested record classes (e.g., `CopyResult`)
- `worker/FileSelector.java` — check for nested record
- `statistics/SpeedProbe.java` — check for record
- `core/UsbHotplugMonitor.java` — check for record

Read each file, find the record, and convert.

- [ ] **Step 4: Verify no remaining records**

```bash
grep -rn "^\s*public\s\+record\s\|^\s*private\s\+record\s\|^\s*record\s" src/ --include="*.java"
```

Expected: no matches.

- [ ] **Step 5: Commit record conversions**

```bash
git add -A
git commit -m "refactor: convert records to final classes for Java 11 compatibility"
```

---

### Task 7: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update Java version references**

In `CLAUDE.md`, update all references from Java 25 to Java 11:
- **Requirements:** `Java 25 JDK` → `Java 11 JDK`
- Remove `(uses preview features)` note
- `java -p target/classes -m UsbThief/... --enable-preview` → remove `--enable-preview`

- [ ] **Step 2: Update architecture notes if needed**

Remove any mentions of preview features or Java 25-specific behavior.

- [ ] **Step 3: Commit CLAUDE.md update**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for Java 11 target"
```

---

### Task 8: Full Verification

- [ ] **Step 1: Verify compilation with Java 11**

Ensure JDK 11 is available and run:

```bash
mvn clean compile
```

Expected: BUILD SUCCESS with no errors.

If JDK 25 is still the default, set JAVA_HOME to JDK 11 for the build:

```bash
JAVA_HOME=/path/to/jdk11 mvn clean compile
```

- [ ] **Step 2: Run tests**

```bash
mvn test
```

Expected: All tests pass.

- [ ] **Step 3: Verify no post-Java-11 syntax remains**

```bash
# Check for records
grep -rn "^\s*\(public\|private\)\?\s*record\s" src/ --include="*.java"
# Check for switch expressions (arrow case)
grep -rn "case\s.\+->" src/ --include="*.java"
# Check for pattern matching instanceof
grep -rn "instanceof\s\+\w\+\s\+\w\+" src/ --include="*.java"
# Check for unnamed variables
grep -rn "\b_\s*->\|catch\s*(\s*\w\+\s+_\s*)" src/ --include="*.java"
# Check for Math.clamp
grep -rn "Math\.clamp" src/ --include="*.java"
# Check for removeFirst
grep -rn "\.removeFirst()" src/ --include="*.java"
# Check for .toList()
grep -rn "\.toList()" src/ --include="*.java"
```

All should return no matches.

- [ ] **Step 4: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: final Java 11 compatibility adjustments"
```
