package com.superredrock.usbthief.core;

import java.awt.Window;

/**
 * Platform-bound window customisation for taskbar presence (batch-3 polish).
 *
 * <p>Business code never references the Windows implementation directly; it goes through
 * {@code Platform.taskbarCustomizer()} and gets the interface matching the running platform.
 * On non-Windows hosts a no-op implementation is returned, so callers need no platform checks.</p>
 */
public interface TaskbarCustomizer {

    /**
     * Applies (or removes) the "hidden from taskbar" state for a window.
     *
     * <p>Must be called after the window peer exists — i.e. after the window has been made
     * visible at least once — because the underlying handle is only valid then.</p>
     *
     * @param window the window to customise
     * @param hidden {@code true} to keep it out of the taskbar, {@code false} for normal presence
     */
    void setHiddenFromTaskbar(Window window, boolean hidden);
}
