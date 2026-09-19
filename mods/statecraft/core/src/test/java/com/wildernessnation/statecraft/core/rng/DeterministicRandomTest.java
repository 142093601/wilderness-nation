package com.wildernessnation.statecraft.core.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeterministicRandomTest {

    @Test
    void sameSeedSeqTagGivesSameValue() {
        DeterministicRandom a = new DeterministicRandom(42L);
        DeterministicRandom b = new DeterministicRandom(42L);
        assertEquals(a.hash(7L, "size#3"), b.hash(7L, "size#3"));
        assertEquals(a.nextDouble(7L, "size#3"), b.nextDouble(7L, "size#3"), 0.0);
    }

    @Test
    void differentSeedOrSeqOrTagChangesValue() {
        DeterministicRandom r = new DeterministicRandom(42L);
        assertNotEquals(r.hash(7L, "size#3"), r.hash(8L, "size#3"));
        assertNotEquals(r.hash(7L, "size#3"), r.hash(7L, "size#4"));
        assertNotEquals(r.hash(7L, "size#3"), new DeterministicRandom(43L).hash(7L, "size#3"));
    }

    @Test
    void nextDoubleIsInUnitInterval() {
        DeterministicRandom r = new DeterministicRandom(1234L);
        for (long seq = 0; seq < 500; seq++) {
            double d = r.nextDouble(seq, "probe");
            assertTrue(d >= 0.0 && d < 1.0, "nextDouble out of range: " + d);
        }
    }

    @Test
    void rangeStaysWithinBoundsInclusive() {
        DeterministicRandom r = new DeterministicRandom(99L);
        for (long seq = 0; seq < 500; seq++) {
            int v = r.range(seq, "size", 1, 10);
            assertTrue(v >= 1 && v <= 10, "range out of bounds: " + v);
        }
    }

    @Test
    void rangeDoubleStaysWithinBounds() {
        DeterministicRandom r = new DeterministicRandom(5L);
        for (long seq = 0; seq < 200; seq++) {
            double v = r.rangeDouble(seq, "jit", -8.0, 8.0);
            assertTrue(v >= -8.0 && v <= 8.0, "rangeDouble out of bounds: " + v);
        }
    }

    @Test
    void rejectsNonPositiveBound() {
        DeterministicRandom r = new DeterministicRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(0L, "x", 0));
    }

    @Test
    void rejectsInvertedRange() {
        DeterministicRandom r = new DeterministicRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> r.range(0L, "x", 5, 4));
    }
}
