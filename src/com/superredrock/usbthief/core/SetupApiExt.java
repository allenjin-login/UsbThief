package com.superredrock.usbthief.core;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Extended SetupAPI interface with additional functions not in JNA's standard SetupApi.
 *
 * <p>Provides functions for device enumeration and property retrieval.
 */
public interface SetupApiExt extends Library {

    SetupApiExt INSTANCE = Native.load("setupapi", SetupApiExt.class, W32APIOptions.DEFAULT_OPTIONS);

    // Flags for SetupDiGetClassDevs
    int DIGCF_PRESENT = 0x00000002;
    int DIGCF_ALLCLASSES = 0x00000004;
    int DIGCF_DEVICEINTERFACE = 0x00000010;

    // Device registry property codes
    int SPDRP_DEVICEDESC = 0x00000000;
    int SPDRP_HARDWAREID = 0x00000001;
    int SPDRP_COMPATIBLEIDS = 0x00000002;
    int SPDRP_SERVICE = 0x00000004;
    int SPDRP_CLASS = 0x00000007;
    int SPDRP_CLASSGUID = 0x00000008;
    int SPDRP_DRIVER = 0x00000009;
    int SPDRP_FRIENDLYNAME = 0x0000000C;
    int SPDRP_LOCATION_INFORMATION = 0x0000000D;
    int SPDRP_PHYSICAL_DEVICE_OBJECT_NAME = 0x0000000E;
    int SPDRP_ENUMERATOR_NAME = 0x00000016;

    /**
     * GUID for disk device interface class.
     * {53F56307-B6BF-11D0-94F2-00A0C91EFB8B}
     */
    Guid.GUID GUID_DEVINTERFACE_DISK = new Guid.GUID("{53F56307-B6BF-11D0-94F2-00A0C91EFB8B}");

    /**
     * GUID for volume device interface class.
     * {53F5630D-B6BF-11D0-94F2-00A0C91EFB8B}
     */
    Guid.GUID GUID_DEVINTERFACE_VOLUME = new Guid.GUID("{53F5630D-B6BF-11D0-94F2-00A0C91EFB8B}");

    /**
     * SP_DEVINFO_DATA structure contains information about a device instance.
     */
    @Structure.FieldOrder({"cbSize", "ClassGuid", "DevInst", "Reserved"})
    class SP_DEVINFO_DATA extends Structure {
        public int cbSize;
        public Guid.GUID ClassGuid;
        public int DevInst;
        public long Reserved;

        public SP_DEVINFO_DATA() {
            super();
            cbSize = size();
        }
    }

    /**
     * SP_DEVICE_INTERFACE_DATA structure defines a device interface.
     */
    @Structure.FieldOrder({"cbSize", "InterfaceClassGuid", "Flags", "Reserved"})
    class SP_DEVICE_INTERFACE_DATA extends Structure {
        public int cbSize;
        public Guid.GUID InterfaceClassGuid;
        public int Flags;
        public long Reserved;

        public SP_DEVICE_INTERFACE_DATA() {
            super();
            cbSize = size();
        }
    }

    /**
     * Creates a device information set for device enumeration.
     *
     * @param ClassGuid    GUID for device setup class or interface class (can be null)
     * @param Enumerator   Enumerator string (can be null)
     * @param hwndParent   Parent window handle (can be null)
     * @param Flags        Control flags
     * @return Handle to device information set, or INVALID_HANDLE_VALUE on failure
     */
    WinNT.HANDLE SetupDiGetClassDevs(Guid.GUID ClassGuid, String Enumerator,
                                     Pointer hwndParent, int Flags);

    /**
     * Enumerates device information elements in a device information set.
     *
     * @param DeviceInfoSet     Handle to device information set
     * @param MemberIndex       Zero-based index
     * @param DeviceInfoData    Buffer for device info data
     * @return TRUE if successful, FALSE otherwise
     */
    boolean SetupDiEnumDeviceInfo(WinNT.HANDLE DeviceInfoSet, int MemberIndex,
                                  SP_DEVINFO_DATA DeviceInfoData);

    /**
     * Enumerates device interfaces in a device information set.
     *
     * @param DeviceInfoSet         Handle to device information set
     * @param DeviceInfoData        Device info data (can be null)
     * @param InterfaceClassGuid    Interface class GUID
     * @param MemberIndex           Zero-based index
     * @param DeviceInterfaceData   Buffer for interface data
     * @return TRUE if successful, FALSE otherwise
     */
    boolean SetupDiEnumDeviceInterfaces(WinNT.HANDLE DeviceInfoSet,
                                        SP_DEVINFO_DATA DeviceInfoData,
                                        Guid.GUID InterfaceClassGuid,
                                        int MemberIndex,
                                        SP_DEVICE_INTERFACE_DATA DeviceInterfaceData);

    /**
     * Retrieves details about a device interface.
     *
     * @param DeviceInfoSet             Handle to device information set
     * @param DeviceInterfaceData       Interface data
     * @param DeviceInterfaceDetailData Buffer for detail data (can be null to get size)
     * @param DeviceInterfaceDetailDataSize Buffer size
     * @param RequiredSize              Required buffer size (output)
     * @param DeviceInfoData            Buffer for device info (can be null)
     * @return TRUE if successful, FALSE otherwise
     */
    boolean SetupDiGetDeviceInterfaceDetail(WinNT.HANDLE DeviceInfoSet,
                                            SP_DEVICE_INTERFACE_DATA DeviceInterfaceData,
                                            Pointer DeviceInterfaceDetailData,
                                            int DeviceInterfaceDetailDataSize,
                                            IntByReference RequiredSize,
                                            SP_DEVINFO_DATA DeviceInfoData);

    /**
     * Retrieves a device registry property.
     *
     * @param DeviceInfoSet     Handle to device information set
     * @param DeviceInfoData    Device info data
     * @param Property          Property to retrieve
     * @param PropertyRegDataType Buffer for property data type (can be null)
     * @param PropertyBuffer    Buffer for property value
     * @param PropertyBufferSize Buffer size
     * @param RequiredSize      Required buffer size (can be null)
     * @return TRUE if successful, FALSE otherwise
     */
    boolean SetupDiGetDeviceRegistryProperty(WinNT.HANDLE DeviceInfoSet,
                                             SP_DEVINFO_DATA DeviceInfoData,
                                             int Property,
                                             IntByReference PropertyRegDataType,
                                             Pointer PropertyBuffer,
                                             int PropertyBufferSize,
                                             IntByReference RequiredSize);

    /**
     * Retrieves the device instance ID for a device.
     *
     * @param DeviceInfoSet     Handle to device information set
     * @param DeviceInfoData    Device info data
     * @param DeviceInstanceId  Buffer for device instance ID (can be null to get size)
     * @param DeviceInstanceIdSize Buffer size in characters
     * @param RequiredSize      Required buffer size (can be null)
     * @return TRUE if successful, FALSE otherwise
     */
    boolean SetupDiGetDeviceInstanceId(WinNT.HANDLE DeviceInfoSet,
                                       SP_DEVINFO_DATA DeviceInfoData,
                                       char[] DeviceInstanceId,
                                       int DeviceInstanceIdSize,
                                       IntByReference RequiredSize);

    /**
     * Destroys a device information set and frees memory.
     *
     * @param DeviceInfoSet Handle to device information set
     * @return TRUE if successful, FALSE otherwise
     */
    boolean SetupDiDestroyDeviceInfoList(WinNT.HANDLE DeviceInfoSet);
}
