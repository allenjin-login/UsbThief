module UsbThief {
    requires jdk.httpserver;
    requires java.prefs;
    requires java.desktop;
    requires com.formdev.flatlaf;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires com.github.benmanes.caffeine;
    requires org.jspecify;


    // No exports: UsbThief is an application module, not a library. The previous
    // 19 exports exposed every internal package for no consumer; package access
    // within the module is unaffected. (architecture-audit [11])

}
