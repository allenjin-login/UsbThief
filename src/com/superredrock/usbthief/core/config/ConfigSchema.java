package com.superredrock.usbthief.core.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.superredrock.usbthief.core.config.ConfigEntry.*;

/**
 * Schema containing all configuration entries for the application.
 * This class acts as a registry for all configuration keys and their metadata.
 */
public class ConfigSchema {
    // Thread pool configuration
    public static final ConfigEntry<Integer> CORE_POOL_SIZE =
            intEntry("corePoolSize", "Minimum number of threads in the thread pool", 2, "Thread Pool");

    public static final ConfigEntry<Integer> MAX_POOL_SIZE =
            intEntry("maxPoolSize", "Maximum number of threads in the thread pool", 8, "Thread Pool");

    public static final ConfigEntry<Integer> KEEP_ALIVE_TIME_SECONDS =
            intEntry("keepAliveTimeSeconds", "Idle thread keep-alive time in seconds", 60, "Thread Pool");

    public static final ConfigEntry<Integer> TASK_QUEUE_CAPACITY =
            intEntry("taskQueueCapacity", "Maximum number of tasks in the queue", 1024, "Thread Pool");

    // Device scanner configuration
    public static final ConfigEntry<Integer> INITIAL_DELAY_SECONDS =
            intEntry("initialDelaySeconds", "Initial delay before first device scan (seconds)", 10, "Device Scanner");

    public static final ConfigEntry<Integer> DELAY_SECONDS =
            intEntry("delaySeconds", "Interval between device scans (seconds)", 500, "Device Scanner");

    // Index management configuration
    public static final ConfigEntry<Integer> SAVE_INITIAL_DELAY_SECONDS =
            intEntry("saveInitialDelaySeconds", "Initial delay before first index save (seconds)", 30, "Index Management");

    public static final ConfigEntry<Integer> SAVE_DELAY_SECONDS =
            intEntry("saveDelaySeconds", "Interval between index saves (seconds)", 60, "Index Management");

    public static final ConfigEntry<String> INDEX_PATH =
            stringEntry("indexPath", "Path to the index file (relative or absolute)", "index.obj", "Index Management");

    // File copy configuration
    public static final ConfigEntry<Integer> BUFFER_SIZE =
            intEntry("bufferSize", "Buffer size for file copying (bytes)", 16 * 1024, "File Copy");

    public static final ConfigEntry<Integer> HASH_BUFFER_SIZE =
            intEntry("hashBufferSize", "Buffer size for hash calculation (bytes)", 1024, "File Copy");

    public static final ConfigEntry<Integer> MAX_FILE_SIZE =
            intEntry("maxFileSize", "Maximum file size to copy (bytes)", 1000 * 1024 * 1024, "File Copy");

    public static final ConfigEntry<Integer> RETRY_COUNT =
            intEntry("retryCount", "Number of retry attempts for failed operations", 5, "File Copy");

    public static final ConfigEntry<Long> TIMEOUT_MILLIS =
            longEntry("timeoutMillis", "Timeout for retry queue polling (milliseconds)", 100L, "File Copy");

    // File watch configuration
    public static final ConfigEntry<Boolean> WATCH_ENABLED =
            booleanEntry("watchEnabled", "Enable/disable real-time file monitoring", true, "File Watch");

    public static final ConfigEntry<Integer> WATCH_THRESHOLD =
            intEntry("watchThreshold", "Number of file changes before triggering copy", 10, "File Watch");

    public static final ConfigEntry<Integer> WATCH_RESET_INTERVAL_SECONDS =
            intEntry("watchResetIntervalSeconds", "Interval to reset change counter (seconds)", 60, "File Watch");

    // Copy rate limiting configuration
    public static final ConfigEntry<Long> COPY_RATE_LIMIT =
            longEntry("copyRateLimit", "Copy rate limit in bytes per second (0 = no limit)", 0L, "Rate Limiting");

