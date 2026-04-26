package com.superredrock.usbthief.core;

import com.superredrock.usbthief.gui.LogBufferAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;

public class LoggingConfig {

    public static final LogBufferAppender BUFFER_APPENDER;

    static {
        BUFFER_APPENDER = LogBufferAppender.createAppender("LogBuffer");
    }

    public static void initialize() {
        new File("logs").mkdirs();

        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();

            // Console appender: INFO+ only
            PatternLayout consoleLayout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern("%d{HH:mm:ss} [%-5level] [%logger{1.}] %msg%n")
                    .build();
            ConsoleAppender console = ConsoleAppender.createDefaultAppenderForLayout(consoleLayout);
            console.start();
            config.addAppender(console);

            // Main log file: INFO+ only
            PatternLayout fileLayout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%-5level] [%logger{1.}] %msg%n")
                    .build();
            RollingFileAppender file = RollingFileAppender.newBuilder()
                    .setName("File")
                    .setLayout(fileLayout)
                    .withFileName("logs/usbthief.log")
                    .withFilePattern("logs/usbthief-%d{yyyy-MM-dd}.log")
                    .withPolicy(CompositeTriggeringPolicy.createPolicy(
                            TimeBasedTriggeringPolicy.createPolicy("1", "true"),
                            SizeBasedTriggeringPolicy.createPolicy("50MB")))
                    .withStrategy(DefaultRolloverStrategy.createStrategy("7", null, null,
                            null, null, true, config))
                    .build();
            file.start();
            config.addAppender(file);

            // Debug log file: all levels
            PatternLayout debugLayout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%-5level] [%logger{1.}] %msg%n")
                    .build();
            RollingFileAppender debugFile = RollingFileAppender.newBuilder()
                    .setName("DebugFile")
                    .setLayout(debugLayout)
                    .withFileName("logs/debug.log")
                    .withFilePattern("logs/debug-%d{yyyy-MM-dd}.log")
                    .withPolicy(CompositeTriggeringPolicy.createPolicy(
                            TimeBasedTriggeringPolicy.createPolicy("1", "true"),
                            SizeBasedTriggeringPolicy.createPolicy("50MB")))
                    .withStrategy(DefaultRolloverStrategy.createStrategy("7", null, null,
                            null, null, true, config))
                    .build();
            debugFile.start();
            config.addAppender(debugFile);

            // Root logger: DEBUG level, per-appender level via addAppender(appender, level, filter)
            LoggerConfig root = config.getRootLogger();
            root.setLevel(Level.DEBUG);
            root.addAppender(console, Level.INFO, null);
            root.addAppender(file, Level.INFO, null);
            root.addAppender(debugFile, null, null);

            // Start LogBufferAppender
            BUFFER_APPENDER.start();
            root.addAppender(BUFFER_APPENDER, null, null);

            ctx.updateLoggers();

        } catch (Exception e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
        }
    }
}
