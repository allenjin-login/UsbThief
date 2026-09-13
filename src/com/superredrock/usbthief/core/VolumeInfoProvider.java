package com.superredrock.usbthief.core;

/**
 * Platform-neutral source of file-system volume information.
 *
 * <p>The Windows implementation (in {@code com.superredrock.usbthief.platform.win}) calls
 * {@code GetVolumeInformationW} through JNA. Platforms without that API get the no-op
 * implementation from {@code com.superredrock.usbthief.platform.stub}, which returns an empty
 * serial number without loading any native library.</p>
 */
public interface VolumeInfoProvider {

    /**
     * Gets the file-system serial number of the given drive.
     *
     * @param drive drive letter (for example {@code "E:"} or {@code "E:\\"})
     * @return the serial number as an eight-digit upper-case hex string, or an empty string
     *         when the platform cannot provide one
     */
    String getVolumeSerial(String drive);
}
