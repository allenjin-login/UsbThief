package com.superredrock.usbthief.core;

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

import com.superredrock.usbthief.gui.LogBufferAppender;

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

            PatternLayout consoleLayout = PatternLayout.newBuilder()
                    .withPattern("%d{HH:mm:ss} [%-5level] [%logger{1.}] %msg%n")
                    .withConfiguration(config)
                    .build();
            ConsoleAppender console = ConsoleAppender.newBuilder()
                    .setName("Console")
                    .setLayout(consoleLayout)
                    .build();
            console.start();
            config.addAppender(console);

            PatternLayout fileLayout = PatternLayout.newBuilder()
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%-5level] [%logger{1.}] %msg%n")
                    .withConfiguration(config)
                    .build();

            CompositeTriggeringPolicy policies = CompositeTriggeringPolicy.createPolicy(
                    TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build(),
                    SizeBasedTriggeringPolicy.createPolicy("50MB"));

            DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                    .withMax("10")
                    .withConfig(config)
                    .build();

            RollingFileAppender file = RollingFileAppender.newBuilder()
                    .setName("File")
                    .withFileName("logs/usbthief.log")
                    .withFilePattern("logs/usbthief-%d{yyyy-MM-dd_HH-mm-ss}.log")
                    .setLayout(fileLayout)
                    .withPolicy(policies)
                    .withStrategy(strategy)
                    .build();
            file.start();
            config.addAppender(file);

            BUFFER_APPENDER.start();
            config.addAppender(BUFFER_APPENDER);

            LoggerConfig root = config.getRootLogger();
            root.setLevel(Level.INFO);
            root.addAppender(console, null, null);
            root.addAppender(file, null, null);
            root.addAppender(BUFFER_APPENDER, null, null);

            ctx.updateLoggers();
        } catch (Exception e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
        }
    }
}
