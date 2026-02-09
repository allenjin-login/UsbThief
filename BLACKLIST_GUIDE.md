# 设备黑名单功能说明

## 概述

UsbThief 现在支持设备黑名单功能。用户可以屏蔽特定的设备路径，防止这些设备被自动扫描和复制。

## 功能特性

1. **黑名单存储** - 使用 `java.util.prefs.Preferences` 持久化存储黑名单
2. **自动过滤** - `DeviceManager` 在检测新设备时自动跳过黑名单中的设备
3. **GUI 管理** - 提供直观的对话框来管理黑名单
4. **便捷入口** - 在设备列表面板中快速访问黑名单管理

## Config API

### 获取黑名单

```java
List<String> blacklist = Config.getDeviceBlacklist();
```

返回字符串列表，每个字符串代表一个设备路径（如 "E:\\"）。

### 设置黑名单

```java
List<String> blacklist = List.of("E:\\", "F:\\");
Config.setDeviceBlacklist(blacklist);
```

直接设置整个黑名单列表。

### 添加设备到黑名单

```java
Config.addToDeviceBlacklist("G:\\");
```

如果设备已在黑名单中，将不会重复添加。

### 从黑名单移除设备

```java
Config.removeFromDeviceBlacklist("E:\\");
```

如果设备不在黑名单中，此方法不会执行任何操作。

### 检查设备是否在黑名单中

```java
// 使用 Path 对象检查
boolean isBlacklisted = Config.isDeviceBlacklisted(Path.of("E:\\"));

// 使用字符串检查
boolean isBlacklisted = Config.isDeviceBlacklisted("E:\\");
```

## DeviceManager 集成

`DeviceManager` 在 `detectNewDevices()` 方法中自动过滤黑名单设备：

```java
private void detectNewDevices() {
    for (Path path : fileSystem.getRootDirectories()) {
        // Skip blacklisted devices
        if (Config.isDeviceBlacklisted(path)) {
            logger.fine("Device blacklisted, ignoring: " + path);
            continue;
        }

        Device device = new Device(path);
        if (devices.add(device)) {
            onDeviceInserted(device);
        }
    }
}
```

黑名单中的设备将被静默忽略，不会触发 `DeviceInsertedEvent`。

## GUI 管理

### BlacklistDialog

位置：`src/com/superredrock/usbthief/gui/BlacklistDialog.java`

**功能**：
- 显示当前黑名单中的所有设备
- 添加新设备到黑名单
- 移除选中的设备
- 清空整个黑名单
- 防止重复添加

**使用方法**：

```java
// 以模态对话框形式显示
List<String> finalBlacklist = BlacklistDialog.showBlacklistDialog(parentFrame);
```

### DeviceListPanel 集成

在设备列表面板顶部添加了"黑名单管理"按钮：

```java
JButton blacklistButton = new JButton("黑名单管理");
blacklistButton.setToolTipText("管理设备黑名单");
blacklistButton.addActionListener(e -> {
    BlacklistDialog.showBlacklistDialog(parentFrame);
});
```

## 使用场景

### 场景1: 屏蔽不信任的USB设备

用户有一个可疑的USB驱动器，不希望自动扫描：

1. 点击"黑名单管理"按钮
2. 点击"添加"
3. 输入设备路径（如 "E:\\"）
4. 确认添加
5. 设备将被自动忽略

### 场景2: 临时禁用某个设备

用户正在处理某个USB驱动器上的文件，不希望应用干扰：

1. 打开黑名单管理
2. 添加设备路径
3. 处理完成后，从黑名单中移除
4. 设备恢复正常监控

### 场景3: 排除系统驱动器

用户只想监控可移动USB设备，排除固定的系统驱动器：

1. 将所有系统驱动器路径添加到黑名单
2. 只有新插入的USB设备会被监控

## 注意事项

1. **路径格式** - 使用 Windows 路径格式，如 "E:\\" 或 "F:\\"
2. **大小写敏感** - 路径比较是大小写敏感的
3. **持久化** - 黑名单自动保存到 Preferences，重启后仍然有效
4. **实时生效** - 添加到黑名单后立即生效，不需要重启应用
5. **日志记录** - 黑名单中的设备会在日志中显示为 "Device blacklisted, ignoring"（FINE 级别）

## 数据格式

黑名单存储为分号分隔的字符串：

```
E:\;F:\;G:\;
```

空字符串表示黑名单为空。

## 重置黑名单

重置所有配置时会清空黑名单：

```java
Config.resetToDefaults();
```

或通过重置所有首选项来清除黑名单：

```java
Preferences prefs = Preferences.userNodeForPackage(Config.class);
prefs.remove("deviceBlacklist");
```

## 扩展建议

未来可以添加的功能：

1. **按驱动器字母过滤** - 快速选择/取消选择 A-Z 驱动器
2. **按文件系统类型过滤** - 屏蔽特定类型的文件系统（如 NTFS）
3. **黑名单导入/导出** - 从文件导入或导出到文件
4. **自动检测** - 记录已连接的设备，一键添加到黑名单
5. **临时禁用** - 定时禁用，时间到后自动恢复
