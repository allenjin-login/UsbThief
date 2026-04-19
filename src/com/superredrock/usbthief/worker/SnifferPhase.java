package com.superredrock.usbthief.worker;

public enum SnifferPhase {
    INITIAL_SCAN("Scanning"),
    MONITORING("Monitoring"),
    FINISHED("Finished");

    private final String display;

    SnifferPhase(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}