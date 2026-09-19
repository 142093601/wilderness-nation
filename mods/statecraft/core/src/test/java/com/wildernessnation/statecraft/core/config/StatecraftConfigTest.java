package com.wildernessnation.statecraft.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatecraftConfigTest {

    /** 默认参数的手写副本，只用于"改一个字段"的用例，避免每次改签名都要动一堆字面量。 */
    private static StatecraftConfig of(
            int nationCount, int spacing, int sizeMin, int sizeMax,
            double devBase, double devPerSize, double devJitter, double devMin, double devMax,
            double stanceBase, double stanceJitter, double rMin, double rMax) {
        return new StatecraftConfig(nationCount, spacing, sizeMin, sizeMax,
                devBase, devPerSize, devJitter, devMin, devMax,
                stanceBase, stanceJitter, rMin, rMax);
    }

    private static StatecraftConfig withDev(double devMin, double devMax) {
        return of(12, 1500, 1, 10, 95.0, 6.0, 8.0, devMin, devMax, 60.0, 30.0, 2000.0, 12000.0);
    }

    @Test
    void defaultsMatchSpec() {
        StatecraftConfig c = StatecraftConfig.defaults();
        assertEquals(12, c.nationCount());
        assertEquals(1500, c.minNationSpacing());
        assertEquals(1, c.sizeMin());
        assertEquals(10, c.sizeMax());
        assertEquals(95.0, c.devBase(), 0.0);
        assertEquals(6.0, c.devPerSize(), 0.0);
        assertEquals(8.0, c.devJitter(), 0.0);
        assertEquals(20.0, c.devMin(), 0.0);
        assertEquals(95.0, c.devMax(), 0.0);
        assertEquals(60.0, c.stanceBase(), 0.0);
        assertEquals(30.0, c.stanceJitter(), 0.0);
        assertEquals(2000.0, c.minPlacementRadius(), 0.0);
        assertEquals(12000.0, c.maxPlacementRadius(), 0.0);
    }

    @Test
    void validatedClampsOutOfRangeValues() {
        // 国家数 -5 → 钳到下限 8；内半径 100 < 国家间距 1500 → 抬到 1500
        StatecraftConfig c = of(-5, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 100.0, 12000.0)
                .validated();
        assertEquals(8, c.nationCount(), "国家数下限 8");
        assertEquals(1500.0, c.minPlacementRadius(), 0.0, "内半径不能小于国家间距");
    }

    @Test
    void validatedClampsNationCountUpwards() {
        assertEquals(StatecraftConfig.NATION_COUNT_MAX,
                of(999, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 2000.0, 12000.0)
                        .validated().nationCount());
    }

    @Test
    void validatedClampsSizeAndJittersAndKeepsGoodValues() {
        assertEquals(StatecraftConfig.defaults(), StatecraftConfig.defaults().validated());

        StatecraftConfig c = of(12, 1500, 0, 40, 95.0, 6.0, -5.0, 20.0, 95.0, 60.0, -7.0, 2000.0, 12000.0)
                .validated();
        assertEquals(1, c.sizeMin(), "sizeMin 下限 1");
        assertEquals(10, c.sizeMax(), "sizeMax 上限 10");
        assertEquals(0.0, c.devJitter(), 0.0, "负抖动钳到 0");
        assertEquals(0.0, c.stanceJitter(), 0.0, "负抖动钳到 0");
    }

    /** 审查发现：dev 上下界原本完全不校验，非法值会晚到 Nation 构造器才炸。 */
    @Test
    void devBoundsAreClampedIntoZeroToHundred() {
        StatecraftConfig low = withDev(-30.0, 95.0).validated();
        assertEquals(0.0, low.devMin(), 0.0);
        assertEquals(95.0, low.devMax(), 0.0);

        StatecraftConfig high = withDev(20.0, 165.0).validated();
        assertEquals(20.0, high.devMin(), 0.0);
        assertEquals(100.0, high.devMax(), 0.0);
    }

    @Test
    void swappedDevBoundsAreReordered() {
        StatecraftConfig c = withDev(80.0, 30.0).validated();
        assertEquals(30.0, c.devMin(), 0.0);
        assertEquals(80.0, c.devMax(), 0.0);
    }

    @Test
    void rejectsNonFiniteNumbers() {
        double nan = Double.NaN;
        double inf = Double.POSITIVE_INFINITY;
        assertThrows(IllegalStateException.class,
                () -> of(12, 1500, 1, 10, nan, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 2000.0, 12000.0)
                        .validated());
        assertThrows(IllegalStateException.class,
                () -> of(12, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 2000.0, inf)
                        .validated());
        assertThrows(IllegalStateException.class, () -> withDev(20.0, nan).validated());
    }

    @Test
    void rejectsNegativeDevPerSize() {
        assertThrows(IllegalStateException.class,
                () -> of(12, 1500, 1, 10, 95.0, -1.0, 8.0, 20.0, 95.0, 60.0, 30.0, 2000.0, 12000.0)
                        .validated());
    }

    @Test
    void rejectsNonSenseSpacing() {
        assertThrows(IllegalStateException.class,
                () -> of(12, 0, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 0.0, 0.0)
                        .validated());
    }

    /** 薄环带原本会被静默钳成退化环带，然后在布点阶段以"间距太严？"的错误原因抛出。 */
    @Test
    void rejectsThinPlacementRingWithActionableMessage() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> of(12, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 2000.0, 1000.0)
                        .validated());
        assertTrue(ex.getMessage().contains("2000"), "报错要带上真正的原因（环带）：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("1500"), "报错要带上间距：" + ex.getMessage());
    }

    @Test
    void rejectsGeometricallyImpossibleRing() {
        assertThrows(IllegalStateException.class,
                () -> of(16, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 1500.0, 4000.0)
                        .validated());
    }

    @Test
    void canRestrictSizeRange() {
        StatecraftConfig c = of(8, 1500, 3, 10, 95.0, 6.0, 8.0, 20.0, 95.0, 60.0, 30.0, 2000.0, 12000.0)
                .validated();
        assertEquals(3, c.sizeMin());
        assertEquals(10, c.sizeMax());
    }
}
