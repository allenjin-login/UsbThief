# GUI Package - Project Knowledge Base

**Package:** `com.superredrock.usbthief.gui`

**Generated:** 2026-01-30
**Updated:** 2026-02-09 (FileHistoryPanel now tracks failed copies)

## OVERVIEW
Swing-based UI for device monitoring, configuration, and real-time statistics.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Main window | `MainFrame.java` | Top-level container with panel layout |
| Device list | `DeviceListPanel.java` | Real-time device status display |
| Logging | `LogPanel.java` | Scrolling log output with auto-scroll |
| Statistics | `StatsPanel.java` | Real-time copy statistics |
| Failed copies | `FileHistoryPanel.java` | Tracks CopyCompletedEvent failures |
| Configuration | `ConfigDialog.java` | Config parameter editor with validation |

## CONVENTIONS
**Event-Driven Updates:**
- All panels subscribe to EventBus events (device, index, worker events)
- Update UI on EDT using `SwingUtilities.invokeLater()`

**Panel Layout:**
- Horizontal scroll disabled: `setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)`
- BorderLayout or BoxLayout for panel organization
- JTable for device list with custom TableModel

**Configuration Dialog:**
- Modifies `ConfigManager` via ConfigSchema entries
- Validates input before applying changes
- Shows current values from ConfigManager

**FileHistoryPanel (Updated):**
- Listens to `CopyCompletedEvent` for failed copies
- Stores records in-memory (FIFO eviction based on FILE_HISTORY_MAX_ENTRIES)
- Does NOT persist failed copy records to disk

**Menu Structure:**
- 操作 (Action): 保存索引, 隐藏, 退出
- 配置: 首选项
- 帮助: 关于

## ANTI-PATTERNS
- **DO NOT block EDT** - all long-running operations must be offloaded
- **DO NOT modify ConfigManager from background threads** - use SwingUtilities.invokeLater() or ConfigDialog
- **DO NOT assume UI is visible** - handle null components safely
