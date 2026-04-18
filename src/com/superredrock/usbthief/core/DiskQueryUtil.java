package com.superredrock.usbthief.core;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for querying disk hardware information using Windows DeviceIoControl API.
 *
 * <p>Uses direct kernel-level API calls instead of WMI for better performance:
 * <ul>
 *   <li>IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS - maps drive letter to physical disk</li>
 *   <li>IOCTL_STORAGE_QUERY_PROPERTY - gets hardware serial number</li>
 *   <li>SetupAPI - enumerates disk devices and gets instance IDs</li>
 * </ul>
 *
 * <p>This approach is faster and lighter than WMI, with no COM initialization required.
 */
public final class DiskQueryUtil {

    private static final Logger logger = LogManager.getLogger(DiskQueryUtil.class);

    // DeviceIoControl codes
    private static final int IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS = 0x00560000;
    private static final int IOCTL_STORAGE_QUERY_PROPERTY = 0x002D1400;
    private static final int IOCTL_STORAGE_GET_DEVICE_NUMBER = 0x0002D1080;

    // Storage property query types
    private static final int PropertyStandardQuery = 0;
    private static final int StorageDeviceProperty = 0;

    // Win32 constants
    private static final int GENERIC_READ = 0x80000000;
    private static final int FILE_SHARE_READ = 0x00000001;
    private static final int FILE_SHARE_WRITE = 0x00000002;
    private static final int OPEN_EXISTING = 3;
    private static final int FILE_ATTRIBUTE_NORMAL = 0x80;

    // SetupAPI constants
    private static final int DIGCF_PRESENT = 0x00000002;
    private static final int DIGCF_DEVICEINTERFACE = 0x00000010;

    // GUID for disk device interface class
    private static final Guid.GUID GUID_DEVINTERFACE_DISK = new Guid.GUID("{53F56307-B6BF-11D0-94F2-00A0C91EFB8B}");

    private DiskQueryUtil() {}

    /**
     * Gets the hardware serial number of the physical disk containing the specified volume.
     *
     * <p>Uses DeviceIoControl API chain:
     * <ol>
     *   <li>Open volume device (\\.\E:)</li>
     *   <li>IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS → get physical disk number</li>
     *   <li>Open physical disk (\\.\PhysicalDriveN)</li>
     *   <li>IOCTL_STORAGE_QUERY_PROPERTY → get STORAGE_DEVICE_DESCRIPTOR</li>
     *   <li>Extract SerialNumber from descriptor</li>
     * </ol>
     *
     * @param driveLetter the drive letter (e.g., "E:" or "E:\\")
     * @return hardware serial number, or empty string if not found or query fails
     */
    public static String getHardwareSerial(String driveLetter) {
        if (driveLetter == null || driveLetter.isEmpty()) {
            return "";
        }

        String normalized = normalizeDriveLetter(driveLetter);
        if (normalized.isEmpty()) {
            return "";
        }

        WinNT.HANDLE hVolume = null;
        WinNT.HANDLE hDisk = null;

        try {
            // Step 1: Open volume device
            String volumePath = "\\\\.\\" + normalized;
            hVolume = Kernel32.INSTANCE.CreateFile(
                volumePath,
                GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                null
            );

            if (hVolume == null || WinBase.INVALID_HANDLE_VALUE.equals(hVolume)) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to open volume {}, error: {}", volumePath, error);
                return "";
            }

            // Step 2: Get disk extents (maps volume to physical disk number)
            int extentsBufferSize = 256;
            Memory extentsBuffer = new Memory(extentsBufferSize);
            IntByReference bytesReturned = new IntByReference();

            boolean success = Kernel32.INSTANCE.DeviceIoControl(
                hVolume,
                IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS,
                null, 0,
                extentsBuffer, extentsBufferSize,
                bytesReturned, null
            );

            if (!success) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to get disk extents for {}, error: {}", normalized, error);
                return "";
            }

            int numberOfExtents = extentsBuffer.getInt(0);
            if (numberOfExtents == 0) {
                logger.debug("No disk extents returned for {}", normalized);
                return "";
            }

            int diskNumber = extentsBuffer.getInt(8);
            logger.debug("Drive {} maps to PhysicalDrive{}", normalized, diskNumber);

            // Step 3: Try to open physical disk (requires admin rights)
            String diskPath = "\\\\.\\PhysicalDrive" + diskNumber;
            hDisk = Kernel32.INSTANCE.CreateFile(
                diskPath,
                GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                null
            );

            WinNT.HANDLE hQuery;
            if (hDisk != null && !WinBase.INVALID_HANDLE_VALUE.equals(hDisk)) {
                hQuery = hDisk;
                logger.debug("Using PhysicalDrive{} for query", diskNumber);
            } else {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to open disk {}, error: {}, falling back to volume handle", diskPath, error);
                hQuery = hVolume;
            }

