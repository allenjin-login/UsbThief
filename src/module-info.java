import com.superredrock.usbthief.core.Service;

module UsbThief {
    uses Service;
    requires java.logging;
    requires java.prefs;
    requires java.desktop;

    exports com.superredrock.usbthief.index;
    exports com.superredrock.usbthief.core;
    exports com.superredrock.usbthief.core.config;
    exports com.superredrock.usbthief.core.event;
    exports com.superredrock.usbthief.core.event.device;
    exports com.superredrock.usbthief.core.event.index;
    exports com.superredrock.usbthief.core.event.worker;
    exports com.superredrock.usbthief.gui;
    exports com.superredrock.usbthief.statistics;

    // Register Service implementations using Java module system
    // Note: Index requires constructor parameters, not loaded via ServiceLoader
    // Index is created by QueueManager and manually registered to ServiceManager
    provides Service
            with com.superredrock.usbthief.index.Index,
                com.superredrock.usbthief.core.DeviceManager,
                com.superredrock.usbthief.worker.TaskScheduler;
}