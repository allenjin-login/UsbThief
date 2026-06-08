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
        // 1. Process command — most reliable for native launchers (Launch4j EXE)
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

        // 2. Launch4j EXE path (set via -Dlaunch4j.exefile="%EXEFILE%")
        String launch4jExe = System.getProperty("launch4j.exefile");
        if (launch4jExe != null && !launch4jExe.isEmpty()) {
            Path parent = Paths.get(launch4jExe).getParent();
            if (parent != null) {
                return parent;
            }
        }

        // 3. jlink runtime — java.home points to the runtime image directory;
        //    the app home is its parent (where the EXE or launcher script lives)
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path jh = Paths.get(javaHome);
            if (Files.exists(jh.resolve("lib").resolve("modules"))) {
                return jh.getParent();
            }
        }

        // 4. Code source location — works for JAR on classpath (development mode)
        try {
            var location = AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            // Skip jrt:/ URIs — they represent modules inside the runtime image
            // and cannot be used as regular filesystem paths
            if (!"jrt".equalsIgnoreCase(location.getProtocol())) {
                Path codePath = Paths.get(location.toURI());
                Path home = Files.isDirectory(codePath) ? codePath : codePath.getParent();
                if (home != null) {
                    return home;
                }
            }
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            logger.debug("Code source detection failed", e);
        }

        // 5. Last resort: user.dir
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
