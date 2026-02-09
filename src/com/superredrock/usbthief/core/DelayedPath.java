package com.superredrock.usbthief.core;

import java.nio.file.Path;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayedPath implements Delayed {
    private final Path path;
    private final int delay;
    private final long createTime = System.currentTimeMillis();

    public DelayedPath(Path path, int delay) {
        this.path = path;
        this.delay = delay;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return this.delay - unit.convert(System.currentTimeMillis() - createTime , unit) ;
    }

    @Override
    public int compareTo(Delayed o) {
        if (o instanceof DelayedPath pin) {
            return this.delay - pin.delay;
        }
        return 0;
    }

    public Path getPath() {
        return path;
    }

}
