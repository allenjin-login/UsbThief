# Compact UI Redesign — 极简曲线面板

**Date:** 2026-04-11
**Approach:** A — 极简曲线面板（用户选择）

## Overview

将 UsbThief 从 1200x800 标准窗口改为以系统托盘为主的紧凑界面。核心变更：
1. 系统托盘为主要交互入口，主窗口按需打开
2. 动态托盘图标，根据传输状态切换
3. 新增实时滚动速度曲线图组件
4. 主窗口缩小至 500x400，布局精简

## Window Layout (500 x 400)

从上到下四个区域：

### 1. Speed Chart Area（顶部，约 45% 高度）
- 自定义 `SpeedChartPanel` 组件
- 实时滚动速度曲线 + 大字显示当前速度
- 渐变填充背景 + 虚线网格 + Y轴刻度
- 右上角 "LIVE" 脉冲指示灯

### 2. Stats Bar（中部，固定高度）
- 横向四格卡片：Total / Files / Queue / Load
- 紧凑数值展示，跟随主题色

### 3. Device List（下部，可滚动）
- 单行卡片样式
- 每行：状态点（颜色圆点） + 设备名 + 容量 + 状态标签
- 保留现有的 VolumeListPanel 但精简为紧凑模式

### 4. Status Bar（底部）
- 最近复制文件名 + 工作目录路径
- 最小化信息展示

### Menu Bar
- 保留但精简，主要功能通过托盘右键菜单访问
- 去掉不必要的菜单项

## System Tray

### Tray Icon States
4种状态，通过 Java 2D 动态生成 BufferedImage：

| State | Color | Overlay | Trigger |
|-------|-------|---------|---------|
| Idle | Gray (#6c7086) | 无 | 无设备活动 |
| Scanning | Green (#a6e3a1) | 放大镜图标 | Sniffer 启动扫描 |
| Copying | Blue (#89b4fa) | 下载箭头 | CopyTask 执行中 |
| Error | Red (#f38ba8) | 感叹号 | CopyTask 异常 |

- 预生成 16x16 和 32x32 两种尺寸（DPI 适配）
- 通过 EventBus 事件切换图标
- 使用 `SystemTray.getTrayIcon().setImage()` 更新

### Tray Tooltip
**全部使用英文**（避免中文乱码）：
```
UsbThief - {speed} MB/s | {count} files copying
UsbThief - Idle
UsbThief - Scanning {device}
```

### Tray Right-Click Menu
**全部使用英文**：
```
► Show Window
──────────────
↓ Current: 12.5 MB/s
📦 Copied: 1.2 GB (47 files)
──────────────
⚙ Settings...
⏸ Pause All
❌ Exit
```

### Behavior
- 启动时自动最小化到托盘
- 单击托盘图标 → 显示/隐藏主窗口
- 关闭窗口 → 最小化到托盘（不退出）
- 托盘 Exit 才真正退出

## Speed Chart Panel (`gui/SpeedChartPanel.java`)

### Data
- 数据源：监听 EventBus 的 CopySpeedEvent，聚合所有 CopyTask 速度
- 采样间隔：500ms
- 数据窗口：保留最近 60 个采样点（30 秒历史）
- 存储：`ArrayDeque<Double>`，容量固定 60，synchronized 线程安全
- 空闲时记录 0 值

### Rendering
- 自定义 JPanel，重写 `paintComponent(Graphics g)`
- Graphics2D 抗锯齿
- 绘制流程：
  1. 计算Y轴缩放（当前最大值 * 1.2）
  2. 画网格虚线 + Y轴标签
  3. 构建曲线 Path2D（贝塞尔 curveTo 平滑）
  4. 画渐变填充区域
  5. 画曲线描边
  6. 画当前点（圆点 + 光晕效果）

### Theme
- 曲线颜色跟随 ThemeManager accent color
- 背景色、网格线、文字跟随深/浅主题
- 注册 ThemeManager 主题变更监听

### Animation
- `javax.swing.Timer` 驱动，500ms 间隔 repaint
- "LIVE" 指示灯脉冲动画
- 无速度时显示平缓零线

## Tray Icon Manager (`gui/TrayIconManager.java`)

### Responsibilities
- 创建和管理 SystemTrayIcon
- 监听 EventBus 事件切换图标状态
- 管理右键弹出菜单
- 处理单击（show/hide window）和双击行为
- Tooltip 动态更新（速度、文件数）

### State Machine
```
IDLE → (DeviceArrived + SnifferStarted) → SCANNING
SCANNING → (CopyTaskStarted) → COPYING
COPYING → (AllCopyTasksDone) → SCANNING or IDLE
ANY → (CopyError) → ERROR (5s 后恢复到上一状态)
```

### Icon Generation
- `generateIcon(TrayState state)` 方法
- 使用 BufferedImage + Graphics2D 绘制
- 绘制 USB 形状 + 状态颜色 + 状态叠加图标
- 缓存已生成的图标，避免每帧重绘

## Files to Create

| File | Purpose |
|------|---------|
| `gui/SpeedChartPanel.java` | 自定义 JPanel，速度曲线采样、存储和绘制 |
| `gui/TrayIconManager.java` | 托盘图标管理，状态切换，右键菜单 |

## Files to Modify

| File | Changes |
|------|---------|
| `gui/MainFrame.java` | 缩小至 500x400，移除旧 StatisticsPanel，集成 SpeedChartPanel，调整布局，关闭行为改为最小化到托盘 |
| `gui/theme/ThemeManager.java` | 新增速度曲线相关颜色属性 |
| `gui/messages_en.properties` | 托盘菜单英文文本 |
| `gui/messages_zh.properties` | 主窗口中文文本（i18n） |
| `gui/messages_ja.properties` | 日文文本 |
| `gui/messages_de.properties` | 德文文本 |

## Constraints

- 不引入第三方图表库，使用 Java 2D 直接绘制
- 托盘文本全部英文
- 保留现有的 i18n 体系
- 兼容 FlatLaf 深色/浅色主题
- 保留 SystemTrayIcon 已有功能的基础上增强
