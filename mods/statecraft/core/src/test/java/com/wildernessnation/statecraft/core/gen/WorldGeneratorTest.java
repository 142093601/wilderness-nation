package com.wildernessnation.statecraft.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of("赤沙", "黑岩", "铁蹄", "灰狼", "断刃", "荒骨")),
            new Culture("desert_raider", "沙盗", List.of("白岩", "烈日", "黄沙", "孤驼", "枯井", "长夜")));

    private static WorldState generate(long seed, StatecraftConfig cfg) {
        return new WorldGenerator(cfg, seed).generate(CULTURES, 0.0, 0.0, "landing", 0);
    }

    @Test
    void generatesRequestedNumberOfNations() {
        assertEquals(12, generate(1L, StatecraftConfig.defaults()).nations().size());
    }

    @Test
    void respectsMinimumSpacingBetweenNations() {
        StatecraftConfig cfg = StatecraftConfig.defaults();
        List<Nation> ns = generate(7L, cfg).nations();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                double d = Math.hypot(ns.get(i).x() - ns.get(j).x(), ns.get(i).z() - ns.get(j).z());
                assertTrue(d >= cfg.minNationSpacing(),
                        "国家 " + i + " 与 " + j + " 距离 " + d + " 小于 " + cfg.minNationSpacing());
            }
        }
    }

    @Test
    void sizeStaysInConfiguredRange() {
        for (Nation n : generate(3L, StatecraftConfig.defaults()).nations()) {
            assertTrue(n.size() >= 1 && n.size() <= 10, "规模越界：" + n.size());
        }
    }

    @Test
    void bigNationsAreLessDeveloped() {
        StatecraftConfig cfg = StatecraftConfig.defaults();
        assertEquals(95.0, WorldGenerator.developmentFor(1, 0.0, cfg), 0.0);
        assertEquals(41.0, WorldGenerator.developmentFor(10, 0.0, cfg), 0.0);
        assertTrue(WorldGenerator.developmentFor(10, 0.0, cfg)
                < WorldGenerator.developmentFor(1, 0.0, cfg));
        assertEquals(95.0, WorldGenerator.developmentFor(1, 8.0, cfg), 0.0, "上限钳到 95");
        assertEquals(33.0, WorldGenerator.developmentFor(10, -8.0, cfg), 0.0, "41 - 8 = 33");
    }

    @Test
    void stanceTracksDevelopment() {
        assertTrue(WorldGenerator.stanceFor(90.0, 0.0) < 0.0, "先进国家偏主和");
        assertTrue(WorldGenerator.stanceFor(20.0, 0.0) > 0.0, "落后国家偏主战");
    }

    @Test
    void nationNearestToSpawnIsPacific() {
        for (long seed = 0; seed < 40; seed++) {
            WorldState s = generate(seed, StatecraftConfig.defaults());
            Nation nearest = s.nations().stream()
                    .min((a, b) -> Double.compare(Math.hypot(a.x(), a.z()), Math.hypot(b.x(), b.z())))
                    .orElseThrow();
            assertTrue(nearest.stance() <= 0.0,
                    "seed " + seed + " 的最近国家 " + nearest.name() + " 倾向 " + nearest.stance());
        }
    }

    @Test
    void namesAndIdsAreUnique() {
        WorldState s = generate(11L, StatecraftConfig.defaults());
        assertEquals(s.nations().size(),
                s.nations().stream().map(Nation::name).collect(Collectors.toSet()).size());
        assertEquals(s.nations().size(),
                s.nations().stream().map(Nation::id).collect(Collectors.toSet()).size());
    }

    @Test
    void sameSeedProducesIdenticalWorld() {
        WorldState a = generate(2026L, StatecraftConfig.defaults());
        WorldState b = generate(2026L, StatecraftConfig.defaults());
        assertEquals(a.nations(), b.nations());
        assertEquals(a.seed(), b.seed());
    }

    @Test
    void differentSeedProducesDifferentWorld() {
        assertNotEquals(generate(1L, StatecraftConfig.defaults()).nations(),
                generate(2L, StatecraftConfig.defaults()).nations());
    }

    @Test
    void culturesComeFromProvidedPool() {
        Set<String> allowed = CULTURES.stream().map(Culture::id).collect(Collectors.toSet());
        for (Nation n : generate(5L, StatecraftConfig.defaults()).nations()) {
            assertTrue(allowed.contains(n.cultureId()), "未知文化：" + n.cultureId());
        }
    }

    @Test
    void rejectsEmptyCulturePool() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldGenerator(StatecraftConfig.defaults(), 1L)
                        .generate(List.of(), 0.0, 0.0, "landing", 0));
    }
}
