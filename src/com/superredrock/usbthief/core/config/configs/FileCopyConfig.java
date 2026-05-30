package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileCopyConfig {
    public static final String CATEGORY = "File Copy";

    public static final ConfigEntry<Integer> BUFFER_SIZE =
            intEntry("bufferSize", "Buffer size for file copying (bytes)", 16 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> HASH_BUFFER_SIZE =
            intEntry("hashBufferSize", "Buffer size for hash calculation (bytes)", 1024, CATEGORY);

    public static final ConfigEntry<Integer> MAX_FILE_SIZE =
            intEntry("maxFileSize", "Maximum file size to copy (bytes)", 1000 * 1024 * 1024, CATEGORY);

    public static final ConfigEntry<Integer> RETRY_COUNT =
            intEntry("retryCount", "Number of retry attempts for failed operations", 5, CATEGORY);

    public static final ConfigEntry<Long> TIMEOUT_MILLIS =
            longEntry("timeoutMillis", "Timeout for retry queue polling (milliseconds)", 100L, CATEGORY);

    public static final ConfigEntry<Boolean> COPY_VERIFY_ENABLED =
            booleanEntry("copyVerifyEnabled", "Enable pre-copy verification (checksum + dedup before copy)", true, CATEGORY);

    public static final ConfigEntry<String> HASH_ALGORITHM =
            stringEntry("hashAlgorithm", "Hash algorithm: SHA-256, MD5, CRC-8, CRC-16, CRC-32, CRC-64", "SHA-256", CATEGORY);

    private FileCopyConfig() {}
}
