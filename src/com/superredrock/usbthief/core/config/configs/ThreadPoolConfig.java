package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class ThreadPoolConfig {
    public static final String CATEGORY = "Thread Pool";

    public static final ConfigEntry<Integer> CORE_POOL_SIZE =
            intEntry("corePoolSize", "Minimum number of threads in the thread pool", 2, CATEGORY);

    public static final ConfigEntry<Integer> MAX_POOL_SIZE =
            intEntry("maxPoolSize", "Maximum number of threads in the thread pool", Runtime.getRuntime().availableProcessors(), CATEGORY);

    public static final ConfigEntry<Integer> KEEP_ALIVE_TIME_SECONDS =
            intEntry("keepAliveTimeSeconds", "Idle thread keep-alive time in seconds", 60, CATEGORY);

    public static final ConfigEntry<Integer> TASK_QUEUE_CAPACITY =
            intEntry("taskQueueCapacity", "Maximum number of tasks in the queue", 1024, CATEGORY);

    private ThreadPoolConfig() {}
}
