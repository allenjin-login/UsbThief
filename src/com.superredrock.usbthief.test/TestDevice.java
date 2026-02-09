package com.superredrock.usbthief.test;

import com.superredrock.usbthief.core.Device;
import java.nio.file.Path;

/**
 * Test helper to expose protected Device constructor.
 */
public class TestDevice extends Device {
    public TestDevice(Path rootPath) {
        super(rootPath);
    }
}
