package com.superredrock.usbthief.statistics;

import com.superredrock.usbthief.core.SizeFormatter;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public final class Statistics {
    private static final Logger logger = Logger.getLogger(Statistics.class.getName());
    private static volatile Statistics INSTANCE;

    private static final String KEY_TOTAL_FILES = "totalFilesCopied";
    private static final String KEY_TOTAL_BYTES = "totalBytesCopied";
    private static final String KEY_TOTAL_ERRORS = "totalErrors";
    private static final String KEY_TOTAL_FOLDERS = "totalFoldersCopied";
    private static final String KEY_TOTAL_DEVICES = "totalDevicesCopied";
    private static final String KEY_DEVICE_SERIALS = "deviceSerials";
    private static final String KEY_EXT_COUNT = "ext.";

    private final AtomicLong totalFilesCopied = new AtomicLong(0);
    private final AtomicLong totalBytesCopied = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalFoldersCopied = new AtomicLong(0);
    private final AtomicLong totalDevicesCopied = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> extensionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<String, Boolean> copiedDeviceSerials = ConcurrentHashMap.newKeySet();

    private final AtomicLong sessionBytesDiscovered = new AtomicLong(0);
    private final AtomicLong sessionBytesCopied = new AtomicLong(0);

    private final Preferences prefs;
    private volatile boolean dirty;

    private Statistics() {
        this.prefs = Preferences.userNodeForPackage(Statistics.class);
        load();
        registerEventListeners();
    }

    public static Statistics getInstance() {
        if (INSTANCE == null) {
            synchronized (Statistics.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Statistics();
                }
            }
        }
        return INSTANCE;
    }

    private void registerEventListeners() {
        EventBus.getInstance().register(CopyCompletedEvent.class, this::onCopyCompleted);
        EventBus.getInstance().register(FileDiscoveredEvent.class, this::onFileDiscovered);
    }

    private void onFileDiscovered(FileDiscoveredEvent event) {
        sessionBytesDiscovered.addAndGet(event.fileSize());
    }

    private void onCopyCompleted(CopyCompletedEvent event) {
        String serial = event.deviceSerial();
        
        if (Files.isDirectory(event.sourcePath())) {
            if (event.isSuccess()) {
                totalFoldersCopied.incrementAndGet();
                if (!serial.isEmpty() && copiedDeviceSerials.add(serial)) {
                    totalDevicesCopied.incrementAndGet();
                }
                markDirty();
            }
        } else {
            if (event.isSuccess()) {
                totalFilesCopied.incrementAndGet();
                totalBytesCopied.addAndGet(event.bytesCopied());
                sessionBytesCopied.addAndGet(event.bytesCopied());

                if (!serial.isEmpty() && copiedDeviceSerials.add(serial)) {
                    totalDevicesCopied.incrementAndGet();
                }

                String fileName = event.sourcePath().getFileName().toString();
                String extension = getFileExtension(fileName);
                if (extension != null) {
                    extensionCounts.computeIfAbsent(extension, _ -> new AtomicLong(0)).incrementAndGet();
                }
                markDirty();
            } else if (event.isFailure()) {
                totalErrors.incrementAndGet();
                markDirty();
            }
        }
    }

    private String getFileExtension(String fileName) {
        // Skip hidden files (starting with .)
        if (fileName.startsWith(".")) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return null;
    }

    private void markDirty() {
        dirty = true;
    }

    public void load() {
        totalFilesCopied.set(prefs.getLong(KEY_TOTAL_FILES, 0));
        totalBytesCopied.set(prefs.getLong(KEY_TOTAL_BYTES, 0));
        totalErrors.set(prefs.getLong(KEY_TOTAL_ERRORS, 0));
        totalFoldersCopied.set(prefs.getLong(KEY_TOTAL_FOLDERS, 0));
        totalDevicesCopied.set(prefs.getLong(KEY_TOTAL_DEVICES, 0));

        try {
            String serialsStr = prefs.get(KEY_DEVICE_SERIALS, "");
            if (!serialsStr.isEmpty()) {
                copiedDeviceSerials.addAll(java.util.Arrays.asList(serialsStr.split(",")));
            }
        } catch (Exception e) {
            logger.warning("Failed to load device serials: " + e.getMessage());
        }

        try {
            for (String key : prefs.keys()) {
                if (key.startsWith(KEY_EXT_COUNT)) {
                    String ext = key.substring(KEY_EXT_COUNT.length());
                    long count = prefs.getLong(key, 0);
                    if (count > 0) {
                        extensionCounts.put(ext, new AtomicLong(count));
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load extension counts: " + e.getMessage());
        }

        logger.info("Statistics loaded: " + totalFilesCopied.get() + " files, " + SizeFormatter.format(totalBytesCopied.get()));
    }

    public void save() {
        if (!dirty) return;

        prefs.putLong(KEY_TOTAL_FILES, totalFilesCopied.get());
        prefs.putLong(KEY_TOTAL_BYTES, totalBytesCopied.get());
        prefs.putLong(KEY_TOTAL_ERRORS, totalErrors.get());
        prefs.putLong(KEY_TOTAL_FOLDERS, totalFoldersCopied.get());
        prefs.putLong(KEY_TOTAL_DEVICES, totalDevicesCopied.get());
        prefs.put(KEY_DEVICE_SERIALS, String.join(",", copiedDeviceSerials));

        extensionCounts.forEach((ext, count) -> {
            prefs.putLong(KEY_EXT_COUNT + ext, count.get());
        });

        dirty = false;
        logger.info("Statistics saved");
    }

    public long getTotalFilesCopied() { return totalFilesCopied.get(); }
    public long getTotalBytesCopied() { return totalBytesCopied.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public long getTotalFoldersCopied() { return totalFoldersCopied.get(); }
    public long getTotalDevicesCopied() { return totalDevicesCopied.get(); }
    public int getCopiedDeviceCount() { return copiedDeviceSerials.size(); }
    public Map<String, Long> getExtensionCounts() {
        var result = new ConcurrentHashMap<String, Long>();
        extensionCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    public long getSessionBytesDiscovered() { return sessionBytesDiscovered.get(); }
    public long getSessionBytesCopied() { return sessionBytesCopied.get(); }
    public int getProgressPercentage() {
        long discovered = sessionBytesDiscovered.get();
        if (discovered == 0) return 0;
        return (int) (sessionBytesCopied.get() * 100 / discovered);
    }

    public void resetSession() {
        sessionBytesDiscovered.set(0);
        sessionBytesCopied.set(0);
    }

    public void resetAll() {
        totalFilesCopied.set(0);
        totalBytesCopied.set(0);
        totalErrors.set(0);
        totalFoldersCopied.set(0);
        totalDevicesCopied.set(0);
        extensionCounts.clear();
        copiedDeviceSerials.clear();

        prefs.putLong(KEY_TOTAL_FILES, 0);
        prefs.putLong(KEY_TOTAL_BYTES, 0);
        prefs.putLong(KEY_TOTAL_ERRORS, 0);
        prefs.putLong(KEY_TOTAL_FOLDERS, 0);
        prefs.putLong(KEY_TOTAL_DEVICES, 0);
        prefs.put(KEY_DEVICE_SERIALS, "");

        try {
            for (String key : prefs.keys()) {
                if (key.startsWith(KEY_EXT_COUNT)) {
                    prefs.remove(key);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to clear extension counts: " + e.getMessage());
        }

        resetSession();
        dirty = false;
        logger.info("Statistics reset");
    }
}
