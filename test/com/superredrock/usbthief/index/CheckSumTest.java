package com.superredrock.usbthief.index;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckSumTest {

    @Test
    void equalsSameContent() {
        byte[] data = {1, 2, 3, 4};
        CheckSum a = new CheckSum(data);
        CheckSum b = new CheckSum(data.clone());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalsDifferentContent() {
        CheckSum a = new CheckSum(new byte[]{1, 2, 3});
        CheckSum b = new CheckSum(new byte[]{4, 5, 6});
        assertNotEquals(a, b);
    }

    @Test
    void equalsNullReturnsFalse() {
        CheckSum a = new CheckSum(new byte[]{1, 2, 3});
        assertFalse(a.equals(null));
    }

    @Test
    void equalsDifferentTypeReturnsFalse() {
        CheckSum a = new CheckSum(new byte[]{1, 2, 3});
        assertFalse(a.equals("not a checksum"));
    }

    @Test
    void preservesContent() {
        byte[] data = {42, 0, -1, 127};
        CheckSum cs = new CheckSum(data);
        assertArrayEquals(data, cs.context());
    }

    @Test
    void emptyByteArray() {
        CheckSum a = new CheckSum(new byte[0]);
        CheckSum b = new CheckSum(new byte[0]);
        assertEquals(a, b);
    }

    @Test
    void sameHashCodeForSameContent() {
        byte[] data = {1, 2, 3, 4, 5};
        CheckSum a = new CheckSum(data);
        CheckSum b = new CheckSum(data.clone());
        assertEquals(a.hashCode(), b.hashCode());
    }
}
