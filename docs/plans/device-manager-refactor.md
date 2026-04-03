# DeviceManager 重构计划

**目标**: 使用 JNA + Windows API 替换轮询检测，分离 Sniffer 管理职责

**日期**: 2026-03-07

---

## 概述

### 问题
- **低效轮询**: DeviceManager 每 2 秒遍历 `FileSystem.getRootDirectories()` 检测 USB
- **职责过重**: 同时负责 USB 检测、Scanner 生命周期、Ghost 设备管理、事件分发

### 解决方案
1. **事件驱动检测**: JNA + Windows `RegisterDeviceNotification` 监听 USB 插拔
2. **职责分离**: Scanner 管理移至独立的 `ScannerManager` 类
3. **简化 DeviceManager**: 专注于设备状态管理和事件分发

---

## 架构变更

### Before
```
DeviceManager (604 行)
├── USB 检测 (轮询 detectNewDevices)
├── Scanner 生命周期管理 (activeScanners Map)
├── Ghost 设备处理
├── 事件分发
└── 设备查找
```

### After
```
UsbHotplugMonitor (新增)
├── JNA Windows API 封装
├── WM_DEVICECHANGE 消息处理
└── 设备路径解析 → DeviceManager 回调

ScannerManager (新增)
├── Map<Device, Sniffer> activeScanners
├── start/stop/pause/resume 方法
└── SnifferLifecycleManager 集成

DeviceManager (简化 ~300 行)
├── 设备状态管理 (devices Set)
├── Ghost 设备处理 (保留)
├── 事件分发 (保留)
├── 设备查找 (保留)
└── UsbHotplugMonitor 回调接口
```

---

## 实施步骤

### Phase 1: 添加 JNA 依赖

**文件**: `pom.xml`

```xml
<dependency>
    <groupId>net.java.dev.jna</groupId>
    <artifactId>jna</artifactId>
    <version>5.14.0</version>
</dependency>
<dependency>
    <groupId>net.java.dev.jna</groupId>
    <artifactId>jna-platform</artifactId>
    <version>5.14.0</version>
</dependency>
```

**验证**: `mvn compile` 成功

---

### Phase 2: 创建 UsbHotplugMonitor (JNA 封装)

**文件**: `src/com/superredrock/usbthief/core/UsbHotplugMonitor.java`

**职责**:
- 封装 Windows API: `RegisterDeviceNotification`, `WM_DEVICECHANGE`
- 创建隐藏窗口接收消息
- 解析设备路径，提取盘符
- 回调 `DeviceListener` 接口

**关键常量**:
```java
// Windows 消息
WM_DEVICECHANGE = 0x0219
DBT_DEVICEARRIVAL = 0x8000      // 设备插入
DBT_DEVICEREMOVECOMPLETE = 0x8004  // 设备移除

// 设备类型
DBT_DEVTYP_DEVICEINTERFACE = 5

// USB GUID
GUID_DEVINTERFACE_USB_DEVICE = "{A5DCBF10-6530-11D2-901F-00C04FB951ED}"
```

**结构体** (使用 JNA Platform):
```java
DEV_BROADCAST_DEVICEINTERFACE
├── int dbcc_size
├── int dbcc_devicetype
├── int dbcc_reserved
├── GUID dbcc_classguid
└── char[] dbcc_name  // 设备路径: \\?\USB#VID_xxxx&PID_xxxx...
```

**接口**:
```java
public interface DeviceListener {
    void onDeviceArrival(String devicePath);    // USB 插入
    void onDeviceRemoval(String devicePath);    // USB 移除
}
```

**实现要点**:
1. 实现 `User32.WindowProc` 接口
2. 创建窗口: `CreateWindowEx(WS_EX_TOPMOST, "STATIC", ...)`
3. 注册通知: `RegisterDeviceNotification(hwnd, filter, DEVICE_NOTIFY_WINDOW_HANDLE)`
4. 消息循环线程: `GetMessage` → `TranslateMessage` → `DispatchMessage`
5. 解析盘符: 从 `dbcc_name` 提取或使用 `GetLogicalDriveStrings` 对比

**陷阱处理**:
- `RegisterDeviceNotification` 返回 `HDEVNOTIFY` (不是 Long)
- `char[] dbcc_name = new char[1]` 必须初始化
- 回调在 Windows 线程，需 `SwingUtilities.invokeLater` 切换
- 不能使用 `HWND_MESSAGE`，必须用真实窗口

**测试**:
```java
@Test
void testDeviceArrival() {
    monitor.start(listener);
    // 模拟插入 USB
    verify(listener, timeout(5000)).onDeviceArrival(anyString());
}
```

---

### Phase 3: 创建 ScannerManager (分离职责)

**文件**: `src/com/superredrock/usbthief/core/ScannerManager.java`

**职责**:
- 管理 `Map<Device, Sniffer> activeScanners`
- 提供 start/stop/pause/resume API
- 集成 SnifferLifecycleManager
- 与 StorageController 联动

**接口**:
```java
public class ScannerManager {
    // Scanner 生命周期
    void startScanner(Device device);
    void stopScanner(Device device);
    void stopAllScanners();
    
    // 暂停/恢复
    void pauseScanner(Device device);
    void resumeScanner(Device device);
    void pauseAllScanners();
    void resumeAllScanners();
    
    // 状态查询
    boolean isScannerRunning(Device device);
    boolean isScannerAlive(Device device);
    boolean hasPausedScanners();
    
    // 关闭
    void shutdown();
}
```

