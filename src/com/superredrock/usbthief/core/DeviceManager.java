package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;

import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Device Management Service
 * <p>
 * Responsible for device detection, monitoring, and lifecycle event management.
 * Uses polling to detect device changes with configurable interval.
 * <p>
 * Extends Service, lifecycle managed by ServiceManager.
 *
 */
public class DeviceManager extends Service {

    private final Set<Device> devices = Collections.synchronizedSet(new HashSet<>());
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final EventBus eventBus;


    protected static final Logger logger = Logger.getLogger(DeviceManager.class.getName());


    /**
     * Creates a new DeviceManager with the default EventBus.
     * Public constructor for ServiceLoader to instantiate.
     */
    public DeviceManager() {
        this(EventBus.getInstance());
    }

    /**
     * Creates a new DeviceManager with a specific EventBus.
     * Allows for dependency injection and testing.
     *
     * @param eventBus the EventBus to use for event dispatching
     */
    protected DeviceManager(EventBus eventBus) {
        this.eventBus = eventBus;
        // Initialize with existing devices
        for (Path path : fileSystem.getRootDirectories()) {
            devices.add(new Device(path));
        }

        // Set singleton instance
    }




    // ========== AbstractService method implementations ==========

    @Override
    protected ScheduledFuture<?> scheduleTask(ScheduledThreadPoolExecutor scheduler) {
        return scheduler.scheduleWithFixedDelay(
                this,
                ConfigManager.getInstance().get(ConfigSchema.INITIAL_DELAY_SECONDS),
                ConfigManager.getInstance().get(ConfigSchema.DELAY_SECONDS),
                TimeUnit.SECONDS
        );
    }

    @Override
    public String getName() {
        return "DeviceManager";
    }

    @Override
    public String getDescription() {
        return "Device detection and monitoring service";
    }

    @Override
    public void run() {
        if (!checkInterrupt()) {
            detectNewDevices();
        }
        if (!checkInterrupt()) {
            updateExistingDevices();
        }
    }

    @Override
    protected void cleanup() {
        // Clear device collection
        devices.clear();
    }

    @Override
    public String getStatus() {
        return String.format("DeviceManager[%s] - Managing devices: %d", state, devices.size());
    }

    // ========== End of AbstractService method implementations ==========

    /**
     * Detects and adds new devices that have been inserted since last update.
     * Devices in blacklist are silently ignored.
     */
    private void detectNewDevices() {
        for (Path path : fileSystem.getRootDirectories()) {
            if (checkInterrupt()) {
                break;
            }

            Device device = new Device(path);

            // Skip blacklisted devices by serial number
            if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(device.getSerialNumber())) {
                logger.fine("Device blacklisted by serial number, ignoring: " + device.getSerialNumber() + " (" + path + ")");
                continue;
            }

            if (devices.add(device)) {
                onDeviceInserted(device);
            }
        }
    }

    /**
     * Updates existing devices and processes state changes.
     */
    private void updateExistingDevices() {
        synchronized (devices) {
            for (Device device : devices) {
                if (checkInterrupt()) {
                    break;
                }

                Device.DeviceState previousState = device.getState();
                device.update();

                // Process state changes if any occurred
                if (device.isChangeAndReset()) {
                    Device.DeviceState newState = device.getState();
                    onDeviceStateChanged(device, previousState, newState);

                    // Special handling for offline devices
                    if (newState == Device.DeviceState.OFFLINE) {
                        onDeviceRemoved(device);
                    }
                }
            }
        }
    }

    /**
     * Checks if the current thread has been interrupted.
     *
     * @return true if interrupted, false otherwise
     */
    private boolean checkInterrupt() {
        return Thread.currentThread().isInterrupted();
    }

    /**
     * Finds the device that contains the given path.
     *
     * @param path the path to search for
     * @return the device containing the path, or null if not found
     */
    public Device getDevice(Path path) {
        synchronized (devices) {
            for (Device device : devices) {
                if (path.startsWith(device.getRootPath())) {
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * Finds the device associated with the given FileStore.
     * Only returns devices that are not offline.
     *
     * @param store the FileStore to search for
     * @return the device with the given FileStore, or null if not found or offline
     */
    public Device getDevice(FileStore store) {
        if (store == null) {
            return null;
        }

        synchronized (devices) {
            for (Device device : devices) {
                if (store.equals(device.getFileStore())) {
                    if (device.getState() == Device.DeviceState.OFFLINE) {
                        return null;
                    }
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * Returns a read-only view of all devices.
     *
     * @return an immutable snapshot of the current devices
     */
    public Set<Device> getAllDevices() {
        synchronized (devices) {
            return Set.copyOf(devices);
        }
    }

    /**
     * Called when a new device is inserted.
     * Dispatches a DeviceInsertedEvent to the EventBus.
     *
     * @param device the newly inserted device
     */
    protected void onDeviceInserted(Device device) {
        logger.info("Device inserted: " + device);
        eventBus.dispatch(new DeviceInsertedEvent(device));
    }

    /**
     * Called when a device goes offline or is removed.
     * Dispatches a DeviceRemovedEvent to the EventBus.
     *
     * @param device the device that went offline
     */
    protected void onDeviceRemoved(Device device) {
        logger.info("Device removed: " + device);
        eventBus.dispatch(new DeviceRemovedEvent(device));
    }

    /**
     * Called when a device's state changes.
     * Dispatches a DeviceStateChangedEvent to the EventBus.
     *
     * @param device      the device whose state changed
     * @param oldState    the previous state
     * @param newState    the new state
     */
    protected void onDeviceStateChanged(Device device, Device.DeviceState oldState, Device.DeviceState newState) {
        logger.fine(String.format("Device state changed: %s %s -> %s", device, oldState, newState));
        eventBus.dispatch(new DeviceStateChangedEvent(device, oldState, newState));
    }
}
