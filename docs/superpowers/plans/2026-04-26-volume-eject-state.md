# Volume Eject State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect Windows USB eject requests and stop all tasks on the affected volume by setting it to EJECTING state.

**Architecture:** Add `EJECTING` to `VolumeState` enum. `UsbHotplugMonitor` handles `DBT_DEVICEQUERYREMOVE` synchronously in `windowProc`, finds the Volume via DeviceManager, sets it to EJECTING, and returns `BROADCAST_QUERY_DENY`. Existing `isAccessible()` checks in CopyTask/VerifyTask naturally reject queued tasks. Active NIO copy loops get an additional accessibility check per buffer read.

**Tech Stack:** Java 25, JNA (Win32 API), NIO FileChannel

---

### Task 1: Add EJECTING state to Volume

**Files:**
- Modify: `src/com/superredrock/usbthief/core/Volume.java`

- [ ] **Step 1: Add `EJECTING` to VolumeState enum**

In `Volume.java` line 17-22, add `EJECTING` to the enum:

```java
public enum VolumeState {
    OFFLINE,       // Volume not present
    UNAVAILABLE,   // Volume exists but inaccessible (AccessDeniedException / IOException)
    IDLE,          // Ready, no active operations
    DISABLED,      // Manually disabled by user
    EJECTING       // Windows requested eject, stopping tasks
}
```

- [ ] **Step 2: Add `setEjecting()` method**

Add after the `disable()` method (after line 174):

```java
/**
 * Marks the volume as ejecting. Terminal state — never returns to IDLE.
 */
public void setEjecting() {
    setState(VolumeState.EJECTING);
    logger.info("Volume set to EJECTING: {}", driveLetter);
}
```

- [ ] **Step 3: Guard `updateState()` against EJECTING**

In `updateState()` at line 101-104, add EJECTING guard alongside DISABLED:

```java
public void updateState() {
    if (state == VolumeState.DISABLED || state == VolumeState.EJECTING) {
        return;
    }
    // ... rest unchanged
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/com/superredrock/usbthief/core/Volume.java
git commit -m "feat: add EJECTING state to Volume"
```

---

### Task 2: Handle DBT_DEVICEQUERYREMOVE in UsbHotplugMonitor + DeviceManager

This task modifies the listener interface, the monitor, and DeviceManager together since they must compile as a unit.

**Files:**
- Modify: `src/com/superredrock/usbthief/core/UsbHotplugMonitor.java`
- Modify: `src/com/superredrock/usbthief/core/DeviceManager.java`

- [ ] **Step 1: Add DBT_DEVICEQUERYREMOVE constant to UsbHotplugMonitor**

At line 36, add:

```java
private static final int DBT_DEVICEQUERYREMOVE = 0x8001;
private static final int BROADCAST_QUERY_DENY = 0x424D5144;
```

- [ ] **Step 2: Add `onVolumeQueryRemove` to VolumeListener interface**

In the `VolumeListener` interface (line 56-59), add:

```java
public interface VolumeListener {
    void onVolumeArrival(String driveLetter);
    void onVolumeRemoval(String driveLetter);

    /**
     * Called synchronously on the Windows message thread when an eject is requested.
     * @return true to allow eject, false to deny.
     */
    default boolean onVolumeQueryRemove(String driveLetter) {
        return true;
    }
}
```

Using `default` so no other implementations break.

- [ ] **Step 3: Change `windowProc` to use return value from handleDeviceChange**

Replace the `windowProc` field (lines 139-148) with:

```java
private final WindowProc windowProc = new WindowProc() {
    @Override
    public LRESULT callback(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam) {
        if (msg == WM_DEVICECHANGE) {
            LRESULT result = handleDeviceChange(wParam, lParam != null ? new Pointer(lParam.longValue()) : null);
            return result != null ? result : new LRESULT(1);
        }
        return user32.DefWindowProc(hwnd, msg, wParam, lParam);
    }
};
```

- [ ] **Step 4: Rewrite `handleDeviceChange` to handle DBT_DEVICEQUERYREMOVE and return LRESULT**

Change method signature from `void` to `LRESULT` and add eject handling. Replace lines 150-195:

