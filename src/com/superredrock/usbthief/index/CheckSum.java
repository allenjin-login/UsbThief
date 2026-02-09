package com.superredrock.usbthief.index;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;

public record CheckSum(byte[] context) implements Comparable<CheckSum> , Serializable {
    private static final ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(ConfigManager.getInstance().get(ConfigSchema.HASH_BUFFER_SIZE)));
    @Override
    public boolean equals(Object obj) {
        return switch (obj) {
            case CheckSum characteristics -> Arrays.equals(context, characteristics.context);
            case null, default -> false;
        };
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(context);
    }

    @Override
    public int compareTo(CheckSum o) {
        return 0;
    }

    public static CheckSum verify(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ByteBuffer buffer = bufferThreadLocal.get();
        try (FileChannel readChannel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (readChannel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        } finally {
            buffer.clear();
        }
        return new CheckSum(digest.digest());
    }
}
