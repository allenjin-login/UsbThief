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
 * <p>Uses RegisterDeviceNotification and WM_DEVICECHANGE messages
 * to detect volume arrival/removal in real-time, replacing
 * inefficient polling-based detection.
 *
 * <p>This class monitors volume-level events (drive letters) rather than
 * USB device events, providing direct drive letter information.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Attaches to an existing HWND (e.g., MainFrame)</li>
 *   <li>Registers for volume notifications using DEV_BROADCAST_VOLUME</li>
 *   <li>Callbacks are delivered on Windows message thread, then switched to EDT</li>
 * </ul>
 */
public class UsbHotplugMonitor {

    private static final Logger logger = Logger.getLogger(UsbHotplugMonitor.class.getName());

    // Windows message constants
    private static final int WM_DEVICECHANGE = 0x0219;
    private static final int DBT_DEVICEARRIVAL = 0x8000;
    private static final int DBT_DEVICEREMOVECOMPLETE = 0x8004;
    private static final int DBT_DEVTYP_VOLUME = 2;  // Volume device type
    private static final int DBT_DEVTYP_DEVICEINTERFACE = 5;  // Device interface type

    // Volume notification filter - broadcast to all volumes
    private static final int DBTF_NET = 0x00000002;  // Network volume

    // GUID for disk device interface (used for device-level notifications)
    // {53F56307-B6BF-11D0-94F2-00A0C91EFB8B}
    private static final GUID GUID_DEVINTERFACE_DISK = new GUID("53F56307-B6BF-11D0-94F2-00A0C91EFB8B");
    private final User32 user32 = User32.INSTANCE;

    private volatile HWND hwnd;
    private volatile HDEVNOTIFY hDevNotify;
    private volatile boolean running;
    private volatile long originalWndProcPtr;

    private VolumeListener volumeListener;
    private DeviceListener deviceListener;
    private HDEVNOTIFY hVolumeNotify;
    private HDEVNOTIFY hDeviceNotify;


    /**
     * Listener interface for volume hot-plug events.
     */
    public interface VolumeListener {
        /**
         * Called when a volume (drive) is inserted.
         *
         * @param driveLetter The drive letter (e.g., "E:")
         */
        void onVolumeArrival(String driveLetter);

        /**
         * Called when a volume (drive) is removed.
         *
         * @param driveLetter The drive letter (e.g., "E:")
         */
        void onVolumeRemoval(String driveLetter);
    }

    /**
     * Listener interface for device-level hot-plug events.
     * Fired when USB device is inserted/removed (before volume mount).
     */
    public interface DeviceListener {
        /**
         * Called when a device is inserted (before volume mount).
         *
         * @param dbccName The device instance path (contains VID/PID/Serial)
         */
        void onDeviceArrival(String dbccName);

        /**
         * Called when a device is removed.
         *
         * @param dbccName The device instance path
         */
        void onDeviceRemoval(String dbccName);
    }

    /**
     * Device broadcast header structure.
     */
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

    /**
     * Volume broadcast structure for DBT_DEVTYP_VOLUME.
     * Contains a bitmask of drive letters affected.
     */
    @Structure.FieldOrder({"dbcv_size", "dbcv_devicetype", "dbcv_reserved", "dbcv_unitmask", "dbcv_flags"})
    public static class DEV_BROADCAST_VOLUME extends Structure {
        public int dbcv_size;
        public int dbcv_devicetype;
        public int dbcv_reserved;
        public int dbcv_unitmask;  // Bitmask: bit 0 = A:, bit 1 = B:, etc.
        public int dbcv_flags;

        public DEV_BROADCAST_VOLUME() {
            super();
        }

        public DEV_BROADCAST_VOLUME(Pointer p) {
            super(p);
            read();
        }

        /**
         * Extracts drive letter from the unit mask.
         * @return Drive letter with colon, e.g., "E:"
         */
        public String getDriveLetter() {
            // Find the lowest set bit (first drive letter in mask)
            int bitPos = Integer.numberOfTrailingZeros(dbcv_unitmask);
            if (bitPos >= 0 && bitPos < 26) {
                return (char) ('A' + bitPos) + ":";
            }
            return null;
        }
    }

