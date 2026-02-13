package com.superredrock.usbthief.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeviceThreadSafetyTest {

    @Test
    @Timeout(10)
    void stateChange_shouldBeThreadSafe() throws InterruptedException {
        int numThreads = 10;
        int iterations = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        Device device = new Device("TEST-SERIAL", "TestVolume");

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterations; j++) {
                        device.disable();
                        device.enable();
                    }
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));

        assertEquals(numThreads, successCount.get());

        executor.shutdown();
    }

    @Test
    void initialState_shouldBeOfflineForGhost() {
        Device ghostDevice = new Device("GHOST-SERIAL", "GhostVolume");

        assertTrue(ghostDevice.isGhost());
        assertEquals(Device.DeviceState.OFFLINE, ghostDevice.getState());
    }

    @Test
    void disable_shouldSetDisabledState() {
        Device device = new Device("TEST-SERIAL", "TestVolume");

        device.disable();

        assertEquals(Device.DeviceState.DISABLED, device.getState());
    }

    @Test
    void enable_shouldEnableFromDisabled() {
        Device device = new Device("TEST-SERIAL", "TestVolume");

        device.disable();
        assertEquals(Device.DeviceState.DISABLED, device.getState());

        device.enable();

        assertEquals(Device.DeviceState.IDLE, device.getState());
    }

    @Test
    void equalsAndHashCode_shouldBeBasedOnSerialNumber() {
        Device device1 = new Device("SERIAL-123", "Volume1");
        Device device2 = new Device("SERIAL-123", "Volume2");
        Device device3 = new Device("SERIAL-456", "Volume1");

        assertEquals(device1, device2);
        assertEquals(device1.hashCode(), device2.hashCode());
        assertNotEquals(device1, device3);
    }

    @Test
    void isChangeAndReset_shouldReturnTrueAfterStateChange() {
        Device device = new Device("TEST-SERIAL", "TestVolume");

        assertFalse(device.isChangeAndReset());

        device.disable();

        assertTrue(device.isChangeAndReset());

        assertFalse(device.isChangeAndReset());
    }
}
