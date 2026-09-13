package com.superredrock.usbthief.worker;

/**
 * A task that belongs to one USB device, identified by its serial number.
 *
 * <p>The scheduler uses this interface instead of enumerating concrete task types when it
 * tracks futures per device and when {@code cancelBySerial(...)} has to drop queued work.
 * Adding a new device-bound task type therefore requires no scheduler change: implement
 * this interface and the cancellation path keeps working.</p>
 */
public interface DeviceBoundTask {

    /**
     * @return the serial number of the device that owns this task; may be empty but never
     *         {@code null} for well-behaved implementations
     */
    String getDeviceSerial();
}