    public static final ConfigEntry<Long> COPY_RATE_BURST_SIZE =
            longEntry("copyRateBurstSize", "Copy rate burst size in bytes", 16L * 1024 * 1024, "Rate Limiting");

    public static final ConfigEntry<Boolean> RATE_LIMITER_LOAD_ADJUSTMENT_ENABLED =
            booleanEntry("rateLimiter.loadAdjustmentEnabled", "Enable load-aware rate limit adjustment", true, "Rate Limiting");

    public static final ConfigEntry<Long> COPY_RATE_LIMIT_BASE =
            longEntry("copyRateLimitBase", "Base copy rate limit in bytes per second (0 = no limit)", 0L, "Rate Limiting");

    public static final ConfigEntry<Integer> RATE_LIMITER_MEDIUM_MULTIPLIER =
            intEntry("rateLimiter.mediumMultiplier", "Rate limit multiplier at MEDIUM load (percentage)", 70, "Rate Limiting");

    public static final ConfigEntry<Integer> RATE_LIMITER_HIGH_MULTIPLIER =
            intEntry("rateLimiter.highMultiplier", "Rate limit multiplier at HIGH load (percentage)", 40, "Rate Limiting");

    // Path configuration
    public static final ConfigEntry<String> WORK_PATH =
            stringEntry("workPath", "Working directory for storing copied files", "devices", "Paths");

    // UI configuration
    public static final ConfigEntry<Integer> FILE_HISTORY_MAX_ENTRIES =
            intEntry("fileHistoryMaxEntries", "Maximum number of file history entries to keep in memory", 10000, "UI");

    // Window visibility configuration
    public static final ConfigEntry<Boolean> START_HIDDEN =
            booleanEntry("gui.startHidden", "Start application with window hidden (runs in background)", false, "Window");

    public static final ConfigEntry<Boolean> SHOW_IN_TASKBAR =
            booleanEntry("gui.showInTaskbar", "Show window in taskbar", true, "Window");

    public static final ConfigEntry<Boolean> ALWAYS_HIDDEN =
            booleanEntry("gui.alwaysHidden", "Always keep window hidden (start hidden and minimize to tray only)", false, "Window");

    // Minimize/Close to tray behavior configuration
    public static final ConfigEntry<String> MINIMIZE_ACTION =
            stringEntry("gui.minimizeAction", "Action when minimizing: ASK, MINIMIZE_TO_TRAY, MINIMIZE_NORMAL", "ASK", "Window");

    public static final ConfigEntry<String> CLOSE_ACTION =
            stringEntry("gui.closeAction", "Action when closing: ASK, MINIMIZE_TO_TRAY, EXIT", "ASK", "Window");

    // Blacklist configuration
    @Deprecated
    public static final ConfigEntry<List<String>> DEVICE_BLACKLIST =
            listEntry("deviceBlacklist", "Device blacklist by path (deprecated, use deviceBlacklistBySerial)", List.of(), "Blacklist");

    public static final ConfigEntry<List<String>> DEVICE_BLACKLIST_BY_SERIAL =
            listEntry("deviceBlacklistBySerial", "Device blacklist by serial number", List.of(), "Blacklist");

    // TaskScheduler configuration
    public static final ConfigEntry<String> PRIORITY_RULES =
            stringEntry("scheduler.priorityRules", "Priority rules configuration (JSON format) - reserved for future use", "{}", "Task Scheduler");

    public static final ConfigEntry<Long> SCHEDULER_INITIAL_DELAY_MS =
            longEntry("scheduler.initialDelayMs", "Initial delay before first scheduler tick (ms)", 1000L, "Task Scheduler");

    public static final ConfigEntry<Long> SCHEDULER_TICK_INTERVAL_MS =
            longEntry("scheduler.tickIntervalMs", "Interval between scheduler ticks (ms)", 500L, "Task Scheduler");

