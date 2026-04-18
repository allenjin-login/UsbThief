module UsbThief {
    requires java.prefs;
    requires java.desktop;
    requires com.formdev.flatlaf;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    exports com.superredrock.usbthief.index;
    exports com.superredrock.usbthief.core;
    exports com.superredrock.usbthief.core.config;
    exports com.superredrock.usbthief.core.event;
    exports com.superredrock.usbthief.core.event.device;
    exports com.superredrock.usbthief.core.event.index;
    exports com.superredrock.usbthief.core.event.worker;
    exports com.superredrock.usbthief.core.filter;
    exports com.superredrock.usbthief.gui;
    exports com.superredrock.usbthief.gui.theme;
    exports com.superredrock.usbthief.gui.components;
    exports com.superredrock.usbthief.worker;
    exports com.superredrock.usbthief.statistics;
}
