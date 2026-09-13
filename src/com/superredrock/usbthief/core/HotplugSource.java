package com.superredrock.usbthief.core;

/**
 * Platform-neutral source of USB hot-plug notifications.
 *
 * <p>Business code (for example {@link DeviceManager}) depends only on this interface. The
 * Windows implementation lives in {@code com.superredrock.usbthief.platform.win} and uses the
 * JNA message-window pipeline; every other platform gets the no-op implementation from
 * {@code com.superredrock.usbthief.platform.stub}, which never touches a native library.</p>
 *
 * <p>Implementations are selected at the assembly point
 * ({@code com.superredrock.usbthief.platform.Platform}) and must be safe to <em>construct</em>
 * on any platform: native libraries may only be resolved once {@link #start()} actually runs.</p>
 */
public interface HotplugSource {

    /**
     * Receives volume-level (drive letter) notifications.
     */
    interface VolumeListener {
        /** Called on the hot-plug callback thread, never on the EDT or the message pump. */
        void onVolumeArrival(String driveLetter);

        /** Called on the hot-plug callback thread, never on the EDT or the message pump. */
        void onVolumeRemoval(String driveLetter);

        /**
         * Eject cleanup hook, called on the hot-plug callback thread after the eject has been
         * allowed. Must not rely on blocking the Windows message pump.
         *
         * @return advisory result; the monitor always allows the eject
         */
        default boolean onVolumeQueryRemove(String driveLetter) {
            return true;
        }
    }

    /**
     * Receives device-interface (hardware) notifications.
     */
    interface DeviceListener {
        /** Called on the hot-plug callback thread, never on the EDT or the message pump. */
        void onDeviceArrival(String dbccName);

        /** Called on the hot-plug callback thread, never on the EDT or the message pump. */
        void onDeviceRemoval(String dbccName);
    }

    /** Registers the volume listener. */
    void setVolumeListener(VolumeListener listener);

    /** Registers the device listener. */
    void setDeviceListener(DeviceListener listener);

    /** Starts listening. Safe to call on platforms without hot-plug support (no-op). */
    void start();

    /** Stops listening and releases any platform resources. */
    void stop();

    /** @return {@code true} while the source is listening */
    boolean isRunning();

    /** Starts eject (query-remove) detection for the given drive letter, if supported. */
    void registerVolumeHandle(String driveLetter);

    /** Stops eject (query-remove) detection for the given drive letter, if supported. */
    void unregisterVolumeHandle(String driveLetter);
}
