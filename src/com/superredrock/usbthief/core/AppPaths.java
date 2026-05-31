package com.superredrock.usbthief.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Centralized path resolution that resolves relative paths against
 * the application's installation directory instead of the JVM's
 * current working directory (which can be System32 on Windows).
 */
public final class AppPaths {

    private static final Logger logger = LogManager.getLogger(AppPaths.class);
    private static final Path APP_HOME;

    static {
        APP_HOME = detectAppHome();
        logger.info("Application home: {}", APP_HOME);
    }

    private AppPaths() {}

    /**
     * Detect the application's installation directory.
     *
     * <p>Tries, in order:
     * <ol>
     *   <li>{@code ProcessHandle} command path (works for Launch4j EXE and jlink runtime)</li>
     *   <li>ProtectionDomain code source location (works for JAR)</li>
     *   <li>{@code user.dir} system property (last resort)</li>
     * </ol>
     */
    private static Path detectAppHome() {
        // 1. Process command — most reliable for native launchers
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isPresent()) {
            Path exePath = Paths.get(command.get());
            if (exePath.getFileName() != null) {
                String name = exePath.getFileName().toString().toLowerCase();
                // Only use if it's an actual executable (exe, bat) not java/javaw
                if ((name.endsWith(".exe") || name.endsWith(".bat"))
                        && !name.startsWith("java") && !name.startsWith("javaw")) {
                    Path parent = exePath.getParent();
                    if (parent != null) {
                        return parent;
                    }
                }
            }
        }

        // 2. Code source location — works for JAR and classpath
        try {
            var location = AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            Path codePath = Paths.get(location.toURI());
            // If it's a JAR or directory, get its parent
            Path home = Files.isDirectory(codePath) ? codePath : codePath.getParent();
            if (home != null) {
                return home;
            }
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            logger.debug("Code source detection failed", e);
        }

        // 3. Last resort: user.dir
        String userDir = System.getProperty("user.dir");
        logger.warn("Falling back to user.dir for app home: {}", userDir);
        return Paths.get(userDir);
    }

    /**
     * Returns the application's installation directory.
     */
    public static Path getAppHome() {
        return APP_HOME;
    }

    /**
     * Resolve a path string against the application home.
     * If the path is absolute, returns it unchanged.
     * If relative, resolves it against the application home directory.
     *
     * @param path the path string (absolute or relative)
     * @return the resolved absolute path
     */
    public static Path resolve(String path) {
        if (path == null || path.isEmpty()) {
            return APP_HOME;
        }
        Path p = Paths.get(path);
        return p.isAbsolute() ? p : APP_HOME.resolve(p);
    }
}
