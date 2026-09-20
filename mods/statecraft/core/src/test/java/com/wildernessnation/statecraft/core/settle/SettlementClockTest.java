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

    // ==================================================================
    // 提前推进时代（时代钥匙）
    // ==================================================================
    //
    // 守的是一条**结构**纪律：加速**不新造旁路**，只把累计小时推到边界，
    // 于是时代级结算（事件 / 国书 / 天下大势）仍由 ERA_ADVANCE 那条老路径完成。
    // 下面最后一条用例把这件事钉死：推到边界之后，plan 必须判定 ERA_ADVANCE。

    @Test
    void acceleratingAsksForExactlyTheHoursLeftToTheBoundary() {
        // 第一时代 24 小时，已经走了 10 小时 → 还差 14
        assertEquals(14.0,
                SettlementClock.hoursToBoundary(10.0, java.util.OptionalDouble.of(24.0)).getAsDouble(),
                1e-9);
    }

    @Test
    void alreadyAtOrPastTheBoundaryAsksForZeroNotNegative() {
        assertEquals(0.0,
                SettlementClock.hoursToBoundary(24.0, java.util.OptionalDouble.of(24.0)).getAsDouble(),
                0.0);
        assertEquals(0.0,
                SettlementClock.hoursToBoundary(30.0, java.util.OptionalDouble.of(24.0)).getAsDouble(),
                0.0);
    }

    /** 最后一个时代没有下一个边界 —— 加速必须被拒绝，而不是悄悄什么都不做。 */
    @Test
    void theLastEraCannotBeAccelerated() {
        assertTrue(SettlementClock.hoursToBoundary(300.0, java.util.OptionalDouble.empty()).isEmpty());
        assertTrue(SettlementClock.hoursToBoundary(0.0, null).isEmpty(), "null 视同没有边界");
    }

    @Test
    void acceleratingRejectsNonsenseInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.hoursToBoundary(-1.0, java.util.OptionalDouble.of(24.0)));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.hoursToBoundary(Double.NaN, java.util.OptionalDouble.of(24.0)));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementClock.hoursToBoundary(1.0,
                        java.util.OptionalDouble.of(Double.POSITIVE_INFINITY)));
    }

    /**
     * **这条是整块的支点**：把小时数推到 {@code hoursToBoundary} 给的值之后，
     * 本来就存在的 {@link SettlementClock#plan} 必须判定为时代推进 ——
     * 也就是说"拿到钥匙"和"时间到"走的是同一段代码，没有第二条路径。
     */
    @Test
    void pushingToTheBoundaryMakesTheOrdinaryPlanDecideToAdvanceTheEra() {
        double boundary = 24.0;
        double elapsed = 3.0;
        double pending = 2.5;                       // 已经攒了但还没结算的（≥ periodicHours 才会小结算）
        double need = SettlementClock
                .hoursToBoundary(elapsed, java.util.OptionalDouble.of(boundary)).getAsDouble();
        assertEquals(21.0, need, 1e-9);             // 24 − 3

        // 加速 = 把这 need 加进 pending（与 /statecraft pending 同一个口子）
        SettlementClock.Plan accelerated =
                SettlementClock.plan(elapsed, pending + need, 1, PERIODIC, boundary);
        assertEquals(SettlementClock.Kind.ERA_ADVANCE, accelerated.kind(),
                "推到边界后必须由**原有**的 plan 判定时代推进");
        assertEquals(pending + need, accelerated.hoursDelta(), 1e-9);

        // 对照：不加速时同一次 plan 只是周期小结算（证明上面那条不是因为别的原因）
        assertEquals(SettlementClock.Kind.PERIODIC,
                SettlementClock.plan(elapsed, pending, 1, PERIODIC, boundary).kind());
    }
}
