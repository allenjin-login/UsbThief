package com.superredrock.usbthief.gui;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Plugin(name = "LogBuffer", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class LogBufferAppender extends AbstractAppender {

    private static final int MAX_ENTRIES = 10000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LogEntry[] buffer = new LogEntry[MAX_ENTRIES];
    private int writeIndex = 0;
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Consumer<LogEntry> listener;

    public record LogEntry(String timestamp, String level, String loggerName, String message) {}

    protected LogBufferAppender(String name) {
        super(name, null, PatternLayout.createDefaultLayout());
    }

    @PluginFactory
    public static LogBufferAppender createAppender(@PluginAttribute("name") String name) {
        return new LogBufferAppender(name);
    }

    @Override
    public void append(LogEvent event) {
        String timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeMillis()), ZoneId.systemDefault()).format(TIME_FORMAT);
        String level = event.getLevel().name();
        String loggerName = shortenLoggerName(event.getLoggerName());
        String message = event.getMessage().getFormattedMessage();

        LogEntry entry = new LogEntry(timestamp, level, loggerName, message);

        lock.lock();
        try {
            buffer[writeIndex] = entry;
            writeIndex = (writeIndex + 1) % MAX_ENTRIES;
            if (count < MAX_ENTRIES) count++;
        } finally {
            lock.unlock();
        }

        Consumer<LogEntry> l = listener;
        if (l != null) {
            l.accept(entry);
        }
    }

    public List<LogEntry> getEntries() {
        lock.lock();
        try {
            List<LogEntry> entries = new ArrayList<>(count);
            int start = (count < MAX_ENTRIES) ? 0 : writeIndex;
            for (int i = 0; i < count; i++) {
                int idx = (start + i) % MAX_ENTRIES;
                entries.add(buffer[idx]);
            }
            return entries;
        } finally {
            lock.unlock();
        }
    }

    public void setListener(Consumer<LogEntry> listener) {
        this.listener = listener;
    }

    public void clear() {
        lock.lock();
        try {
            writeIndex = 0;
            count = 0;
        } finally {
            lock.unlock();
        }
    }

    private static String shortenLoggerName(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.split("\\.");
        if (parts.length <= 2) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i < parts.length - 2) {
                sb.append(parts[i].charAt(0)).append(".");
            } else {
                sb.append(parts[i]);
                if (i < parts.length - 1) sb.append(".");
            }
        }
        return sb.toString();
    }
}
