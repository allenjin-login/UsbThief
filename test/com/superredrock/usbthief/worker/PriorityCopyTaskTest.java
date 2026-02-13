package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.Device;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriorityCopyTaskTest {

    @Test
    void constructor_shouldClampPriorityToValidRange() {
        CopyTask mockTask = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask highPriority = new PriorityCopyTask(mockTask, 150, mockDevice, now);
        PriorityCopyTask lowPriority = new PriorityCopyTask(mockTask, -10, mockDevice, now);

        assertEquals(100, highPriority.getPriority());
        assertEquals(0, lowPriority.getPriority());
    }

    @Test
    void getPriority_shouldReturnCorrectValue() {
        CopyTask mockTask = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask task = new PriorityCopyTask(mockTask, 50, mockDevice, now);

        assertEquals(50, task.getPriority());
    }

    @Test
    void unwrap_shouldReturnOriginalTask() {
        CopyTask original = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask wrapped = new PriorityCopyTask(original, 5, mockDevice, now);

        assertSame(original, wrapped.unwrap());
    }

    @Test
    void getDevice_shouldReturnCorrectDevice() {
        CopyTask mockTask = mock(CopyTask.class);
        Device device = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask task = new PriorityCopyTask(mockTask, 5, device, now);

        assertSame(device, task.getDevice());
    }

    @Test
    void getCreationTime_shouldReturnCorrectTime() {
        CopyTask mockTask = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask task = new PriorityCopyTask(mockTask, 5, mockDevice, now);

        assertEquals(now, task.getCreationTime());
    }

    @Test
    void compareTo_shouldOrderByPriorityDescending() {
        CopyTask mockTask = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask low = new PriorityCopyTask(mockTask, 1, mockDevice, now);
        PriorityCopyTask high = new PriorityCopyTask(mockTask, 10, mockDevice, now);

        assertTrue(low.compareTo(high) > 0);
        assertTrue(high.compareTo(low) < 0);
    }

    @Test
    void compareTo_shouldReturnZeroForSamePriorityAndTime() {
        CopyTask mockTask = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant now = Instant.now();

        PriorityCopyTask task1 = new PriorityCopyTask(mockTask, 5, mockDevice, now);
        PriorityCopyTask task2 = new PriorityCopyTask(mockTask, 5, mockDevice, now);

        assertEquals(0, task1.compareTo(task2));
    }

    @Test
    void compareTo_shouldOrderByCreationTimeWhenPriorityIsSame() {
        CopyTask mockTask = mock(CopyTask.class);
        Device mockDevice = mock(Device.class);
        Instant earlier = Instant.now();
        Instant later = earlier.plusSeconds(1);

        PriorityCopyTask first = new PriorityCopyTask(mockTask, 5, mockDevice, earlier);
        PriorityCopyTask second = new PriorityCopyTask(mockTask, 5, mockDevice, later);

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
    }
}
