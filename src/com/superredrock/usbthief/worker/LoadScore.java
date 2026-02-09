package com.superredrock.usbthief.worker;

/**
 * Immutable record holding load evaluation results.
 *
 * @param score  Calculated load score (0-100)
 * @param level  Determined load level (LOW/MEDIUM/HIGH)
 */
public record LoadScore(int score, LoadLevel level) {
    public LoadScore {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score must be 0-100");
        }
        if (level == null) {
            throw new IllegalArgumentException("Level cannot be null");
        }
    }
}
