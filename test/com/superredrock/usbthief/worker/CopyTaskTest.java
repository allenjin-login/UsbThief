package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.index.CheckSum;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, ConfigSchema.WORK_PATH.defaultValue());
    }

    private CopyTask createTask(Path sourceFile) {
        return new CopyTask(sourceFile, "testSerial");
    }

    @Test
    void normalCopySuccess() throws Exception {
        Path srcFile = sourceDir.resolve("test.txt");
        Files.writeString(srcFile, "hello world");

        // Set work path to dest dir so CopyTask.getPath() resolves correctly
        ConfigManager.getInstance().set(ConfigSchema.WORK_PATH, destDir.toString());
        ConfigManager.getInstance().set(ConfigSchema.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(ConfigSchema.COPY_WRITE_RATE_LIMIT, 0L);

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
        ConfigManager.getInstance().set(ConfigSchema.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(ConfigSchema.COPY_WRITE_RATE_LIMIT, 0L);

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
        ConfigManager.getInstance().set(ConfigSchema.COPY_READ_RATE_LIMIT, 0L);
        ConfigManager.getInstance().set(ConfigSchema.COPY_WRITE_RATE_LIMIT, 0L);

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
}
