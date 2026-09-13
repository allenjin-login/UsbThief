package com.superredrock.usbthief.core;

import java.io.IOException;
import java.nio.file.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for device-related operations.
 *
 * <p>Platform-neutral helpers only: path arithmetic, device instance path parsing and the OS root
 * lookup. Everything that needs a Windows API now lives behind the platform interfaces in
 * {@link VolumeInfoProvider} / {@link NativeDiskQuery} and is implemented in
 * {@code com.superredrock.usbthief.platform.win}.
 */
public class DeviceUtils {

    protected static final Logger logger = LogManager.getLogger(DeviceUtils.class);

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
        return getPath(workPath, target, null);
    }

    /**
     * Constructs a destination path for file copying, reusing an already known volume.
     *
     * <p>When the caller already holds the {@link Volume} that owns {@code target}, the volume
     * name and serial number come from it directly. That removes a
     * {@code Files.getFileStore(target).name()} call (tens of microseconds per file on Linux,
     * a native call on Windows) plus a {@link DeviceManager} lookup from the copy hot path.
     * Callers without a volume (for example ad-hoc copies) fall back to the previous behaviour.</p>
     *
     * @param workPath the working path
     * @param target the target file path
     * @param volume the volume that owns {@code target}, or {@code null} to look it up
     * @return the destination path
     * @throws IOException if an I/O error occurs while resolving the file store
     */
    public static Path getPath(Path workPath, Path target, Volume volume) throws IOException {
        Path root = target.getRoot();
        Path relative = root.relativize(target);

        String storeName = null;
        if (volume != null) {
            String volumeName = volume.getVolumeName();
            if (volumeName != null && !volumeName.isEmpty()) {
                storeName = volumeName;
            }
        }
        if (storeName == null) {
            storeName = Files.getFileStore(target).name();
        }

        Volume owner = volume != null ? volume : QueueManager.getDeviceManager().getVolume(target);
        return workPath.resolve(storeName + "_" + owner.getSerialNumber()).resolve(relative);
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
    public record DeviceIdentity(
        String vid,
        String pid,
        String serial
    ) {
        /**
         * Returns the unique identifier for this device.
         *
         * @return the serial number as the unique identifier
         */
        public String getIdentifier() {
            return serial;
        }
    }
}
