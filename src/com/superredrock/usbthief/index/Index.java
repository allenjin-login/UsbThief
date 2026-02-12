package com.superredrock.usbthief.index;

import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.core.event.index.FileIndexedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Index extends Service {
    private static final Logger logger = Logger.getLogger(Index.class.getName());

    private final Set<CheckSum> digest;
    private final Path indexPath;
    private volatile boolean dirty;

    public Index(){
        Path path = Path.of(ConfigManager.getInstance().get(ConfigSchema.INDEX_PATH));
        Path indexBasePath = path.getParent() != null ? path.getParent() : Paths.get(".");
        this(indexBasePath);
    }

    public Index(Path basePath) {
        this.digest = ConcurrentHashMap.newKeySet();
        this.indexPath = basePath.resolve("index.obj");
        this.dirty = false;
        ensureDirectories();
    }

    private void ensureDirectories() {
        try {
            if (!Files.exists(indexPath.getParent())) {
                Files.createDirectories(indexPath.getParent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create index directory", e);
        }
    }

    public void load() {
        loadDigest();
        dirty = false;

        logger.info(String.format("Index loaded: %d checksums", digest.size()));

        EventBus.getInstance().dispatch(new IndexLoadedEvent(digest.size()));
    }

    private void loadDigest() {
        if (!Files.exists(indexPath)) {
            logger.info("Index file not found, starting with empty digest");
            return;
        }

        try (ObjectInputStream objectInput = new ObjectInputStream(Files.newInputStream(indexPath))) {
            while (true) {
                CheckSum checksum = (CheckSum) objectInput.readObject();
                if (checksum != null) {
                    digest.add(checksum);
                } else {
                    break;
                }
            }
        } catch (EOFException _) {
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Failed to load digest", e);
            digest.clear();
        }
    }

    public void save() {
        if (!dirty) {
            logger.fine("Index not dirty, skipping save");
            return;
        }

        saveDigest();
        dirty = false;

        logger.info(String.format("Index saved: %d checksums", digest.size()));

        EventBus.getInstance().dispatch(new IndexSavedEvent(digest.size()));
    }

    private void saveDigest() {
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(
                Files.newOutputStream(indexPath, StandardOpenOption.CREATE))) {
            for (CheckSum checksum : digest) {
                objectOutput.writeObject(checksum);
            }
            objectOutput.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save digest", e);
        }
    }

    @Override
    protected void tick() {
        try {
            if (dirty) {
                logger.fine("Executing periodic index save");
                save();
            } else {
                logger.fine("Index not modified, skipping periodic save");
            }
        } catch (Exception e) {
            logger.severe("Index save failed: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    @Override
    protected long getTickIntervalMs() {
        return 60000;
    }

    @Override
    public String getServiceName() {
        return "Index";
    }

    @Override
    public String getDescription() {
        return "File index and periodic save service";
    }

    @Override
    protected void cleanup() {
        save();
    }

    @Override
    public String getStatus() {
        return String.format("Index[%s] - Checksums: %d, State: %s",
                state, digest.size(), dirty ? "dirty" : "clean");
    }

    public boolean contains(CheckSum checksum) {
        return digest.contains(checksum);
    }

    public boolean checkDuplicate(Path filePath, CheckSum checksum) {
        boolean isDuplicate = contains(checksum);
        if (isDuplicate) {
            EventBus.getInstance().dispatch(new DuplicateDetectedEvent(checksum, filePath, 1));
        }
        return isDuplicate;
    }

    public boolean addChecksum(CheckSum checksum) {
        boolean added = digest.add(checksum);
        if (added) {
            markDirty();
        }
        return added;
    }

    public void addFile(CheckSum checksum, Path filePath, long fileSize) {
        boolean added = addChecksum(checksum);
        if (added) {
            EventBus.getInstance().dispatch(new FileIndexedEvent(checksum, filePath, fileSize, getDigestSize()));
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clear() {
        int oldDigestSize = digest.size();

        digest.clear();
        markDirty();

        logger.info(String.format("Index cleared: %d checksums removed", oldDigestSize));
    }

    public Set<CheckSum> getDigest() {
        return Set.copyOf(digest);
    }

    public Path getIndexPath() {
        return indexPath;
    }

    public int getDigestSize() {
        return digest.size();
    }
}
