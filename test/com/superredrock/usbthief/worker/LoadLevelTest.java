package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadLevelTest {

    @Test
    void values_shouldContainThreeLevels() {
        LoadLevel[] levels = LoadLevel.values();

        assertEquals(3, levels.length);
    }

    @Test
    void valueOf_shouldReturnCorrectLevel() {
        assertEquals(LoadLevel.LOW, LoadLevel.valueOf("LOW"));
        assertEquals(LoadLevel.MEDIUM, LoadLevel.valueOf("MEDIUM"));
        assertEquals(LoadLevel.HIGH, LoadLevel.valueOf("HIGH"));
    }

    @Test
    void ordinal_shouldReturnCorrectOrder() {
        assertEquals(0, LoadLevel.LOW.ordinal());
        assertEquals(1, LoadLevel.MEDIUM.ordinal());
        assertEquals(2, LoadLevel.HIGH.ordinal());
    }
}
