package com.superredrock.usbthief.platform;

import com.superredrock.usbthief.core.HotplugSource;
import com.superredrock.usbthief.core.NativeDiskQuery;
import com.superredrock.usbthief.core.VolumeInfoProvider;

/**
 * Assembly point for platform-bound services.
 *
 * <p>Business code never references a Windows (JNA) class directly; it asks this factory for the
 * interface implementation that matches the running platform. The Windows classes are only
 * <em>instantiated</em> when {@link #isWindows()} is true, and none of them resolves a native
 * library from a field initializer or static block, so simply constructing a platform service is
 * always safe.</p>
 */
public final class Platform {

    private static final boolean WINDOWS = detectWindows();

    private Platform() {
    }

    /**
     * @return {@code true} when running on a Windows host (based on {@code os.name})
     */
    public static boolean isWindows() {
        return WINDOWS;
    }

    /**
     * Creates a new hot-plug source. Monitors are stateful, so callers own their instance.
     *
     * @return a Windows message-window monitor on Windows, a no-op monitor elsewhere
     */
    public static HotplugSource hotplugSource() {
        if (WINDOWS) {
            return new com.superredrock.usbthief.platform.win.UsbHotplugMonitor();
        }
        return new com.superredrock.usbthief.platform.stub.NoopHotplugSource();
    }

    /**
     * @return the volume-information provider for this platform
     */
    public static VolumeInfoProvider volumeInfoProvider() {
        return VolumeInfoProviderHolder.INSTANCE;
    }

    /**
     * @return the physical-disk query for this platform
     */
    public static NativeDiskQuery diskQuery() {
        return DiskQueryHolder.INSTANCE;
    }

    private static boolean detectWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().startsWith("windows");
    }

    // Lazily initialised holders: the platform-specific classes are only loaded (and their
    // constructors only run) on first use, never while Platform itself is initialised.
    private static final class VolumeInfoProviderHolder {
        private static final VolumeInfoProvider INSTANCE =
                WINDOWS ? new com.superredrock.usbthief.platform.win.WindowsVolumeInfoProvider()
                        : new com.superredrock.usbthief.platform.stub.NoopVolumeInfoProvider();
    }

    private static final class DiskQueryHolder {
        private static final NativeDiskQuery INSTANCE =
                WINDOWS ? new com.superredrock.usbthief.platform.win.WindowsNativeDiskQuery()
                        : new com.superredrock.usbthief.platform.stub.NoopNativeDiskQuery();
    }
}
