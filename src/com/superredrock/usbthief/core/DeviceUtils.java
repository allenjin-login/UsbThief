package com.superredrock.usbthief.core;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Utility class for device-related operations.
 *
 * <p>Uses JNA to call Windows API for efficient device information retrieval.
 * Falls back to command-line methods if JNA calls fail.
 */
public class DeviceUtils {

    protected static final Logger logger = Logger.getLogger(DeviceUtils.class.getName());

    private static final Map<String, String> serialNumberCache = new ConcurrentHashMap<>();

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

        // Normalize drive path to "X:\\" format required by Windows API
        String normalizedDrive = normalizeDrivePath(drive);

        // Check cache first
        String cacheKey = normalizedDrive.substring(0, 2); // "E:"
        String cached = serialNumberCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Try JNA method first (fast, no process spawn)
        String serial = getSerialNumberViaJna(normalizedDrive);

        // Fallback to legacy methods if JNA fails
        if (serial.isEmpty()) {
            logger.fine("JNA method failed for drive: " + normalizedDrive + ", trying fallback methods");
            serial = getSerialNumberViaWmic(cacheKey);

            if (serial.isEmpty()) {
                serial = getSerialNumberViaVbs(cacheKey);
            }
        }

        // Cache result
        if (!serial.isEmpty()) {
            serialNumberCache.put(cacheKey, serial);
        }

        return serial;
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
                logger.fine("GetVolumeInformation failed for " + drivePath + ", error: " + error);
                return "";
            }

            // Convert serial number to hex string (8 digits, zero-padded)
            int serial = volumeSerialNumber.getValue();
            String serialHex = String.format("%08X", serial);

            logger.fine("Got serial number via JNA for " + drivePath + ": " + serialHex);
            return serialHex;

        } catch (Exception e) {
            logger.fine("JNA method exception for drive " + drivePath + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Gets serial number using wmic command (fallback method).
     *
     * @param drive normalized drive letter (e.g., "E:")
     * @return serial number or empty string if failed
     */
    private static String getSerialNumberViaWmic(String drive) {
        StringBuilder result = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try {
            // Try different wmic command formats
            String[] commands = {
                "wmic logicaldisk where \"DeviceID='" + drive + "'\" get VolumeSerialNumber /value",
                "wmic logicaldisk where \"DeviceID='" + drive + "'\" get VolumeSerialNumber",
                "wmic logicaldisk get Name,VolumeSerialNumber"
            };

            for (String cmd : commands) {
                Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", cmd});

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("VolumeSerialNumber=")) {
                        result.append(line.substring("VolumeSerialNumber=".length()).trim());
                        break;
                    } else if (!line.isEmpty() && !line.startsWith("VolumeSerialNumber") && !line.startsWith("Node") && !line.startsWith("Name")) {
                        // Try to parse tab-separated format
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2 && parts[0].equals(drive)) {
                            result.append(parts[1]);
                            break;
                        }
                    }
                }

                // Read error output
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }

                reader.close();
                errorReader.close();

                int exitCode = process.waitFor();

                if (exitCode == 0 && !result.toString().isEmpty()) {
                    return result.toString();
                }

                logger.fine("wmic command failed: " + cmd + " - exit code: " + exitCode);
                if (!errorOutput.toString().isEmpty()) {
                    logger.fine("wmic error output: " + errorOutput);
                }

                // Reset for next attempt
                result.setLength(0);
                errorOutput.setLength(0);
            }

        } catch (IOException | InterruptedException e) {
            logger.fine("wmic exception: " + e.getMessage());
        }

        return "";
    }

    /**
     * Gets serial number using VBS script (fallback method).
     *
     * @param drive normalized drive letter (e.g., "E:")
     * @return serial number or empty string if failed
     */
    private static String getSerialNumberViaVbs(String drive) {
        StringBuilder result = new StringBuilder();
        Path vbsPath = null;
        Process process = null;

        try {
            vbsPath = Files.createTempFile("getsn", ".vbs");

            String vbs = "Set objFSO = CreateObject(\"Scripting.FileSystemObject\")\n"
                    + "Set colDrives = objFSO.Drives\n"
                    + "On Error Resume Next\n"
                    + "Set objDrive = colDrives.item(\"" + drive + "\")\n"
                    + "If Err.Number <> 0 Then\n"
                    + "  Wscript.Echo \"\"\n"
                    + "Else\n"
                    + "  Wscript.Echo objDrive.SerialNumber\n"
                    + "End If";

            Files.writeString(vbsPath, vbs);

            process = Runtime.getRuntime().exec("cscript //NoLogo \"" + vbsPath + "\"");

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warning("VBS script failed with exit code: " + exitCode + " for drive: " + drive);
            }

        } catch (IOException | InterruptedException e) {
            logger.warning("VBS method failed for drive: " + drive + " - " + e.getMessage());
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
            if (vbsPath != null) {
                try {
                    Files.deleteIfExists(vbsPath);
                } catch (IOException e) {
                    logger.fine("Failed to delete temp file: " + vbsPath);
                }
            }
        }

        return result.toString().trim();
    }

    /**
     * Clears the serial number cache.
     * Useful for testing or when drives are hot-swapped.
     */
    public static void clearSerialNumberCache() {
        serialNumberCache.clear();
        logger.fine("Serial number cache cleared");
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
     * Gets a list of USB disk file stores.
     * Identifies USB drives by their filesystem type (exFAT or FAT32).
     *
     * @return list of USB disk file stores
     */
    public static List<FileStore> getUsbDisk(){
        ArrayList<FileStore> list = new ArrayList<>();
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            if (store.type().equals("exFAT") || store.type().equals("FAT32")) {
                list.add(store);
            }
        }
        return list;
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
            "USBSTOR#Disk&Ven_([^&]+)&Prod_([^#]+)#([^&]+)",
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

        logger.warning("Failed to parse device path: " + dbccName);
        return null;
    }

    /**
     * Gets hardware serial number from a volume using DeviceIoControl API.
     *
     * <p>This method retrieves the hardware serial number of the physical device
     * that contains the specified volume. It uses Windows DeviceIoControl API
     * instead of WMI or spawning external processes.
     *
     * <p>Fallback chain:
     * <ol>
     *   <li>Try DeviceIoControl with PhysicalDrive handle (requires admin)</li>
     *   <li>Fallback to DeviceIoControl with volume handle</li>
     *   <li>Final fallback to volume serial number via GetVolumeInformation</li>
     * </ol>
     *
     * @param driveLetter the drive letter (e.g., "E:" or "E:\\")
     * @return hardware serial number, or empty string if retrieval fails
     */
    public static String getHardwareSerialFromVolume(String driveLetter) {
        if (driveLetter == null || driveLetter.isEmpty()) {
            return "";
        }

        // Try DeviceIoControl first (hardware serial)
        String serial = DiskQueryUtil.getHardwareSerial(driveLetter);
        if (!serial.isEmpty()) {
            return serial;
        }

        // Fallback to volume serial number (software serial)
        logger.fine("DeviceIoControl failed for " + driveLetter + ", falling back to volume serial number");
        return getVolumeSN(driveLetter);
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
