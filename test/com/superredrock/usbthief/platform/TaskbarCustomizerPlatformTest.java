package com.superredrock.usbthief.platform;

import com.superredrock.usbthief.core.TaskbarCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Platform-factory contract for the taskbar customiser (batch-3 polish).
 *
 * <p>These tests exercise the factory and the never-throws contract, which holds on every
 * platform. Creating a real window needs a display, so that part is skipped on headless
 * CI hosts; the Windows JNA path is validated on a real desktop.</p>
 */
@Timeout(15)
class TaskbarCustomizerPlatformTest {

    @Test
    void factoryNeverReturnsNull() {
        assertNotNull(Platform.taskbarCustomizer());
    }

    @Test
    void factoryIsStable() {
        TaskbarCustomizer a = Platform.taskbarCustomizer();
        TaskbarCustomizer b = Platform.taskbarCustomizer();
        assertSame(a, b, "factory should return the cached instance");
    }

    @Test
    void neverThrowsForNullWindow() {
        // Hard contract: a cosmetic customiser must never break the caller, whatever it is
        // handed. Implementations may no-op or log, but never propagate exceptions.
        assertDoesNotThrow(() -> Platform.taskbarCustomizer().setHiddenFromTaskbar(null, true));
        assertDoesNotThrow(() -> Platform.taskbarCustomizer().setHiddenFromTaskbar(null, false));
    }

    @Test
    void neverThrowsForRealWindow() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        var window = new javax.swing.JFrame("probe");
        try {
            assertDoesNotThrow(() -> Platform.taskbarCustomizer().setHiddenFromTaskbar(window, true));
            assertDoesNotThrow(() -> Platform.taskbarCustomizer().setHiddenFromTaskbar(window, false));
        } finally {
            window.dispose();
        }
    }
}
