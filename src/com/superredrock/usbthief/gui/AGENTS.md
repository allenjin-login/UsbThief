# GUI Package

**Parent:** [../../../../../AGENTS.md](../../../../../AGENTS.md)

## OVERVIEW
Swing-based UI with hot language switching. Event-driven updates via EventBus. All panels implement `refreshLanguage()` for i18n.

## STRUCTURE
```
gui/
├── MainFrame.java         # Tabbed main window, LocaleChangeListener
├── I18NManager.java       # Singleton i18n manager, ResourceBundle loader
├── LanguageDiscovery.java # Auto-discovers messages_*.properties
├── LanguageInfo.java      # Record: (Locale, displayName, nativeName, priority)
├── LanguageConfig.java    # User preferences for language display/hidden
├── DeviceListPanel.java   # Device cards with refreshLanguage()
├── EventPanel.java        # EventBus listener, SwingUtilities.invokeLater
├── FileHistoryPanel.java  # Copy history with i18n table headers
├── LogPanel.java          # Log viewer with search
├── StatisticsPanel.java   # Stats display
├── ConfigDialog.java      # Settings dialog
├── BlacklistDialog.java   # Device blacklist management
├── SystemTrayIcon.java    # Tray icon with menu
└── messages*.properties   # i18n resource bundles (en, zh_CN, de)
```

## WHERE TO LOOK
| Task | File | Key Method |
|------|------|------------|
| Add new panel | MainFrame.java | `addTab()` in constructor |
| Add language key | messages.properties | Add key to all locale files |
| Handle language change | Any panel | Implement `refreshLanguage()` |
| Listen for events | EventPanel.java | `EventBus.register(this, EventType.class)` |
| Update UI from event | Any panel | `SwingUtilities.invokeLater(() -> ...)` |

## KEY PATTERNS

### I18N Hot-Switching
```java
// All panels implement LocaleChangeListener
public class SomePanel extends JPanel implements LocaleChangeListener {
    public SomePanel() {
        I18NManager.getInstance().addLocaleChangeListener(this);
    }
    
    @Override
    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            label.setText(i18n.getMessage("key.name"));
            button.setToolTipText(i18n.getMessage("button.tooltip"));
        });
    }
}
```

### Language Discovery
- Scans classpath for `messages_*.properties` in gui package
- Extracts locale from filename (e.g., `messages_zh_CN.properties` → `zh_CN`)
- Auto-generates language menu in MainFrame

### EventBus → UI Update Flow
```
1. EventBus.dispatch(event) from worker/core
2. Panel.onEvent(event) receives on dispatch thread
3. SwingUtilities.invokeLater(() -> updateUI())
4. Never block EDT - all I/O in worker threads
```

### Dynamic Table Headers
```java
// Override getColumnName for i18n
@Override
public String getColumnName(int column) {
    return I18NManager.getInstance().getMessage("table.column." + column);
}
```

## ANTI-PATTERNS (gui-specific)
- **DO NOT hard-code UI strings** - always use `I18NManager.getMessage("key")`
- **DO NOT update Swing components off-EDT** - wrap in `SwingUtilities.invokeLater()`
- **DO NOT forget refreshLanguage()** - all text must update on locale change
- **DO NOT block in event listeners** - offload to executor, update UI via invokeLater
