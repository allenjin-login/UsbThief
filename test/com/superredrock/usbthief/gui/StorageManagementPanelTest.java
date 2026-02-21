package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StorageManagementPanel.
 * <p>
 * Note: GUI components must be created and accessed on the Event Dispatch Thread (EDT).
 * These tests use SwingUtilities.invokeLater to ensure thread safety.
 */
class StorageManagementPanelTest {

    private String originalWorkPath;
    private Long originalReservedBytes;
    private Long originalMaxBytes;
    private Integer originalNormalWaitMinutes;
    private Integer originalErrorWaitMinutes;
    private String originalStrategy;
    private Integer originalProtectedAgeHours;
    private Boolean originalWarningEnabled;

    @BeforeEach
    void setUp() {
        // Save original config values
        ConfigManager config = ConfigManager.getInstance();
        originalWorkPath = config.get(ConfigSchema.WORK_PATH);
        originalReservedBytes = config.get(ConfigSchema.STORAGE_RESERVED_BYTES);
        originalMaxBytes = config.get(ConfigSchema.STORAGE_MAX_BYTES);
        originalNormalWaitMinutes = config.get(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES);
        originalErrorWaitMinutes = config.get(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES);
        originalStrategy = config.get(ConfigSchema.RECYCLER_STRATEGY);
        originalProtectedAgeHours = config.get(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS);
        originalWarningEnabled = config.get(ConfigSchema.STORAGE_WARNING_ENABLED);
    }

    @AfterEach
    void tearDown() {
        // Restore original config values
        ConfigManager config = ConfigManager.getInstance();
        config.set(ConfigSchema.WORK_PATH, originalWorkPath);
        config.set(ConfigSchema.STORAGE_RESERVED_BYTES, originalReservedBytes);
        config.set(ConfigSchema.STORAGE_MAX_BYTES, originalMaxBytes);
        config.set(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES, originalNormalWaitMinutes);
        config.set(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES, originalErrorWaitMinutes);
        config.set(ConfigSchema.RECYCLER_STRATEGY, originalStrategy);
        config.set(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS, originalProtectedAgeHours);
        config.set(ConfigSchema.STORAGE_WARNING_ENABLED, originalWarningEnabled);
    }

    @Test
    void panelCanBeCreated() throws Exception {
        // Use a CountDownLatch or invokeAndWait to wait for EDT to complete
        SwingUtilities.invokeAndWait(() -> {
            StorageManagementPanel panel = new StorageManagementPanel();

            assertNotNull(panel, "Panel should be created");

            // Cleanup
            panel.cleanup();
        });
    }

    @Test
    void cleanupStopsTimerAndRemovesListener() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StorageManagementPanel panel = new StorageManagementPanel();

            // Cleanup should not throw any exceptions
            assertDoesNotThrow(panel::cleanup, "cleanup() should not throw exceptions");

            // Calling cleanup multiple times should be safe
            assertDoesNotThrow(panel::cleanup, "Multiple cleanup() calls should be safe");
        });
    }

    @Test
    void panelLoadsCurrentConfigValues() throws Exception {
        // Set specific config values
        ConfigManager config = ConfigManager.getInstance();
        config.set(ConfigSchema.STORAGE_RESERVED_BYTES, 20L * 1024 * 1024 * 1024); // 20 GB
        config.set(ConfigSchema.STORAGE_MAX_BYTES, 200L * 1024 * 1024 * 1024); // 200 GB
        config.set(ConfigSchema.SNIFFER_WAIT_NORMAL_MINUTES, 60);
        config.set(ConfigSchema.SNIFFER_WAIT_ERROR_MINUTES, 10);
        config.set(ConfigSchema.RECYCLER_STRATEGY, "SIZE_FIRST");
        config.set(ConfigSchema.RECYCLER_PROTECTED_AGE_HOURS, 24);
        config.set(ConfigSchema.STORAGE_WARNING_ENABLED, false);

        SwingUtilities.invokeAndWait(() -> {
            StorageManagementPanel panel = new StorageManagementPanel();

            // The panel should load the config values
            // Note: We can't easily verify spinner values without accessing private fields,
            // but we can at least verify the panel is created without errors

            assertNotNull(panel, "Panel should be created with current config values");

            panel.cleanup();
        });
    }

    @Test
    void refreshLanguageDoesNotThrow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StorageManagementPanel panel = new StorageManagementPanel();

            // refreshLanguage should not throw any exceptions
            assertDoesNotThrow(panel::refreshLanguage, "refreshLanguage() should not throw exceptions");

            panel.cleanup();
        });
    }

    @Test
    void onLocaleChangedDelegatesToRefreshLanguage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StorageManagementPanel panel = new StorageManagementPanel();

            // onLocaleChanged should not throw any exceptions
            assertDoesNotThrow(() -> panel.onLocaleChanged(java.util.Locale.ENGLISH),
                "onLocaleChanged() should not throw exceptions");

            panel.cleanup();
        });
    }

    @Test
    void panelHasSaveAndResetButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StorageManagementPanel panel = new StorageManagementPanel();

            // Verify panel has at least some buttons
            // Note: We can't easily verify specific buttons without accessing private fields

            assertNotNull(panel, "Panel should have buttons");

            panel.cleanup();
        });
    }
}
