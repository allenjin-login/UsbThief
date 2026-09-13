package com.superredrock.usbthief.platform.stub;

import com.superredrock.usbthief.core.NativeDiskQuery;

/**
 * No-op {@link NativeDiskQuery} for platforms without {@code DeviceIoControl}/{@code SetupAPI}.
 *
 * <p>Every query returns an empty string and no native library is touched.</p>
 */
public class NoopNativeDiskQuery implements NativeDiskQuery {

    @Override
    public String getHardwareSerial(String driveLetter) {
        return "";
    }

    @Override
    public String getDeviceInstanceId(String driveLetter) {
        return "";
    }

    @Override
    public String getHardwareSerialViaSetupApi(String driveLetter) {
        return "";
    }
}
