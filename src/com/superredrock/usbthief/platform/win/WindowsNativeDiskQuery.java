package com.superredrock.usbthief.platform.win;

import com.superredrock.usbthief.core.NativeDiskQuery;

/**
 * Windows implementation of {@link NativeDiskQuery}, delegating to the JNA {@code DeviceIoControl}
 * / {@code SetupAPI} pipeline in {@link DiskQueryUtil}.
 *
 * <p>Behaviour is exactly the previous {@code DiskQueryUtil} behaviour; this class only adds the
 * interface indirection so that business code never touches the native pipeline on other
 * platforms.</p>
 *
 * <p><b>Cold path (PF-20).</b> Every method here is a millisecond-scale native call chain
 * ({@code CreateFile(\\.\X:)} → {@code DeviceIoControl(IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS)}
 * → optional {@code CreateFile(\\.\PhysicalDriveN)} → {@code IOCTL_STORAGE_QUERY_PROPERTY}, plus
 * an O(device-count) {@code SetupAPI} enumeration for {@link #getDeviceInstanceId}). It is reached
 * only through {@code Platform.diskQuery()} when a {@code Volume} is identified — never per file
 * or per copy chunk. Do not call it from copy/scan hot paths without caching the result per
 * volume serial.</p>
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
