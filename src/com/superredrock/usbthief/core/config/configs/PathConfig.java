package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import java.nio.file.Paths;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class PathConfig {
    public static final String CATEGORY = "Paths";

    /** Folder name of the zero-config default backup location. */
    public static final String DEFAULT_FOLDER_NAME = "UsbThiefData";

    public static final ConfigEntry<String> WORK_PATH =
            stringEntry("workPath", "Working directory for storing copied files",
                    defaultWorkPath(), CATEGORY);

    /**
     * Zero-wizard default backup location: {@code <user home>/UsbThiefData}.
     *
     * <p>Relative defaults resolve against the installation directory (see
     * {@code AppPaths}), which is not guaranteed to be writable on Windows
     * (e.g. {@code C:\Program Files\...} needs UAC elevation). The user home is
     * always writable by the running user and is predictable across platforms,
     * so "plug in and it just works" holds without asking the user anything.
     *
     * <p>Falls back to the bare folder name (resolved against the app home) in the
     * unlikely case that {@code user.home} is missing, which keeps portable
     * installs working.
     */
    private static String defaultWorkPath() {
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            return Paths.get(home, DEFAULT_FOLDER_NAME).toString();
        }
        return DEFAULT_FOLDER_NAME;
    }

    private PathConfig() {}
}
