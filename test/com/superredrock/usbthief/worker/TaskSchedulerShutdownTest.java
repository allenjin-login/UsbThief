package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.ServiceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-03 regression: shutdown must be deterministic.
 *
 * <p>{@code stopService()} rejects new work, waits a bounded time for tasks already handed to the
 * pool and drops queued-but-unexecuted tasks with a log entry and a counter. {@code close()} owns
 * the final pool shutdown. Both paths are idempotent and must not pump the scheduler queue into a
 * pool that is about to be interrupted.</p>
 */
@Timeout(30)
class TaskSchedulerShutdownTest {

    @Test
    void stopRejectsNewTasksAndStopsTheService() throws Exception {
        TaskScheduler scheduler = newScheduler();
        try {
            scheduler.start();
            assertNotNull(scheduler.submit(() -> "first"));

            scheduler.stopService();

            assertNull(scheduler.submit(() -> "second"), "stopped scheduler must refuse new tasks");
            assertEquals(0, scheduler.getQueueDepth(), "refused tasks must not be queued");
            assertEquals(ServiceState.STOPPED, scheduler.getServiceState());
        } finally {
            scheduler.close();
        }
    }

    @Test
    void queuedTasksAreDiscardedAndCountedOnStop() throws Exception {
        TaskScheduler scheduler = newScheduler();
        try {
            startPaused(scheduler);

            int taskCount = 5;
            CountDownLatch executed = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                assertNotNull(scheduler.submit(() -> {
                    executed.countDown();
                    return null;
                }));
            }
            assertEquals(taskCount, scheduler.getQueueDepth());

            scheduler.stopService();

            assertEquals(0, scheduler.getQueueDepth(), "queued tasks must be removed from the queue");
            assertEquals(taskCount, scheduler.getDiscardedTaskCount(),
                    "discarded tasks must be counted");
            assertFalse(executed.await(200, TimeUnit.MILLISECONDS),
                    "discarded tasks must not be pumped into the pool and executed");

            // Idempotent: stopping again neither re-discards nor double counts.
            scheduler.stopService();
            assertEquals(taskCount, scheduler.getDiscardedTaskCount());

            scheduler.close();
            assertEquals(taskCount, scheduler.getDiscardedTaskCount());
        } finally {
            scheduler.close();
        }
    }

    @Test
    void stopWaitsForInFlightTasks() throws Exception {
        TaskScheduler scheduler = newScheduler();
        try {
            scheduler.start();

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean finished = new AtomicBoolean(false);
            assertNotNull(scheduler.submit(() -> {
                started.countDown();
                release.await();
                finished.set(true);
                return null;
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS), "task never reached the pool");

            Thread stopper = new Thread(scheduler::stopService, "scheduler-stopper");
            stopper.setDaemon(true);
            stopper.start();
            Thread.sleep(300);
            assertTrue(stopper.isAlive(), "stopService must wait for the in-flight task");

            release.countDown();
            stopper.join(10_000);
            assertFalse(stopper.isAlive(), "stopService did not finish after the task completed");
            assertTrue(finished.get(), "in-flight task must be allowed to finish");
        } finally {
            scheduler.close();
        }
    }

    @Test
    void cancelBySerialUsesDeviceBoundTaskInterfaceForQueuedTasks() throws Exception {
        TaskScheduler scheduler = newScheduler();
        try {
            startPaused(scheduler);

            assertNotNull(scheduler.submit(new StubDeviceTask("serial-a")));
            assertNotNull(scheduler.submit(new StubDeviceTask("serial-b")));

            assertEquals(1, scheduler.cancelBySerial("serial-a"));
            assertEquals(1, scheduler.getQueueDepth(), "only the matching task must be dropped");
            assertEquals(0, scheduler.cancelBySerial("serial-a"));
        } finally {
            scheduler.close();
        }
    }

    @Test
    void cancelBySerialCancelsSubmittedFutureViaInterface() throws Exception {
        TaskScheduler scheduler = newScheduler();
        try {
            scheduler.start();

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            StubDeviceTask blocking = new StubDeviceTask("serial-c");
            blocking.started = started;
            blocking.block = release;

            PriorityTask<Callable<Object>, Object> submitted = scheduler.submit(blocking);
            assertNotNull(submitted);
            assertTrue(started.await(5, TimeUnit.SECONDS), "task never reached the pool");

            assertEquals(1, scheduler.cancelBySerial("serial-c"));
            assertTrue(submitted.getFuture().isCancelled(),
                    "cancelBySerial must cancel the future of any DeviceBoundTask");

            release.countDown();
        } finally {
            scheduler.close();
        }
    }

    /** A task type the scheduler has never heard of; only the interface makes it cancellable. */
    private static final class StubDeviceTask implements Callable<Object>, DeviceBoundTask {
        private final String serial;
        private CountDownLatch started;
        private CountDownLatch block;

        StubDeviceTask(String serial) {
            this.serial = serial;
        }

        @Override
        public String getDeviceSerial() {
            return serial;
        }

        @Override
        public Object call() throws Exception {
            if (started != null) {
                started.countDown();
            }
            if (block != null) {
                block.await();
            }
            return null;
        }
    }

    /**
     * Creates an isolated scheduler instead of touching the process-wide singleton so the shared
     * pool of other tests is unaffected.
     */
    private static TaskScheduler newScheduler() throws Exception {
        Constructor<TaskScheduler> constructor = TaskScheduler.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * Starts the scheduler in the paused state so that submitted tasks stay in the scheduler queue.
     *
     * <p>The short settle delay guarantees that a dispatch tick which started before the pause has
     * finished, so the queue state is deterministic.</p>
     */
    private static void startPaused(TaskScheduler scheduler) throws InterruptedException {
        scheduler.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (scheduler.getServiceState() != ServiceState.RUNNING
                && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        scheduler.pause();
        assertEquals(ServiceState.PAUSED, scheduler.getServiceState(),
                "scheduler must be paused so queued tasks stay in the queue");
        Thread.sleep(100);
    }
}
