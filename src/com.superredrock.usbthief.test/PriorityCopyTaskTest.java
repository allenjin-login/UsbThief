package com.superredrock.usbthief.test;

import com.superredrock.usbthief.worker.CopyTask;
import com.superredrock.usbthief.worker.PriorityCopyTask;
import java.nio.file.Paths;
import java.time.Instant;

public class PriorityCopyTaskTest {
    static void main(String[] args) {
        testComparableOrdersByPriority();
        testComparableOrdersByTimeWhenSamePriority();
        testUnwrapReturnsOriginalTask();
        System.out.println("All tests passed!");
    }

    private static void testComparableOrdersByPriority() {
        TestDevice device = new TestDevice(Paths.get("C:\\test"));
        CopyTask task1 = new CopyTask(Paths.get("file1.pdf"));
        CopyTask task2 = new CopyTask(Paths.get("file2.txt"));

        PriorityCopyTask high = new PriorityCopyTask(task1, 10, device, Instant.now());
        PriorityCopyTask low = new PriorityCopyTask(task2, 5, device, Instant.now());

        assert high.compareTo(low) < 0 : "High priority should come first";
        System.out.println("✓ Priority ordering works");
    }

    private static void testComparableOrdersByTimeWhenSamePriority() {
        TestDevice device = new TestDevice(Paths.get("C:\\test"));
        CopyTask task1 = new CopyTask(Paths.get("file1.pdf"));
        CopyTask task2 = new CopyTask(Paths.get("file2.pdf"));

        Instant earlier = Instant.now().minusSeconds(1);
        Instant later = Instant.now();

        PriorityCopyTask first = new PriorityCopyTask(task1, 10, device, earlier);
        PriorityCopyTask second = new PriorityCopyTask(task2, 10, device, later);

        assert first.compareTo(second) < 0 : "Earlier task should come first when priority equal";
        System.out.println("✓ FIFO tiebreaker works");
    }

    private static void testUnwrapReturnsOriginalTask() {
        TestDevice device = new TestDevice(Paths.get("C:\\test"));
        CopyTask original = new CopyTask(Paths.get("file.pdf"));
        PriorityCopyTask wrapper = new PriorityCopyTask(original, 10, device, Instant.now());

        assert wrapper.unwrap() == original : "unwrap() should return original CopyTask";
        System.out.println("✓ Unwrap returns original task");
    }
}
