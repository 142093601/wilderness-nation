package com.wildernessnation.statecraft.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NationTest {

    /** 固定用这个工厂造"普通国家"；扩展字段（status/fatigue/…）在各自用例里单独说。 */
    private static Nation nation(
            String id, String name, String cultureId, int size,
            double development, double stance, double military, double treasury,
            double x, double z, boolean met, double attitude) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitude, Nation.Status.ALIVE, Nation.PartyStatus.NEUTRAL, 0L, 0.0,
                0.0, 0);
    }

    /** 只为了把"扩展字段的非法组合"逐个试一遍。 */
    private static Nation settlementFields(
            Nation.PartyStatus partyStatus, long untilSeq, double warScore, double fatigue,
            int absorbed) {
        return new Nation("n0", "赤沙", "bandit", 5, 60, 0, 20, 50, 0, 0, false, 0.0,
                Nation.Status.ALIVE, partyStatus, untilSeq, warScore, fatigue, absorbed);
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
        assertTrue(n.alive());
    }

    /** 开局工厂：存活、未交战、无疲劳、没吸收过别人。 */
    @Test
    void spawnUsesQuietDefaults() {
        Nation n = Nation.spawn("n1", "黑岩", "bandit", 3, 80.0, -20.0, 16.0, 60.0, 0.0, 0.0);
        assertEquals(Nation.Status.ALIVE, n.status());
        assertEquals(Nation.PartyStatus.NEUTRAL, n.partyStatus());
        assertEquals(0.0, n.fatigue(), 0.0);
        assertEquals(0, n.absorbedCount());
        assertFalse(n.met());
        assertEquals(0.0, n.attitudeToParty(), 0.0);
        assertTrue(n.alive());
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
    void rejectsBadSettlementFields() {
        assertThrows(IllegalArgumentException.class, () -> settlementFields(null, 0L, 0.0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> settlementFields(Nation.PartyStatus.NEUTRAL, 0L, 0.0, -0.1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> settlementFields(Nation.PartyStatus.NEUTRAL, 0L, Double.NaN, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> settlementFields(Nation.PartyStatus.NEUTRAL, 0L, 0.0, 0.0, -1));
        // 只有 TRUCE 才有期限，别的状态带期限就是写错了
        assertThrows(IllegalArgumentException.class,
                () -> settlementFields(Nation.PartyStatus.NEUTRAL, -5L, 0.0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> settlementFields(Nation.PartyStatus.WAR, 9L, 0.0, 0.0, 0));
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
        assertSameExcept(a, b, "position");
    }

    @Test
    void withPositionStillValidatesCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> ok().withPosition(Double.NaN, 0.0));
    }

    /**
     * 16 个位置参数最容易出的错是"改这个字段时顺手动了那个"（或者插错顺序而类型恰好兼容）。
     * 这里逐个 {@code withXxx} 断言：目标字段变了、**其余全部逐字段相等**。
     */
    @Test
    void everyWithMethodChangesExactlyOneField() {
        Nation a = nation("n0", "赤沙", "bandit", 5, 60.0, -10.0, 20.0, 50.0, 100.0, 200.0, true, 7.0);

        Nation size = a.withSize(9);
        assertEquals(9, size.size());
        assertSameExcept(a, size, "size");

        Nation dev = a.withDevelopment(75.5);
        assertEquals(75.5, dev.development(), 0.0);
        assertSameExcept(a, dev, "development");

        Nation mil = a.withMilitary(33.0);
        assertEquals(33.0, mil.military(), 0.0);
        assertSameExcept(a, mil, "military");

        Nation tre = a.withTreasury(99.0);
        assertEquals(99.0, tre.treasury(), 0.0);
        assertSameExcept(a, tre, "treasury");

        Nation att = a.withAttitudeToParty(-45.0);
        assertEquals(-45.0, att.attitudeToParty(), 0.0);
        assertSameExcept(a, att, "attitudeToParty");

        Nation met = a.withMet(false);
        assertFalse(met.met());
        assertSameExcept(a, met, "met");

        Nation dead = a.withStatus(Nation.Status.DEAD);
        assertEquals(Nation.Status.DEAD, dead.status());
        assertFalse(dead.alive());
        assertSameExcept(a, dead, "status");

        Nation war = a.withPartyStatus(Nation.PartyStatus.WAR, 0L);
        assertTrue(war.atWarWithParty());
        assertSameExcept(a, war, "partyStatus");

        Nation fat = a.withFatigue(2.5);
        assertEquals(2.5, fat.fatigue(), 0.0);
        assertSameExcept(a, fat, "fatigue");

        Nation abs = a.withAbsorbedCount(3);
        assertEquals(3, abs.absorbedCount());
        assertSameExcept(a, abs, "absorbedCount");
    }

    /** 除 {@code changed} 指的那一个（或 "position" = x/z）之外，所有字段必须相等。 */
    private static void assertSameExcept(Nation a, Nation b, String changed) {
        boolean coords = changed.equals("position");
        if (!coords) {
            assertEquals(a.x(), b.x(), 0.0, "x 不该变");
            assertEquals(a.z(), b.z(), 0.0, "z 不该变");
        }
        assertEquals(a.id(), b.id(), "id 不该变");
        assertEquals(a.name(), b.name(), "name 不该变");
        assertEquals(a.cultureId(), b.cultureId(), "cultureId 不该变");
        if (!changed.equals("size")) {
            assertEquals(a.size(), b.size(), "size 不该变");
        }
        if (!changed.equals("development")) {
            assertEquals(a.development(), b.development(), 0.0, "development 不该变");
        }
        assertEquals(a.stance(), b.stance(), 0.0, "stance 不该变");
        if (!changed.equals("military")) {
            assertEquals(a.military(), b.military(), 0.0, "military 不该变");
        }
        if (!changed.equals("treasury")) {
            assertEquals(a.treasury(), b.treasury(), 0.0, "treasury 不该变");
        }
        if (!changed.equals("met")) {
            assertEquals(a.met(), b.met(), "met 不该变");
        }
        if (!changed.equals("attitudeToParty")) {
            assertEquals(a.attitudeToParty(), b.attitudeToParty(), 0.0, "attitudeToParty 不该变");
        }
        if (!changed.equals("status")) {
            assertEquals(a.status(), b.status(), "status 不该变");
        }
        if (!changed.equals("partyStatus")) {
            assertEquals(a.partyStatus(), b.partyStatus(), "partyStatus 不该变");
            assertEquals(a.partyStatusUntilSeq(), b.partyStatusUntilSeq(), "期限不该变");
            assertEquals(a.partyWarScore(), b.partyWarScore(), 0.0, "战果不该变");
        }
        if (!changed.equals("fatigue")) {
            assertEquals(a.fatigue(), b.fatigue(), 0.0, "fatigue 不该变");
        }
        if (!changed.equals("absorbedCount")) {
            assertEquals(a.absorbedCount(), b.absorbedCount(), "absorbedCount 不该变");
        }
    }
}