    /**
     * Device interface broadcast structure for DBT_DEVTYP_DEVICEINTERFACE.
     * Contains the device instance path with VID/PID/Serial information.
     */
    @Structure.FieldOrder({"dbcc_size", "dbcc_devicetype", "dbcc_reserved", "dbcc_classguid", "dbcc_name"})
    public static class DEV_BROADCAST_DEVICEINTERFACE extends Structure {
        public int dbcc_size;
        public int dbcc_devicetype;
        public int dbcc_reserved;
        public GUID dbcc_classguid;
        public char[] dbcc_name = new char[1];  // Variable length, will be resized

        public DEV_BROADCAST_DEVICEINTERFACE() {
            super();
        }

        public DEV_BROADCAST_DEVICEINTERFACE(Pointer p) {
            super(p);
            read();
        }

        /**
         * Extracts the device instance path from dbcc_name.
         * Format: \\?\USB#VID_xxxx&PID_xxxx#Serial#{GUID}
         * @return Device instance path string, or null if unavailable
         */
        public String getDeviceName() {
            // Read the wide char string from the pointer
            // dbcc_name starts after the header (4 ints + 16 bytes GUID = 32 bytes)
            Pointer p = getPointer();
            int nameOffset = 4 + 4 + 4 + 16; // size + devicetype + reserved + GUID
            return p.getWideString(nameOffset);
        }
    }

    private class DeviceWindowProc implements WindowProc {
        @Override
        public LRESULT callback(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam) {
            if (msg == WM_DEVICECHANGE) {
                int eventType = wParam.intValue();

                if ((eventType == DBT_DEVICEARRIVAL || eventType == DBT_DEVICEREMOVECOMPLETE)
                    && lParam != null) {

                    DEV_BROADCAST_HDR hdr = new DEV_BROADCAST_HDR(lParam.toPointer());

                    if (hdr.dbch_devicetype == DBT_DEVTYP_VOLUME) {
                        DEV_BROADCAST_VOLUME volume = new DEV_BROADCAST_VOLUME(lParam.toPointer());
                        String driveLetter = volume.getDriveLetter();

                        if (driveLetter != null) {
                            final String eventDrive = driveLetter;
                            SwingUtilities.invokeLater(() -> {
                                if (volumeListener != null) {
                                    if (eventType == DBT_DEVICEARRIVAL) {
                                        logger.info("Volume arrived: " + eventDrive);
                                        volumeListener.onVolumeArrival(eventDrive);
                                    } else {
                                        logger.info("Volume removed: " + eventDrive);
                                        volumeListener.onVolumeRemoval(eventDrive);
                                    }
                                }
                            });
                        }
                    } else if (hdr.dbch_devicetype == DBT_DEVTYP_DEVICEINTERFACE) {
                        DEV_BROADCAST_DEVICEINTERFACE device = new DEV_BROADCAST_DEVICEINTERFACE(lParam.toPointer());
                        String dbccName = device.getDeviceName();

                        if (dbccName != null) {
                            final String eventDevice = dbccName;
                            SwingUtilities.invokeLater(() -> {
                                if (deviceListener != null) {
                                    if (eventType == DBT_DEVICEARRIVAL) {
                                        logger.info("Device arrived: " + eventDevice);
                                        deviceListener.onDeviceArrival(eventDevice);
                                    } else {
                                        logger.info("Device removed: " + eventDevice);
                                        deviceListener.onDeviceRemoval(eventDevice);
                                    }
                                }
                            });
                        }
                    }
                }
                return new LRESULT(1);
            }

            if (originalWndProcPtr != 0) {
                Pointer originalProc = new Pointer(originalWndProcPtr);
                return user32.CallWindowProc(originalProc, hwnd, msg, wParam, lParam);
            }

            return user32.DefWindowProc(hwnd, msg, wParam, lParam);
        }
    }

    public void setVolumeListener(VolumeListener listener) {
        this.volumeListener = listener;
    }

