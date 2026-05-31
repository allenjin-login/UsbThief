package com.superredrock.usbthief.core;

import com.superredrock.usbthief.gui.LogBufferAppender;
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

public class LoggingConfig {

    public static final LogBufferAppender BUFFER_APPENDER;

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
        try {
            Path logsDir = AppPaths.resolve("logs");
            Files.createDirectories(logsDir);
            // Archive previous session logs by renaming with startup timestamp
            archiveIfExists(logsDir, "lastest.log");
            archiveIfExists(logsDir, "debug.log");
        } catch (IOException e) {
            if (!(e instanceof FileAlreadyExistsException)) {
                return;
            } else {
                throw new RuntimeException(e);
            }
        }

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
                    AppPaths.resolve("logs/lastest.log").toString(),
                    AppPaths.resolve("logs").resolve("info-%d{yyyy-MM-dd}.log").toString(), fileLayout);
            config.addAppender(file);

            RollingFileAppender debugFile = createRollingAppender(config, "DebugFile",
                    AppPaths.resolve("logs/debug.log").toString(),
                    AppPaths.resolve("logs").resolve("debug-%d{yyyy-MM-dd}.log").toString(), fileLayout);
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
     * Rename an existing log file to include a timestamp, preserving previous session logs.
     * e.g. lastest.log → lastest_20260531_143022.log
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
