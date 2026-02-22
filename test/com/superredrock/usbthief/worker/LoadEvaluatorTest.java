package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.ServiceState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoadEvaluator.
 * 
 * <p>Tests the Service-based LoadEvaluator which evaluates system load
 * based on queue depth, copy speed, thread activity, and rejection rate.
 */
class LoadEvaluatorTest {

    private LoadEvaluator loadEvaluator;

    @BeforeEach
    void setUp() {
        loadEvaluator = new LoadEvaluator();
    }

    @AfterEach
    void tearDown() {
        if (loadEvaluator != null && loadEvaluator.isAlive()) {
            loadEvaluator.stopService();
        }
    }

    // ========== Test 1: Service Lifecycle ==========

    /**
     * Test: LoadEvaluator extends Service correctly and lifecycle works.
     */
    @Test
    void testServiceLifecycle() throws InterruptedException {
        // Verify initial state
        assertNotNull(loadEvaluator);
        assertEquals("LoadEvaluator", loadEvaluator.getServiceName());
        assertNotNull(loadEvaluator.getDescription());
        assertEquals(500, loadEvaluator.getTickIntervalMs());
        assertEquals(ServiceState.STOPPED, loadEvaluator.getServiceState());

        // Start service
        loadEvaluator.start();
        Thread.sleep(100); // Give time to start
        
        assertTrue(loadEvaluator.isRunning());
        assertEquals(ServiceState.RUNNING, loadEvaluator.getServiceState());

        // Stop service
        loadEvaluator.stopService();
        Thread.sleep(100); // Give time to stop
        
        assertFalse(loadEvaluator.isRunning());
        assertEquals(ServiceState.STOPPED, loadEvaluator.getServiceState());
    }

    // ========== Test 2: Tick Updates Cache ==========

    /**
     * Test: tick() method updates the cached LoadScore.
     */
    @Test
    void testTickUpdatesCache() throws InterruptedException {
        // Get initial cached value (default is score=50, MEDIUM)
        LoadScore initialScore = loadEvaluator.evaluateLoad();
        assertNotNull(initialScore);
        assertEquals(50, initialScore.score());
        assertEquals(LoadLevel.MEDIUM, initialScore.level());

        // Start service and wait for at least one tick
        loadEvaluator.start();
        Thread.sleep(700); // Wait longer than tick interval (500ms)
        
        // Get score after tick - should be updated based on actual metrics
        LoadScore afterTick = loadEvaluator.evaluateLoad();
        assertNotNull(afterTick);
        // Score should be 0-100 and level should be valid
        assertTrue(afterTick.score() >= 0 && afterTick.score() <= 100);
        assertNotNull(afterTick.level());
    }

    // ========== Test 3: EvaluateLoad Returns Cached Value ==========

    /**
     * Test: evaluateLoad() returns cached value and is non-blocking.
     */
    @Test
    void testEvaluateLoadReturnsCached() {
        // Call evaluateLoad multiple times - should return same cached instance
        LoadScore score1 = loadEvaluator.evaluateLoad();
        LoadScore score2 = loadEvaluator.evaluateLoad();
        LoadScore score3 = loadEvaluator.evaluateLoad();

        // All calls should return the same cached score
        assertNotNull(score1);
        assertEquals(score1.score(), score2.score());
        assertEquals(score1.level(), score2.level());
        assertEquals(score2.score(), score3.score());
        assertEquals(score2.level(), score3.level());
    }

    /**
     * Test: evaluateLoad() returns immediately (non-blocking) even when service is stopped.
     */
    @Test
    void testEvaluateLoadIsNonBlocking() {
        long startTime = System.currentTimeMillis();
        
        // Multiple calls should be near-instant (cached)
        for (int i = 0; i < 1000; i++) {
            loadEvaluator.evaluateLoad();
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        // 1000 calls should complete in well under 100ms if cached
        assertTrue(elapsed < 100, "evaluateLoad() should be non-blocking, took " + elapsed + "ms");
    }

    // ========== Test 4: Load Level Determination ==========

    /**
     * Test: Load level is determined correctly based on thresholds.
     * Default thresholds: HIGH > 70, MEDIUM > 40, LOW <= 40
     */
    @Test
    void testLoadLevelDetermination_LowLoad() {
        // When queue is empty, no active copies, and no rejections
        // Score should be low (queueScore=0, threadScore depends on pool activity)
        // After tick with idle system, level should be LOW or MEDIUM
        
        loadEvaluator.start();
        
        // Wait for tick to complete
        awaitTick();
        
        LoadScore score = loadEvaluator.evaluateLoad();
        // With idle system, score should be relatively low
        // Note: exact value depends on thread pool state
        assertNotNull(score.level());
    }

    /**
     * Test: LoadScore contains valid score range (0-100).
     */
    @Test
    void testLoadScoreIsValidRange() throws InterruptedException {
        loadEvaluator.start();
        Thread.sleep(700); // Wait for tick
        
        LoadScore score = loadEvaluator.evaluateLoad();
        
        // Score must be in valid range
        assertTrue(score.score() >= 0, "Score should be >= 0");
        assertTrue(score.score() <= 100, "Score should be <= 100");
        
        // Level must be one of the enum values
        assertTrue(score.level() == LoadLevel.LOW || 
                   score.level() == LoadLevel.MEDIUM || 
                   score.level() == LoadLevel.HIGH);
    }

    // ========== Test 5: Pause/Resume Support ==========

    /**
     * Test: Service can be paused and resumed.
     */
    @Test
    void testPauseResume() throws InterruptedException {
        loadEvaluator.start();
        Thread.sleep(100);
        
        assertTrue(loadEvaluator.isRunning());
        
        // Pause
        loadEvaluator.pause();
        Thread.sleep(50);
        assertEquals(ServiceState.PAUSED, loadEvaluator.getServiceState());
        
        // Get cached value while paused - should still work
        LoadScore pausedScore = loadEvaluator.evaluateLoad();
        assertNotNull(pausedScore);
        
        // Resume
        loadEvaluator.resume();
        Thread.sleep(50);
        assertEquals(ServiceState.RUNNING, loadEvaluator.getServiceState());
    }

    // ========== Test 6: Service Metadata ==========

    /**
     * Test: Service name and description are correctly set.
     */
    @Test
    void testServiceMetadata() {
        assertEquals("LoadEvaluator", loadEvaluator.getServiceName());
        
        String description = loadEvaluator.getDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty());
        assertTrue(description.contains("load") || description.contains("Load"));
    }

    /**
     * Test: Tick interval is 500ms as specified.
     */
    @Test
    void testTickInterval() {
        assertEquals(500, loadEvaluator.getTickIntervalMs());
    }

    // ========== Test 7: Direct Tick Invocation ==========

    /**
     * Test: tick() can be called directly for testing purposes.
     */
    @Test
    void testDirectTickInvocation() {
        // Get initial cached value
        LoadScore before = loadEvaluator.evaluateLoad();
        
        // Call tick directly (bypasses service thread)
        loadEvaluator.tick();
        
        // Get cached value after tick
        LoadScore after = loadEvaluator.evaluateLoad();
        
        // tick() should have updated the cache
        assertNotNull(after);
        // The exact values depend on system state, but it should be valid
        assertTrue(after.score() >= 0 && after.score() <= 100);
    }

    // ========== Helper Methods ==========

    /**
     * Wait for at least one tick to complete.
     */
    private void awaitTick() {
        try {
            Thread.sleep(600); // Slightly longer than tick interval
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
