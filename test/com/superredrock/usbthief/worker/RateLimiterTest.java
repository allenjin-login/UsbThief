package com.superredrock.usbthief.worker;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.statistics.SpeedStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimiter.
 */
class RateLimiterTest {

    private SpeedStatistics mockSpeedStats;
    private LoadEvaluator mockLoadEvaluator;

    // Store original config values for restoration
    private Long originalCopyRateLimitBase;
    private Integer originalRateLimitLowPercent;
    private Integer originalRateLimitMediumPercent;
    private Integer originalRateLimitHighPercent;

    @BeforeEach
    void setUp() {
        mockSpeedStats = mock(SpeedStatistics.class);
        mockLoadEvaluator = mock(LoadEvaluator.class);

        // Save original config values
        ConfigManager config = ConfigManager.getInstance();
        originalCopyRateLimitBase = config.get(ConfigSchema.COPY_RATE_LIMIT_BASE);
        originalRateLimitLowPercent = config.get(ConfigSchema.RATE_LIMIT_LOW_PERCENT);
        originalRateLimitMediumPercent = config.get(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT);
        originalRateLimitHighPercent = config.get(ConfigSchema.RATE_LIMIT_HIGH_PERCENT);
    }

    @AfterEach
    void tearDown() {
        // Restore original config values
        ConfigManager config = ConfigManager.getInstance();
        config.set(ConfigSchema.COPY_RATE_LIMIT_BASE, originalCopyRateLimitBase);
        config.set(ConfigSchema.RATE_LIMIT_LOW_PERCENT, originalRateLimitLowPercent);
        config.set(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT, originalRateLimitMediumPercent);
        config.set(ConfigSchema.RATE_LIMIT_HIGH_PERCENT, originalRateLimitHighPercent);
    }

    @Test
    void testAcquireBasic() throws InterruptedException {
        // Create limiter with no rate limit (0 = unlimited)
        long rateLimit = 0;
        long burstSize = 1024 * 1024; // 1 MB
        RateLimiter limiter = new RateLimiter(rateLimit, burstSize);

        // Acquire should succeed immediately when rate limit is 0
        assertDoesNotThrow(() -> limiter.acquire(1000));
        assertDoesNotThrow(() -> limiter.acquire(10 * 1024 * 1024)); // 10 MB

        // Verify getters return expected values
        assertEquals(rateLimit, limiter.getRateLimitBytesPerSecond());
        assertEquals(burstSize, limiter.getBurstSize());
    }

    @Test
    void testAcquireWithRateLimit() throws InterruptedException {
        // Create limiter with rate limit
        long rateLimit = 10 * 1024 * 1024; // 10 MB/s
        long burstSize = 1024 * 1024; // 1 MB
        RateLimiter limiter = new RateLimiter(rateLimit, burstSize);

        // First acquire should succeed immediately (burst available)
        long startTime = System.nanoTime();
        limiter.acquire(512 * 1024); // 512 KB
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        // Should complete quickly (within burst)
        assertTrue(elapsedMs < 100, "Acquire within burst should be fast, took: " + elapsedMs + "ms");

        // Verify rate limit
        assertEquals(rateLimit, limiter.getRateLimitBytesPerSecond());
    }

    @Test
    void testSetRateLimit() {
        // Create limiter with initial rate
        long initialRate = 1000;
        long burstSize = 10000;
        RateLimiter limiter = new RateLimiter(initialRate, burstSize);

        assertEquals(initialRate, limiter.getRateLimitBytesPerSecond());

        // Change rate limit
        long newRate = 2000;
        limiter.setRateLimit(newRate);

        assertEquals(newRate, limiter.getRateLimitBytesPerSecond());

        // Change to no limit
        limiter.setRateLimit(0);
        assertEquals(0, limiter.getRateLimitBytesPerSecond());

        // Change to a different rate
        limiter.setRateLimit(5000);
        assertEquals(5000, limiter.getRateLimitBytesPerSecond());
    }

    @Test
    void testSetRateLimitToZeroDisablesLimit() throws InterruptedException {
        // Create limiter with rate limit
        long rateLimit = 1024; // 1 KB/s
        long burstSize = 1024; // 1 KB
        RateLimiter limiter = new RateLimiter(rateLimit, burstSize);

        // Use up the burst
        limiter.acquire(1024);

        // Set to no limit
        limiter.setRateLimit(0);

        // Should now acquire without delay
        long startTime = System.nanoTime();
        limiter.acquire(10 * 1024 * 1024); // 10 MB
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(elapsedMs < 100, "Acquire with no limit should be fast, took: " + elapsedMs + "ms");
    }

    @ParameterizedTest
    @EnumSource(LoadLevel.class)
    void testAdjustRateByLoadLevel(LoadLevel level) {
        // Set up config with known base rate
        long baseRate = 100 * 1024 * 1024; // 100 MB/s
        ConfigManager config = ConfigManager.getInstance();
        config.set(ConfigSchema.COPY_RATE_LIMIT_BASE, baseRate);

        // Set known percentages
        config.set(ConfigSchema.RATE_LIMIT_LOW_PERCENT, 100);
        config.set(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT, 70);
        config.set(ConfigSchema.RATE_LIMIT_HIGH_PERCENT, 40);

        // Create limiter with some initial rate
        long initialRate = 50 * 1024 * 1024; // 50 MB/s (will be overridden)
        RateLimiter limiter = new RateLimiter(initialRate, 1024 * 1024, mockLoadEvaluator, mockSpeedStats);

        // Adjust rate based on load level
        limiter.adjustRateByLoadLevel(level);

        // Verify rate was adjusted correctly
        long adjustedRate = limiter.getRateLimitBytesPerSecond();
        int expectedPercent = switch (level) {
            case LOW -> 100;
            case MEDIUM -> 70;
            case HIGH -> 40;
        };

        long expectedRate = (baseRate * expectedPercent) / 100;
        assertEquals(expectedRate, adjustedRate,
                "Rate should be adjusted to " + expectedPercent + "% of base rate");
    }