    public void setDeviceListener(DeviceListener listener) {
        this.deviceListener = listener;
    }

    public synchronized void start(HWND hwnd) {
        if (running) {
            throw new IllegalStateException("Monitor already running");
        }
        if (hwnd == null) {
            throw new IllegalArgumentException("HWND cannot be null");
        }

        this.hwnd = hwnd;

        DeviceWindowProc newProc = new DeviceWindowProc();
        Pointer newProcPtr = com.sun.jna.CallbackReference.getFunctionPointer(newProc);

        Pointer originalProc = user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, newProcPtr);
        originalWndProcPtr = originalProc != null ? Pointer.nativeValue(originalProc) : 0;

        if (originalWndProcPtr == 0) {
            logger.warning("Failed to subclass window, attempting to continue without subclassing");
        } else {
            logger.fine("Window subclassed successfully");
        }

        // Register for both volume and device interface notifications
        hVolumeNotify = registerVolumeNotification(hwnd);
        if (hVolumeNotify == null) {
            int error = Kernel32.INSTANCE.GetLastError();
            logger.severe("Failed to register for volume notifications, error: " + error);

            if (originalWndProcPtr != 0) {
                user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, new Pointer(originalWndProcPtr));
                originalWndProcPtr = 0;
            }

            this.hwnd = null;
            throw new RuntimeException("Failed to register volume notification, error: " + error);
        }

        hDeviceNotify = registerDeviceInterfaceNotification(hwnd);
        if (hDeviceNotify == null) {
            int error = Kernel32.INSTANCE.GetLastError();
            logger.warning("Failed to register for device interface notifications, error: " + error + " (continuing with volume-only mode)");
            // Don't fail - device interface notification is optional enhancement
        }

        running = true;
        logger.info("USB hot-plug monitor started on hwnd=" + hwnd);
    }

    public synchronized void start(long hwndValue) {
        start(new HWND(Pointer.createConstant(hwndValue)));
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (hVolumeNotify != null) {
            user32.UnregisterDeviceNotification(hVolumeNotify);
            hVolumeNotify = null;
        }

        if (hDeviceNotify != null) {
            user32.UnregisterDeviceNotification(hDeviceNotify);
            hDeviceNotify = null;
        }

        if (hwnd != null && originalWndProcPtr != 0) {
            user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, new Pointer(originalWndProcPtr));
            originalWndProcPtr = 0;
        }

        hwnd = null;

        logger.info("USB hot-plug monitor stopped");
    }

    /**
     * Register for volume-level notifications (drive letters).
     */
    private HDEVNOTIFY registerVolumeNotification(HWND hwnd) {
        DEV_BROADCAST_VOLUME filter = new DEV_BROADCAST_VOLUME();
        filter.dbcv_size = filter.size();
        filter.dbcv_devicetype = DBT_DEVTYP_VOLUME;
        filter.dbcv_reserved = 0;
        filter.dbcv_unitmask = 0;  // Receive notifications for all volumes
        filter.dbcv_flags = 0;     // All volume types

        filter.write();

        return user32.RegisterDeviceNotification(
            hwnd,
            filter,
            User32.DEVICE_NOTIFY_WINDOW_HANDLE
        );
    }

    /**
     * Register for device interface notifications (USB device arrival).
     * This provides device-level events before volume mount.
     */
    private HDEVNOTIFY registerDeviceInterfaceNotification(HWND hwnd) {
        DEV_BROADCAST_DEVICEINTERFACE filter = new DEV_BROADCAST_DEVICEINTERFACE();
        filter.dbcc_size = filter.size();
        filter.dbcc_devicetype = DBT_DEVTYP_DEVICEINTERFACE;
        filter.dbcc_reserved = 0;
        filter.dbcc_classguid = GUID_DEVINTERFACE_DISK;
        // dbcc_name is not used for registration

        filter.write();

        return user32.RegisterDeviceNotification(
            hwnd,
            filter,
            User32.DEVICE_NOTIFY_WINDOW_HANDLE
        );
    }

    public boolean isRunning() {
        return running;
    }
}