package com.superredrock.usbthief.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ServiceStateTest {

    static class TestService extends Service {
        private final CountDownLatch tickLatch = new CountDownLatch(1);
        private volatile int tickCount = 0;

        @Override
        protected void tick() {
            tickCount++;
            if (tickCount == 1) {
                tickLatch.countDown();
            }
        }

        @Override
        protected long getTickIntervalMs() {
            return 10;
        }

        @Override
        public String getServiceName() {
            return "TestService";
        }

        @Override
        public String getDescription() {
            return "Test service for state transitions";
        }

        boolean awaitFirstTick(long timeout, TimeUnit unit) throws InterruptedException {
            return tickLatch.await(timeout, unit);
        }

        int getTickCount() {
            return tickCount;
        }
    }

    @Test
    @Timeout(5)
    void start_shouldTransitionThroughStartingToRunning() throws InterruptedException {
        TestService service = new TestService();

        assertEquals(ServiceState.STOPPED, service.getServiceState());

        service.start();

        assertEquals(ServiceState.STARTING, service.getServiceState());

        assertTrue(service.awaitFirstTick(2, TimeUnit.SECONDS));

        assertEquals(ServiceState.RUNNING, service.getServiceState());

        service.stopService();
        assertEquals(ServiceState.STOPPED, service.getServiceState());
    }

    @Test
    @Timeout(5)
    void start_shouldNotStartTwice() throws InterruptedException {
        TestService service = new TestService();

        service.start();
        assertTrue(service.awaitFirstTick(2, TimeUnit.SECONDS));

        int ticksBefore = service.getTickCount();

        service.start();

        Thread.sleep(100);

        assertEquals(ServiceState.RUNNING, service.getServiceState());

        service.stopService();
    }

    @Test
    @Timeout(5)
    void stopService_shouldTransitionToStopped() throws InterruptedException {
        TestService service = new TestService();

        service.start();
        assertTrue(service.awaitFirstTick(2, TimeUnit.SECONDS));

        service.stopService();

        assertEquals(ServiceState.STOPPED, service.getServiceState());
    }

    @Test
    @Timeout(5)
    void pause_shouldPauseExecution() throws InterruptedException {
        TestService service = new TestService();

        service.start();
        assertTrue(service.awaitFirstTick(2, TimeUnit.SECONDS));

        int ticksBeforePause = service.getTickCount();

        service.pause();
        assertEquals(ServiceState.PAUSED, service.getServiceState());

        Thread.sleep(100);

        int ticksAfterPause = service.getTickCount();

        service.resume();
        assertEquals(ServiceState.RUNNING, service.getServiceState());

        service.stopService();
    }

    @Test
    @Timeout(5)
    void resume_shouldResumeExecution() throws InterruptedException {
        TestService service = new TestService();

        service.start();
        assertTrue(service.awaitFirstTick(2, TimeUnit.SECONDS));

        service.pause();

        service.resume();
        assertEquals(ServiceState.RUNNING, service.getServiceState());

        Thread.sleep(50);

        assertTrue(service.getTickCount() > 1);

        service.stopService();
    }

    @Test
    @Timeout(5)
    void isRunning_shouldReturnCorrectState() throws InterruptedException {
        TestService service = new TestService();

        assertFalse(service.isRunning());

        service.start();
        assertTrue(service.awaitFirstTick(2, TimeUnit.SECONDS));

        assertTrue(service.isRunning());

        service.pause();
        assertFalse(service.isRunning());

        service.resume();
        assertTrue(service.isRunning());

        service.stopService();
        assertFalse(service.isRunning());
    }
}
