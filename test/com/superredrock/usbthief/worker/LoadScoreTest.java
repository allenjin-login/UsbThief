package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LoadScoreTest {

    @Test
    void constructor_shouldCreateValidInstance() {
        LoadScore score = new LoadScore(50, LoadLevel.MEDIUM);

        assertEquals(50, score.score());
        assertEquals(LoadLevel.MEDIUM, score.level());
    }

    @ParameterizedTest
    @CsvSource({
        "0, LOW",
        "40, LOW",
        "41, MEDIUM",
        "50, MEDIUM",
        "70, MEDIUM",
        "71, HIGH",
        "100, HIGH"
    })
    void score_shouldMapToCorrectLevel(int scoreValue, LoadLevel expectedLevel) {
        LoadScore score = new LoadScore(scoreValue, expectedLevel);

        assertEquals(scoreValue, score.score());
        assertEquals(expectedLevel, score.level());
    }

    @Test
    void equals_shouldReturnTrueForSameValues() {
        LoadScore score1 = new LoadScore(50, LoadLevel.MEDIUM);
        LoadScore score2 = new LoadScore(50, LoadLevel.MEDIUM);

        assertEquals(score1, score2);
    }

    @Test
    void equals_shouldReturnFalseForDifferentScore() {
        LoadScore score1 = new LoadScore(50, LoadLevel.MEDIUM);
        LoadScore score2 = new LoadScore(60, LoadLevel.MEDIUM);

        assertNotEquals(score1, score2);
    }

    @Test
    void equals_shouldReturnFalseForDifferentLevel() {
        LoadScore score1 = new LoadScore(50, LoadLevel.LOW);
        LoadScore score2 = new LoadScore(50, LoadLevel.MEDIUM);

        assertNotEquals(score1, score2);
    }

    @Test
    void hashCode_shouldBeConsistent() {
        LoadScore score1 = new LoadScore(50, LoadLevel.MEDIUM);
        LoadScore score2 = new LoadScore(50, LoadLevel.MEDIUM);

        assertEquals(score1.hashCode(), score2.hashCode());
    }
}
