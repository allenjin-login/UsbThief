package com.superredrock.usbthief.platform.win;

import com.superredrock.usbthief.core.TaskbarCustomizer;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Window;

/**
 * Windows implementation of {@link TaskbarCustomizer} using the extended window style
 * {@code WS_EX_TOOLWINDOW}, which keeps a window out of the taskbar while leaving it fully
 * functional (tray-managed windows are the classic case).
 *
 * <p>Failure is always non-fatal: on any error the window simply keeps its default taskbar
 * presence and the event is logged at warn level.</p>
 */
public class WindowsTaskbarCustomizer implements TaskbarCustomizer {

    private static final Logger logger = LogManager.getLogger(WindowsTaskbarCustomizer.class);

    private static final int GWL_EXSTYLE = -20;
    private static final long WS_EX_TOOLWINDOW = 0x00000080L;
    private static final long WS_EX_APPWINDOW = 0x00040000L;

    // SetWindowPos flags: keep position/size/z-order, but let the shell re-evaluate the frame.
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        long GetWindowLongPtrW(Pointer hWnd, int nIndex);

        long SetWindowLongPtrW(Pointer hWnd, int nIndex, long dwNewLong);

        boolean SetWindowPos(Pointer hWnd, Pointer hWndInsertAfter, int x, int y, int cx, int cy, int uFlags);
    }

    @Override
    public void setHiddenFromTaskbar(Window window, boolean hidden) {
        try {
            Pointer hwnd = Native.getWindowPointer(window);
            if (hwnd == null) {
                logger.warn("Cannot apply taskbar visibility: window peer not ready yet");
                return;
            }

            long exStyle = User32.INSTANCE.GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
            long newStyle = hidden
                    ? (exStyle | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW
                    : (exStyle & ~WS_EX_TOOLWINDOW) | WS_EX_APPWINDOW;

            if (newStyle != exStyle) {
                User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_EXSTYLE, newStyle);
                // Ask the shell to re-evaluate the frame so the taskbar button appears/disappears now.
                User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                        SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_FRAMECHANGED);
                logger.info("Taskbar visibility applied: hidden={}", hidden);
            }
        } catch (Throwable t) {
            // Never let a cosmetic tweak break startup or window toggling.
            logger.warn("Failed to apply taskbar visibility (non-fatal): {}", t.getMessage());
        }
    }
}
