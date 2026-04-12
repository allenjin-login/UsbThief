package com.superredrock.usbthief.core;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;

import javax.swing.SwingUtilities;
import java.util.logging.Logger;

/**
 * USB hot-plug monitor using Windows API via JNA.
 *
 * <p>Creates its own hidden window to receive WM_DEVICECHANGE broadcasts,
 * avoiding interference with AWT/Swing's window procedure.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Creates a hidden message-only window for receiving device notifications</li>
 *   <li>Volume notifications (DBT_DEVTYP_VOLUME) are broadcast to all top-level windows</li>
 *   <li>Device interface notifications require RegisterDeviceNotification</li>
 *   <li>Callbacks are delivered on Windows message thread, then switched to EDT</li>
 * </ul>
 */
public class UsbHotplugMonitor {

    private static final Logger logger = Logger.getLogger(UsbHotplugMonitor.class.getName());

    // Windows message constants
    private static final int WM_DEVICECHANGE = 0x0219;
    private static final int DBT_DEVICEARRIVAL = 0x8000;
    private static final int DBT_DEVICEREMOVECOMPLETE = 0x8004;
    private static final int DBT_DEVTYP_VOLUME = 2;
    private static final int DBT_DEVTYP_DEVICEINTERFACE = 5;

    // GUID for disk device interface
    private static final GUID GUID_DEVINTERFACE_DISK = new GUID("53F56307-B6BF-11D0-94F2-00A0C91EFB8B");

    private final User32 user32 = User32.INSTANCE;

    private volatile HWND hwnd;
    private volatile boolean running;
    private volatile Thread messageThread;

    private VolumeListener volumeListener;
    private DeviceListener deviceListener;
    private HDEVNOTIFY hDeviceNotify;

    /**
     * Listener interface for volume hot-plug events.
     */
    public interface VolumeListener {
        void onVolumeArrival(String driveLetter);
        void onVolumeRemoval(String driveLetter);
    }

    /**
     * Listener interface for device-level hot-plug events.
     */
    public interface DeviceListener {
        void onDeviceArrival(String dbccName);
        void onDeviceRemoval(String dbccName);
    }

    // ========== Broadcast structures ==========

    @Structure.FieldOrder({"dbch_size", "dbch_devicetype", "dbch_reserved"})
    public static class DEV_BROADCAST_HDR extends Structure {
        public int dbch_size;
        public int dbch_devicetype;
        public int dbch_reserved;

        public DEV_BROADCAST_HDR() {
            super();
        }

        public DEV_BROADCAST_HDR(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"dbcv_size", "dbcv_devicetype", "dbcv_reserved", "dbcv_unitmask", "dbcv_flags"})
    public static class DEV_BROADCAST_VOLUME extends Structure {
        public int dbcv_size;
        public int dbcv_devicetype;
        public int dbcv_reserved;
        public int dbcv_unitmask;
        public int dbcv_flags;

        public DEV_BROADCAST_VOLUME() {
            super();
        }

        public DEV_BROADCAST_VOLUME(Pointer p) {
            super(p);
            read();
        }

        public String getDriveLetter() {
            int bitPos = Integer.numberOfTrailingZeros(dbcv_unitmask);
            if (bitPos >= 0 && bitPos < 26) {
                return (char) ('A' + bitPos) + ":";
            }
            return null;
        }
    }

    @Structure.FieldOrder({"dbcc_size", "dbcc_devicetype", "dbcc_reserved", "dbcc_classguid", "dbcc_name"})
    public static class DEV_BROADCAST_DEVICEINTERFACE extends Structure {
        public int dbcc_size;
        public int dbcc_devicetype;
        public int dbcc_reserved;
        public GUID dbcc_classguid;
        public char[] dbcc_name = new char[1];

        public DEV_BROADCAST_DEVICEINTERFACE() {
            super();
        }

        public DEV_BROADCAST_DEVICEINTERFACE(Pointer p) {
            super(p);
            read();
        }

        public String getDeviceName() {
            Pointer p = getPointer();
            int nameOffset = 4 + 4 + 4 + 16;
            return p.getWideString(nameOffset);
        }
    }

    // ========== Window procedure for hidden window ==========

    private final WindowProc windowProc = new WindowProc() {
        @Override
        public LRESULT callback(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam) {
            if (msg == WM_DEVICECHANGE) {
                handleDeviceChange(wParam, lParam != null ? new Pointer(lParam.longValue()) : null);
                return new LRESULT(1);
            }
            return user32.DefWindowProc(hwnd, msg, wParam, lParam);
        }
    };

