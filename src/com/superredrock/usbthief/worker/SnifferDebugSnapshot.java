package com.superredrock.usbthief.worker;

import java.util.Objects;

public final class SnifferDebugSnapshot {
    private final String driveLetter;
    private final String serialNumber;
    private final SnifferPhase phase;
    private final int changeCount;
    private final int threshold;
    private final int secondsUntilReset;
    private final int resetIntervalSec;
    private final int watchedDirCount;
    private final long cooldownRemainingMs;
    private final String cooldownReason;

    public SnifferDebugSnapshot(String driveLetter, String serialNumber, SnifferPhase phase,
                               int changeCount, int threshold, int secondsUntilReset,
                               int resetIntervalSec, int watchedDirCount,
                               long cooldownRemainingMs, String cooldownReason) {
        this.driveLetter = driveLetter;
        this.serialNumber = serialNumber;
        this.phase = phase;
        this.changeCount = changeCount;
        this.threshold = threshold;
        this.secondsUntilReset = secondsUntilReset;
        this.resetIntervalSec = resetIntervalSec;
        this.watchedDirCount = watchedDirCount;
        this.cooldownRemainingMs = cooldownRemainingMs;
        this.cooldownReason = cooldownReason;
    }

    public String driveLetter() { return driveLetter; }
    public String serialNumber() { return serialNumber; }
    public SnifferPhase phase() { return phase; }
    public int changeCount() { return changeCount; }
    public int threshold() { return threshold; }
    public int secondsUntilReset() { return secondsUntilReset; }
    public int resetIntervalSec() { return resetIntervalSec; }
    public int watchedDirCount() { return watchedDirCount; }
    public long cooldownRemainingMs() { return cooldownRemainingMs; }
    public String cooldownReason() { return cooldownReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnifferDebugSnapshot)) return false;
        SnifferDebugSnapshot that = (SnifferDebugSnapshot) o;
        return changeCount == that.changeCount &&
               threshold == that.threshold &&
               secondsUntilReset == that.secondsUntilReset &&
               resetIntervalSec == that.resetIntervalSec &&
               watchedDirCount == that.watchedDirCount &&
               cooldownRemainingMs == that.cooldownRemainingMs &&
               Objects.equals(driveLetter, that.driveLetter) &&
               Objects.equals(serialNumber, that.serialNumber) &&
               phase == that.phase &&
               Objects.equals(cooldownReason, that.cooldownReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driveLetter, serialNumber, phase, changeCount, threshold,
                           secondsUntilReset, resetIntervalSec, watchedDirCount,
                           cooldownRemainingMs, cooldownReason);
    }

    @Override
    public String toString() {
        return "SnifferDebugSnapshot[driveLetter=" + driveLetter + ", serialNumber=" + serialNumber +
               ", phase=" + phase + ", changeCount=" + changeCount + ", threshold=" + threshold +
               ", secondsUntilReset=" + secondsUntilReset + ", resetIntervalSec=" + resetIntervalSec +
               ", watchedDirCount=" + watchedDirCount + ", cooldownRemainingMs=" + cooldownRemainingMs +
               ", cooldownReason=" + cooldownReason + "]";
    }
}