package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Manages Windows auto-start functionality via registry.
 * Uses HKCU\Software\Microsoft\Windows\CurrentVersion\Run key.
 */
public final class AutoStartManager {
    
    private static final Logger logger = Logger.getLogger(AutoStartManager.class.getName());
    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "UsbThief";
    
    private static volatile AutoStartManager INSTANCE;
    
    private AutoStartManager() {}
    
    public static AutoStartManager getInstance() {
        if (INSTANCE == null) {
            synchronized (AutoStartManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AutoStartManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Check if running on Windows platform.
     */
    public boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }
    
    /**
     * Check if auto-start is currently enabled in registry.
     */
    public boolean isAutoStartEnabled() {
        if (!isWindows()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", REG_KEY, "/v", APP_NAME);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            logger.warning("Failed to check auto-start status: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Enable auto-start by adding registry entry.
     * Only works when running from packaged EXE.
     */
    public boolean enableAutoStart() {
        if (!isWindows()) {
            logger.warning("Auto-start only supported on Windows");
            return false;
        }
        
        String command = getLaunchCommand();
        if (command == null) {
            // Not running from packaged EXE
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "add", REG_KEY,
                "/v", APP_NAME, "/t", "REG_SZ", "/d", command, "/f");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            int exitCode = p.waitFor();
            
            if (exitCode == 0) {
                logger.info("Auto-start enabled: " + command);
                ConfigManager.getInstance().set(ConfigSchema.AUTO_START_ENABLED, true);
                return true;
            } else {
                logger.warning("Failed to enable auto-start: " + output);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to enable auto-start: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Disable auto-start by removing registry entry.
     */
    public boolean disableAutoStart() {
        if (!isWindows()) {
            return false;
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "delete", REG_KEY,
                "/v", APP_NAME, "/f");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            int exitCode = p.waitFor();
            
            // Exit code 0 = deleted, 1 = didn't exist (both OK for disable)
            if (exitCode == 0 || exitCode == 1) {
                logger.info("Auto-start disabled");
                ConfigManager.getInstance().set(ConfigSchema.AUTO_START_ENABLED, false);
                return true;
            } else {
                logger.warning("Failed to disable auto-start: " + output);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to disable auto-start: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Toggle auto-start state.
     */
    public boolean toggleAutoStart() {
        if (isAutoStartEnabled()) {
            return disableAutoStart();
        } else {
            return enableAutoStart();
        }
    }
    
    /**
     * Get the launch command for auto-start.
     * Uses launch.exefile system property (set by Launch4j) when available.
     * Returns null if not running from packaged EXE.
     */
    private String getLaunchCommand() {
        // Check if running from Launch4j packaged EXE
        String exePath = System.getProperty("launch4j.exefile");
        
        if (exePath != null && !exePath.isBlank()) {
            // Running as packaged EXE
            return "\"" + exePath + "\"";
        }
        
        // Not running from packaged EXE - auto-start not available
        logger.warning("Auto-start only available when running from packaged EXE");
        return null;
    }
}
