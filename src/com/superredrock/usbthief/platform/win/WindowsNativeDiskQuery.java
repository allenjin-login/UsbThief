package com.superredrock.usbthief.platform.win;

import com.superredrock.usbthief.core.NativeDiskQuery;

/**
 * Windows implementation of {@link NativeDiskQuery}, delegating to the JNA {@code DeviceIoControl}
 * / {@code SetupAPI} pipeline in {@link DiskQueryUtil}.
 *
 * <p>Behaviour is exactly the previous {@code DiskQueryUtil} behaviour; this class only adds the
 * interface indirection so that business code never touches the native pipeline on other
 * platforms.</p>
 */
public class WindowsNativeDiskQuery implements NativeDiskQuery {

    @Override
    public String getHardwareSerial(String driveLetter) {
        return DiskQueryUtil.getHardwareSerial(driveLetter);
    }

    @Override
    public String getDeviceInstanceId(String driveLetter) {
        return DiskQueryUtil.getDeviceInstanceId(driveLetter);
    }

    @Override
    public String getHardwareSerialViaSetupApi(String driveLetter) {
        return DiskQueryUtil.getHardwareSerialViaSetupApi(driveLetter);
    }
}