```java
private LRESULT handleDeviceChange(WPARAM wParam, Pointer lParam) {
    int eventType = wParam.intValue();

    if (lParam == null) {
        return null;
    }

    // Handle DBT_DEVICEQUERYREMOVE synchronously (must return value to Windows)
    if (eventType == DBT_DEVICEQUERYREMOVE) {
        DEV_BROADCAST_HDR hdr = new DEV_BROADCAST_HDR(lParam);
        if (hdr.dbch_devicetype == DBT_DEVTYP_VOLUME) {
            DEV_BROADCAST_VOLUME vol = new DEV_BROADCAST_VOLUME(lParam);
            String driveLetter = vol.getDriveLetter();
            if (driveLetter != null && volumeListener != null) {
                boolean allow = volumeListener.onVolumeQueryRemove(driveLetter);
                logger.info("DBT_DEVICEQUERYREMOVE for {}: {}", driveLetter, allow ? "allowed" : "denied");
                return allow ? new LRESULT(1) : new LRESULT(BROADCAST_QUERY_DENY);
            }
        }
        return new LRESULT(1);
    }

    // Handle arrival/removal events (dispatched to EDT)
    if ((eventType != DBT_DEVICEARRIVAL && eventType != DBT_DEVICEREMOVECOMPLETE)) {
        return null;
    }

    DEV_BROADCAST_HDR hdr = new DEV_BROADCAST_HDR(lParam);

    if (hdr.dbch_devicetype == DBT_DEVTYP_VOLUME) {
        DEV_BROADCAST_VOLUME volume = new DEV_BROADCAST_VOLUME(lParam);
        String driveLetter = volume.getDriveLetter();

        if (driveLetter != null) {
            SwingUtilities.invokeLater(() -> {
                if (volumeListener != null) {
                    if (eventType == DBT_DEVICEARRIVAL) {
                        logger.info("Volume arrived: {}", driveLetter);
                        volumeListener.onVolumeArrival(driveLetter);
                    } else {
                        logger.info("Volume removed: {}", driveLetter);
                        volumeListener.onVolumeRemoval(driveLetter);
                    }
                }
            });
        }
    } else if (hdr.dbch_devicetype == DBT_DEVTYP_DEVICEINTERFACE) {
        DEV_BROADCAST_DEVICEINTERFACE device = new DEV_BROADCAST_DEVICEINTERFACE(lParam);
        String dbccName = device.getDeviceName();

        if (dbccName != null) {
            SwingUtilities.invokeLater(() -> {
                if (deviceListener != null) {
                    if (eventType == DBT_DEVICEARRIVAL) {
                        logger.info("Device arrived: {}", dbccName);
                        deviceListener.onDeviceArrival(dbccName);
                    } else {
                        logger.info("Device removed: {}", dbccName);
                        deviceListener.onDeviceRemoval(dbccName);
                    }
                }
            });
        }
    }
    return null;
}
```

- [ ] **Step 5: Add `getVolumeByDriveLetter` to DeviceManager**

In `DeviceManager.java`, add after `getVolumeBySerial` (after line 102):

```java
public Volume getVolumeByDriveLetter(String driveLetter) {
    return volumesMap.search(1, (_, v) ->
            driveLetter.equals(v.getDriveLetter()) ? v : null);
}
```

- [ ] **Step 6: Implement `onVolumeQueryRemove` in DeviceManager**

In `DeviceManager.java`, add after `onVolumeRemoval` (after line 232):

```java
@Override
public boolean onVolumeQueryRemove(String driveLetter) {
    Volume volume = getVolumeByDriveLetter(driveLetter);
    if (volume != null) {
        volume.setEjecting();
        logger.info("Volume eject requested and denied: {} ({})", driveLetter, volume.getSerialNumber());
        return false; // Deny eject to allow cleanup
    }
    return true; // Unknown volume, allow eject
}
```

- [ ] **Step 7: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/com/superredrock/usbthief/core/UsbHotplugMonitor.java src/com/superredrock/usbthief/core/DeviceManager.java
git commit -m "feat: handle DBT_DEVICEQUERYREMOVE to block eject and set volume EJECTING"
```

---

### Task 3: Interrupt active NIO copy on EJECTING

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/CopyTask.java`

