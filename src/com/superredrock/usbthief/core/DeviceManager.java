package com.superredrock.usbthief.core;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;


import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Device management service.
 * <p>
 * Manages USB device detection, state tracking, and lifecycle using UsbHotplugMonitor
 * for real-time device arrival/removal detection.
 */
public class DeviceManager extends Service implements UsbHotplugMonitor.VolumeListener, UsbHotplugMonitor.DeviceListener {

    private static final Logger logger = Logger.getLogger(DeviceManager.class.getName());

    private static volatile DeviceManager INSTANCE;

    private final UsbHotplugMonitor monitor = new UsbHotplugMonitor();
    private final ConcurrentHashMap<String, Device> devicesMap = new ConcurrentHashMap<>();
    private HWND hwnd;


    private DeviceManager() {
        monitor.setVolumeListener(this);
        monitor.setDeviceListener(this);
    }

    public static DeviceManager getInstance() {
        if (INSTANCE == null) {
            synchronized (DeviceManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeviceManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Sets the window handle for UsbHotplugMonitor and starts monitoring.
     * Must be called after the main window is created.
     *
     * @param hwnd the window handle
     */
    public void setHwnd(HWND hwnd) {
        this.hwnd = hwnd;
    }

    /**
     * Sets the window handle using long value.
     *
     * @param hwndValue the window handle value
     */
    public void setHwnd(long hwndValue) {
        setHwnd(new HWND(Pointer.createConstant(hwndValue)));
    }

    @Override
    public void start() {
        super.start();
        if (hwnd != null && !monitor.isRunning()) {
            try {
                monitor.start(hwnd);
                logger.info("UsbHotplugMonitor started successfully");
            } catch (Exception e) {
                logger.severe("Failed to start UsbHotplugMonitor: " + e.getMessage());
            }
        }
    }

    @Override
    protected void tick() {
        for (Device device : devicesMap.values()) {
            Device.DeviceState oldState = device.getState();
            device.updateState();
            if (device.isChangeAndReset()) {
                logger.fine("Device " + device.getSerialNumber() + " state changed: " + oldState + " -> " + device.getState());
                EventBus.getInstance().dispatch(new DeviceStateChangedEvent(device, oldState, device.getState()));
            }
        }
    }

    /**
     * Finds a device matching the given predicate.
     *
     * @param predicate the predicate to match
     * @return the matching device, or null if not found
     */
    private Device findDevice(Predicate<Device> predicate) {
        for (Device device : devicesMap.values()) {
            if (predicate.test(device)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Gets a device by its root path.
     *
     * @param path the root path
     * @return the device, or null if not found
     */
    public Device getDevice(Path path) {
        return findDevice(device -> path.equals(device.getRootPath()));
    }

    /**
     * Gets a device by its FileStore.
     *
     * @param store the FileStore
     * @return the device, or null if not found
     */
    public Device getDevice(FileStore store) {
        return findDevice(device -> store.equals(device.getFileStore()));
    }

    /**
     * Gets a device by its serial number.
     *
     * @param serialNumber the serial number
     * @return the device, or null if not found
     */
    public Device getDeviceBySerial(String serialNumber) {
        return devicesMap.get(serialNumber);
    }

    /**
     * Gets all devices.
     *
     * @return a set of all devices
     */
    public Set<Device> getAllDevices() {
        return Collections.unmodifiableSet((Set<? extends Device>) devicesMap.values());
    }

    /**
     * Enables a device for scanning.
     *
     * @param device the device to enable
     */
    public void enable(Device device) {
        if (device != null) {
            device.enable();
            logger.info("Device enabled: " + device.getSerialNumber());
        }
    }

    /**
     * Disables a device from scanning.
     *
     * @param device the device to disable
     */
    public void disable(Device device) {
        if (device != null) {
            device.disable();
            logger.info("Device disabled: " + device.getSerialNumber());
        }
    }

    /**
     * Removes a device from management.
     *
     * @param device the device to remove
     */
    public void remove(Device device) {
        if (device != null) {
            devicesMap.remove(device.getSerialNumber());
            logger.info("Device removed: " + device.getSerialNumber());
        }
    }

    @Override
    protected long getTickIntervalMs() {
        return 2000;
    }

    @Override
    public String getServiceName() {
        return "DeviceManager";
    }

    @Override
    public String getDescription() {
        return "USB device detection and state management service";
    }

    @Override
    public void onVolumeArrival(String driveLetter) {
        logger.info("Volume arrived: " + driveLetter);
        Path rootPath = Path.of(driveLetter + "\\\\");
        
        if (!Files.isDirectory(rootPath)) {
            logger.warning("Volume path is not a directory: " + rootPath);
            return;
        }
        
        // Get hardware serial number (not volume serial)
        String serial = DeviceUtils.getHardwareSerialFromVolume(rootPath.toString());
        if (serial.isEmpty()) {
            logger.warning("Could not get hardware serial number for volume: " + rootPath);
            return;
        }
        
        // Check blacklist
        if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
            logger.fine("Ignoring blacklisted device: " + serial);
            return;
        }

        // Check if device already exists
        Device existing = devicesMap.get(serial);
        if (existing != null) {
            // Device exists - add this volume to it
            Volume volume = new Volume(rootPath);
            existing.addVolume(volume);
            existing.updateState();
            logger.info("Device volume added: " + serial + " at " + rootPath);
        }
    }

    @Override
    public void onVolumeRemoval(String driveLetter) {
        logger.info("Volume removed: " + driveLetter);

        // Find device containing this volume and remove it
        for (Device device : devicesMap.values()) {
            Volume volume = device.getVolume(driveLetter);
            if (volume != null) {
                device.removeVolume(driveLetter);
                logger.info("Volume removed from device: " + driveLetter + " (" + device.getSerialNumber() + ")");
                
                // If device has no more volumes, set to OFFLINE
                if (!device.hasVolumes()) {
                    device.setState(Device.DeviceState.OFFLINE);
                }
            }
        }
    }

    @Override
    public void onDeviceArrival(String dbccName) {
        logger.info("Device interface arrived: " + dbccName);
        
        // Parse device instance path to extract serial number
        DeviceUtils.DeviceIdentity identity = DeviceUtils.parseDeviceInstancePath(dbccName);
        if (identity == null) {
            logger.warning("Could not parse device instance path: " + dbccName);
            return;
        }
        
        String serial = identity.serial();
        if (serial == null || serial.isEmpty()) {
            logger.warning("No serial number in device path: " + dbccName);
            return;
        }
        
        // Check blacklist
        if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
            logger.fine("Ignoring blacklisted device: " + serial);
            return;
        }
        
        // Create new empty device (no volumes yet - will be added when volume mounts)
        Device newDevice = new Device(serial);
        newDevice.setState(Device.DeviceState.OFFLINE);
        
        devicesMap.putIfAbsent(serial,newDevice);
        logger.info("New device interface detected: " + serial + " (VID:" + identity.vid() + ", PID:" + identity.pid() + ")");
        EventBus.getInstance().dispatch(new NewDeviceJoinedEvent(newDevice));
        EventBus.getInstance().dispatch(new DeviceInsertedEvent(newDevice));
    }

    @Override
    public void onDeviceRemoval(String dbccName) {
        logger.info("Device interface removed: " + dbccName);
        
        // Parse device instance path to extract serial number
        DeviceUtils.DeviceIdentity identity = DeviceUtils.parseDeviceInstancePath(dbccName);
        if (identity == null) {
            logger.warning("Could not parse device instance path: " + dbccName);
            return;
        }
        
        String serial = identity.serial();
        if (serial == null || serial.isEmpty()) {
            logger.warning("No serial number in device path: " + dbccName);
            return;
        }
        
        // Find device by serial and mark as UNAVAILABLE
        Device device = devicesMap.get(serial);
        if (device != null) {
            device.setState(Device.DeviceState.OFFLINE);
            logger.info("Device marked OFFLINE: " + serial);
            EventBus.getInstance().dispatch(new DeviceRemovedEvent(device));
        }
    }
    /**
     * Pauses operations for the given device.
     * Used by SnifferLifecycleManager when storage is full.
     *
     * @param device the device to pause
     */
    public void pauseScanner(Device device) {
        if (device != null) {
            device.disable();
            logger.fine("Paused device: " + device.getSerialNumber());
        }
    }

    /**
     * Resumes operations for the given device.
     * Used by SnifferLifecycleManager after storage delay.
     *
     * @param device the device to resume
     */
    public void restartScanner(Device device) {
        if (device != null) {
            device.enable();
            logger.fine("Resumed device: " + device.getSerialNumber());
        }
    }

    @Override
    protected void cleanup() {
        if (monitor.isRunning()) {
            monitor.stop();
            logger.info("UsbHotplugMonitor stopped");
        }
    }

}
