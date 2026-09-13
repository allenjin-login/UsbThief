package com.superredrock.usbthief.core;

/**
 * Platform-neutral source of physical-disk (hardware) identification.
 *
 * <p>The Windows implementation (in {@code com.superredrock.usbthief.platform.win}) drives
 * {@code DeviceIoControl} and {@code SetupAPI}. Platforms without those APIs get the no-op
 * implementation from {@code com.superredrock.usbthief.platform.stub}, which returns empty
 * strings without loading any native library.</p>
 */
public interface NativeDiskQuery {

    /**
     * Gets the hardware serial number of the physical disk containing the given volume.
     *
     * @param driveLetter the drive letter (for example {@code "E:"})
     * @return hardware serial number, or an empty string when unavailable
     */
    String getHardwareSerial(String driveLetter);

    /**
     * Gets the device instance ID of the physical disk containing the given volume.
     *
     * @param driveLetter the drive letter (for example {@code "E:"})
     * @return device instance ID, or an empty string when unavailable
     */
    String getDeviceInstanceId(String driveLetter);

    /**
     * Gets the hardware serial number from a volume using the device-instance-ID chain.
     *
     * @param driveLetter the drive letter (for example {@code "E:"})
     * @return hardware serial number, or an empty string when unavailable
     */
    String getHardwareSerialViaSetupApi(String driveLetter);
}
