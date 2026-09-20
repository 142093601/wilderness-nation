package com.wildernessnation.statecraft.core.settle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 结算节拍（§七 的前两种触发）。
 *
 * <p>这组用例守的是两条纪律：**不在线不推进**、**时代推进优先于周期小结算**。
 * 没有它，那条"按累计在线时间推进"的设计就只是文档里的一句话。
 */
class SettlementClockTest {

    private static final double PERIODIC = 2.0;

    @Test
    void nobodyOnlineMeansNoProgressAtAll() {
        SettlementClock.Plan p = SettlementClock.plan(10.0, 5.0, 0, PERIODIC, 0.0);
        assertEquals(SettlementClock.Kind.NONE, p.kind());
        assertFalse(p.due());
        assertThrows(IllegalArgumentException.class, () -> new SettlementClock.Plan(
                SettlementClock.Kind.NONE, 1.0), "NONE 不许带增量");
    }

    @Test
    void aPeriodicSettlementFiresOnceTwoHoursHaveAccumulated() {
        assertEquals(SettlementClock.Kind.NONE,
                SettlementClock.plan(10.0, PERIODIC - 0.01, 1, PERIODIC, 0.0).kind());
        SettlementClock.Plan p = SettlementClock.plan(10.0, PERIODIC, 1, PERIODIC, 0.0);
        assertEquals(SettlementClock.Kind.PERIODIC, p.kind());
        assertEquals(PERIODIC, p.hoursDelta(), 1e-9);
    }

    /** 攒到 2.5 小时才回来结算时，delta 是 2.5（真实攒下的），不是四舍五入到 2 —— 时钟不漂。 */
    @Test
    void theDeltaIsWhatActuallyAccumulatedNotTheNominalPeriod() {
        SettlementClock.Plan p = SettlementClock.plan(10.0, 2.5, 1, PERIODIC, 0.0);
        assertEquals(SettlementClock.Kind.PERIODIC, p.kind());
        assertEquals(2.5, p.hoursDelta(), 1e-9);
    }

    @Test
    void crossingAnEraBoundaryWinsOverThePeriodicBeat() {
        // 已经 23 小时（第一个时代 24 小时），又攒了 2 小时 → 会跨过 24 小时的边界
        SettlementClock.Plan p = SettlementClock.plan(23.0, 2.0, 1, PERIODIC, 24.0);
        assertEquals(SettlementClock.Kind.ERA_ADVANCE, p.kind(),
                "跨时代时只跑一次，而且是时代推进（§七 的主节拍）");
        assertEquals(2.0, p.hoursDelta(), 1e-9);
    }

    @Test
    void anEraBoundaryFarAwayDoesNotChangeThePeriodicBeat() {
        assertEquals(SettlementClock.Kind.PERIODIC,
                SettlementClock.plan(3.0, 2.0, 1, PERIODIC, 24.0).kind());
    }

    /** 最后一个时代没有下一个边界（传 0）—— 那就只剩周期小结算。 */
    @Test
    void theLastEraHasNoBoundary() {
        assertEquals(SettlementClock.Kind.PERIODIC,
                SettlementClock.plan(299.0, 2.0, 1, PERIODIC, 0.0).kind());
    }

    /** 时代推进也是**至少要有在线时间**才发生。 */
    @Test
    void anEraBoundaryWithNoPendingTimeDoesNothing() {
        assertEquals(SettlementClock.Kind.NONE,
                SettlementClock.plan(24.0, 0.0, 1, PERIODIC, 24.0).kind());
    }

    @Test
    void secondsConvertToHoursInExactlyOnePlace() {
        assertEquals(1.0, SettlementClock.secondsToHours(3600.0), 1e-12);
        assertEquals(2.0 / 60.0, SettlementClock.secondsToHours(120.0), 1e-12);
        assertEquals(0.0, SettlementClock.secondsToHours(0.0), 0.0);
        assertThrows(IllegalArgumentException.class, () -> SettlementClock.secondsToHours(-1.0));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.secondsToHours(Double.NaN));
    }

    @Test
    void nonsenseInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.plan(-1.0, 1.0, 1, PERIODIC, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.plan(1.0, Double.NaN, 1, PERIODIC, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.plan(1.0, 1.0, 1, 0.0, 0.0), "周期必须为正");
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.plan(1.0, 1.0, 1, PERIODIC, Double.POSITIVE_INFINITY));
    }

    @Test
    void aDuePlanAlwaysCarriesPositiveTime() {
        SettlementClock.Plan p = SettlementClock.plan(0.0, 1.0, 3, PERIODIC, 0.0);
        assertFalse(p.due());
        assertTrue(SettlementClock.plan(0.0, 2.0, 3, PERIODIC, 0.0).due());
    }
}
