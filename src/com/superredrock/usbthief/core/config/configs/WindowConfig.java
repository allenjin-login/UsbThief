package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class WindowConfig {
    public static final String CATEGORY = "Window";

    public static final ConfigEntry<Boolean> AUTO_START_ENABLED =
            booleanEntry("gui.autoStartEnabled", "Start application automatically on Windows login", false, CATEGORY);

    public static final ConfigEntry<Boolean> SHOW_IN_TASKBAR =
            booleanEntry("gui.showInTaskbar", "Show window in taskbar", true, CATEGORY);

    public static final ConfigEntry<String> CLOSE_ACTION =
            stringEntry("gui.closeAction", "Action when closing: ASK, MINIMIZE_TO_TRAY, EXIT", "ASK", CATEGORY);

    public static final ConfigEntry<Boolean> CLOSE_ACTION_REMEMBER =
            booleanEntry("gui.closeActionRemember", "Remember the close action choice", false, CATEGORY);

    private WindowConfig() {}
}