- [ ] **Step 1: Pass Volume reference to doCopy**

Change the `doCopy` call sites in `call()` (lines 118 and 125). First, store the volume in a local variable at line 99, then pass it to doCopy:

At line 99-102, change:
```java
Volume volume = QueueManager.getDeviceManager().getVolume(processingPath);
if (volume != null && !volume.isAccessible()) {
    return CopyResult.FAIL;
}
```

Then change the doCopy calls (lines 118 and 125) from:
```java
doCopy(processingPath, destinationPath, size, preVerifiedHash, buffer);
```
to:
```java
doCopy(processingPath, destinationPath, size, preVerifiedHash, buffer, volume);
```

And (line 125):
```java
doCopy(processingPath, destinationPath, size, hash, buffer, volume);
```
to:
```java
doCopy(processingPath, destinationPath, size, hash, buffer, volume);
```

- [ ] **Step 2: Update doCopy signature and add accessibility check in NIO loop**

Change the doCopy method signature (line 154) and add the volume check inside the while loop (after the interrupt check at line 161-163):

```java
private void doCopy(Path source, Path dest, long size, CheckSum hash, ByteBuffer buffer, Volume volume) throws IOException, InterruptedException {
    Files.createDirectories(dest.getParent());
    BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    try (FileChannel readChannel = FileChannel.open(source, StandardOpenOption.READ);
         FileChannel writeChannel = FileChannel.open(dest, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
        logger.debug("Copying:{} to {}", source, dest);
        while (readChannel.read(buffer) != -1) {
            if (Thread.currentThread().isInterrupted()){
                throw new InterruptedException("Copy cancelled");
            }
            if (volume != null && !volume.isAccessible()) {
                throw new IOException("Volume ejecting, aborting copy: " + source);
            }
            buffer.flip();
            int bytesWritten = writeChannel.write(buffer);
            taskProbe.record(bytesWritten);
            getRateLimiter().acquire(bytesWritten);

            long now = System.currentTimeMillis();
            long lastLog = lastLogTime.get();
            if (now - lastLog >= LOG_INTERVAL_MS) {
                if (lastLogTime.compareAndSet(lastLog, now)) {
                    double speed = speedProbeGroup.getTotalSpeed();
                    logger.info("Copying: {} - Global: {} MB/s",
                        source.getFileName(), String.format("%.2f", speed));
                }
            }

            buffer.clear();
        }
    }
    copyFileAttributes(source, dest, attributes);
    QueueManager.getIndex().addFile(hash, source, size);
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/worker/CopyTask.java
git commit -m "feat: interrupt active NIO copy when volume enters EJECTING state"
```

---

### Task 4: Add accessibility check to VerifyTask

**Files:**
- Modify: `src/com/superredrock/usbthief/worker/VerifyTask.java`

- [ ] **Step 1: Add accessibility check after isRegularFile check**

In `VerifyTask.java`, after the `isRegularFile` check (line 36-38), add:

```java
if (!Files.isRegularFile(processingPath)) {
    return CopyResult.SKIPPED;
}

Volume volume = QueueManager.getDeviceManager().getVolume(processingPath);
if (volume != null && !volume.isAccessible()) {
    return CopyResult.SKIPPED;
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/worker/VerifyTask.java
git commit -m "feat: skip verification when volume is in EJECTING state"
```

---

### Task 5: Manual integration test

- [ ] **Step 1: Build and run**

```bash
mvn package -q
java -p target/classes -m UsbThief/com.superredrock.usbthief.Main --enable-preview
```

- [ ] **Step 2: Test eject flow**

1. Insert a USB drive
2. Wait for copy tasks to start
3. Right-click the drive in Windows Explorer → "Eject"
4. Verify: Windows shows "device is in use" (eject denied)
5. Verify: logs show `Volume set to EJECTING`
6. Verify: queued tasks fail with `isAccessible() = false`
7. Verify: active copies abort with `Volume ejecting`
8. Physically remove and re-insert the drive
9. Verify: new Volume created in IDLE state, normal operation resumes
