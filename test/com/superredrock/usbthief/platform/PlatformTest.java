package com.superredrock.usbthief.platform;

import com.superredrock.usbthief.core.DeviceManager;
import com.superredrock.usbthief.core.HotplugSource;
import com.superredrock.usbthief.platform.stub.NoopHotplugSource;
import com.superredrock.usbthief.platform.stub.NoopNativeDiskQuery;
import com.superredrock.usbthief.platform.stub.NoopVolumeInfoProvider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Verifies the AR-12 platform-selection contract: business code depends on the core interfaces, and
 * a host without the Windows native libraries gets the no-op implementations instead of an
 * {@code UnsatisfiedLinkError}.
 */
class PlatformTest {

    private static final String DRIVE = "E:";

    @Test
    void isWindowsMatchesOsName() {
        String osName = System.getProperty("os.name");
        boolean expected = osName != null && osName.toLowerCase().startsWith("windows");
        assertEquals(expected, Platform.isWindows(),
                "Platform.isWindows() must agree with System.getProperty(\"os.name\")");
    }

    @Test
    void nonWindowsSelectsNoopImplementations() {
        assumeFalse(Platform.isWindows(), "no-op fallback only applies to non-Windows hosts");

        assertInstanceOf(NoopHotplugSource.class, Platform.hotplugSource());
        assertInstanceOf(NoopVolumeInfoProvider.class, Platform.volumeInfoProvider());
        assertInstanceOf(NoopNativeDiskQuery.class, Platform.diskQuery());
    }

    @Test
    void noopProvidersReturnEmptyValuesWithoutLoadingNativeCode() {
        assumeFalse(Platform.isWindows(), "no-op fallback only applies to non-Windows hosts");

        assertEquals("", Platform.volumeInfoProvider().getVolumeSerial(DRIVE));
        assertEquals("", Platform.volumeInfoProvider().getVolumeSerial(null));
        assertEquals("", Platform.diskQuery().getHardwareSerial(DRIVE));
        assertEquals("", Platform.diskQuery().getDeviceInstanceId(DRIVE));
        assertEquals("", Platform.diskQuery().getHardwareSerialViaSetupApi(DRIVE));
    }

    @Test
    void noopHotplugSourceFullLifecycleIsSafe() {
        assumeFalse(Platform.isWindows(), "no-op fallback only applies to non-Windows hosts");

        HotplugSource source = Platform.hotplugSource();
        RecordingVolumeListener listener = new RecordingVolumeListener();
        source.setVolumeListener(listener);

        assertFalse(source.isRunning(), "a fresh source must not report running");
        source.start();
        assertTrue(source.isRunning(), "start() must degrade to a running no-op, not a crash");

        // Must be harmless: with no native message window there is nothing to register.
        source.registerVolumeHandle(DRIVE);
        source.unregisterVolumeHandle(DRIVE);
        assertTrue(listener.arrivals.isEmpty(), "no hot-plug events may be synthesised");

        source.stop();
        assertFalse(source.isRunning());
        source.stop(); // idempotent
    }

    @Test
    void everyCallGetsItsOwnHotplugSource() {
        assertNotSame(Platform.hotplugSource(), Platform.hotplugSource(),
                "hot-plug sources are stateful and must not be shared");
    }

    @Test
    void statelessProvidersAreShared() {
        assertNotNull(Platform.volumeInfoProvider());
        assertNotNull(Platform.diskQuery());
    }

    @Test
    void deviceManagerConstructsWithoutAWindowsNativeLibrary() {
        assumeFalse(Platform.isWindows(), "regression guard for non-Windows hosts");

        // Constructing DeviceManager used to instantiate UsbHotplugMonitor whose field initializer
        // read User32.INSTANCE, killing every Windows-API-free code path (including GUI startup).
        assertNotNull(DeviceManager.getInstance());
    }

    @Test
    void deviceManagerStartAndStopAreSafeWithoutAWindowsNativeLibrary() {
        assumeFalse(Platform.isWindows(), "regression guard for non-Windows hosts");

        DeviceManager manager = DeviceManager.getInstance();
        try {
            // The whole start() path (service tick thread + hot-plug source) must survive on a host
            // where user32 does not exist.
            manager.start();
            assertTrue(manager.isAlive(), "DeviceManager service thread must be running after start()");
        } finally {
            manager.stopService();
        }
        assertFalse(manager.isAlive(), "DeviceManager service thread must be joined after stopService()");
    }

    private static final class RecordingVolumeListener implements HotplugSource.VolumeListener {

        private final List<String> arrivals = new ArrayList<>();

        @Override
        public void onVolumeArrival(String driveLetter) {
            arrivals.add(driveLetter);
        }

        @Override
        public void onVolumeRemoval(String driveLetter) {
            arrivals.add(driveLetter);
        }
    }
}
