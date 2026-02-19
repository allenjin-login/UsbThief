package com.superredrock.usbthief.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeviceState enum.
 */
class DeviceStateTest {

    @Test
    void pausedStateExists() {
        // Verify PAUSED state exists and is a valid enum value
        assertNotNull(Device.DeviceState.PAUSED);
    }

    @Test
    void pausedIsDistinctFromDisabled() {
        // Verify PAUSED and DISABLED are different states
        assertNotEquals(Device.DeviceState.PAUSED, Device.DeviceState.DISABLED,
            "PAUSED and DISABLED should be distinct states");
    }

    @Test
    void allStatesAreUnique() {
        // Verify all states are unique
        Device.DeviceState[] states = Device.DeviceState.values();
        for (int i = 0; i < states.length; i++) {
            for (int j = i + 1; j < states.length; j++) {
                assertNotEquals(states[i], states[j],
                    "DeviceState values should be unique: " + states[i] + " == " + states[j]);
            }
        }
    }
}
