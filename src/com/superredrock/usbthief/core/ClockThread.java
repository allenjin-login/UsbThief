package com.superredrock.usbthief.core;

import java.util.concurrent.TimeUnit;

public class Clock {
    private final TimeUnit unit;

    public Clock(TimeUnit unit) {
        this.unit = unit;
    }
}
