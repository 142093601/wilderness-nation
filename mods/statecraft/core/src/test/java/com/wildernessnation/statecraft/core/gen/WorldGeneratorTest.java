package com.wildernessnation.statecraft.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of("赤沙", "黑岩", "铁蹄", "灰狼", "断刃", "荒骨")),
            new Culture("desert_raider", "沙盗", List.of("白岩", "烈日", "黄沙", "孤驼", "枯井", "长夜")));

    /** 每个文化名字都够多（≥ nationCount），用来验证"优先本国文化池"。 */
    private static final List<Culture> ROOMY_CULTURES = List.of(
            new Culture("bandit", "匪帮", names("匪", 16)),
            new Culture("desert_raider", "沙盗", names("沙", 16)));

    private static List<String> names(String prefix, int count) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            out.add(prefix + i);
        }
        return out;
    }

    private static StatecraftConfig cfg() {
        return StatecraftConfig.defaults();
    }

    private static StatecraftConfig of(
            int nationCount, int spacing, int sizeMin, int sizeMax,
            double devBase, double devPerSize, double devJitter, double devMin, double devMax,
            double stanceBase, double stanceJitter, double rMin, double rMax) {
        return new StatecraftConfig(nationCount, spacing, sizeMin, sizeMax,
                devBase, devPerSize, devJitter, devMin, devMax,
                stanceBase, stanceJitter, rMin, rMax).validated();
    }

    private static WorldState generate(long seed, StatecraftConfig cfg) {
        return generate(seed, cfg, CULTURES);
    }

    private static WorldState generate(long seed, StatecraftConfig cfg, List<Culture> cultures) {
        return new WorldGenerator(cfg, seed).generate(cultures, 0.0, 0.0, "landing", 0);
    }

    @Test
    void generatesRequestedNumberOfNations() {
        assertEquals(12, generate(1L, cfg()).nations().size());
    }

    @Test
    void respectsMinimumSpacingBetweenNations() {
        StatecraftConfig cfg = cfg();
        List<Nation> ns = generate(7L, cfg).nations();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                double d = Math.hypot(ns.get(i).x() - ns.get(j).x(), ns.get(i).z() - ns.get(j).z());
                assertTrue(d >= cfg.minNationSpacing(),
                        "国家 " + i + " 与 " + j + " 距离 " + d + " 小于 " + cfg.minNationSpacing());
            }
        }
    }

    /** 审查发现：从来没人断言过"点真的落在环带里"。 */
    @Test
    void everyNationLiesInsideThePlacementRing() {
        StatecraftConfig cfg = cfg();
        for (Nation n : generate(23L, cfg).nations()) {
            double r = Math.hypot(n.x(), n.z());
            assertTrue(r >= cfg.minPlacementRadius() - 1e-6 && r <= cfg.maxPlacementRadius() + 1e-6,
                    "国家 " + n.id() + " 半径 " + r + " 不在环带 "
                            + cfg.minPlacementRadius() + ".." + cfg.maxPlacementRadius() + " 内");
        }
    }

    @Test
    void sizeStaysInConfiguredRange() {
        for (Nation n : generate(3L, cfg()).nations()) {
            assertTrue(n.size() >= 1 && n.size() <= 10, "规模越界：" + n.size());
        }
    }

    /** 审查发现：只测过"配置收窄成功"，没测过"生成器真的遵守收窄后的范围"。 */
    @Test
    void generatorHonoursNarrowedSizeRange() {
        StatecraftConfig narrow = of(10, 1500, 4, 6, 95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0, 2000.0, 12000.0);
        for (long seed = 0; seed < 20; seed++) {
            for (Nation n : generate(seed, narrow).nations()) {
                assertTrue(n.size() >= 4 && n.size() <= 6, "seed " + seed + " 规模越界：" + n.size());
            }
        }
    }

    @Test
    void bigNationsAreLessDeveloped() {
        StatecraftConfig cfg = cfg();
        assertEquals(95.0, WorldGenerator.developmentFor(1, 0.0, cfg), 0.0);
        assertEquals(41.0, WorldGenerator.developmentFor(10, 0.0, cfg), 0.0);
        assertTrue(WorldGenerator.developmentFor(10, 0.0, cfg)
                < WorldGenerator.developmentFor(1, 0.0, cfg));
        assertEquals(95.0, WorldGenerator.developmentFor(1, 8.0, cfg), 0.0, "上限钳到 95");
        assertEquals(33.0, WorldGenerator.developmentFor(10, -8.0, cfg), 0.0, "41 - 8 = 33");
    }

    /** 审查发现：developmentFor 的下界钳制完全没测，默认配置下也不可达。 */
    @Test
    void developmentIsClampedToConfiguredBounds() {
        StatecraftConfig floored = of(10, 1500, 1, 10, 95.0, 6.0, 8.0, 50.0, 95.0,
                60.0, 30.0, 2000.0, 12000.0);
        assertEquals(50.0, WorldGenerator.developmentFor(10, 0.0, floored), 0.0, "下界钳到 devMin");
        assertEquals(95.0, WorldGenerator.developmentFor(1, 0.0, floored), 0.0);
        // devMin 被抬到 50 后，规模 10 也还是 50：单调性只保证"不增"
        assertTrue(WorldGenerator.developmentFor(10, 0.0, floored)
                <= WorldGenerator.developmentFor(1, 0.0, floored));
    }

    @Test
    void stanceTracksDevelopment() {
        StatecraftConfig cfg = cfg();
        assertTrue(WorldGenerator.stanceFor(90.0, 0.0, cfg) < 0.0, "先进国家偏主和");
        assertTrue(WorldGenerator.stanceFor(20.0, 0.0, cfg) > 0.0, "落后国家偏主战");
    }

    /** 审查发现：stanceFor 的 ±100 钳制没测过。 */
    @Test
    void stanceIsClampedToPlusMinusHundred() {
        StatecraftConfig cfg = cfg();
        assertEquals(100.0, WorldGenerator.stanceFor(0.0, 200.0, cfg), 0.0);
        assertEquals(-100.0, WorldGenerator.stanceFor(100.0, -200.0, cfg), 0.0);
    }

    /** stanceBase 原本是死配置（值写死在代码里），这里钉住它真的生效。 */
    @Test
    void stanceBaseIsWiredIn() {
        StatecraftConfig shifted = of(12, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0,
                0.0, 30.0, 2000.0, 12000.0);
        assertEquals(-90.0, WorldGenerator.stanceFor(90.0, 0.0, shifted), 0.0);
        assertNotEquals(
                WorldGenerator.stanceFor(90.0, 0.0, cfg()),
                WorldGenerator.stanceFor(90.0, 0.0, shifted));
    }

    @Test
    void nationNearestToSpawnIsPacific() {
        for (long seed = 0; seed < 40; seed++) {
            WorldState s = generate(seed, cfg());
            Nation nearest = s.nations().stream()
                    .min((a, b) -> Double.compare(Math.hypot(a.x(), a.z()), Math.hypot(b.x(), b.z())))
                    .orElseThrow();
            assertTrue(nearest.stance() <= 0.0,
                    "seed " + seed + " 的最近国家 " + nearest.name() + " 倾向 " + nearest.stance());
        }
    }

    /**
     * 硬规则 4 的<strong>回归护栏</strong>：最近国变主和只能是"交换位置"，绝不能是"改数值"。
     *
     * <p>原来只断言了"最近国 stance ≤ 0"——把它的 stance 直接翻成负数也能过。
     * 这里改成：每个国家的倾向必须仍然落在 {@code stanceBase - 发展度 ± stanceJitter} 里，
     * 于是任何"事后改倾向"的实现都会立刻红。
     */
    @Test
    void stanceAlwaysStaysInsideTheFormulaEnvelope() {
        StatecraftConfig cfg = cfg();
        for (long seed = 0; seed < 40; seed++) {
            WorldState s = generate(seed, cfg);
            for (Nation n : s.nations()) {
                double centre = cfg.stanceBase() - n.development();
                assertTrue(Math.abs(n.stance() - centre) <= cfg.stanceJitter() + 1e-9,
                        "seed " + seed + " 的 " + n.id() + " 倾向 " + n.stance()
                                + " 偏离公式中心 " + centre + " 超过抖动 " + cfg.stanceJitter()
                                + " —— 说明有代码在直接改倾向，而不是交换位置");
            }
        }
    }

    /** 最近国的位置交换会改变"哪个国家在哪儿"，但间距与环带都必须守恒。 */
    @Test
    void spacingAndRingHoldAcrossManySeedsWhereSwapsHappen() {
        StatecraftConfig cfg = cfg();
        for (long seed = 0; seed < 40; seed++) {
            List<Nation> ns = generate(seed, cfg).nations();
            for (int i = 0; i < ns.size(); i++) {
                double r = Math.hypot(ns.get(i).x(), ns.get(i).z());
                assertTrue(r >= cfg.minPlacementRadius() - 1e-6
                                && r <= cfg.maxPlacementRadius() + 1e-6,
                        "seed " + seed + " 的 " + ns.get(i).id() + " 被换到环带外：r=" + r);
                for (int j = i + 1; j < ns.size(); j++) {
                    double d = Math.hypot(ns.get(i).x() - ns.get(j).x(),
                            ns.get(i).z() - ns.get(j).z());
                    assertTrue(d >= cfg.minNationSpacing(),
                            "seed " + seed + " 交换后 " + i + "/" + j + " 距离 " + d + " 过近");
                }
            }
        }
    }

    /** 全员主战时硬规则无法满足——必须响亮地失败，不能静默放过。 */
    @Test
    void rejectsWorldWhereNoNationIsPacific() {
        StatecraftConfig allAggressive = of(12, 1500, 1, 10, 0.0, 0.0, 0.0, 0.0, 0.0,
                60.0, 0.0, 2000.0, 12000.0);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> generate(1L, allAggressive));
        assertTrue(ex.getMessage().contains("硬规则"), "报错要说清是哪条硬规则：" + ex.getMessage());
    }

    @Test
    void namesAndIdsAreUnique() {
        WorldState s = generate(11L, cfg());
        assertEquals(s.nations().size(),
                s.nations().stream().map(Nation::name).collect(Collectors.toSet()).size());
        assertEquals(s.nations().size(),
                s.nations().stream().map(Nation::id).collect(Collectors.toSet()).size());
    }

    /** 名字池恰好等于国家数（计划自带的 2×6=12）也必须能生成。 */
    @Test
    void namePoolExactlyAsLargeAsNationCountStillWorks() {
        int distinct = CULTURES.stream()
                .flatMap(c -> c.namePrefixes().stream())
                .collect(Collectors.toSet())
                .size();
        assertEquals(12, distinct, "前提：默认例子恰好 12 个不同名字");
        assertEquals(12, generate(1L, cfg()).nations().size());
    }

    /** 审查发现：16 国（配置允许的上限）配 12 个名字，原本是在生成中途才炸的。 */
    @Test
    void rejectsWhenNamePoolCannotCoverNationCount() {
        StatecraftConfig sixteen = of(16, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0, 2000.0, 12000.0);
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> generate(1L, sixteen));
        assertTrue(ex.getMessage().contains("国名"), "报错要指向国名池：" + ex.getMessage());
    }

    /** 国名优先取自本国文化池；池子够大时不应出现跨文化取名。 */
    @Test
    void namesPreferTheirOwnCulturePool() {
        for (long seed = 0; seed < 20; seed++) {
            WorldState s = generate(seed, cfg(), ROOMY_CULTURES);
            for (Nation n : s.nations()) {
                Culture own = ROOMY_CULTURES.stream()
                        .filter(c -> c.id().equals(n.cultureId()))
                        .findFirst()
                        .orElseThrow();
                assertTrue(own.namePrefixes().contains(n.name()),
                        "seed " + seed + " 的 " + n.id() + "（" + own.displayName() + "）拿到了别的文化的名字 "
                                + n.name());
            }
        }
    }

    /** 池子不够时必须回退到别的文化池，而不是崩——跨文化名字总比没有名字好。 */
    @Test
    void fallsBackToOtherCulturePoolsWhenOwnPoolIsExhausted() {
        List<Culture> tiny = List.of(
                new Culture("a", "甲", List.of("甲一", "甲二")),
                new Culture("b", "乙", List.of("乙一", "乙二", "乙三", "乙四", "乙五", "乙六")));
        StatecraftConfig small = of(8, 1500, 1, 10, 95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0, 2000.0, 12000.0);
        WorldState s = generate(4L, small, tiny);
        assertEquals(8, s.nations().size());
        Set<String> picked = s.nations().stream().map(Nation::name).collect(Collectors.toSet());
        Set<String> wholePool = tiny.stream()
                .flatMap(c -> c.namePrefixes().stream())
                .collect(Collectors.toSet());
        assertEquals(8, picked.size(), "名字必须全局唯一");
        // 乙文化只有 6 个名字，凑出 8 个不同名字必然用到了甲文化的池子 → 回退真的发生了
        assertEquals(wholePool, picked, "8 个不同名字应当正好用光 2+6 的池子");
    }

    @Test
    void sameSeedProducesIdenticalWorld() {
        WorldState a = generate(2026L, cfg());
        WorldState b = generate(2026L, cfg());
        assertEquals(a.nations(), b.nations());
        assertEquals(a.seed(), b.seed());
    }

    @Test
    void differentSeedProducesDifferentWorld() {
        assertNotEquals(generate(1L, cfg()).nations(), generate(2L, cfg()).nations());
    }

    @Test
    void culturesComeFromProvidedPool() {
        Set<String> allowed = CULTURES.stream().map(Culture::id).collect(Collectors.toSet());
        for (Nation n : generate(5L, cfg()).nations()) {
            assertTrue(allowed.contains(n.cultureId()), "未知文化：" + n.cultureId());
        }
    }

    @Test
    void startingEraIsCarriedIntoTheWorldState() {
        WorldState s = new WorldGenerator(cfg(), 9L).generate(CULTURES, 0.0, 0.0, "founding", 1);
        assertEquals("founding", s.eraId());
        assertEquals(1, s.eraOrdinal());
        assertEquals(0L, s.seq());
        assertEquals(9L, s.seed());
    }

    @Test
    void rejectsEmptyCulturePool() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldGenerator(cfg(), 1L)
                        .generate(List.of(), 0.0, 0.0, "landing", 0));
    }

    @Test
    void rejectsNullCulturePool() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldGenerator(cfg(), 1L).generate(null, 0.0, 0.0, "landing", 0));
    }

    /** 生成结果必须是不可变快照，调用方改不动它。 */
    @Test
    void generatedNationListIsImmutable() {
        WorldState s = generate(1L, cfg());
        assertEquals(12, s.nations().size());
        assertThrows(UnsupportedOperationException.class, () -> s.nations().clear());
    }
}
