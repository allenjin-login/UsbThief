# UsbThief

<p align="center">
  <img src="src/com/superredrock/usbthief/gui/App.png" alt="UsbThief Logo" width="32" height="32">
</p>

<p align="center">
  <strong>Smart USB Device Monitoring & File Copying Utility for Windows</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#usage">Usage</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#tech-stack">Tech Stack</a>
</p>

<p align="center">
  <a href="https://github.com/superRedRock/usb-thief/releases">
    <img src="https://img.shields.io/github/v/release/superRedRock/usb-thief?include_prereleases&style=flat-square" alt="Release">
  </a>
  <a href="https://github.com/superRedRock/usb-thief/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License">
  </a>
  <img src="https://img.shields.io/badge/Java-24%20%28Preview%29-orange?style=flat-square" alt="Java 24">
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=flat-square" alt="Platform">
  <a href="https://github.com/superRedRock/usb-thief/actions">
    <img src="https://img.shields.io/github/actions/workflow/status/superRedRock/usb-thief/build.yml?branch=master&style=flat-square" alt="Build">
  </a>
</p>

---

## Overview

UsbThief is a Windows desktop application that automatically detects USB drives, monitors file changes in real-time, and copies files with intelligent deduplication. Built with Java 24 and a modern Swing UI, it runs quietly in the background or system tray.

**Why UsbThief?**
- Automatic USB detection and file monitoring
- MD5 checksum-based deduplication (copies only new files)
- Priority-based scheduling (important files first)
- Adaptive load control (adjusts to system conditions)
- Hot language switching (English, Chinese, German)
- System tray integration for background operation

---

## Features

### Core Capabilities

| Feature | Description |
|---------|-------------|
| **USB Detection** | Real-time monitoring with automatic drive detection |
| **Two-Phase Scanning** | Initial scan, then WatchService for incremental changes |
| **Checksum Deduplication** | MD5-based duplicate detection with O(1) lookup |
| **Priority Scheduling** | Extension-based priorities (PDF=10, DOCX=8, TXT=5, TMP=1) |
| **Adaptive Load Control** | Queue depth + copy speed + thread activity + rejection rate |
| **Load-Aware Rate Limiting** | Auto-adjusts: LOW=100%, MEDIUM=70%, HIGH=40% |
| **Ghost Device Persistence** | Remembers offline devices, restores on reconnect |
| **System Tray Integration** | Minimize to tray for background operation |

### User Interface

- Modern Swing UI with FlatLaf 3.5.4 (light/dark themes)
- Tabbed interface with device list, file history, statistics, and logs
- Device cards with real-time status and controls
- Configurable filters and rate limiting
- First-run welcome dialog

### Internationalization

| Language | Status |
|----------|--------|
| English (en) | Complete |
| Chinese Simplified (zh_CN) | Complete |
| German (de) | Complete |

Languages can be switched at runtime without restarting the application.

---

## Screenshots

> **Note:** Screenshots will be added in a future release.

<!--
<p align="center">
  <img src="docs/screenshots/main.png" alt="Main Interface" width="600">
  <br><em>Main interface with device list and file history</em>
</p>
-->

---

## Installation

### Option 1: Download Release (Recommended)

Download the latest release from the [Releases](https://github.com/superRedRock/usb-thief/releases) page:

1. Download `UsbThief-x.x.x.zip`
2. Extract to your preferred location
3. Run `UsbThief-x.x.x.exe`

The distribution includes a custom JRE built with jlink, so no Java installation is required.

### Option 2: Build from Source

<details>
<summary>Click to expand build instructions</summary>

#### Prerequisites

- Java 24 JDK (or later)
- Maven 3.9+

#### Steps

```bash
# Clone the repository
git clone https://github.com/superRedRock/usb-thief.git
cd usb-thief

# Compile
mvn clean compile

# Run tests
mvn test

# Build EXE + ZIP distribution
mvn package
```

The built files will be in `target/`:
- `UsbThief-1.1.0.exe` - Windows executable
- `UsbThief-1.1.0.zip` - Distribution package
- `runtime/` - Custom JRE

#### Run from Source (Development)

```bash
# Compile first
mvn compile

# Run directly
java -p out -m UsbThief/com.superredrock.usbthief.Main --enable-preview
```

</details>

---

## Usage

### Quick Start

1. **Launch** UsbThief.exe
2. **Configure** the destination folder via Settings dialog
3. **Insert** a USB drive - it will be detected automatically
4. **Monitor** the file history and statistics tabs

### Device Management

- **Enable/Disable**: Toggle device monitoring with the enable button
- **Scan Now**: Trigger immediate file scan
- **Pause/Resume**: Control scanning per device

### Configuration

Configure these via **Settings > Configuration**:

| Setting | Description |
|---------|-------------|
| Destination Folder | Where copied files are stored |
| File Filters | Include/exclude patterns |
| Rate Limiting | Maximum copy speed |
| Priority Rules | File type priorities |

### System Tray

When minimized, UsbThief runs in the system tray. Right-click the tray icon for quick actions:

- Show/Hide window
- Pause/Resume all operations
- Exit application

---

## Configuration

Configuration files are stored in `~/.usbthief/`:

```
~/.usbthief/
├── languages.properties    # Language preferences
├── devices/                # Ghost device records
│   ├── ABC123.record       # Per-device persistence
│   └── XYZ789.record
└── index/                  # Checksum index
```

All directories are auto-created on first run.

<details>
<summary>Language Configuration</summary>

Edit `~/.usbthief/languages.properties` to customize:

```properties
# Set language priority (higher = first in menu)
zh_CN.priority=10
en.priority=5

# Custom display names
zh_CN.displayName=Simplified Chinese
zh_CN.nativeName=简体中文

# Set default language
default.language=en
```

</details>

---

## Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Java 24 (modular, JPMS, preview features) |
| **UI Framework** | Swing with FlatLaf 3.5.4 |
| **Build System** | Maven 3.9+ |
| **Runtime** | Custom JRE via jlink |
| **Packaging** | Launch4j (Windows EXE) |
| **Testing** | JUnit 5.11.4, Mockito 5.15.2 |
| **Platform** | Windows only |

### Architecture Highlights

- **Event-driven**: EventBus with parallel listener dispatch
- **Thread-based services**: Each service runs in its own Thread with tick-based execution
- **Singleton pattern**: Central managers (ConfigManager, EventBus, I18NManager)
- **Immutable events**: All events are immutable after creation
- **Thread-safe collections**: CopyOnWriteArrayList, ConcurrentHashMap throughout

---

## Contributing

Contributions are welcome. Here's how to help:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Guidelines

- Follow the existing code style (see `AGENTS.md` in the repo)
- Write tests for new functionality
- Update documentation for user-facing changes
- Ensure `mvn test` passes before submitting PRs

---

## License

This project is licensed under the MIT License.

```
Copyright (c) 2026 SuperRedRock

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<p align="center">
  Made with care by <a href="https://github.com/superRedRock">SuperRedRock</a>
</p>
