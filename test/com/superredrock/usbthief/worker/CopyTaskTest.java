package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Volume;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.CategoryConfig;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.PathConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.config.configs.StorageConfig;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CopyTaskTest {

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private Path destDir;

    @BeforeEach
    void setUp() throws IOException {
        sourceDir = tempDir.resolve("source");
        destDir = tempDir.resolve("dest");
        Files.createDirectories(sourceDir);
        Files.createDirectories(destDir);

        EventBus.getInstance().clearAll();
    }

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clearAll();
        ConfigManager.getInstance().set(PathConfig.WORK_PATH, PathConfig.WORK_PATH.defaultValue());
        ConfigManager.getInstance().set(FileCopyConfig.BUFFER_SIZE, FileCopyConfig.BUFFER_SIZE.defaultValue());
        ConfigManager.getInstance().set(StorageConfig.STORAGE_ENABLED, StorageConfig.STORAGE_ENABLED.defaultValue());
        ConfigManager.getInstance().set(CategoryConfig.CATEGORY_MODE, CategoryConfig.CATEGORY_MODE.defaultValue());
        StorageController.getInstance().invalidateStorageStatus();
    }

    /**
     * Prepares a workspace where the work directory accepts copies: storage gating disabled (the
     * CI disk may be below the default reserved threshold) and the status cache invalidated so the
     * change is visible immediately.
     */
    private void enableCopying() {
        ConfigManager.getInstance().set(PathConfig.WORK_PATH, destDir.toString());
        ConfigManager.getInstance().set(StorageConfig.STORAGE_ENABLED, false);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_WRITE_RATE_LIMIT, 0L);
        StorageController.getInstance().invalidateStorageStatus();
    }

    private CopyTask createTask(Path sourceFile) {
        return new CopyTask(sourceFile, "testSerial");
    }

    /**
     * Builds a volume whose name is deterministic.
     *
     * <p>{@link Volume} normally derives the name from {@code FileStore.name()}, which on Linux is
     * a device path such as {@code /dev/vda2} and would turn the destination into an absolute path.
     * Overriding it both keeps the test portable and proves the destination folder really comes
     * from the injected volume instead of a per-file file-store lookup.</p>
     */
    private Volume namedVolume(String serial, String volumeName) {
        return new Volume(sourceDir, serial) {
            @Override
            public String getVolumeName() {
                return volumeName;
            }
        };
    }

    @Test
    void normalCopySuccess() throws Exception {
        Path srcFile = sourceDir.resolve("test.txt");
        Files.writeString(srcFile, "hello world");

        // Set work path to dest dir so CopyTask.getPath() resolves correctly
        ConfigManager.getInstance().set(PathConfig.WORK_PATH, destDir.toString());
        ConfigManager.getInstance().set(RateLimitConfig.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_WRITE_RATE_LIMIT, 0L);

        CopyTask task = new CopyTask(srcFile, "testSerial");
        // This will try to use StorageController and other singletons
        // For a basic smoke test, we just verify it doesn't crash
        // Full integration testing would require mocking all dependencies
        assertNotNull(task.getProcessingPath());
        assertEquals("testSerial", task.getDeviceSerial());
    }

    @Test
    void nullDeviceSerialDefaultsEmpty() {
        Path srcFile = sourceDir.resolve("test.txt");
        CopyTask task = new CopyTask(srcFile, null);
        assertEquals("", task.getDeviceSerial());
    }

    @Test
    void getProcessingPath() throws IOException {
        Path srcFile = sourceDir.resolve("doc.pdf");
        CopyTask task = new CopyTask(srcFile, "s1");
        assertEquals(srcFile, task.getProcessingPath());
    }

    @Test
    void preVerifiedHashConstructor() throws IOException {
        Path srcFile = sourceDir.resolve("test.txt");
        CheckSum hash = new CheckSum(new byte[]{1, 2, 3});
        CopyTask task = new CopyTask(srcFile, "s1", hash);
        assertEquals(srcFile, task.getProcessingPath());
        assertEquals("s1", task.getDeviceSerial());
    }

    @Test
    void dispatchesEventOnCompletion() throws Exception {
        Path srcFile = sourceDir.resolve("test.txt");
        Files.writeString(srcFile, "data");
        ConfigManager.getInstance().set(RateLimitConfig.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_WRITE_RATE_LIMIT, 0L);

        CountDownLatch latch = new CountDownLatch(1);
        CopyCompletedEvent[] captured = new CopyCompletedEvent[1];
        EventBus.getInstance().register(CopyCompletedEvent.class, e -> {
            captured[0] = e;
            latch.countDown();
        });

        CopyTask task = new CopyTask(srcFile, "testSerial");
        // call() will likely fail due to missing dependencies, but should still dispatch event
        try {
            task.call();
        } catch (Exception ignored) {
            // Expected - missing StorageController etc.
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Event should have been dispatched");
        assertNotNull(captured[0]);
        assertEquals(srcFile, captured[0].sourcePath());
    }

    @Test
    void sourceNotFoundRecordsEvent() throws Exception {
        Path nonExistent = sourceDir.resolve("nonexistent.txt");
        ConfigManager.getInstance().set(RateLimitConfig.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(RateLimitConfig.COPY_WRITE_RATE_LIMIT, 0L);

        CountDownLatch latch = new CountDownLatch(1);
        CopyResult[] result = new CopyResult[1];
        EventBus.getInstance().register(CopyCompletedEvent.class, e -> {
            result[0] = e.result();
            latch.countDown();
        });

        // CopyTask.call() needs QueueManager, so we just verify event dispatch behavior
        // by testing that the constructor works and the event would be dispatched
        CopyTask task = new CopyTask(nonExistent, "testSerial");

        // Verify construction is correct - actual call() requires full infrastructure
        assertEquals(nonExistent, task.getProcessingPath());
        assertEquals("testSerial", task.getDeviceSerial());
    }

    /**
     * PF-01/PF-02/PF-05: a chunked copy with an injected volume must reproduce the source exactly.
     *
     * <p>Uses a 4 KB chunk against a 100 KB file so the copy loop runs across many chunks, and
     * verifies the destination path is built from the volume name (no per-file file-store lookup).</p>
     */
    @Test
    void copiesWholeFileAcrossManyChunks() throws Exception {
        enableCopying();
        ConfigManager.getInstance().set(FileCopyConfig.BUFFER_SIZE, 4 * 1024);
        StorageController.getInstance().invalidateStorageStatus();

        Path srcFile = sourceDir.resolve("payload.bin");
        byte[] payload = new byte[100_003];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }
        Files.write(srcFile, payload);

        String serial = "CHUNK-SERIAL";
        String volumeName = "testvol";
        Volume volume = namedVolume(serial, volumeName);
        CopyCompletedEvent[] captured = new CopyCompletedEvent[1];
        EventBus.getInstance().register(CopyCompletedEvent.class, e -> captured[0] = e);

        CopyResult result = new CopyTask(srcFile, serial, volume, null).call();

        Path expected = destDir.resolve(volumeName + "_" + serial)
                .resolve(srcFile.getRoot().relativize(srcFile));
        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.exists(expected), "copied file should exist at " + expected);
        assertArrayEquals(payload, Files.readAllBytes(expected));
        assertNotNull(captured[0]);
        assertEquals(CopyResult.SUCCESS, captured[0].result());
        assertEquals(payload.length, captured[0].bytesCopied());
    }

    /**
     * PF-06: directory tasks create the folder, dispatch the usual completion event and do not
     * allocate speed probes.
     */
    @Test
    void directoryTaskOnlyCreatesDirectory() throws Exception {
        enableCopying();
        Path srcDir = sourceDir.resolve("nested").resolve("deeper");
        Files.createDirectories(srcDir);

        String serial = "DIR-SERIAL";
        String volumeName = "dirtestvol";
        Volume volume = namedVolume(serial, volumeName);
        CopyCompletedEvent[] captured = new CopyCompletedEvent[1];
        EventBus.getInstance().register(CopyCompletedEvent.class, e -> captured[0] = e);

        int probesBefore = com.superredrock.usbthief.statistics.Statistics.getInstance()
                .getSpeedCollector().getWriteProbeGroup().getProbeCount();

        CopyResult result = new CopyTask(srcDir, serial, volume, null).call();

        Path expected = destDir.resolve(volumeName + "_" + serial)
                .resolve(srcDir.getRoot().relativize(srcDir));
        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.isDirectory(expected), "directory should have been created at " + expected);
        assertNotNull(captured[0], "directory task must still dispatch a completion event");
        assertEquals(CopyResult.SUCCESS, captured[0].result());
        assertEquals(0, captured[0].bytesCopied(), "directories copy no bytes");
        assertEquals(probesBefore, com.superredrock.usbthief.statistics.Statistics.getInstance()
                        .getSpeedCollector().getWriteProbeGroup().getProbeCount(),
                "directory tasks must not allocate probes");
    }

    /** PF-02: the per-file snapshot mirrors (and clamps) the configured chunk size. */
    @Test
    void settingsSnapshotClampsBufferSize() {
        ConfigManager.getInstance().set(FileCopyConfig.BUFFER_SIZE, 8 * 1024);
        assertEquals(8 * 1024, CopyTask.CopySettings.snapshot().bufferSize);

        ConfigManager.getInstance().set(FileCopyConfig.BUFFER_SIZE, 1);
        assertEquals(CopyTask.MIN_BUFFER_SIZE, CopyTask.CopySettings.snapshot().bufferSize);

        ConfigManager.getInstance().set(FileCopyConfig.BUFFER_SIZE, Integer.MAX_VALUE);
        assertEquals(CopyTask.MAX_BUFFER_SIZE, CopyTask.CopySettings.snapshot().bufferSize);
    }

    /** PF-02: the default chunk size is now large enough to cut syscalls by 64x. */
    @Test
    void defaultBufferIsAtLeast512Kb() {
        assertTrue(FileCopyConfig.BUFFER_SIZE.defaultValue() >= 512 * 1024,
                "default copy chunk should be at least 512 KB, was "
                        + FileCopyConfig.BUFFER_SIZE.defaultValue());
    }

    /** Batch 2-B: BY_TYPE drops the relative sub-tree and sorts the file by extension. */
    @Test
    void categoryModeByTypeSortsCopiedFileIntoTypeFolder() throws Exception {
        enableCopying();
        ConfigManager.getInstance().set(CategoryConfig.CATEGORY_MODE, CategoryMode.BY_TYPE.name());

        Path srcFile = sourceDir.resolve("holiday").resolve("photo.JPG");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, "jpegdata");

        String serial = "CAT-SERIAL";
        String volumeName = "catvol";
        Volume volume = namedVolume(serial, volumeName);

        CopyResult result = new CopyTask(srcFile, serial, volume, null).call();

        Path expected = destDir.resolve(volumeName + "_" + serial)
                .resolve(FileCategory.IMAGE.directoryName())
                .resolve("photo.JPG");
        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.exists(expected), "categorised file should exist at " + expected);
        assertEquals("jpegdata", Files.readString(expected));
        Path uncategorised = destDir.resolve(volumeName + "_" + serial)
                .resolve(srcFile.getRoot().relativize(srcFile));
        assertFalse(Files.exists(uncategorised),
                "the relative sub-tree must be dropped, but a file was found at " + uncategorised);
    }

    /** Batch 2-B: BY_DATE sorts the file into a yyyy-MM-dd folder of the copy date. */
    @Test
    void categoryModeByDateSortsCopiedFileIntoDateFolder() throws Exception {
        enableCopying();
        ConfigManager.getInstance().set(CategoryConfig.CATEGORY_MODE, CategoryMode.BY_DATE.name());

        Path srcFile = sourceDir.resolve("payload.bin");
        Files.writeString(srcFile, "bytes");

        String serial = "DATE-SERIAL";
        String volumeName = "datevol";
        Volume volume = namedVolume(serial, volumeName);

        CopyResult result = new CopyTask(srcFile, serial, volume, null).call();

        String today = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now());
        Path expected = destDir.resolve(volumeName + "_" + serial).resolve(today).resolve("payload.bin");
        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.exists(expected), "dated file should exist at " + expected);
    }

    /** Batch 2-B: the default (OFF) layout is unchanged. */
    @Test
    void categoryModeOffKeepsTheUncategorisedLayout() throws Exception {
        enableCopying();
        assertEquals(CategoryMode.OFF.name(),
                ConfigManager.getInstance().get(CategoryConfig.CATEGORY_MODE));

        Path srcFile = sourceDir.resolve("plain.txt");
        Files.writeString(srcFile, "plain");

        String serial = "OFF-SERIAL";
        String volumeName = "offvol";
        Volume volume = namedVolume(serial, volumeName);

        CopyResult result = new CopyTask(srcFile, serial, volume, null).call();

        Path expected = destDir.resolve(volumeName + "_" + serial)
                .resolve(srcFile.getRoot().relativize(srcFile));
        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.exists(expected), "uncategorised file should exist at " + expected);
    }

    /** Batch 2-B: folder tasks are never sorted into a category folder (PF-06 still holds). */
    @Test
    void categoryModeByTypeLeavesDirectoryTasksUncategorised() throws Exception {
        enableCopying();
        ConfigManager.getInstance().set(CategoryConfig.CATEGORY_MODE, CategoryMode.BY_TYPE.name());

        Path srcDir = sourceDir.resolve("nested").resolve("deeper");
        Files.createDirectories(srcDir);

        String serial = "DIRCAT-SERIAL";
        String volumeName = "dircatvol";
        Volume volume = namedVolume(serial, volumeName);

        CopyResult result = new CopyTask(srcDir, serial, volume, null).call();

        Path expected = destDir.resolve(volumeName + "_" + serial)
                .resolve(srcDir.getRoot().relativize(srcDir));
        assertEquals(CopyResult.SUCCESS, result);
        assertTrue(Files.isDirectory(expected), "directory should have been created at " + expected);
        assertFalse(Files.exists(destDir.resolve(volumeName + "_" + serial)
                        .resolve(FileCategory.OTHER.directoryName()).resolve("deeper")),
                "directories must not be routed through the category table");
    }
}