**字段**:
```java
private final Map<Device, Sniffer> activeScanners = new ConcurrentHashMap<>();
private final SnifferLifecycleManager lifecycleManager;
private final EventBus eventBus;
```

**迁移代码**: 从 DeviceManager 移动以下方法:
- `startScanner(Device)`
- `stopScanner(Device)`
- `stopAllScanners()`
- `pauseScanner(Device)`
- `resumeScanner(Device)`
- `pauseAllScanners()`
- `resumeAllScanners()`
- `hasPausedScanners()`
- `isScannerRunning(Device)`
- `isScannerAlive(Device)`
- `manageScanner(Device)` (内部方法)

---

### Phase 4: 重构 DeviceManager (简化)

**变更**:

1. **移除字段**:
   ```java
   // 删除
   private final Map<Device, Sniffer> activeScanners = new ConcurrentHashMap<>();
   ```

2. **添加字段**:
   ```java
   private final UsbHotplugMonitor hotplugMonitor;
   private final ScannerManager scannerManager;
   ```

3. **移除方法** (移至 ScannerManager):
   - 所有 Scanner 生命周期方法 (11 个)

4. **重构 tick()**:
   ```java
   @Override
   protected void tick() {
       // 只保留存储控制逻辑
       StorageLevel level = storageController.getStorageLevel();
       if (level == CRITICAL) {
           scannerManager.pauseAllScanners();
       } else if (level == OK && scannerManager.hasPausedScanners()) {
           scannerManager.resumeAllScanners();
       }
       // 删除 detectNewDevices() 和 updateAllDevices()
   }
   ```

5. **实现 DeviceListener**:
   ```java
   private class HotplugHandler implements UsbHotplugMonitor.DeviceListener {
       @Override
       public void onDeviceArrival(String devicePath) {
           SwingUtilities.invokeLater(() -> handleDeviceArrival(devicePath));
       }
       
       @Override
       public void onDeviceRemoval(String devicePath) {
           SwingUtilities.invokeLater(() -> handleDeviceRemoval(devicePath));
       }
   }
   
   private void handleDeviceArrival(String devicePath) {
       // 1. 解析设备路径获取 Path 和 serial
       // 2. 检查黑名单
       // 3. 查找或创建 Device
       // 4. mergeGhostToDevice (如果存在)
       // 5. scannerManager.startScanner(device)
       // 6. onDeviceInserted(device)
   }
   
   private void handleDeviceRemoval(String devicePath) {
       // 1. 查找对应的 Device
       // 2. scannerManager.stopScanner(device)
       // 3. onDeviceRemoved(device)
       // 4. convertToGhost(device) (如果之前已知)
   }
   ```

6. **更新构造函数**:
   ```java
   public DeviceManager() {
       this.hotplugMonitor = new UsbHotplugMonitor();
       this.scannerManager = new ScannerManager(eventBus);
       this.hotplugMonitor.setListener(new HotplugHandler());
   }
   ```

7. **更新 startService/stopService**:
   ```java
   @Override
   public void startService() {
       super.startService();
       hotplugMonitor.start();
       scannerManager.resumeAllScanners(); // 恢复之前暂停的扫描器
   }
   
   @Override
   public void stopService() {
       hotplugMonitor.stop();
       scannerManager.stopAllScanners();
       scannerManager.shutdown();
       super.stopService();
   }
   ```

---

### Phase 5: 更新依赖代码

**受影响文件**:
1. `MainFrame.java` - 调用 `deviceManager.startScanner()` → `scannerManager.startScanner()`
2. `DeviceCard.java` - 同上
3. `SnifferLifecycleManager.java` - 调用 `deviceManager.resumeScanner()` → 通过 ScannerManager

**重构策略**:
- 方案 A: `DeviceManager` 委托调用 `scannerManager.xxx()` (保持 API 兼容)
- 方案 B: 直接注入 `ScannerManager` 到需要的类 (推荐)

---

### Phase 6: 测试

**单元测试**:
1. `UsbHotplugMonitorTest.java` - 模拟 Windows 消息
2. `ScannerManagerTest.java` - Scanner 生命周期
3. `DeviceManagerRefactoredTest.java` - 集成测试

**集成测试**:
- 手动测试: 插入/拔出 USB 设备，观察日志和 UI 更新

---

## 风险与缓解

### 风险 1: JNA 跨平台兼容性
- **影响**: 仅支持 Windows
- **缓解**: 保留轮询作为 fallback (通过 `UsbDetector` 接口抽象)

### 风险 2: 设备路径解析失败
- **影响**: 无法正确识别 USB 设备
- **缓解**: 使用 `GetLogicalDriveStrings` 对比前后盘符变化

### 风险 3: Windows 消息线程阻塞
- **影响**: UI 卡顿
- **缓解**: 消息处理立即返回，通过 `invokeLater` 异步处理

---

## 预期收益

1. **性能**: 零轮询开销，CPU 使用率降低
2. **响应速度**: 毫秒级检测 (vs 2 秒轮询)
3. **代码质量**: DeviceManager 从 604 行降至 ~300 行
4. **可维护性**: 职责清晰，易于测试

---

## 回滚计划

如果 JNA 方案失败:
1. 保留 `detectNewDevices()` 代码 (注释掉)
2. 通过配置切换: `usb.detection=polling|jna`
3. 5 分钟内可回滚

---

## 检查点

- [ ] Phase 1: JNA 依赖添加
- [ ] Phase 2: UsbHotplugMonitor 实现
- [ ] Phase 3: ScannerManager 分离
- [ ] Phase 4: DeviceManager 重构
- [ ] Phase 5: 依赖代码更新
- [ ] Phase 6: 测试通过
