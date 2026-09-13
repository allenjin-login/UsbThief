package com.superredrock.usbthief.platform.stub;

import com.superredrock.usbthief.core.VolumeInfoProvider;

/**
 * No-op {@link VolumeInfoProvider} for platforms without {@code GetVolumeInformationW}.
 *
 * <p>Returns an empty serial number, matching the previous behaviour on non-Windows hosts, and
 * never loads a native library.</p>
 */
public class NoopVolumeInfoProvider implements VolumeInfoProvider {

    @Override
    public String getVolumeSerial(String drive) {
        return "";
    }
}
