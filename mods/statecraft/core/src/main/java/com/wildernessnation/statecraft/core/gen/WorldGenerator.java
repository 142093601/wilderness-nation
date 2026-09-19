package com.wildernessnation.statecraft.core.gen;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 开局生成：布点 → 规模 → 发展度 → 政治倾向 → 国名。
 *
 * <p>两条硬规则（来自设计）：
 * <ol>
 *   <li><strong>规模与发展度反比</strong>：大而落后、小而先进；倾向由发展度推出（落后更主战）</li>
 *   <li><strong>离出生点最近的国家必须主和</strong>，且是"从候选里挑"而不是"改它的数值"</li>
 * </ol>
 */
public final class WorldGenerator {

    private final StatecraftConfig cfg;
    private final long seed;
    private final DeterministicRandom rng;

    public WorldGenerator(StatecraftConfig cfg, long seed) {
        this.cfg = cfg.validated();
        this.seed = seed;
        this.rng = new DeterministicRandom(seed);
    }

    /** 发展度：与规模反比。纯函数，方便直接断言。 */
    public static double developmentFor(int size, double jitter, StatecraftConfig cfg) {
        double raw = cfg.devBase() - cfg.devPerSize() * (size - 1) + jitter;
        return Math.max(cfg.devMin(), Math.min(cfg.devMax(), raw));
    }

    /** 政治倾向：越落后越主战。正值 = 主战，负值 = 主和。 */
    public static double stanceFor(double development, double jitter) {
        double raw = 60.0 - development + jitter;
        return Math.max(-100.0, Math.min(100.0, raw));
    }

    public WorldState generate(
            List<Culture> cultures,
            double spawnX,
            double spawnZ,
            String startingEraId,
            int startingEraOrdinal) {
        if (cultures == null || cultures.isEmpty()) {
            throw new IllegalArgumentException("至少要有一个文化");
        }
        List<double[]> points = placeNations(spawnX, spawnZ);
        List<String> names = pickNames(cultures, points.size());

        List<Nation> nations = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Culture culture = cultures.get(rng.nextInt(i, "culture#" + i, cultures.size()));
            int size = rng.range(i, "size#" + i, cfg.sizeMin(), cfg.sizeMax());
            double devJitter = rng.rangeDouble(i, "devJit#" + i, -cfg.devJitter(), cfg.devJitter());
            double development = developmentFor(size, devJitter, cfg);
            double stanceJitter =
                    rng.rangeDouble(i, "stanceJit#" + i, -cfg.stanceJitter(), cfg.stanceJitter());
            double stance = stanceFor(development, stanceJitter);
            double military = 10.0 + size * 2.0;
            double treasury = 20.0 + development * 0.5;
            nations.add(new Nation(
                    "n" + i, names.get(i), culture.id(), size, development, stance,
                    military, treasury, points.get(i)[0], points.get(i)[1], false, 0.0));
        }

        nations = ensureNearestIsPacific(nations, spawnX, spawnZ);
        return new WorldState(seed, nations, startingEraId, startingEraOrdinal, 0L);
    }

    /**
     * 最近的国家必须主和：找一个倾向 ≤ 0 的国家与它<strong>交换位置</strong>。
     * 交换而不是改数值——否则"发展度决定倾向"的公式就被破坏了。
     */
    private static List<Nation> ensureNearestIsPacific(
            List<Nation> nations, double spawnX, double spawnZ) {
        int nearest = 0;
        for (int i = 1; i < nations.size(); i++) {
            if (nations.get(i).distanceTo(spawnX, spawnZ)
                    < nations.get(nearest).distanceTo(spawnX, spawnZ)) {
                nearest = i;
            }
        }
        if (nations.get(nearest).stance() <= 0.0) {
            return nations;
        }
        for (int j = 0; j < nations.size(); j++) {
            if (nations.get(j).stance() <= 0.0) {
                Nation a = nations.get(nearest);
                Nation b = nations.get(j);
                nations.set(nearest, a.withPosition(b.x(), b.z()));
                nations.set(j, b.withPosition(a.x(), a.z()));
                return nations;
            }
        }
        return nations;
    }

    /** 布点：极坐标 + 拒绝采样，保证两两间距 ≥ minNationSpacing。 */
    private List<double[]> placeNations(double spawnX, double spawnZ) {
        List<double[]> out = new ArrayList<>();
        int maxAttempts = cfg.nationCount() * 400;
        double rMin = cfg.minPlacementRadius();
        double rMax = cfg.maxPlacementRadius();

        for (int attempt = 0; attempt < maxAttempts && out.size() < cfg.nationCount(); attempt++) {
            double angle = rng.rangeDouble(attempt, "angle#" + attempt, 0.0, Math.PI * 2.0);
            double radius = rng.rangeDouble(attempt, "radius#" + attempt, rMin, rMax);
            double x = spawnX + Math.cos(angle) * radius;
            double z = spawnZ + Math.sin(angle) * radius;

            boolean ok = true;
            for (double[] pt : out) {
                if (Math.hypot(pt[0] - x, pt[1] - z) < cfg.minNationSpacing()) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(new double[] {x, z});
            }
        }
        if (out.size() < cfg.nationCount()) {
            throw new IllegalStateException(
                    "布点失败：在 " + maxAttempts + " 次尝试内只放下 " + out.size()
                            + " 个国家（间距要求 " + cfg.minNationSpacing() + " 格太严？）");
        }
        return out;
    }

    /** 国名：跨文化取，遇到重名就换下一个候选，保证全局唯一。 */
    private List<String> pickNames(List<Culture> cultures, int count) {
        List<String> pool = new ArrayList<>();
        for (Culture c : cultures) {
            pool.addAll(c.namePrefixes());
        }
        if (pool.size() < count) {
            throw new IllegalStateException(
                    "国名池不够：" + pool.size() + " 个候选放不下 " + count + " 个国家");
        }
        List<String> picked = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int start = rng.nextInt(i, "name#" + i, pool.size());
            for (int step = 0; step < pool.size(); step++) {
                String candidate = pool.get((start + step) % pool.size());
                if (used.add(candidate)) {
                    picked.add(candidate);
                    break;
                }
            }
        }
        if (picked.size() < count) {
            throw new IllegalStateException("国名唯一性无法满足：只取到 " + picked.size());
        }
        return picked;
    }
}
