package com.superredrock.usbthief.platform.stub;

import com.superredrock.usbthief.core.HotplugSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * No-op {@link HotplugSource} for platforms without USB hot-plug notifications (Linux, CI, …).
 *
 * <p>Constructing, starting and stopping this source is completely safe: it never references
 * {@code User32}, {@code Kernel32} or any other native library. Listeners are accepted and
 * ignored, and the degraded mode is logged once per instance so operators can tell why no arrival
 * events show up.</p>
 */
public class NoopHotplugSource implements HotplugSource {

    private static final Logger logger = LogManager.getLogger(NoopHotplugSource.class);

    private volatile VolumeListener volumeListener;
    private volatile DeviceListener deviceListener;
    private volatile boolean running;
    private volatile boolean degradationLogged;

    @Override
    public void setVolumeListener(VolumeListener listener) {
        this.volumeListener = listener;
    }

    @Override
    public void setDeviceListener(DeviceListener listener) {
        this.deviceListener = listener;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        if (!degradationLogged) {
            degradationLogged = true;
            logger.info(
                    "USB hot-plug monitoring is not supported on {}: hot-plug detection is disabled "
                            + "(listeners registered: volume={}, device={})",
                    System.getProperty("os.name", "unknown platform"),
                    volumeListener != null,
                    deviceListener != null);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void registerVolumeHandle(String driveLetter) {
        // No eject detection without a Windows message window.
    }

    @Override
    public void unregisterVolumeHandle(String driveLetter) {
        // No eject detection without a Windows message window.
    }
}