    public static final ConfigEntry<Integer> SCHEDULER_ACCUMULATION_MAX_QUEUE =
            intEntry("scheduler.accumulationMaxQueue", "Maximum queue size during high-load accumulation", 2000, "Task Scheduler");

    public static final ConfigEntry<Integer> SCHEDULER_LOW_BATCH_SIZE =
            intEntry("scheduler.lowBatchSize", "Number of tasks to submit per tick at LOW load", 30, "Task Scheduler");

    public static final ConfigEntry<Integer> SCHEDULER_MEDIUM_BATCH_SIZE =
            intEntry("scheduler.mediumBatchSize", "Number of tasks to submit per tick at MEDIUM load", 50, "Task Scheduler");

    public static final ConfigEntry<Integer> LOAD_QUEUE_WEIGHT_PERCENT =
            intEntry("scheduler.load.queueWeightPercent", "Queue length weight percentage (0-100)", 35, "Task Scheduler");

    public static final ConfigEntry<Integer> LOAD_SPEED_WEIGHT_PERCENT =
            intEntry("scheduler.load.speedWeightPercent", "Copy speed weight percentage (0-100)", 35, "Task Scheduler");

    public static final ConfigEntry<Integer> LOAD_THREAD_WEIGHT_PERCENT =
            intEntry("scheduler.load.threadWeightPercent", "Thread activity weight percentage (0-100)", 15, "Task Scheduler");

    public static final ConfigEntry<Integer> LOAD_REJECTION_WEIGHT_PERCENT =
            intEntry("scheduler.load.rejectionWeightPercent", "Rejection rate weight percentage (0-100)", 15, "Task Scheduler");

    public static final ConfigEntry<Integer> LOAD_HIGH_THRESHOLD =
            intEntry("scheduler.load.highThreshold", "High load threshold (0-100)", 70, "Task Scheduler");

    public static final ConfigEntry<Integer> LOAD_LOW_THRESHOLD =
            intEntry("scheduler.load.lowThreshold", "Low load threshold (0-100)", 40, "Task Scheduler");

    public static final ConfigEntry<Integer> SCHEDULER_MEDIUM_BATCH =
            intEntry("scheduler.batch.medium", "Medium load batch size", 20, "Task Scheduler");

    public static final ConfigEntry<Integer> SCHEDULER_HIGH_BATCH =
            intEntry("scheduler.batch.high", "High load batch size", 50, "Task Scheduler");

    public static final ConfigEntry<Integer> SCHEDULER_HIGH_PRIORITY_THRESHOLD =
            intEntry("scheduler.highPriorityThreshold", "High priority threshold (>= this value triggers immediate dispatch on low load)", 8, "Task Scheduler");

    // File filter configuration
    public static final ConfigEntry<Long> FILE_FILTER_MAX_SIZE =
            longEntry("fileFilter.maxSize", "Maximum file size to copy (bytes)", 100L * 1024 * 1024, "File Filter");

    public static final ConfigEntry<Boolean> FILE_FILTER_TIME_ENABLED =
            booleanEntry("fileFilter.timeEnabled", "Enable time-based file filtering", false, "File Filter");

    public static final ConfigEntry<Long> FILE_FILTER_TIME_VALUE =
            longEntry("fileFilter.timeValue", "Time filter value (combined with timeUnit)", 24L, "File Filter");

    public static final ConfigEntry<String> FILE_FILTER_TIME_UNIT =
            stringEntry("fileFilter.timeUnit", "Time filter unit: HOURS, DAYS, WEEKS, MONTHS, YEARS", "HOURS", "File Filter");

    public static final ConfigEntry<Boolean> FILE_FILTER_INCLUDE_HIDDEN =
            booleanEntry("fileFilter.includeHidden", "Include hidden files in copy", false, "File Filter");

    public static final ConfigEntry<Boolean> FILE_FILTER_SKIP_SYMLINKS =
            booleanEntry("fileFilter.skipSymlinks", "Skip symbolic links during copy", true, "File Filter");

