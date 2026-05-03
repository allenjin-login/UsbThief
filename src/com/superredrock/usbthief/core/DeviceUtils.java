package com.superredrock.usbthief.core;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.nio.file.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for device-related operations.
 *
 * <p>Uses JNA to call Windows API for efficient device information retrieval.
 * Falls back to command-line methods if JNA calls fail.
 */
public class DeviceUtils {

    protected static final Logger logger = LogManager.getLogger(DeviceUtils.class);


    // JNA instances
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    // Maximum length for volume name and filesystem name buffers
    private static final int MAX_VOLUME_NAME_SIZE = 256;
    private static final int MAX_FILESYSTEM_NAME_SIZE = 256;

    /**
     * Gets volume serial number for a drive using JNA (Windows API).
     *
     * <p>Uses {@code GetVolumeInformationW} for efficient retrieval without
     * spawning external processes. Results are cached for performance.
     *
     * @param drive drive letter (e.g., "E:" or "E:\\\")
     * @return volume serial number, or empty string if retrieval fails
     */
    public static String getVolumeSN(String drive) {
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

    /**
     * Gets the root path from a FileStore.
     *
     * @param store the file store
     * @return the root path
     */
    public static Path getRoot(FileStore store){
        String DiskID = store.toString().substring(store.toString().length() - 4);
        return Path.of(DiskID.substring(1,3));
    }

    /**
     * Gets the system root path based on OS.
     *
     * @return the system root path
     */
    public static Path getSystemRoot(){
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return Path.of("C:");
        } else {
            Path path = Path.of("/");
            if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                return path;
            } else if (os.contains("mac")) {
                return path;
            } else {
                return Path.of("");
            }
        }
    }

    /**
     * Constructs a destination path for file copying.
     *
     * @param workPath the working path
     * @param target the target file path
     * @return the destination path
     * @throws IOException if an I/O error occurs
     */
    public static Path getPath(Path workPath, Path target) throws IOException {
        Path root = target.getRoot();
        Path relative = root.relativize(target);
        String storeName = Files.getFileStore(target).name();
        Volume volume = QueueManager.getDeviceManager().getVolume(target);
        return workPath.resolve(storeName + "_" + volume.getSerialNumber()).resolve(relative);
    }

    /**
     * Parses a Windows device instance path to extract device identifiers.
     *
     * <p>Supports multiple device path formats:
     * <ul>
     *   <li>{@code \\?\USB#VID_xxxx&PID_xxxx#Serial#{GUID}}</li>
     *   <li>{@code \\?\USBSTOR#Disk&Ven_Vendor&Prod_Product#Serial&0#{GUID}}</li>
     * </ul>
     *
     * @param dbccName device instance path from DEV_BROADCAST_DEVICEINTERFACE
     * @return DeviceIdentity containing VID, PID, and serial; or null if parsing fails
     */
    public static DeviceIdentity parseDeviceInstancePath(String dbccName) {
        if (dbccName == null || dbccName.isEmpty()) {
            return null;
        }

        // Pattern 1: USB device
        // \\?\USB#VID_1234&PID_5678#ABC123#{GUID}
        java.util.regex.Pattern usbPattern = java.util.regex.Pattern.compile(
            "USB#VID_([0-9A-Fa-f]{4})&PID_([0-9A-Fa-f]{4})#([^#]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher usbMatcher = usbPattern.matcher(dbccName);
        if (usbMatcher.find()) {
            return new DeviceIdentity(
                usbMatcher.group(1).toUpperCase(),  // VID
                usbMatcher.group(2).toUpperCase(),  // PID
                usbMatcher.group(3)                 // Serial
            );
        }

        // Pattern 2: USBSTOR device (mass storage)
        // \\?\USBSTOR#Disk&Ven_Kingston&Prod_DataTraveler#ABC123&0#{GUID}
        java.util.regex.Pattern storPattern = java.util.regex.Pattern.compile(
            "USBSTOR#Disk&Ven_([^&]*)&Prod_([^#]+)#([^&]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher storMatcher = storPattern.matcher(dbccName);
        if (storMatcher.find()) {
            // USBSTOR path serial is in the 3rd capture group
            return new DeviceIdentity(
                null,                               // VID (not available)
                storMatcher.group(2),               // Product
                storMatcher.group(3)                // Serial
            );
        }

        logger.warn("Failed to parse device path: {}", dbccName);
        return null;
    }

    /**
     * Immutable record containing device identity information.
     *
     * @param vid    Vendor ID (4-digit hex for USB devices, null for USBSTOR)
     * @param pid    Product ID or product name
     * @param serial Device serial number (used as unique identifier)
     */
    public static final class DeviceIdentity {
        private final String vid;
        private final String pid;
        private final String serial;

        public DeviceIdentity(String vid, String pid, String serial) {
            this.vid = vid;
            this.pid = pid;
            this.serial = serial;
        }

        public String vid() { return vid; }
        public String pid() { return pid; }
        public String serial() { return serial; }

        /**
         * Returns the unique identifier for this device.
         *
         * @return the serial number as the unique identifier
         */
        public String getIdentifier() {
            return serial;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DeviceIdentity)) return false;
            DeviceIdentity that = (DeviceIdentity) o;
            return java.util.Objects.equals(vid, that.vid) &&
                   java.util.Objects.equals(pid, that.pid) &&
                   java.util.Objects.equals(serial, that.serial);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(vid, pid, serial);
        }

        @Override
        public String toString() {
            return "DeviceIdentity[vid=" + vid + ", pid=" + pid + ", serial=" + serial + "]";
        }
    }
}
