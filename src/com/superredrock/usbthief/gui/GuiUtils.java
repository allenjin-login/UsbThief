package com.superredrock.usbthief.gui;

import javax.swing.*;
import java.util.logging.Logger;

public class GuiUtils {
    /**
     * Gets the native window handle (HWND) for a JFrame on Windows.
     *
     * @param frame the JFrame
     * @return the HWND value, or 0 if not available
     */
    public static long getHWND(JFrame frame) {
        try {
            // Ensure the window is displayable
            if (!frame.isDisplayable()) {
                frame.addNotify();
            }

            // Use JNA to get the window handle
            com.sun.jna.platform.win32.User32 user32 = com.sun.jna.platform.win32.User32.INSTANCE;

            // Try to find window by title first
            String title = frame.getTitle();
            if (title != null && !title.isEmpty()) {
                com.sun.jna.platform.win32.WinDef.HWND hwnd = user32.FindWindow(null, title);
                if (hwnd != null) {
                    return com.sun.jna.Pointer.nativeValue(hwnd.getPointer());
                }
            }

            // Fallback: use active window
            com.sun.jna.platform.win32.WinDef.HWND hwnd = user32.GetActiveWindow();
            if (hwnd != null) {
                return com.sun.jna.Pointer.nativeValue(hwnd.getPointer());
            }
        } catch (Exception e) {
            Logger.getLogger(MainFrame.class.getName()).fine("Could not get HWND: " + e.getMessage());
        }
        return 0;
    }
}