    public static final ConfigEntry<Boolean> FILE_FILTER_ALLOW_NO_EXT =
            booleanEntry("fileFilter.allowNoExtension", "Allow files without extension", true, "File Filter");

    // Suffix filter configuration
    public static final ConfigEntry<String> SUFFIX_FILTER_MODE =
            stringEntry("suffixFilter.mode", "Suffix filter mode: NONE, WHITELIST, or BLACKLIST", "NONE", "Suffix Filter");

    public static final ConfigEntry<List<String>> SUFFIX_FILTER_WHITELIST =
            listEntry("suffixFilter.whitelist", "Whitelist of file extensions (without dot)", List.of(), "Suffix Filter");

    public static final ConfigEntry<List<String>> SUFFIX_FILTER_BLACKLIST =
            listEntry("suffixFilter.blacklist", "Blacklist of file extensions (without dot)", List.of(), "Suffix Filter");

    public static final ConfigEntry<String> SUFFIX_FILTER_PRESET =
            stringEntry("suffixFilter.preset", "Selected preset name (empty string for none)", "", "Suffix Filter");

    // Storage management configuration
    public static final ConfigEntry<Long> STORAGE_RESERVED_BYTES =
            longEntry("storage.reservedBytes", "Minimum free space to preserve (bytes)", 10L * 1024 * 1024 * 1024, "Storage Management");

    public static final ConfigEntry<Long> STORAGE_MAX_BYTES =
            longEntry("storage.maxBytes", "Maximum space for copied files (bytes)", 100L * 1024 * 1024 * 1024, "Storage Management");

    public static final ConfigEntry<Integer> SNIFFER_WAIT_NORMAL_MINUTES =
            intEntry("sniffer.waitNormalMinutes", "Wait time after normal completion (minutes)", 30, "Storage Management");

    public static final ConfigEntry<Integer> SNIFFER_WAIT_ERROR_MINUTES =
            intEntry("sniffer.waitErrorMinutes", "Wait time after error (minutes)", 5, "Storage Management");

    public static final ConfigEntry<String> RECYCLER_STRATEGY =
            stringEntry("recycler.strategy", "Recycler strategy: TIME_FIRST, SIZE_FIRST, or AUTO", "AUTO", "Storage Management");

    public static final ConfigEntry<Integer> RECYCLER_PROTECTED_AGE_HOURS =
            intEntry("recycler.protectedAgeHours", "Protect files newer than X hours from deletion", 1, "Storage Management");

    public static final ConfigEntry<Boolean> STORAGE_WARNING_ENABLED =
            booleanEntry("storage.warningEnabled", "Log warning when storage space is critical", true, "Storage Management");

    // All entries registry
    private static final Map<String, ConfigEntry<?>> ALL_ENTRIES = new ConcurrentHashMap<>();

