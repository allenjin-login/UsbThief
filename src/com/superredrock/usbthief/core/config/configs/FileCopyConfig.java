package com.superredrock.usbthief.core.config.configs;

import com.superredrock.usbthief.core.config.ConfigEntry;
import static com.superredrock.usbthief.core.config.ConfigEntry.*;

public final class FileCopyConfig {
    public static final String CATEGORY = "File Copy";

    /**
     * Size of a single copy chunk.
     *
     * <p>Each chunk costs one {@code read} plus one {@code write} syscall, so the default was raised
     * from 16 KB (which needed 64 syscall pairs per MB) to 1 MB (one syscall pair per MB). Values
     * below {@code CopyTask.MIN_BUFFER_SIZE} / above {@code CopyTask.MAX_BUFFER_SIZE} are clamped
     * when a copy starts.</p>
     */
    public static final ConfigEntry<Integer> BUFFER_SIZE =
            intEntry("bufferSize", "Buffer size for file copying (bytes)", 1024 * 1024, CATEGORY);

    /**
     * Size of the read buffer used while hashing a file.
     *
     * <p>PF-19: raised from 1 KB to 64 KB. {@code VerifyTask} is currently not wired into the copy
     * path, so this has no runtime effect today; if checksum verification is ever connected, 1 KB
     * would cost 1024 {@code read} syscalls per MB (≈100 million for a 100 GB copy).</p>
     */
    public static final ConfigEntry<Integer> HASH_BUFFER_SIZE =
            intEntry("hashBufferSize", "Buffer size for hash calculation (bytes)", 64 * 1024, CATEGORY);

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
