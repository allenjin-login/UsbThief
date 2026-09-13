package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PF-08 regression: a saturated thread pool must not collapse scheduler throughput.
 *
 * <p>The old implementation drained the priority queue every 500 ms and, on rejection, shrank a
 * dispatch budget to {@code max(1, dispatched / 2 + 1)} - one rejected task in an empty batch left
 * the budget at 1, so recovering 300 tasks took ~9 half-second ticks (~4.5 s). The scheduler now
 * dispatches against the pool's free capacity on every (short) tick, so recovery happens as soon as
 * queue slots free up.</p>
 */
@Timeout(30)
class TaskSchedulerThroughputTest {

    @Test
    void saturatedPoolRecoversFullThroughput() throws Exception {
        TaskScheduler scheduler = newScheduler();
        ThreadPoolExecutor pool = scheduler.getPool();
        CountDownLatch release = new CountDownLatch(1);

        try {
            scheduler.start();
            saturatePool(pool, release);

            int taskCount = 300;
            CountDownLatch completed = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                scheduler.submit(() -> {
                    completed.countDown();
                    return null;
                });
            }

            // Must outlast at least one full dispatch tick (500 ms in the old implementation) so
            // that the saturated pool is actually observed by the scheduler: that tick is what
            // decayed the old dispatch budget to 1 and cost ~4.5 s to recover from afterwards.
            Thread.sleep(700);

            long start = System.nanoTime();
            release.countDown();
            assertTrue(completed.await(3, TimeUnit.SECONDS),
                    "Scheduler did not drain the backlog after the pool freed up");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue(elapsedMs < 2000,
                    "Throughput collapsed: draining " + taskCount + " tasks took " + elapsedMs + "ms");
        } finally {
            release.countDown();
            scheduler.close();
        }
    }

    /**
     * Occupies every worker thread and every queue slot so that the pool rejects new submissions,
     * which is the state that triggered the throughput collapse.
     */
    private static void saturatePool(ThreadPoolExecutor pool, CountDownLatch release) {
        Runnable blocker = () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        int guard = pool.getMaximumPoolSize() + pool.getQueue().remainingCapacity() + 16;
        for (int i = 0; i < guard; i++) {
            try {
                pool.execute(blocker);
            } catch (RejectedExecutionException e) {
                break;
            }
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
}
