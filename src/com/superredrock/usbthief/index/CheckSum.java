package com.superredrock.usbthief.index;

import java.util.Arrays;

public record CheckSum(byte[] context) {
    @Override
    public boolean equals(Object obj) {
        return switch (obj) {
            case CheckSum that -> Arrays.equals(context, that.context);
            case null, default -> false;
        };
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(context);
    }
}
