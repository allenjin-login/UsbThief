package com.superredrock.usbthief.worker;

/**
 * Load level classification for adaptive task scheduling.
 */
public enum LoadLevel {
    LOW,     // System is idle
    MEDIUM,  // Moderate load
    HIGH     // System congested
}
