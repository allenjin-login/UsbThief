package com.superredrock.usbthief.index;

import java.util.Arrays;

public record CheckSum(byte[] context) {
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CheckSum) {
            CheckSum that = (CheckSum) obj;
            return Arrays.equals(context, that.context);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(context);
    }
}
