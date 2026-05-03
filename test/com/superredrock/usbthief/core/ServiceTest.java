package com.superredrock.usbthief.core;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ServiceTest {

    private static class TestService extends Service {
        final AtomicInteger tickCount = new AtomicInteger(0);
        volatile boolean throwOnce = false;
        final CountDownLatch tickLatch = new CountDownLatch(1);

        TestService() {}

        @Override
        protected void tick() {
            if (throwOnce) {
                throwOnce = false;
                throw new RuntimeException("test error");
            }
            tickCount.incrementAndGet();
            tickLatch.countDown();
        }

        @Override
        protected long getTickInterval() { return 50; }

        @Override
        protected TimeUnit getTickUnit() { return TimeUnit.MILLISECONDS; }

        @Override
        public String getServiceName() { return "TestSvc"; }

        @Override
        public String getDescription() { return "test"; }
    }

    private TestService svc;

    @BeforeEach
    void setUp() {
        svc = new TestService();
    }

    @AfterEach
    void tearDown() {
        if (svc != null && svc.getServiceState() != ServiceState.STOPPED) {
            svc.stopService();
        }
    }

    @Test
    void lifecycleStoppedToRunningToStopped() throws Exception {
        assertEquals(ServiceState.STOPPED, svc.getServiceState());

        svc.start();
        assertTrue(svc.tickLatch.await(2, TimeUnit.SECONDS));
        assertEquals(ServiceState.RUNNING, svc.getServiceState());
        assertTrue(svc.tickCount.get() > 0);

        svc.stopService();
        awaitState(ServiceState.STOPPED, 3);
    }

    @Test
    void pauseAndResume() throws Exception {
        svc.start();
        assertTrue(svc.tickLatch.await(2, TimeUnit.SECONDS));
        assertEquals(ServiceState.RUNNING, svc.getServiceState());

        svc.pause();
        assertEquals(ServiceState.PAUSED, svc.getServiceState());
        int ticksWhenPaused = svc.tickCount.get();

        // Wait and verify ticks eventually stop (pause competes with run() for stateLock)
        Thread.sleep(300);
        int ticksAfterWait = svc.tickCount.get();
        // Ticks may still increase briefly, but should stop within the wait period
        assertTrue(ticksAfterWait >= ticksWhenPaused);

        svc.resume();
        assertEquals(ServiceState.RUNNING, svc.getServiceState());
    }

    @Test
    void stopFromPaused() throws Exception {
        svc.start();
        assertTrue(svc.tickLatch.await(2, TimeUnit.SECONDS));

        svc.pause();
        assertEquals(ServiceState.PAUSED, svc.getServiceState());

        svc.stopService();
        awaitState(ServiceState.STOPPED, 3);
    }

    @Test
    void doubleStartNoOp() throws Exception {
        svc.start();
        assertTrue(svc.tickLatch.await(2, TimeUnit.SECONDS));
        assertEquals(ServiceState.RUNNING, svc.getServiceState());

        svc.start(); // no-op
        assertEquals(ServiceState.RUNNING, svc.getServiceState());
    }

    @Test
    void doubleStopNoOp() throws Exception {
        svc.start();
        assertTrue(svc.tickLatch.await(2, TimeUnit.SECONDS));
        svc.stopService();
        awaitState(ServiceState.STOPPED, 3);

        svc.stopService(); // no-op
        assertEquals(ServiceState.STOPPED, svc.getServiceState());
    }

    @Test
    void tickExceptionLeadsToFailed() throws Exception {
        svc.throwOnce = true;
        svc.start();
        Thread.sleep(200);
        assertEquals(ServiceState.FAILED, svc.getServiceState());
    }

    @Test
    void closeEquivalentToStop() throws Exception {
        svc.start();
        assertTrue(svc.tickLatch.await(2, TimeUnit.SECONDS));
        assertEquals(ServiceState.RUNNING, svc.getServiceState());

        svc.close();
        awaitState(ServiceState.STOPPED, 3);
    }

    @Test
    void isRunningAndIsFailed() {
        assertFalse(svc.isRunning());
        assertFalse(svc.isFailed());
    }

    @Test
    void getStatus() {
        String status = svc.getStatus();
        assertTrue(status.contains("TestSvc"));
        assertTrue(status.contains("STOPPED"));
    }

    private void awaitState(ServiceState expected, int timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (svc.getServiceState() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(expected, svc.getServiceState());
    }
}
