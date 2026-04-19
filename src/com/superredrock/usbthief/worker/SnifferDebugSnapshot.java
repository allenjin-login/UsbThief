package com.superredrock.usbthief.worker;

public record SnifferDebugSnapshot(
    String driveLetter,
    String serialNumber,
    SnifferPhase phase,
    int changeCount,
    int threshold,
    int secondsUntilReset,
    int resetIntervalSec,
    int watchedDirCount,
    long cooldownRemainingMs,
    String cooldownReason
) {}