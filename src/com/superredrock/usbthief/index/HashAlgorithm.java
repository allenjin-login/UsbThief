package com.superredrock.usbthief.index;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.zip.Checksum;

/**
 * Supported hash algorithms for file checksum computation.
 */
public enum HashAlgorithm {
    SHA_256("SHA-256", 32),
    MD5("MD5", 16),
    CRC_8("CRC-8", 1),
    CRC_16("CRC-16", 2),
    CRC_32("CRC-32", 4),
    CRC_64("CRC-64", 8);

    private final String id;
    private final int outputLength;

    HashAlgorithm(String id, int outputLength) {
        this.id = id;
        this.outputLength = outputLength;
    }

    public String id() { return id; }
    public int outputLength() { return outputLength; }

    /**
     * Resolve an algorithm by its string ID. Falls back to SHA-256 on unknown input.
     */
    public static HashAlgorithm fromId(String id) {
        for (var algo : values()) {
            if (algo.id.equalsIgnoreCase(id)) {
                return algo;
            }
        }
        return SHA_256;
    }

    /**
     * Compute a file checksum using this algorithm.
     */
    public CheckSum compute(Path path, ByteBuffer buffer) throws java.io.IOException {
        byte[] hash = switch (this) {
            case SHA_256, MD5 -> computeMessageDigest(path, buffer);
            case CRC_8 -> computeWithChecksum(path, buffer, new CRC8());
            case CRC_16 -> computeWithChecksum(path, buffer, new CRC16());
            case CRC_32 -> computeWithChecksum(path, buffer, new CRC32J());
            case CRC_64 -> computeWithChecksum(path, buffer, new CRC64());
        };
        return new CheckSum(hash);
    }

    private byte[] computeMessageDigest(Path path, ByteBuffer buffer) throws java.io.IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(id);
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                while (ch.read(buffer) != -1) {
                    if (Thread.interrupted()) throw new java.io.IOException("Hash computation interrupted");
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] computeWithChecksum(Path path, ByteBuffer buffer, Checksum checksum) throws java.io.IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            while (ch.read(buffer) != -1) {
                if (Thread.interrupted()) throw new java.io.IOException("Hash computation interrupted");
                buffer.flip();
                if (buffer.hasArray()) {
                    checksum.update(buffer.array(), buffer.arrayOffset(), buffer.limit());
                } else {
                    while (buffer.hasRemaining()) checksum.update(buffer.get());
                }
                buffer.clear();
            }
        }
        return longToBytes(checksum.getValue(), outputLength);
    }

    private static byte[] longToBytes(long value, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[length - 1 - i] = (byte) (value >> (i * 8));
        }
        return result;
    }

    // --- CRC implementations ---

    private static final class CRC8 implements Checksum {
        private static final int POLY = 0x07;
        private static final int[] TABLE = new int[256];
        static {
            for (int i = 0; i < 256; i++) {
                int crc = i;
                for (int j = 0; j < 8; j++)
                    crc = (crc & 1) != 0 ? (crc >>> 1) ^ POLY : crc >>> 1;
                TABLE[i] = crc & 0xFF;
            }
        }
        private int value;

        @Override public void update(int b) { value = TABLE[(value ^ b) & 0xFF]; }
        @Override public void update(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) update(b[i]);
        }
        @Override public long getValue() { return value & 0xFFL; }
        @Override public void reset() { value = 0; }
    }

    private static final class CRC16 implements Checksum {
        private static final int POLY = 0x1021;
        private static final int[] TABLE = new int[256];
        static {
            for (int i = 0; i < 256; i++) {
                int crc = i << 8;
                for (int j = 0; j < 8; j++)
                    crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ POLY) & 0xFFFF : (crc << 1) & 0xFFFF;
                TABLE[i] = crc;
            }
        }
        private int value = 0xFFFF;

        @Override public void update(int b) { value = ((value << 8) & 0xFF00) ^ TABLE[((value >>> 8) ^ b) & 0xFF]; }
        @Override public void update(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) update(b[i] & 0xFF);
        }
        @Override public long getValue() { return value & 0xFFFFL; }
        @Override public void reset() { value = 0xFFFF; }
    }

    private static final class CRC32J implements Checksum {
        private final java.util.zip.CRC32 delegate = new java.util.zip.CRC32();
        @Override public void update(int b) { delegate.update(b); }
        @Override public void update(byte[] b, int off, int len) { delegate.update(b, off, len); }
        @Override public long getValue() { return delegate.getValue(); }
        @Override public void reset() { delegate.reset(); }
    }

    private static final class CRC64 implements Checksum {
        private static final long POLY = 0x42F0E1EBA9EA3693L;
        private static final long[] TABLE = new long[256];
        static {
            for (int i = 0; i < 256; i++) {
                long crc = i;
                for (int j = 0; j < 8; j++)
                    crc = (crc & 1) != 0 ? (crc >>> 1) ^ POLY : crc >>> 1;
                TABLE[i] = crc;
            }
        }
        private long value;

        @Override public void update(int b) { value = (value >>> 8) ^ TABLE[(int) ((value ^ b) & 0xFF)]; }
        @Override public void update(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) update(b[i] & 0xFF);
        }
        @Override public long getValue() { return value; }
        @Override public void reset() { value = 0; }
    }
}