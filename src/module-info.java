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

    // 使用 Java 模块系统注册 AbstractService 实现
    // 注意：Index 需要构造参数，不通过 ServiceLoader 加载
    // Index 由 QueueManager 创建并手动注册到 ServiceManager
    provides Service
            with com.superredrock.usbthief.index.Index , com.superredrock.usbthief.core.DeviceManager;
}