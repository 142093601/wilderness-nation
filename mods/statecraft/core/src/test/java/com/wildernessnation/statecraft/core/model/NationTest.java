package com.wildernessnation.statecraft.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NationTest {

    private static Nation nation(
            String id, String name, String cultureId, int size,
            double development, double stance, double military, double treasury,
            double x, double z, boolean met, double attitude) {
        return new Nation(id, name, cultureId, size, development, stance,
                military, treasury, x, z, met, attitude);
    }

    private static Nation ok() {
        return nation("n0", "赤沙", "bandit", 5, 60.0, -10.0, 20.0, 50.0, 100.0, 200.0, false, 0.0);
    }

    @Test
    void acceptsAValidNation() {
        Nation n = ok();
        assertEquals("n0", n.id());
        assertEquals("赤沙", n.name());
        assertEquals("bandit", n.cultureId());
        assertEquals(5, n.size());
        assertEquals(60.0, n.development(), 0.0);
        assertEquals(-10.0, n.stance(), 0.0);
    }

    @Test
    void rejectsBlankIdentityText() {
        assertThrows(IllegalArgumentException.class,
                () -> nation(null, "赤沙", "bandit", 5, 60, 0, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "  ", "bandit", 5, 60, 0, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", null, 5, 60, 0, 20, 50, 0, 0, false, 0));
    }

    @Test
    void rejectsOutOfRangeSize() {
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 0, 60, 0, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 11, 60, 0, 20, 50, 0, 0, false, 0));
    }

    /**
     * 审查发现：NaN 对 {@code v < lo || v > hi} 两侧都是 false，所以原本能一路静默通过。
     */
    @Test
    void rejectsNaNInEveryRangeCheckedField() {
        double nan = Double.NaN;
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, nan, 0, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, nan, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, nan, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, nan, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50, nan, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50, 0, 0, false, nan));
    }

    @Test
    void rejectsInfiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50,
                        Double.POSITIVE_INFINITY, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50,
                        0, Double.NEGATIVE_INFINITY, false, 0));
    }

    /** 负军力会让"袭击规模 = 2 + military/25"算出负数，是必须挡住的非法状态。 */
    @Test
    void rejectsNegativeMilitaryOrTreasury() {
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, -5, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, -1, 0, 0, false, 0));
    }

    @Test
    void rejectsOutOfRangeDevelopmentStanceAndAttitude() {
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 101, 0, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, -1, 0, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 101, 20, 50, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50, 0, 0, false, -101));
        // 边界是闭区间
        assertEquals(100.0, nation("n0", "赤沙", "bandit", 10, 100, 100, 20, 50, 0, 0, false, 100)
                .development(), 0.0);
    }

    @Test
    void distanceToIsHorizontalDistance() {
        assertEquals(5.0, nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50, 3.0, 4.0, false, 0)
                .distanceTo(0.0, 0.0), 1e-9);
        assertEquals(0.0, ok().distanceTo(100.0, 200.0), 1e-9);
    }

    /** 换位只能动坐标，其它字段一个都不许变。 */
    @Test
    void withPositionOnlyChangesCoordinates() {
        Nation a = ok();
        Nation b = a.withPosition(500.0, -700.0);
        assertEquals(500.0, b.x(), 0.0);
        assertEquals(-700.0, b.z(), 0.0);
        assertEquals(a.id(), b.id());
        assertEquals(a.name(), b.name());
        assertEquals(a.cultureId(), b.cultureId());
        assertEquals(a.size(), b.size());
        assertEquals(a.development(), b.development(), 0.0);
        assertEquals(a.stance(), b.stance(), 0.0);
        assertEquals(a.military(), b.military(), 0.0);
        assertEquals(a.treasury(), b.treasury(), 0.0);
        assertEquals(a.met(), b.met());
        assertEquals(a.attitudeToParty(), b.attitudeToParty(), 0.0);
        assertTrue(a != b);
    }

    @Test
    void withPositionStillValidatesCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> ok().withPosition(Double.NaN, 0.0));
    }
}
