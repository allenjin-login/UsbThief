package com.superredrock.usbthief.platform.stub;

import com.superredrock.usbthief.core.TaskbarCustomizer;

import java.awt.Window;

/**
 * Non-Windows stand-in for {@link TaskbarCustomizer}: does nothing.
 */
public class NoopTaskbarCustomizer implements TaskbarCustomizer {

    @Override
    public void setHiddenFromTaskbar(Window window, boolean hidden) {
        // No-op: taskbar presence is a Windows-only concern here.
    }
}
