# VolumeListPanel Device-Volume 嵌套结构设计

## 目标

将 VolumeListPanel 从扁平列表改为 Device-Volume 嵌套结构：多卷设备显示可折叠分组，单卷设备保持平铺。

## 方案

组合 JPanel 嵌套（方案 B）：保持现有 BoxLayout.Y_AXIS 框架，新增 DeviceGroupPanel 内部类处理多卷设备的分组头和折叠逻辑。VolumeCard 基本不变。

## 组件结构

`devicesPanel` 的子组件有两种：

1. **单卷设备** → 直接放一个 VolumeCard（和现在一样）
2. **多卷设备** → 放一个 DeviceGroupPanel，内含分组头 + 若干 VolumeCard

### DeviceGroupPanel（新增内部类）

- 布局：`BoxLayout.Y_AXIS`
- 子组件：分组头（Header）+ volumesContainer（存放 VolumeCard 的 JPanel）

### 分组头（Header）布局

```
[▸ 箭头] [VID:PID  SN:xxxx  总计 64GB] [⋮ 操作]
```

- 左侧：展开/折叠箭头按钮（▶/▼），点击切换 `volumesContainer.setVisible()`
- 中间：VID/PID（如有）、设备序列号、所有卷总容量之和
- 右侧："⋮" 按钮弹出菜单：全部禁用、全部启用、全部黑名单

## 数据映射

- `Map<Volume, VolumeCard> volumeCards` — 保留不变
- `Map<Device, DeviceGroupPanel> deviceGroups` — 新增，追踪多卷设备的分组

## 事件处理

### VolumeInsertedEvent

1. 检查 `volume.getDevice().getVolumes().size()`
2. 若 Device 卷数从 1 变为 2：把原平铺 VolumeCard 从 devicesPanel 移除，创建 DeviceGroupPanel，把两个 VolumeCard 都移入
3. 若 Device 卷数 >= 2：在已有 DeviceGroupPanel 追加新 VolumeCard
4. 若 Device 卷数 == 1：平铺 VolumeCard，和现在一样

### VolumeRemovedEvent

1. 从 DeviceGroupPanel 移除 VolumeCard
2. 若移除后 Device 只剩 1 个卷：拆散 DeviceGroupPanel，把剩余 VolumeCard 改回平铺
3. 若移除后 Device 无卷：移除整个 DeviceGroupPanel

### VolumeStateChangedEvent

不影响分组结构，只更新 VolumeCard 本身。分组头总容量在此时机刷新。

## 批量操作

### 分组头操作（按设备）

通过分组头 "⋮" 按钮弹出菜单，不依赖 checkbox：
- 全部禁用该 Device 下所有卷
- 全部启用该 Device 下所有卷
- 将该 Device 序列号加入黑名单

### 顶部菜单（按选择）

顶部 "⋮" 菜单逻辑不变，仍按 checkbox 选择来批量操作。

## VolumeCard 改动

基本不变。唯一变化：将 VolumeCard 构造逻辑提取为 `VolumeListPanel.createVolumeCard(Volume)` 工厂方法，方便 VolumeListPanel 和 DeviceGroupPanel 共用。VolumeCard 仍为内部类。

## i18n 新增 key

| Key | 用途 |
|-----|------|
| `device.group.vidPid` | VID:PID 格式展示 |
| `device.group.totalCapacity` | 总容量标签 |
| `device.group.batchDisable` | 分组级全部禁用 |
| `device.group.batchEnable` | 分组级全部启用 |
| `device.group.batchBlacklist` | 分组级全部黑名单 |
