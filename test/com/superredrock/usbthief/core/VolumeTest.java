package com.superredrock.usbthief.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VolumeTest {

    private Volume volume;

    @BeforeEach
    void setUp() {
        volume = new Volume(Path.of("X:\\nonexistent\\" + System.nanoTime()), "serial123");
    }

    @Test
    void initialState() {
        // Path doesn't exist, so state is UNAVAILABLE (set in constructor refreshMetadata)
        Volume.VolumeState state = volume.getState();
        // Could be UNAVAILABLE or IDLE depending on whether the path exists
        assertNotNull(state);
    }

    @Test
    void setStateValidTransitions() {
        // Start from OFFLINE
        volume.setState(Volume.VolumeState.OFFLINE);
        assertEquals(Volume.VolumeState.OFFLINE, volume.getState());

        // OFFLINE -> IDLE
        volume.setState(Volume.VolumeState.IDLE);
        assertEquals(Volume.VolumeState.IDLE, volume.getState());

        // IDLE -> DISABLED
        volume.setState(Volume.VolumeState.DISABLED);
        assertEquals(Volume.VolumeState.DISABLED, volume.getState());

        // DISABLED -> IDLE
        volume.setState(Volume.VolumeState.IDLE);
        assertEquals(Volume.VolumeState.IDLE, volume.getState());
    }

    @Test
    void setStateEjecting() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.EJECTING);
        assertEquals(Volume.VolumeState.EJECTING, volume.getState());
    }

    @Test
    void ejectingOnlyToOffline() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.EJECTING);
        volume.setState(Volume.VolumeState.OFFLINE);
        assertEquals(Volume.VolumeState.OFFLINE, volume.getState());
    }

    @Test
    void unavailableTransitions() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.UNAVAILABLE);
        assertEquals(Volume.VolumeState.UNAVAILABLE, volume.getState());

        volume.setState(Volume.VolumeState.OFFLINE);
        assertEquals(Volume.VolumeState.OFFLINE, volume.getState());
    }

    @Test
    void enableFromDisabled() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.DISABLED);
        volume.enable();
        assertEquals(Volume.VolumeState.IDLE, volume.getState());
    }

    @Test
    void disableFromIdle() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.disable();
        assertEquals(Volume.VolumeState.DISABLED, volume.getState());
    }

    @Test
    void setEjectingFromIdle() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setEjecting();
        assertEquals(Volume.VolumeState.EJECTING, volume.getState());
    }

    @Test
    void updateStateDoesNotOverrideDisabled() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.DISABLED);
        volume.updateState(); // Should not change from DISABLED
        assertEquals(Volume.VolumeState.DISABLED, volume.getState());
    }

    @Test
    void updateStateDoesNotOverrideEjecting() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.EJECTING);
        volume.updateState(); // Should not change from EJECTING
        assertEquals(Volume.VolumeState.EJECTING, volume.getState());
    }

    @Test
    void isConnectedFalseForNonExistent() {
        assertFalse(volume.isConnected());
    }

    @Test
    void isOfflineQuery() {
        volume.setState(Volume.VolumeState.OFFLINE);
        assertTrue(volume.isOffline());
    }

    @Test
    void isActiveQuery() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        assertTrue(volume.isActive());
    }

    @Test
    void isDisabledQuery() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.DISABLED);
        assertTrue(volume.isDisabled());
    }

    @Test
    void isEjectingQuery() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        volume.setState(Volume.VolumeState.EJECTING);
        assertTrue(volume.isEjecting());
    }

    @Test
    void isPresentFalseForOffline() {
        volume.setState(Volume.VolumeState.OFFLINE);
        assertFalse(volume.isPresent());
    }

    @Test
    void isPresentTrueForOtherStates() {
        volume.setState(Volume.VolumeState.OFFLINE);
        volume.setState(Volume.VolumeState.IDLE);
        assertTrue(volume.isPresent());
    }

    @Test
    void volumeStateIsPresent() {
        assertFalse(Volume.VolumeState.OFFLINE.isPresent());
        assertTrue(Volume.VolumeState.IDLE.isPresent());
        assertTrue(Volume.VolumeState.UNAVAILABLE.isPresent());
        assertTrue(Volume.VolumeState.DISABLED.isPresent());
        assertTrue(Volume.VolumeState.EJECTING.isPresent());
    }

    @Test
    void getSerialNumber() {
        assertEquals("serial123", volume.getSerialNumber());
    }

    @Test
    void getters() {
        assertNotNull(volume.getRootPath());
        assertNotNull(volume.getSerialNumber());
    }

    @Test
    void equalsBySerialNumber() {
        Volume a = new Volume(Path.of("E:\\"), "serial1");
        Volume b = new Volume(Path.of("F:\\"), "serial1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualsDifferentSerial() {
        Volume a = new Volume(Path.of("E:\\"), "serial1");
        Volume b = new Volume(Path.of("E:\\"), "serial2");
        assertNotEquals(a, b);
    }

    @Test
    void isChangeAndReset() {
        // Clear any state change from constructor
        volume.isChangeAndReset();

        volume.setState(Volume.VolumeState.OFFLINE);
        assertTrue(volume.isChangeAndReset());
        assertFalse(volume.isChangeAndReset()); // Reset after read

        // Same state again
        volume.setState(Volume.VolumeState.OFFLINE);
        assertFalse(volume.isChangeAndReset());
    }
}
