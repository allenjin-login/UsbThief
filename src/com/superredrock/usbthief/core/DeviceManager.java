package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * USB device and volume management service.
 * <p>
 * Tracks Device (hardware info) and Volume (drive/operational) independently.
 * Device = pure info board (VID/PID/serial), Volume = operational entity (state/copy).
 * No parent-child relationship between them.
 */
public class DeviceManager extends Service implements UsbHotplugMonitor.VolumeListener, UsbHotplugMonitor.DeviceListener {

    private static final Logger logger = Logger.getLogger(DeviceManager.class.getName());

    private static volatile DeviceManager INSTANCE;

    private final UsbHotplugMonitor monitor = new UsbHotplugMonitor();

    // Independent maps — no cross-referencing
    private final ConcurrentHashMap<String, Device> devicesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Volume> volumesMap = new ConcurrentHashMap<>();

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

    @Override
    public void start() {
        super.start();
        if (!monitor.isRunning()) {
            try {
                monitor.start();
                logger.info("UsbHotplugMonitor started successfully");
            } catch (Exception e) {
                logger.severe("Failed to start UsbHotplugMonitor: " + e.getMessage());
            }
        }
    }

    @Override
    protected void tick() {
        volumesMap.forEach((_, volume) -> {
            Volume.VolumeState oldState = volume.getState();
            volume.updateState();
            if (volume.isChangeAndReset()) {
                logger.fine("Volume " + volume.getSerialNumber() + " state changed: " + oldState + " -> " + volume.getState());
                EventBus.getInstance().dispatch(new VolumeStateChangedEvent(volume, oldState, volume.getState()));
            }
        });
    }

    // ========== Device queries ==========

    public Collection<Device> getAllDevices() {
        return Collections.unmodifiableCollection(devicesMap.values());
    }

    public Device getDeviceBySerial(String serial) {
        return devicesMap.get(serial);
    }

    // ========== Volume queries ==========

    public Collection<Volume> getAllVolumes() {
        return Collections.unmodifiableCollection(volumesMap.values());
    }

    public Volume getVolume(Path path) {
        return volumesMap.search(1, (_, volume) ->
                path.equals(volume.getRootPath()) ? volume : null);
    }

    public Volume getVolumeBySerial(String serial) {
        return volumesMap.get(serial);
    }

    // ========== Volume operations ==========

    public void enable(Volume volume) {
        if (volume != null) {
            volume.enable();
            logger.info("Volume enabled: " + volume.getSerialNumber());
        }
    }

    public void disable(Volume volume) {
        if (volume != null) {
            volume.disable();
            logger.info("Volume disabled: " + volume.getSerialNumber());
        }
    }

    public void remove(Volume volume) {
        if (volume != null) {
            volumesMap.remove(volume.getSerialNumber());
            logger.info("Volume removed: " + volume.getSerialNumber());
        }
    }

    public void pauseScanner(Volume volume) {
        if (volume != null) {
            volume.disable();
            logger.fine("Paused volume: " + volume.getSerialNumber());
        }
    }

    public void restartScanner(Volume volume) {
        if (volume != null) {
            volume.enable();
            logger.fine("Resumed volume: " + volume.getSerialNumber());
        }
    }

    // ========== Service lifecycle ==========

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
        return "USB device and volume management service";
    }

    // ========== DeviceListener — hardware device events ==========

    @Override
    public void onDeviceArrival(String dbccName) {
        logger.info("Device interface arrived: " + dbccName);

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

        if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
            logger.fine("Ignoring blacklisted device: " + serial);
            return;
        }
        // Register Device (pure info board) — independent of Volume
        Device device = devicesMap.computeIfAbsent(serial, _ -> {
            Device d = new Device(serial, identity.vid(), identity.pid(), dbccName);
            logger.info("New device registered: " + serial + " (VID:" + identity.vid() + ", PID:" + identity.pid() + ")");
            EventBus.getInstance().dispatch(new NewDeviceJoinedEvent(d));
            return d;
        });
        EventBus.getInstance().dispatch(new DeviceArrivalEvent(device));
    }

    @Override
    public void onDeviceRemoval(String dbccName) {
        logger.info("Device interface removed: " + dbccName);

        DeviceUtils.DeviceIdentity identity = DeviceUtils.parseDeviceInstancePath(dbccName);
        if (identity == null) {
            logger.warning("Could not parse device instance path: " + dbccName);
            return;
        }

        String serial = identity.serial();
        if (serial == null || serial.isEmpty()) {
            return;
        }

        Device device = devicesMap.get(serial);
        if (device != null) {
            EventBus.getInstance().dispatch(new DeviceRemovalEvent(device));
            // Keep device info (it may reconnect later)
        }
    }

    // ========== VolumeListener — drive letter events ==========

    @Override
    public void onVolumeArrival(String driveLetter) {
        logger.info("Volume arrived: " + driveLetter);
        Path rootPath = Path.of(driveLetter + "\\\\");
        String serial;
        serial = DeviceUtils.getVolumeSN(driveLetter);
        if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
            logger.fine("Ignoring blacklisted volume: " + serial);
            return;
        }
        Volume newVolume = new Volume(rootPath, serial);
        newVolume.updateState();

        if (volumesMap.putIfAbsent(serial, newVolume) == null) {
            logger.info("New volume registered: " + serial + " at " + rootPath);
            EventBus.getInstance().dispatch(new VolumeInsertedEvent(newVolume));
        }

    }

    @Override
    public void onVolumeRemoval(String driveLetter) {
        logger.info("Volume removed: " + driveLetter);

        Volume volume = volumesMap.search(1, (_, v) ->
                driveLetter.equals(v.getDriveLetter()) ? v : null);
        if (volume != null) {
            volume.setState(Volume.VolumeState.OFFLINE);
            logger.info("Volume marked OFFLINE: " + driveLetter + " (" + volume.getSerialNumber() + ")");
            EventBus.getInstance().dispatch(new VolumeRemovedEvent(volume));
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