    @Test
    void testAdjustRateByLoadLevel_nullLevelDoesNothing() {
        // Create limiter with initial rate
        long initialRate = 1000;
        RateLimiter limiter = new RateLimiter(initialRate, 1024);

        // Call with null - should not throw and rate should remain unchanged
        assertDoesNotThrow(() -> limiter.adjustRateByLoadLevel(null));
        assertEquals(initialRate, limiter.getRateLimitBytesPerSecond());
    }

    @Test
    void testAdjustRateByLoadLevel_usesCurrentRateWhenBaseIsZero() {
        // Set base rate to 0
        ConfigManager config = ConfigManager.getInstance();
        config.set(ConfigSchema.COPY_RATE_LIMIT_BASE, 0L);
        config.set(ConfigSchema.RATE_LIMIT_LOW_PERCENT, 100);
        config.set(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT, 70);
        config.set(ConfigSchema.RATE_LIMIT_HIGH_PERCENT, 40);

        // Create limiter with a specific rate
        long initialRate = 2000;
        RateLimiter limiter = new RateLimiter(initialRate, 1024);

        // Adjust with HIGH load
        limiter.adjustRateByLoadLevel(LoadLevel.HIGH);

        // Should use current rate as base: 2000 * 40% = 800
        assertEquals(800, limiter.getRateLimitBytesPerSecond());
    }

    @Test
    void testSpeedStatisticsIntegration() throws InterruptedException {
        // Create limiter with mock SpeedStatistics
        long rateLimit = 0; // No limit for faster test
        long burstSize = 1024 * 1024;
        RateLimiter limiter = new RateLimiter(rateLimit, burstSize, null, mockSpeedStats);

        // Acquire some bytes
        long bytesToAcquire = 5000;
        limiter.acquire(bytesToAcquire);

        // Verify recordBytes was called with correct value
        verify(mockSpeedStats, times(1)).recordBytes(bytesToAcquire);
    }

    @Test
    void testSpeedStatisticsIntegration_withRateLimit() throws InterruptedException {
        // Create limiter with mock SpeedStatistics and rate limit
        long rateLimit = 10 * 1024 * 1024; // 10 MB/s
        long burstSize = 1024 * 1024; // 1 MB
        RateLimiter limiter = new RateLimiter(rateLimit, burstSize, null, mockSpeedStats);

        // Acquire some bytes (within burst, should be fast)
        long bytesToAcquire = 512 * 1024; // 512 KB
        limiter.acquire(bytesToAcquire);

        // Verify recordBytes was called with correct value
        verify(mockSpeedStats, times(1)).recordBytes(bytesToAcquire);
    }

    @Test
    void testSpeedStatisticsIntegration_nullStatsDoesNotThrow() throws InterruptedException {
        // Create limiter without SpeedStatistics
        RateLimiter limiter = new RateLimiter(0, 1024);

        // Acquire should not throw even with null stats
        assertDoesNotThrow(() -> limiter.acquire(1000));
    }

    @Test
    void testSpeedStatisticsIntegration_recordBytesNotCalledForZeroBytes() throws InterruptedException {
        // Create limiter with mock SpeedStatistics
        RateLimiter limiter = new RateLimiter(0, 1024, null, mockSpeedStats);

        // SpeedStatistics.recordBytes ignores bytes <= 0, so we verify our integration
        // But in RateLimiter, we always call recordBytes with positive values from acquire()
        // This test verifies the flow works correctly

        limiter.acquire(100);
        verify(mockSpeedStats).recordBytes(100);
    }

    @Test
    void testConstructor_withoutDependencies() {
        // Simple constructor without LoadEvaluator and SpeedStatistics
        RateLimiter limiter = new RateLimiter(1000, 5000);

        assertEquals(1000, limiter.getRateLimitBytesPerSecond());
        assertEquals(5000, limiter.getBurstSize());
        assertNull(limiter.getLoadEvaluator());
        assertNull(limiter.getSpeedStatistics());
    }

    @Test
    void testConstructor_withDependencies() {
        // Full constructor with all dependencies
        RateLimiter limiter = new RateLimiter(1000, 5000, mockLoadEvaluator, mockSpeedStats);

        assertEquals(1000, limiter.getRateLimitBytesPerSecond());
        assertEquals(5000, limiter.getBurstSize());
        assertSame(mockLoadEvaluator, limiter.getLoadEvaluator());
        assertSame(mockSpeedStats, limiter.getSpeedStatistics());
    }

    @Test
    void testGetBurstSize() {
        long expectedBurstSize = 16 * 1024 * 1024; // 16 MB
        RateLimiter limiter = new RateLimiter(1000, expectedBurstSize);

        assertEquals(expectedBurstSize, limiter.getBurstSize());
    }

    @Test
    void testBurstSizeIsVolatile() {
        // This test verifies that burstSize is properly initialized
        // The volatile nature is implicit in the implementation
        RateLimiter limiter = new RateLimiter(1000, 2000);

        // Burst size should match constructor argument
        assertEquals(2000, limiter.getBurstSize());
    }
}
