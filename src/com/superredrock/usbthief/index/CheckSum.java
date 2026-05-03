package com.superredrock.usbthief.index;

import java.util.Arrays;

public final class CheckSum {
    private final byte[] context;

    public CheckSum(byte[] context) {
        this.context = context;
    }

    public byte[] context() {
        return context;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CheckSum)) return false;
        CheckSum that = (CheckSum) obj;
        return Arrays.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(context);
    }

    @Override
    public String toString() {
        return "CheckSum[context=" + java.util.Arrays.toString(context) + "]";
    }
}
