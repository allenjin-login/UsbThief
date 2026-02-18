package com.superredrock.usbthief.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Application version information.
 * Version is loaded from version.properties which is filtered by Maven during build.
 */
public final class Version {
    
    private static final Logger logger = Logger.getLogger(Version.class.getName());
    
    private static final String VERSION_FILE = "version.properties";
    
    private static final String MAJOR;
    private static final String MINOR;
    private static final String PATCH;
    private static final String BUILD;
    private static final String VERSION_STRING;
    private static final String FULL_VERSION;
    
    static {
        Properties props = new Properties();
        String major = "0";
        String minor = "0";
        String patch = "0";
        String build = "";
        
        try (InputStream is = Version.class.getResourceAsStream(VERSION_FILE)) {
            if (is != null) {
                props.load(is);
                major = props.getProperty("version.major", "0");
                minor = props.getProperty("version.minor", "0");
                patch = props.getProperty("version.patch", "0");
                build = props.getProperty("version.build", "");
            } else {
                logger.warning("version.properties not found, using defaults");
            }
        } catch (IOException e) {
            logger.warning("Failed to load version.properties: " + e.getMessage());
        }
        
        MAJOR = major;
        MINOR = minor;
        PATCH = patch;
        BUILD = build;
        VERSION_STRING = MAJOR + "." + MINOR + "." + PATCH;
        FULL_VERSION = BUILD.isEmpty() ? VERSION_STRING : VERSION_STRING + "+" + BUILD;
    }
    
    private Version() {
        // Utility class, no instantiation
    }
    
    /**
     * Get major version number.
     * @return Major version (e.g., "1" for version 1.2.3)
     */
    public static String getMajor() {
        return MAJOR;
    }
    
    /**
     * Get minor version number.
     * @return Minor version (e.g., "2" for version 1.2.3)
     */
    public static String getMinor() {
        return MINOR;
    }
    
    /**
     * Get patch version number.
     * @return Patch version (e.g., "3" for version 1.2.3)
     */
    public static String getPatch() {
        return PATCH;
    }
    
    /**
     * Get build metadata.
     * @return Build metadata (e.g., "20260217" or empty if not set)
     */
    public static String getBuild() {
        return BUILD;
    }
    
    /**
     * Get version string without build metadata.
     * @return Version string (e.g., "1.2.3")
     */
    public static String getVersion() {
        return VERSION_STRING;
    }
    
    /**
     * Get full version string with build metadata.
     * @return Full version string (e.g., "1.2.3+20260217")
     */
    public static String getFullVersion() {
        return FULL_VERSION;
    }
    
    /**
     * Get version for Windows file version (4 numbers).
     * @return Version array [major, minor, patch, build] for Windows version info
     */
    public static int[] getWindowsVersion() {
        return new int[] {
            Integer.parseInt(MAJOR),
            Integer.parseInt(MINOR),
            Integer.parseInt(PATCH),
            BUILD.isEmpty() ? 0 : Integer.parseInt(BUILD.replaceAll("[^0-9]", "").substring(0, Math.min(4, BUILD.replaceAll("[^0-9]", "").length())))
        };
    }
    
    @Override
    public String toString() {
        return FULL_VERSION;
    }
}
