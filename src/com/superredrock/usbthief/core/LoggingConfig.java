package com.superredrock.usbthief.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bootstraps the Log4j appenders (console, rolling file, debug file and the
 * in-memory {@link LogBufferAppender} surfaced in the UI).
 *
 * <p>Log files live in the {@code logs} directory below {@link AppPaths#getAppHome()}:
 * {@code latest.log} (INFO and above) and {@code debug.log} (DEBUG and above). At
 * startup the previous session's files are archived next to them as
 * {@code latest_&lt;timestamp&gt;.log} / {@code debug_&lt;timestamp&gt;.log}.</p>
 *
 * <p>Initialization is intentionally fault tolerant: a failure to prepare the log
 * directory is reported and initialization continues with a degraded appender set
 * instead of silently disabling logging.</p>
 */
public class LoggingConfig {

    public static final LogBufferAppender BUFFER_APPENDER;

    /** Name of the active session log inside the {@code logs} directory. */
    private static final String LATEST_LOG_NAME = "latest.log";

    /**
     * Misspelled name used by releases up to v1.3.0. Kept only so the file can be
     * migrated to {@link #LATEST_LOG_NAME} once (architecture-audit [20]).
     */
    private static final String LEGACY_LATEST_LOG_NAME = "lastest.log";

    private static final String DEBUG_LOG_NAME = "debug.log";

    static {
        BUFFER_APPENDER = LogBufferAppender.createAppender("LogBuffer");
    }

    private static RollingFileAppender createRollingAppender(
            Configuration config, String name, String fileName, String filePattern,
            PatternLayout layout) {
        RollingFileAppender appender = RollingFileAppender.newBuilder()
                .setConfiguration(config)
                .setName(name)
                .setLayout(layout)
                .withFileName(fileName)
                .withFilePattern(filePattern)
                .withPolicy(CompositeTriggeringPolicy.createPolicy(
                        TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build(),
                        SizeBasedTriggeringPolicy.createPolicy("50MB")))
                .build();
        if (appender != null) {
            appender.start();
        }
        return appender;
    }

    public static void initialize() {
        Path logsDir = AppPaths.resolve("logs");
        try {
            Files.createDirectories(logsDir);
        } catch (FileAlreadyExistsException e) {
            // Benign: something already occupies the path. Never abort startup for it.
            System.err.println("Log directory path already exists, continuing: " + logsDir);
        } catch (IOException e) {
            // A genuine IO failure (missing permission, read-only volume, full disk).
            // Logging is exactly what is needed to diagnose such a failure, so do NOT
            // abandon initialization: report the problem and degrade to the console /
            // in-memory appenders installed below.
            System.err.println("Failed to create log directory " + logsDir + ": " + e);
        }

        // Preserve the previous session's logs: migrate the legacy misspelled file
        // name first, then rename whatever exists with a startup timestamp.
        migrateLegacyLogName(logsDir);
        archiveIfExists(logsDir, LATEST_LOG_NAME);
        archiveIfExists(logsDir, DEBUG_LOG_NAME);

        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();

            PatternLayout consoleLayout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern("%d{HH:mm:ss} %highlight{[%level]}{WARN=red} [%logger{1.}] %msg%n")
                    .build();
            ConsoleAppender console = ConsoleAppender.createDefaultAppenderForLayout(consoleLayout);
            console.start();
            config.addAppender(console);

            PatternLayout fileLayout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%level] [%logger{1.}] %msg%n")
                    .build();
            RollingFileAppender file = createRollingAppender(config, "File",
                    logsDir.resolve(LATEST_LOG_NAME).toString(),
                    logsDir.resolve("info-%d{yyyy-MM-dd}.log").toString(), fileLayout);
            config.addAppender(file);

            RollingFileAppender debugFile = createRollingAppender(config, "DebugFile",
                    logsDir.resolve(DEBUG_LOG_NAME).toString(),
                    logsDir.resolve("debug-%d{yyyy-MM-dd}.log").toString(), fileLayout);
            config.addAppender(debugFile);

            LoggerConfig root = config.getRootLogger();
            root.getAppenders().keySet().forEach(root::removeAppender);
            root.setLevel(Level.DEBUG);
            root.addAppender(console, Level.INFO, null);
            root.addAppender(file, Level.INFO,null);
            root.addAppender(debugFile, Level.DEBUG,null);

            BUFFER_APPENDER.start();
            root.addAppender(BUFFER_APPENDER, null, null);

            ctx.updateLoggers();
        } catch (Exception e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static final java.time.format.DateTimeFormatter ARCHIVE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Rename the misspelled {@code lastest.log} written by releases up to v1.3.0 to
     * {@code latest.log} (architecture-audit [20]). Runs once: afterwards the file is
     * maintained under the correct name and archived as usual. Older archives named
     * {@code lastest_&lt;timestamp&gt;.log} are deliberately left untouched so no log
     * history is rewritten.
     */
    private static void migrateLegacyLogName(Path logsDir) {
        Path legacy = logsDir.resolve(LEGACY_LATEST_LOG_NAME);
        Path current = logsDir.resolve(LATEST_LOG_NAME);
        if (!Files.exists(legacy) || Files.exists(current)) {
            return;
        }
        try {
            Files.move(legacy, current);
        } catch (IOException e) {
            System.err.println("Failed to migrate log file name: " + legacy + " -> " + current);
        }
    }

    /**
     * Rename an existing log file to include a timestamp, preserving previous session logs.
     * e.g. latest.log → latest_20260531_143022.log
     */
    private static void archiveIfExists(Path logsDir, String fileName) {
        Path logFile = logsDir.resolve(fileName);
        if (!Files.exists(logFile)) return;
        String baseName = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String ext = fileName.contains(".") ?
                fileName.substring(fileName.lastIndexOf('.')) : "";
        String timestamp = java.time.LocalDateTime.now().format(ARCHIVE_FORMAT);
        Path archived = logsDir.resolve(baseName + "_" + timestamp + ext);
        try {
            Files.move(logFile, archived);
        } catch (IOException e) {
            System.err.println("Failed to archive log file: " + logFile + " -> " + archived);
        }
    }
}
