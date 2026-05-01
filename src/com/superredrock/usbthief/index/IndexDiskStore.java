package com.superredrock.usbthief.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IndexDiskStore {

    private static final Logger logger = LogManager.getLogger(IndexDiskStore.class);
    private static final int MAGIC = 0x49445846; // "IDXF"
    private static final int VERSION = 1;

    private final Path filePath;
    private final ConcurrentHashMap<Path, CheckSum> memoryIndex = new ConcurrentHashMap<>();

    IndexDiskStore(Path filePath) {
        this.filePath = filePath;
    }

    public Map<Path, CheckSum> load() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }

        try (DataInputStream in = new DataInputStream(
                Files.newInputStream(filePath, StandardOpenOption.READ))) {

            int magic = in.readInt();
            if (magic != MAGIC) {
                logger.warn("Invalid index file magic: {}, expected {}", magic, MAGIC);
                return new HashMap<>();
            }

            int version = in.readInt();
            if (version != VERSION) {
                logger.warn("Unsupported index version: {}, expected {}", version, VERSION);
                return new HashMap<>();
            }

            int count = in.readInt();
            in.readInt(); // reserved

            Map<Path, CheckSum> entries = new HashMap<>(Math.max(count, 16));
            int i = 0;
            while (true) {
                try {
                    Path path = readPath(in);
                    CheckSum checksum = readCheckSum(in);
                    entries.put(path, checksum);
                    i++;
                } catch (IOException e) {
                    if (i < count) {
                        logger.warn("Error reading entry {} in index file (expected {})", i, count, e);
                    }
                    break;
                }
            }
            memoryIndex.putAll(entries);
            return entries;
        } catch (IOException e) {
            logger.warn("Failed to load index file", e);
            return new HashMap<>();
        }
    }

    public CheckSum lookup(Path path) {
        return memoryIndex.get(path);
    }

    protected synchronized void append(Path path, CheckSum checksum) {
        boolean fileExists = Files.exists(filePath);
        try (DataOutputStream out = new DataOutputStream(
                Files.newOutputStream(filePath,
                        fileExists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE))) {

            if (!fileExists) {
                writeHeader(out, 0);
            }

            writeEntry(out, path, checksum);
            memoryIndex.put(path, checksum);
        } catch (IOException e) {
            logger.warn("Failed to append to index file", e);
        }
    }

    protected synchronized void compact(Map<Path, CheckSum> entries) {
        try {
            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");

            try (DataOutputStream out = new DataOutputStream(
                    Files.newOutputStream(tempFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                writeHeader(out, entries.size());
                for (var entry : entries.entrySet()) {
                    writeEntry(out, entry.getKey(), entry.getValue());
                }
            }

            Files.move(tempFile, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            memoryIndex.clear();
            memoryIndex.putAll(entries);
            logger.info("Index compacted: {} entries", entries.size());
        } catch (IOException e) {
            logger.warn("Failed to compact index file", e);
        }
    }

    protected synchronized void clear() {
        memoryIndex.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.warn("Failed to delete index file", e);
        }
    }

    private void writeHeader(DataOutputStream out, int count) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(count);
        out.writeInt(0); // reserved
    }

    private void writeEntry(DataOutputStream out, Path path, CheckSum checksum) throws IOException {
        byte[] pathBytes = path.toString().getBytes(StandardCharsets.UTF_8);
        out.writeShort(pathBytes.length);
        out.write(pathBytes);
        out.writeByte(checksum.context().length);
        out.write(checksum.context());
    }

    private Path readPath(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return Path.of(new String(bytes, StandardCharsets.UTF_8));
    }

    private CheckSum readCheckSum(DataInputStream in) throws IOException {
        int len = in.readUnsignedByte();
        byte[] hash = new byte[len];
        in.readFully(hash);
        return new CheckSum(hash);
    }

    Path getFilePath() {
        return filePath;
    }
}
