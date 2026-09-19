package com.wildernessnation.statecraft.core.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentRulesTest {

    private static final EnvironRequirement BASIC = EnvironRequirement.defaultBasic();

    private static EnvironmentSnapshot snapshot(
            boolean roof, double enclosure, int volume, boolean claim) {
        return new EnvironmentSnapshot(roof, enclosure, volume, claim);
    }

    /** 刚好满足 §十二 那套默认条件。 */
    private static EnvironmentSnapshot ok() {
        return snapshot(true, 60.0, 12, true);
    }

    // ---- §十二 的默认阈值 ----

    @Test
    void defaultRequirementMatchesTheSpec() {
        assertEquals("basic", BASIC.tierId());
        assertEquals(60.0, BASIC.minEnclosurePercent(), 0.0, "§十二 封闭度 ≥60%");
        assertEquals(12, BASIC.minVolume(), "§十二 内部体积 ≥12 格");
        assertTrue(BASIC.requireRoof(), "§十二 要有屋顶");
        assertTrue(BASIC.requireClaim(), "§十二 要在领地内");
    }

    /** 边界必须逐个数着测：60.0 过、59.9 不过；12 格过、11 格不过。 */
    @Test
    void thresholdsAreInclusiveAtTheBoundary() {
        assertTrue(BASIC.satisfiedBy(snapshot(true, 60.0, 12, true)), "正好 60% / 12 格要算过");
        assertFalse(BASIC.satisfiedBy(snapshot(true, 59.9, 12, true)), "59.9% 必须算不过");
        assertFalse(BASIC.satisfiedBy(snapshot(true, 60.0, 11, true)), "11 格必须算不过");
        assertFalse(BASIC.satisfiedBy(snapshot(true, 100.0, 999, false)), "不在领地内就是不行");
        assertFalse(BASIC.satisfiedBy(snapshot(false, 100.0, 999, true)), "没屋顶就是不行");
    }

    /** 玩家要看到"我还差什么、差多少"，所以不满足的每一条都要列出来，且带实际读数。 */
    @Test
    void everyUnmetConditionIsListedWithItsNumber() {
        List<String> unmet = BASIC.unmet(snapshot(false, 42.0, 9, false));
        assertEquals(4, unmet.size(), "四条都不满足就该有四条原因：" + unmet);
        String joined = String.join(" | ", unmet);
        assertTrue(joined.contains("屋顶"), joined);
        assertTrue(joined.contains("42.0"), "要说清差多少：" + joined);
        assertTrue(joined.contains("60.0"), joined);
        assertTrue(joined.contains("9"), joined);
        assertTrue(joined.contains("12"), joined);
        assertTrue(joined.contains("领地"), joined);
    }

    @Test
    void satisfiedRequirementHasNoReasons() {
        assertEquals(List.of(), BASIC.unmet(ok()));
    }

    // ---- 档位选择 ----

    private static final List<EnvironRequirement> THREE_TIERS = List.of(
            new EnvironRequirement("basic", true, 60.0, 12, true),
            new EnvironRequirement("good", true, 75.0, 24, true),
            new EnvironRequirement("great", true, 90.0, 48, true));

    @Test
    void picksTheHighestSatisfiedTier() {
        EnvironmentVerdict two = EnvironmentRules.evaluate(THREE_TIERS, snapshot(true, 80.0, 30, true));
        assertEquals("good", two.usableTierId().orElseThrow(), "满足 basic+good → 停在 good");
        assertEquals("great", two.nextTierId().orElseThrow(), "下一步是 great");
        assertTrue(two.usable());
        assertTrue(two.describe().contains("great"), two.describe());

        EnvironmentVerdict all = EnvironmentRules.evaluate(THREE_TIERS, snapshot(true, 95.0, 60, true));
        assertEquals("great", all.usableTierId().orElseThrow());
        assertTrue(all.nextTierId().isEmpty(), "已是最高档就没有下一档");
        assertEquals(List.of(), all.unmet());
        assertTrue(all.describe().contains("最高档"), all.describe());
    }

    @Test
    void nothingSatisfiedPointsAtTheLowestTier() {
        EnvironmentVerdict none = EnvironmentRules.evaluate(THREE_TIERS, snapshot(false, 0.0, 0, false));
        assertFalse(none.usable());
        assertTrue(none.usableTierId().isEmpty());
        assertEquals("basic", none.nextTierId().orElseThrow(), "一档都没满足时，该补的是最低档");
        assertEquals(4, none.unmet().size());
        assertTrue(none.describe().startsWith("还不能用"), none.describe());
    }

    @Test
    void exactlyOneTierSatisfied() {
        EnvironmentVerdict one = EnvironmentRules.evaluate(THREE_TIERS, snapshot(true, 60.0, 12, true));
        assertEquals("basic", one.usableTierId().orElseThrow());
        assertEquals("good", one.nextTierId().orElseThrow());
        assertTrue(one.describe().contains("basic"), one.describe());
    }

    // ---- 数据错误要早报 ----

    @Test
    void rejectsEmptyOrDuplicateTierTable() {
        assertThrows(IllegalArgumentException.class,
                () -> EnvironmentRules.evaluate(List.of(), ok()));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentRules.evaluate(List.of(
                new EnvironRequirement("basic", true, 60.0, 12, true),
                new EnvironRequirement("basic", false, 10.0, 1, false)), ok()));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentRules.evaluate(null, ok()));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentRules.evaluate(THREE_TIERS, null));
    }

    @Test
    void rejectsNonsenseReadingsAndRequirements() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(true, -0.1, 12, true));
        assertThrows(IllegalArgumentException.class, () -> snapshot(true, 100.1, 12, true));
        assertThrows(IllegalArgumentException.class, () -> snapshot(true, Double.NaN, 12, true));
        assertThrows(IllegalArgumentException.class, () -> snapshot(true, 50.0, -1, true));
        assertThrows(IllegalArgumentException.class, () -> new EnvironRequirement(" ", true, 60, 12, true));
        assertThrows(IllegalArgumentException.class, () -> new EnvironRequirement("x", true, 101, 12, true));
        assertThrows(IllegalArgumentException.class, () -> new EnvironRequirement("x", true, 60, -1, true));
    }

    // ---- 结构性护栏：这条才是"不做形状检测"的保证 ----

    /**
     * §10.5 要求"**软性判定，不做形状检测**"。光靠注释拦不住人 ——
     * 所以这里把 {@link EnvironmentSnapshot} 的字段集合钉死：**恰好这四个读数**。
     *
     * <p>谁要是想加"方块列表 / 形状描述 / 长宽高"，这条会立刻红，
     * 逼他先把"为什么又回到形状检测"写清楚，而不是顺手加个字段。
     */
    @Test
    void snapshotCarriesExactlyFourReadingsAndNoShapeInformation() {
        List<String> components = java.util.Arrays.stream(
                        EnvironmentSnapshot.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertEquals(List.of("hasRoof", "enclosurePercent", "volume", "insideClaim"), components,
                "环境快照只能有这四个读数 —— 加了形状/方块信息就等于把'不做形状检测'废掉了");
    }

    @Test
    void snapshotDescribesItselfForLogs() {
        String text = snapshot(true, 42.0, 9, false).describe();
        assertTrue(text.contains("42.0"), text);
        assertTrue(text.contains("9"), text);
    }
}