    private void handleDeviceChange(WPARAM wParam, Pointer lParam) {
        int eventType = wParam.intValue();

        if ((eventType != DBT_DEVICEARRIVAL && eventType != DBT_DEVICEREMOVECOMPLETE)
                || lParam == null) {
            return;
        }

        DEV_BROADCAST_HDR hdr = new DEV_BROADCAST_HDR(lParam);

        if (hdr.dbch_devicetype == DBT_DEVTYP_VOLUME) {
            DEV_BROADCAST_VOLUME volume = new DEV_BROADCAST_VOLUME(lParam);
            String driveLetter = volume.getDriveLetter();

            if (driveLetter != null) {
                SwingUtilities.invokeLater(() -> {
                    if (volumeListener != null) {
                        if (eventType == DBT_DEVICEARRIVAL) {
                            logger.info("Volume arrived: " + driveLetter);
                            volumeListener.onVolumeArrival(driveLetter);
                        } else {
                            logger.info("Volume removed: " + driveLetter);
                            volumeListener.onVolumeRemoval(driveLetter);
                        }
                    }
                });
            }
        } else if (hdr.dbch_devicetype == DBT_DEVTYP_DEVICEINTERFACE) {
            DEV_BROADCAST_DEVICEINTERFACE device = new DEV_BROADCAST_DEVICEINTERFACE(lParam);
            String dbccName = device.getDeviceName();

            if (dbccName != null) {
                SwingUtilities.invokeLater(() -> {
                    if (deviceListener != null) {
                        if (eventType == DBT_DEVICEARRIVAL) {
                            logger.info("Device arrived: " + dbccName);
                            deviceListener.onDeviceArrival(dbccName);
                        } else {
                            logger.info("Device removed: " + dbccName);
                            deviceListener.onDeviceRemoval(dbccName);
                        }
                    }
                });
            }
        }
    }

    // ========== Public API ==========

    public void setVolumeListener(VolumeListener listener) {
        this.volumeListener = listener;
    }

    public void setDeviceListener(DeviceListener listener) {
        this.deviceListener = listener;
    }

    /**
     * Creates a hidden window and starts a dedicated message thread.
     */
    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("Monitor already running");
        }

        messageThread = Thread.ofPlatform()
                .name("UsbHotplugMonitor-MsgThread")
                .daemon(true)
                .start(() -> {
                    // Register a window class
                    String className = "UsbThiefMonitorClass";
                    WNDCLASSEX wndClass = new WNDCLASSEX();
                    wndClass.lpszClassName = className;
                    wndClass.lpfnWndProc = windowProc;
                    wndClass.hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
                    user32.RegisterClassEx(wndClass);

                    // Create hidden window
                    hwnd = user32.CreateWindowEx(
                            0, className, "UsbThiefMonitor", 0,
                            0, 0, 0, 0,
                            null, null, wndClass.hInstance, null
                    );

                    if (hwnd == null) {
                        logger.severe("Failed to create hidden window for UsbHotplugMonitor");
                        return;
                    }

                    // Register for device interface notifications on our own window
                    hDeviceNotify = registerDeviceInterfaceNotification(hwnd);
                    if (hDeviceNotify == null) {
                        int error = Kernel32.INSTANCE.GetLastError();
                        logger.warning("Failed to register for device interface notifications, error: " + error + " (continuing with volume-only mode)");
                    }

                    running = true;
                    logger.info("USB hot-plug monitor started with hidden window hwnd=" + hwnd);

                    // Message loop — blocks until WM_QUIT
                    MSG msg = new MSG();
                    while (user32.GetMessage(msg, null, 0, 0) > 0) {
                        user32.TranslateMessage(msg);
                        user32.DispatchMessage(msg);
                    }

                    // Cleanup after WM_QUIT
                    if (hDeviceNotify != null) {
                        user32.UnregisterDeviceNotification(hDeviceNotify);
                        hDeviceNotify = null;
                    }
                    user32.DestroyWindow(hwnd);
                    user32.UnregisterClass(className, wndClass.hInstance);
                    hwnd = null;
                    running = false;
                    logger.info("USB hot-plug monitor message loop exited");
                });
    }

    public synchronized void start(long hwndValue) {
        start();
    }

    public synchronized void stop() {
        if (!running || hwnd == null) {
            return;
        }

        // Post WM_QUIT to the message thread to break the loop
        user32.PostMessage(hwnd, WinUser.WM_QUIT, null, null);

        if (messageThread != null) {
            try {
                messageThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            messageThread = null;
        }

        logger.info("USB hot-plug monitor stopped");
    }

    public boolean isRunning() {
        return running;
    }

    // ========== Private helpers ==========

    private HDEVNOTIFY registerDeviceInterfaceNotification(HWND hwnd) {
        DEV_BROADCAST_DEVICEINTERFACE filter = new DEV_BROADCAST_DEVICEINTERFACE();
        filter.dbcc_size = filter.size();
        filter.dbcc_devicetype = DBT_DEVTYP_DEVICEINTERFACE;
        filter.dbcc_reserved = 0;
        filter.dbcc_classguid = GUID_DEVINTERFACE_DISK;
        filter.write();

        HDEVNOTIFY result = user32.RegisterDeviceNotification(
                hwnd, filter, User32.DEVICE_NOTIFY_WINDOW_HANDLE
        );

        if (result == null) {
            logger.severe("RegisterDeviceNotification failed, error: " + Kernel32.INSTANCE.GetLastError());
        } else {
            logger.info("Device interface notification registered: " + result);
        }

        return result;
    }
}