    static {
        registerEntry(CORE_POOL_SIZE);
        registerEntry(MAX_POOL_SIZE);
        registerEntry(KEEP_ALIVE_TIME_SECONDS);
        registerEntry(TASK_QUEUE_CAPACITY);
        registerEntry(INITIAL_DELAY_SECONDS);
        registerEntry(DELAY_SECONDS);
        registerEntry(SAVE_INITIAL_DELAY_SECONDS);
        registerEntry(SAVE_DELAY_SECONDS);
        registerEntry(INDEX_PATH);
        registerEntry(BUFFER_SIZE);
        registerEntry(HASH_BUFFER_SIZE);
        registerEntry(MAX_FILE_SIZE);
        registerEntry(RETRY_COUNT);
        registerEntry(TIMEOUT_MILLIS);
        registerEntry(WATCH_ENABLED);
        registerEntry(WATCH_THRESHOLD);
        registerEntry(WATCH_RESET_INTERVAL_SECONDS);
        registerEntry(COPY_RATE_LIMIT);
        registerEntry(COPY_RATE_BURST_SIZE);
        registerEntry(RATE_LIMITER_LOAD_ADJUSTMENT_ENABLED);
        registerEntry(COPY_RATE_LIMIT_BASE);
        registerEntry(RATE_LIMITER_MEDIUM_MULTIPLIER);
        registerEntry(RATE_LIMITER_HIGH_MULTIPLIER);
        registerEntry(WORK_PATH);
        registerEntry(FILE_HISTORY_MAX_ENTRIES);
        registerEntry(DEVICE_BLACKLIST);
        registerEntry(DEVICE_BLACKLIST_BY_SERIAL);
        registerEntry(START_HIDDEN);
        registerEntry(SHOW_IN_TASKBAR);
        registerEntry(ALWAYS_HIDDEN);
        registerEntry(MINIMIZE_ACTION);
        registerEntry(CLOSE_ACTION);
        registerEntry(PRIORITY_RULES);
        registerEntry(SCHEDULER_INITIAL_DELAY_MS);
        registerEntry(SCHEDULER_TICK_INTERVAL_MS);
        registerEntry(SCHEDULER_ACCUMULATION_MAX_QUEUE);
        registerEntry(SCHEDULER_LOW_BATCH_SIZE);
        registerEntry(SCHEDULER_MEDIUM_BATCH_SIZE);
        registerEntry(LOAD_QUEUE_WEIGHT_PERCENT);
        registerEntry(LOAD_SPEED_WEIGHT_PERCENT);
        registerEntry(LOAD_THREAD_WEIGHT_PERCENT);
        registerEntry(LOAD_REJECTION_WEIGHT_PERCENT);
        registerEntry(LOAD_HIGH_THRESHOLD);
        registerEntry(LOAD_LOW_THRESHOLD);
        registerEntry(SCHEDULER_MEDIUM_BATCH);
        registerEntry(SCHEDULER_HIGH_BATCH);
        registerEntry(SCHEDULER_HIGH_PRIORITY_THRESHOLD);
        registerEntry(FILE_FILTER_MAX_SIZE);
        registerEntry(FILE_FILTER_TIME_ENABLED);
        registerEntry(FILE_FILTER_TIME_VALUE);
        registerEntry(FILE_FILTER_TIME_UNIT);
        registerEntry(FILE_FILTER_INCLUDE_HIDDEN);
        registerEntry(FILE_FILTER_SKIP_SYMLINKS);
        registerEntry(FILE_FILTER_ALLOW_NO_EXT);
        registerEntry(SUFFIX_FILTER_MODE);
        registerEntry(SUFFIX_FILTER_WHITELIST);
        registerEntry(SUFFIX_FILTER_BLACKLIST);
        registerEntry(SUFFIX_FILTER_PRESET);
        registerEntry(STORAGE_RESERVED_BYTES);
        registerEntry(STORAGE_MAX_BYTES);
        registerEntry(SNIFFER_WAIT_NORMAL_MINUTES);
        registerEntry(SNIFFER_WAIT_ERROR_MINUTES);
        registerEntry(RECYCLER_STRATEGY);
        registerEntry(RECYCLER_PROTECTED_AGE_HOURS);
        registerEntry(STORAGE_WARNING_ENABLED);
    }

    private ConfigSchema() {
        // Static utility class
    }

    /**
     * Register a configuration entry.
     */
    private static void registerEntry(ConfigEntry<?> entry) {
        ALL_ENTRIES.put(entry.key(), entry);
    }

    /**
     * Get all registered configuration entries.
     */
    public static Map<String, ConfigEntry<?>> getAllEntries() {
        return Map.copyOf(ALL_ENTRIES);
    }

    /**
     * Get all entries grouped by category.
     */
    public static Map<String, List<ConfigEntry<?>>> getEntriesByCategory() {
        return ALL_ENTRIES.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(ConfigEntry::category));
    }

    /**
     * Get entry by key.
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigEntry<T> getEntry(String key) {
        return (ConfigEntry<T>) ALL_ENTRIES.get(key);
    }
}
