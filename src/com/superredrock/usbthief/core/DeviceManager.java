package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;

import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static com.superredrock.usbthief.core.DeviceUtils.getHardDiskSN;

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

    private static final String PREF_KEY_KNOWN_SERIALS = "knownDeviceSerials";
    private static final String SERIAL_DELIMITER = "::";

    private final Set<Device> devices = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> knownSerials = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, String> knownVolumeNames = new ConcurrentHashMap<>();
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final EventBus eventBus;
    private final Preferences prefs = Preferences.userNodeForPackage(DeviceManager.class);


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
        loadKnownSerials();
        createGhostDevices();
        initializeExistingDevices();
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

            String serial = getHardDiskSN(path.toString());

            if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
                logger.fine("Device blacklisted by serial number, ignoring: " + serial + " (" + path + ")");
                continue;
            }

            Device existing = findDeviceBySerial(serial);

            if (existing != null) {
                if (existing.isGhost()) {
                    existing.merge(path);
                    onDeviceInserted(existing);
                }
            } else {
                Device device = new Device(path);
                devices.add(device);
                addKnownSerial(serial, device.getVolumeName());
                onNewDeviceJoined(device);
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

    private void loadKnownSerials() {
        String stored = prefs.get(PREF_KEY_KNOWN_SERIALS, "");
        if (!stored.isEmpty()) {
            // Split by device delimiter (||)
            String[] devices = stored.split("\\|\\|");
            for (String device : devices) {
                // Each device is "serial::name"
                String[] parts = device.split(SERIAL_DELIMITER, 2);
                if (parts.length >= 1) {
                    String serial = parts[0].trim();
                    // Filter out empty and invalid serials
                    if (!serial.isEmpty() && !serial.equals(SERIAL_DELIMITER)) {
                        knownSerials.add(serial);
                        if (parts.length >= 2) {
                            knownVolumeNames.put(serial, parts[1].trim());
                        }
                    }
                }
            }
        }
        logger.info("Loaded " + knownSerials.size() + " known device serials");
    }

    private void saveKnownSerials() {
        String[] devices = knownSerials.stream()
                .map(serial -> {
                    String name = knownVolumeNames.getOrDefault(serial, "");
                    return serial + SERIAL_DELIMITER + name;
                })
                .toArray(String[]::new);
        String stored = String.join("||", devices);
        prefs.put(PREF_KEY_KNOWN_SERIALS, stored);
    }

    private void addKnownSerial(String serial, String volumeName) {
        // Clean serial number and validate
        if (serial != null) {
            serial = serial.trim();
            // Filter out empty strings and delimiter-only strings
            if (!serial.isEmpty() && !serial.equals(SERIAL_DELIMITER) && knownSerials.add(serial)) {
                if (volumeName != null && !volumeName.trim().isEmpty()) {
                    knownVolumeNames.put(serial, volumeName.trim());
                }
                saveKnownSerials();
            }
        }
    }

    /**
     * Clears all stored device serials from preferences.
     * Useful for resetting corrupted data due to delimiter changes.
     */
    public void clearKnownSerials() {
        prefs.remove(PREF_KEY_KNOWN_SERIALS);
        knownSerials.clear();
        knownVolumeNames.clear();
        logger.info("Cleared all known device serials");
    }

    private void createGhostDevices() {
        for (String serial : knownSerials) {
            String volumeName = knownVolumeNames.get(serial);
            devices.add(new Device(serial, volumeName));
        }
    }

    private void initializeExistingDevices() {
        for (Path path : fileSystem.getRootDirectories()) {
            String serial = getHardDiskSN(path.toString());

            if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
                continue;
            }

            Device ghostDevice = findGhostDevice(serial);

            if (ghostDevice != null) {
                ghostDevice.merge(path);
            } else {
                Device device = new Device(path);
                if (!knownSerials.contains(serial)) {
                    addKnownSerial(serial, device.getVolumeName());
                    onNewDeviceJoined(device);
                }
                devices.add(device);
            }
        }
    }

    private Device findGhostDevice(String serial) {
        synchronized (devices) {
            for (Device device : devices) {
                if (device.isGhost() && serial.equals(device.getSerialNumber())) {
                    return device;
                }
            }
        }
        return null;
    }

    private Device findDeviceBySerial(String serial) {
        synchronized (devices) {
            for (Device device : devices) {
                if (serial.equals(device.getSerialNumber())) {
                    return device;
                }
            }
        }
        return null;
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
     * Updates the volume name for a device and persists it.
     *
     * @param serial the serial number of the device
     * @param volumeName the new volume name
     */
    public void updateDeviceVolumeName(String serial, String volumeName) {
        if (serial != null && !serial.isEmpty() && knownSerials.contains(serial)) {
            if (volumeName != null && !volumeName.trim().isEmpty()) {
                knownVolumeNames.put(serial, volumeName.trim());
                saveKnownSerials();
                Device device = findDeviceBySerial(serial);
                if (device != null && !device.isGhost()) {
                    device.setVolumeName(volumeName.trim());
                }
            }
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

    protected void onNewDeviceJoined(Device device) {
        logger.info("New device joined (first time): " + device.getSerialNumber());
        eventBus.dispatch(new NewDeviceJoinedEvent(device));
    }

    /**
     * Completely removes a device from the device manager.
     * This will remove the device from the device list, known serials, and persistent storage.
     *
     * @param serial the serial number of the device to remove
     * @return true if the device was found and removed, false otherwise
     */
    public boolean removeDeviceCompletely(String serial) {
        if (serial == null || serial.isEmpty()) {
            return false;
        }

        Device deviceToRemove = null;
        synchronized (devices) {
            for (Device device : devices) {
                if (serial.equals(device.getSerialNumber())) {
                    deviceToRemove = device;
                    break;
                }
            }

            if (deviceToRemove != null) {
                devices.remove(deviceToRemove);
            }
        }

        if (deviceToRemove != null) {
            knownSerials.remove(serial);
            knownVolumeNames.remove(serial);
            saveKnownSerials();
            logger.info("Device completely removed: " + serial);
            return true;
        }

        return false;
    }
}
