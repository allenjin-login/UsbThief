# UsbThief

USB device monitoring and file copying tool for Windows.

English | [中文](#中文)

## Features

- **Auto Detection** — Automatically detects USB drives and monitors file changes in real-time via WatchService
- **Smart Deduplication** — MD5 checksum-based deduplication with Caffeine LRU cache and binary disk persistence
- **Rate Limiting** — Adaptive bandwidth control with auto/manual modes and real-time speed chart
- **Device Management** — Enable/disable individual volumes, blacklist devices by serial number, batch operations
- **File Filtering** — By extension (whitelist/blacklist), size, time, hidden files, and symlinks
- **Storage Control** — Disk space monitoring with OK/LOW/CRITICAL thresholds and automatic recycling
- **Statistics** — Cumulative and per-session metrics: files copied, speed, extension counts, error tracking
- **Internationalization** — English, Chinese, Japanese, German with runtime switching
- **System Tray** — Minimize to tray, auto-start on login, start hidden
- **Dark/Light Theme** — FlatLaf-based theming with toggle

## Requirements

- Windows 10/11 (64-bit)
- Java 25 JDK (uses preview features)
- Maven 3.9+

## Build

```bash
mvn clean package
```

Produces:
- `target/UsbThief-{version}.exe` — Windows executable (Launch4j)
- `target/UsbThief-{version}.zip` — Distribution with bundled jlink runtime
- `target/runtime/` — Custom JRE image

## Run from Source

```bash
java -p target/classes -m UsbThief/com.superredrock.usbthief.Main --enable-preview
```

## Configuration

All settings are accessible via the GUI:

| Setting | Description |
|---------|-------------|
| File Filters | Extension whitelist/blacklist, max file size, time range, hidden files |
| Rate Limit | Adaptive bandwidth control, base rate, load-level percentages |
| Storage Management | Reserved space, max copy space, recycle strategy, protected file age |
| Device Blacklist | Block devices by serial number |
| Auto-start | Launch on Windows login |
| Language | English / 中文 / 日本語 / Deutsch |

Configuration is stored in XML and supports import/export.

## Architecture

Built with Java 25 (JPMS modules), Swing + FlatLaf, JNA for Windows API, Log4j2, and Caffeine cache.

```
USB Detection (JNA) → Sniffer (WatchService) → CopyTask → Index (dedup) → NIO copy
                                                          ↓
                                              TaskScheduler (priority queue)
                                                          ↓
                                              RateLimiter → StorageController
```

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## License

Copyright (C) 2026 SuperRedRock

---

<a id="中文"></a>

# 中文

Windows USB 设备监控与文件复制工具。

[English](#usbthief) | 中文

## 功能

- **自动检测** — 自动检测 USB 设备，通过 WatchService 实时监控文件变化
- **智能去重** — 基于 MD5 校验和的去重，使用 Caffeine LRU 缓存 + 二进制磁盘持久化
- **速率限制** — 自适应带宽控制，支持自动/手动模式和实时速度图表
- **设备管理** — 单独启用/禁用卷、按序列号拉黑设备、批量操作
- **文件过滤** — 按扩展名（白名单/黑名单）、大小、时间、隐藏文件和符号链接过滤
- **存储控制** — 磁盘空间监控，OK/LOW/CRITICAL 三级阈值，自动回收
- **统计信息** — 累计和当前会话指标：已复制文件数、速度、扩展名统计、错误追踪
- **多语言** — 英语、中文、日语、德语，支持运行时切换
- **系统托盘** — 最小化到托盘、开机自启、启动时隐藏
- **深色/浅色主题** — 基于 FlatLaf 的主题切换

## 环境要求

- Windows 10/11（64 位）
- Java 25 JDK（使用预览特性）
- Maven 3.9+

## 构建

```bash
mvn clean package
```

产出物：
- `target/UsbThief-{version}.exe` — Windows 可执行文件（Launch4j）
- `target/UsbThief-{version}.zip` — 包含 jlink 运行时的发行包
- `target/runtime/` — 自定义 JRE 镜像

## 从源码运行

```bash
java -p target/classes -m UsbThief/com.superredrock.usbthief.Main --enable-preview
```

## 配置

所有设置均可通过 GUI 界面访问：

| 设置 | 说明 |
|------|------|
| 文件过滤 | 扩展名白名单/黑名单、最大文件大小、时间范围、隐藏文件 |
| 速率限制 | 自适应带宽控制、基准速率、负载级别百分比 |
| 存储管理 | 预留空间、最大复制空间、回收策略、受保护文件时长 |
| 设备黑名单 | 按序列号屏蔽设备 |
| 开机自启 | Windows 登录时自动启动 |
| 语言 | English / 中文 / 日本語 / Deutsch |

配置以 XML 存储，支持导入/导出。

## 架构

基于 Java 25（JPMS 模块）、Swing + FlatLaf、JNA（Windows API）、Log4j2 和 Caffeine 缓存构建。

```
USB 检测 (JNA) → Sniffer (WatchService) → CopyTask → Index (去重) → NIO 复制
                                                          ↓
                                              TaskScheduler (优先级队列)
                                                          ↓
                                              RateLimiter → StorageController
```

详细架构文档见 [CLAUDE.md](CLAUDE.md)。

## 许可证

Copyright (C) 2026 SuperRedRock