            // Step 4: Query storage device property
            STORAGE_PROPERTY_QUERY query = new STORAGE_PROPERTY_QUERY();
            query.propertyId = StorageDeviceProperty;
            query.queryType = PropertyStandardQuery;
            query.write();

            int bufferSize = 1024;
            Memory buffer = new Memory(bufferSize);
            bytesReturned.setValue(0);

            success = Kernel32.INSTANCE.DeviceIoControl(
                hQuery,
                IOCTL_STORAGE_QUERY_PROPERTY,
                query.getPointer(), query.size(),
                buffer, bufferSize,
                bytesReturned, null
            );

            if (!success || bytesReturned.getValue() < 40) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to query storage property, error: {}", error);
                return "";
            }

            int serialNumberOffset = buffer.getInt(36);
            if (serialNumberOffset == 0 || serialNumberOffset >= bytesReturned.getValue()) {
                logger.debug("No serial number offset in descriptor for {}", normalized);
                return "";
            }

            String serial = buffer.getString(serialNumberOffset);
            if (serial != null && !serial.isEmpty()) {
                logger.debug("Got hardware serial via DeviceIoControl for {}: {}", normalized, serial);
                return serial.trim();
            }

            logger.debug("No serial number found for drive: {}", normalized);
            return "";

        } catch (Exception e) {
            logger.debug("DeviceIoControl query failed for {}: {}", normalized, e);
            return "";
        } finally {
            if (hVolume != null) {
                Kernel32.INSTANCE.CloseHandle(hVolume);
            }
            if (hDisk != null) {
                Kernel32.INSTANCE.CloseHandle(hDisk);
            }
        }
    }

    /**
     * Normalizes drive letter to "E:" format.
     */
    private static String normalizeDriveLetter(String driveLetter) {
        if (driveLetter == null || driveLetter.isEmpty()) {
            return "";
        }

        String normalized = driveLetter.trim();

        if (normalized.length() >= 2 && normalized.charAt(1) == ':') {
            return normalized.substring(0, 2).toUpperCase();
        } else if (normalized.length() == 1 && Character.isLetter(normalized.charAt(0))) {
            return normalized.toUpperCase() + ":";
        }

        return "";
    }

    // ========== Native Structures ==========

    /**
     * STORAGE_PROPERTY_QUERY - input for IOCTL_STORAGE_QUERY_PROPERTY.
     */
    @Structure.FieldOrder({"propertyId", "queryType", "additionalParameters"})
    public static class STORAGE_PROPERTY_QUERY extends Structure {
        public int propertyId;
        public int queryType;
        public byte[] additionalParameters = new byte[1];

        public STORAGE_PROPERTY_QUERY() {
            super();
        }
    }

    /**
     * STORAGE_DEVICE_NUMBER structure returned by IOCTL_STORAGE_GET_DEVICE_NUMBER.
     */
    @Structure.FieldOrder({"DeviceType", "DeviceNumber", "PartitionNumber"})
    public static class STORAGE_DEVICE_NUMBER extends Structure {
        public int DeviceType;
        public int DeviceNumber;
        public int PartitionNumber;

        public STORAGE_DEVICE_NUMBER() {
            super();
        }
    }

    /**
     * Gets the device instance ID for the physical disk containing the specified volume.
     *
     * <p>Uses SetupAPI to enumerate disk devices and matches by device number.
     * The device instance ID contains the hardware serial number.
     *
     * <p>Example device instance ID: USBSTOR\DISK&VEN_KINGSTON&PROD_DATATRAVELER\ABC123&0
     *
     * @param driveLetter the drive letter (e.g., "E:" or "E:\\")
     * @return device instance ID, or empty string if not found
     */
    public static String getDeviceInstanceId(String driveLetter) {
        if (driveLetter == null || driveLetter.isEmpty()) {
            return "";
        }

        String normalized = normalizeDriveLetter(driveLetter);
        if (normalized.isEmpty()) {
            return "";
        }

        // Step 1: Get the disk number
        int diskNumber = getDiskNumber(normalized);
        if (diskNumber < 0) {
            logger.debug("Failed to get disk number for {}", normalized);
            return "";
        }

        logger.debug("Drive {} maps to disk {}", normalized, diskNumber);

        // Step 2: Use SetupAPI to find matching disk device
        return findDiskDeviceInstanceId(diskNumber);
    }

    /**
     * Gets the disk number for a volume using IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS.
     */
    private static int getDiskNumber(String driveLetter) {
        WinNT.HANDLE hVolume = null;

        try {
            String volumePath = "\\\\.\\" + driveLetter;
            hVolume = Kernel32.INSTANCE.CreateFile(
                volumePath,
                GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                null
            );

            if (hVolume == null || WinBase.INVALID_HANDLE_VALUE.equals(hVolume)) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to open volume {}, error: {}", volumePath, error);
                return -1;
            }

            int extentsBufferSize = 256;
            Memory extentsBuffer = new Memory(extentsBufferSize);
            IntByReference bytesReturned = new IntByReference();

            boolean success = Kernel32.INSTANCE.DeviceIoControl(
                hVolume,
                IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS,
                null, 0,
                extentsBuffer, extentsBufferSize,
                bytesReturned, null
            );

            if (!success) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to get disk extents for {}, error: {}", driveLetter, error);
                return -1;
            }

            int numberOfExtents = extentsBuffer.getInt(0);
            if (numberOfExtents == 0) {
                logger.debug("No disk extents returned for {}", driveLetter);
                return -1;
            }

            return extentsBuffer.getInt(8);

        } catch (Exception e) {
            logger.debug("Exception getting disk number for {}: {}", driveLetter, e);
            return -1;
        } finally {
            if (hVolume != null) {
                Kernel32.INSTANCE.CloseHandle(hVolume);
            }
        }
    }

    /**
     * Finds the device instance ID for a disk by enumerating disk devices via SetupAPI.
     */
    private static String findDiskDeviceInstanceId(int diskNumber) {
        WinNT.HANDLE hDevInfo = null;

        try {
            // Enumerate all present disk devices
            hDevInfo = SetupApiExt.INSTANCE.SetupDiGetClassDevs(
                GUID_DEVINTERFACE_DISK,
                null,
                null,
                DIGCF_DEVICEINTERFACE | DIGCF_PRESENT
            );

            if (hDevInfo == null || WinBase.INVALID_HANDLE_VALUE.equals(hDevInfo)) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("SetupDiGetClassDevs failed, error: {}", error);
                return "";
            }

            int memberIndex = 0;
            SetupApiExt.SP_DEVICE_INTERFACE_DATA deviceInterfaceData = new SetupApiExt.SP_DEVICE_INTERFACE_DATA();
            SetupApiExt.SP_DEVINFO_DATA devInfoData = new SetupApiExt.SP_DEVINFO_DATA();

            while (true) {
                // Enumerate device interfaces
                boolean result = SetupApiExt.INSTANCE.SetupDiEnumDeviceInterfaces(
                    hDevInfo,
                    null,
                    GUID_DEVINTERFACE_DISK,
                    memberIndex,
                    deviceInterfaceData
                );

                if (!result) {
                    int error = Kernel32.INSTANCE.GetLastError();
                    if (error != 259) { // ERROR_NO_MORE_ITEMS
                        logger.debug("SetupDiEnumDeviceInterfaces failed at index {}, error: {}", memberIndex, error);
                    }
                    break;
                }

                // Get required buffer size
                IntByReference requiredSize = new IntByReference();
                SetupApiExt.INSTANCE.SetupDiGetDeviceInterfaceDetail(
                    hDevInfo,
                    deviceInterfaceData,
                    null,
                    0,
                    requiredSize,
                    devInfoData
                );

                // Allocate buffer and get interface detail
                int detailSize = requiredSize.getValue();
                Memory detailBuffer = new Memory(detailSize);

                // Set cbSize (4 on x86, 8 on x64)
                int cbSize = Native.POINTER_SIZE == 8 ? 8 : 6;
                detailBuffer.setInt(0, cbSize);

                result = SetupApiExt.INSTANCE.SetupDiGetDeviceInterfaceDetail(
                    hDevInfo,
                    deviceInterfaceData,
                    detailBuffer,
                    detailSize,
                    requiredSize,
                    devInfoData
                );

                if (!result) {
                    int error = Kernel32.INSTANCE.GetLastError();
                    logger.debug("SetupDiGetDeviceInterfaceDetail failed at index {}, error: {}", memberIndex, error);
                    memberIndex++;
                    continue;
                }

                // Extract device path (after cbSize)
                String devicePath = detailBuffer.getWideString(cbSize);
                logger.debug("Found disk device: {}", devicePath);

                // Open device and get its device number
                int deviceDiskNumber = getDeviceDiskNumber(devicePath);
                if (deviceDiskNumber == diskNumber) {
                    // Match found - get device instance ID
                    String instanceId = getDeviceInstanceIdFromDevInfo(hDevInfo, devInfoData);
                    logger.info("Matched disk {} to device instance: {}", diskNumber, instanceId);
                    return instanceId;
                }

                memberIndex++;
            }

            logger.debug("No matching disk device found for disk number {}", diskNumber);
            return "";

        } catch (Exception e) {
            logger.debug("Exception finding disk device instance ID: {}", e);
            return "";
        } finally {
            if (hDevInfo != null) {
                SetupApiExt.INSTANCE.SetupDiDestroyDeviceInfoList(hDevInfo);
            }
        }
    }

    /**
     * Gets the disk number for a device path using IOCTL_STORAGE_GET_DEVICE_NUMBER.
     */
    private static int getDeviceDiskNumber(String devicePath) {
        WinNT.HANDLE hDevice = null;

        try {
            hDevice = Kernel32.INSTANCE.CreateFile(
                devicePath,
                GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                null
            );

            if (hDevice == null || WinBase.INVALID_HANDLE_VALUE.equals(hDevice)) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("Failed to open device {}, error: {}", devicePath, error);
                return -1;
            }

            STORAGE_DEVICE_NUMBER sdn = new STORAGE_DEVICE_NUMBER();
            IntByReference bytesReturned = new IntByReference();

            boolean success = Kernel32.INSTANCE.DeviceIoControl(
                hDevice,
                IOCTL_STORAGE_GET_DEVICE_NUMBER,
                null, 0,
                sdn.getPointer(), sdn.size(),
                bytesReturned, null
            );

            if (!success) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("IOCTL_STORAGE_GET_DEVICE_NUMBER failed for {}, error: {}", devicePath, error);
                return -1;
            }

            sdn.read();
            return sdn.DeviceNumber;

        } catch (Exception e) {
            logger.debug("Exception getting device disk number: {}", e);
            return -1;
        } finally {
            if (hDevice != null) {
                Kernel32.INSTANCE.CloseHandle(hDevice);
            }
        }
    }

    /**
     * Gets the device instance ID from device info data.
     */
    private static String getDeviceInstanceIdFromDevInfo(WinNT.HANDLE hDevInfo, SetupApiExt.SP_DEVINFO_DATA devInfoData) {
        try {
            // Get required buffer size
            IntByReference requiredSize = new IntByReference();
            SetupApiExt.INSTANCE.SetupDiGetDeviceInstanceId(
                hDevInfo,
                devInfoData,
                null,
                0,
                requiredSize
            );

            // Allocate buffer and get instance ID
            int bufferSize = requiredSize.getValue();
            char[] buffer = new char[bufferSize];

            boolean result = SetupApiExt.INSTANCE.SetupDiGetDeviceInstanceId(
                hDevInfo,
                devInfoData,
                buffer,
                bufferSize,
                null
            );

            if (!result) {
                int error = Kernel32.INSTANCE.GetLastError();
                logger.debug("SetupDiGetDeviceInstanceId failed, error: {}", error);
                return "";
            }

            // Convert char array to string
            int len = 0;
            for (char c : buffer) {
                if (c == 0) break;
                len++;
            }
            return new String(buffer, 0, len);

        } catch (Exception e) {
            logger.debug("Exception getting device instance ID: {}", e);
            return "";
        }
    }

    /**
     * Extracts the serial number from a device instance ID.
     *
     * <p>Example instance IDs:
     * <ul>
     *   <li>USBSTOR\DISK&VEN_KINGSTON&PROD_DATATRAVELER_100_G3&REV_PMAP\001CC0EC3370B01000000000&0</li>
     *   <li>USB\VID_0951&PID_1666\001CC0EC3370B01000000000</li>
     * </ul>
     *
     * @param instanceId device instance ID
     * @return serial number extracted from instance ID, or empty string if parsing fails
     */
    public static String extractSerialFromInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return "";
        }

        // Pattern 1: USBSTOR\Disk&Ven_XXX&Prod_XXX\Serial&0
        if (instanceId.startsWith("USBSTOR\\")) {
            String[] parts = instanceId.split("\\\\");
            if (parts.length >= 3) {
                String lastPart = parts[parts.length - 1];
                int ampIndex = lastPart.indexOf('&');
                if (ampIndex > 0) {
                    return lastPart.substring(0, ampIndex);
                }
                return lastPart;
            }
        }

        // Pattern 2: USB\VID_xxxx&PID_xxxx\Serial
        if (instanceId.startsWith("USB\\")) {
            String[] parts = instanceId.split("\\\\");
            if (parts.length >= 3) {
                return parts[parts.length - 1];
            }
        }

        logger.debug("Could not parse serial from instance ID: {}", instanceId);
        return "";
    }

    /**
     * Gets the hardware serial number from a volume using SetupAPI.
     *
     * <p>This is the preferred method to get the real hardware serial number.
     * It enumerates disk devices via SetupAPI and matches by device number.
     *
     * @param driveLetter the drive letter (e.g., "E:" or "E:\\")
     * @return hardware serial number, or empty string if not found
     */
    public static String getHardwareSerialViaSetupApi(String driveLetter) {
        String instanceId = getDeviceInstanceId(driveLetter);
        if (instanceId.isEmpty()) {
            return "";
        }

        return extractSerialFromInstanceId(instanceId);
    }
}
