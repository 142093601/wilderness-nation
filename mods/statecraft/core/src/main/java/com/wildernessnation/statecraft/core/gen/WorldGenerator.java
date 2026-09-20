package com.wildernessnation.statecraft.core.gen;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 开局生成：布点 → 文化 → 规模 → 发展度 → 政治倾向 → 国名。
 *
 * <p>两条硬规则（来自设计）：
 * <ol>
 *   <li><strong>规模与发展度反比</strong>：大而落后、小而先进；倾向由发展度推出（落后更主战）</li>
 *   <li><strong>离出生点最近的国家必须主和</strong>，且是"从候选里挑"而不是"改它的数值"</li>
 * </ol>
 *
 * <p>确定性：每个取值都按 {@code (index, 用途标签)} 键控，<strong>不依赖调用顺序</strong>。
 * 唯一会进入结果的外部顺序是 {@code cultures} 列表本身（文化抽取按列表下标），调用方要保证它稳定。
 *
 * <p>失败策略：配置层面不可行（名字池不够、环带太薄）在 {@link #generate} <strong>入口</strong>就抛，
 * 而不是生成到一半才炸——报错要指向真正的原因。
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

    /**
     * 政治倾向：越落后越主战。正值 = 主战，负值 = 主和。
     *
     * <p>有意的窄区间：只由 {@code 发展度 + 抖动} 决定，不吃文化/国名等别的输入——
     * 硬规则 4 靠"交换两个国家的位置"满足，而不是靠在这里动手脚。
     */
    public static double stanceFor(double development, double jitter, StatecraftConfig cfg) {
        double raw = cfg.stanceBase() - development + jitter;
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
        int distinctNames = distinctNameCount(cultures);
        if (distinctNames < cfg.nationCount()) {
            throw new IllegalStateException("国名池不够：" + cultures.size() + " 个文化一共只有 "
                    + distinctNames + " 个不同国名，放不下 " + cfg.nationCount()
                    + " 个国家 —— 请给文化加名字或调低 nationCount");
        }

        List<double[]> points = placeNations(spawnX, spawnZ);

        List<Nation> nations = new ArrayList<>();
        Set<String> usedNames = new LinkedHashSet<>();
        for (int i = 0; i < points.size(); i++) {
            int cultureIndex = rng.nextInt(i, "culture#" + i, cultures.size());
            Culture culture = cultures.get(cultureIndex);
            String name = pickName(i, cultureIndex, cultures, usedNames);
            int size = rng.range(i, "size#" + i, cfg.sizeMin(), cfg.sizeMax());
            double devJitter = rng.rangeDouble(i, "devJit#" + i, -cfg.devJitter(), cfg.devJitter());
            double development = developmentFor(size, devJitter, cfg);
            double stanceJitter =
                    rng.rangeDouble(i, "stanceJit#" + i, -cfg.stanceJitter(), cfg.stanceJitter());
            double stance = stanceFor(development, stanceJitter, cfg);
            double military = 10.0 + size * 2.0;
            double treasury = 20.0 + development * 0.5;
            nations.add(Nation.spawn(
                    "n" + i, name, culture.id(), size, development, stance,
                    military, treasury, points.get(i)[0], points.get(i)[1]));
        }

        ensureNearestIsPacific(nations, spawnX, spawnZ);
        return new WorldState(
                WorldState.CURRENT_SCHEMA_VERSION, seed, startingEraId,
                startingEraOrdinal, 0L, nations, initialRelations(nations), List.of(), List.of(), List.of(),
                0.0);
    }

    /**
     * 开局关系：所有国家两两之间都是和平、态度 0。
     *
     * <p>刻意<strong>不</strong>随机初始态度：设计里国家之间的关系是由后续结算推出来的
     * （§八"由 stance、attitude、邻接关系决定开战概率"），开局就撒随机态度只会让
     * 世界变得更难复现，而没有设计上的必要。
     */
    private static List<Relation> initialRelations(List<Nation> nations) {
        List<Relation> out = new ArrayList<>();
        for (int i = 0; i < nations.size(); i++) {
            for (int j = i + 1; j < nations.size(); j++) {
                out.add(Relation.peace(nations.get(i).id(), nations.get(j).id()));
            }
        }
        return out;
    }

    /**
     * 最近的国家必须主和：找一个倾向 ≤ 0 的国家与它<strong>交换位置</strong>。
     * 交换而不是改数值——否则"发展度决定倾向"的公式就被破坏了。
     *
     * <p>全员主战时<strong>抛异常</strong>：硬规则不允许被静默放弃，而这必然是配置问题
     * （把 devBase 抬到 95 以下、或给 stanceJitter 留出负向空间就能解决），早暴露早好。
     */
    private static void ensureNearestIsPacific(
            List<Nation> nations, double spawnX, double spawnZ) {
        int nearest = 0;
        for (int i = 1; i < nations.size(); i++) {
            if (nations.get(i).distanceTo(spawnX, spawnZ)
                    < nations.get(nearest).distanceTo(spawnX, spawnZ)) {
                nearest = i;
            }
        }
        if (nations.get(nearest).stance() <= 0.0) {
            return;
        }
        for (int j = 0; j < nations.size(); j++) {
            if (nations.get(j).stance() <= 0.0) {
                Nation a = nations.get(nearest);
                Nation b = nations.get(j);
                nations.set(nearest, a.withPosition(b.x(), b.z()));
                nations.set(j, b.withPosition(a.x(), a.z()));
                return;
            }
        }
        throw new IllegalStateException("硬规则被破坏：没有任何国家主和（全员倾向 > 0），"
                + "无法让离出生点最近的国家主和 —— 请降低 devBase 或增大 stanceJitter");
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
            throw new IllegalStateException(String.format(
                    "布点失败：%d 次尝试后只放下 %d/%d 个国家"
                            + "（环带 rmin=%.0f rmax=%.0f、间距 %d）",
                    maxAttempts, out.size(), cfg.nationCount(),
                    rMin, rMax, cfg.minNationSpacing()));
        }
        return out;
    }

    /**
     * 国名：<strong>优先本国文化池</strong>，本国池用尽才按文化表顺序回退到别的文化池；
     * 无论走哪条路都保证全局不重名（重名世界的存档没法读）。
     *
     * <p>回退是有意的：每个文化只有几个名字，而 nationCount 允许到 16，
     * 硬要求"只能用本国池"会让完全合法的配置直接崩掉——跨文化拿个名字总比没有名字好。
     */
    private String pickName(
            int index, int cultureIndex, List<Culture> cultures, Set<String> used) {
        List<String> own = distinct(cultures.get(cultureIndex).namePrefixes());
        String hit = firstUnused(own, rng.nextInt(index, "name#" + index, own.size()), used);
        if (hit != null) {
            return hit;
        }
        for (int k = 0; k < cultures.size(); k++) {
            if (k == cultureIndex) {
                continue;
            }
            List<String> pool = distinct(cultures.get(k).namePrefixes());
            hit = firstUnused(
                    pool,
                    rng.nextInt(index, "fallbackName#" + k + "#" + index, pool.size()),
                    used);
            if (hit != null) {
                return hit;
            }
        }
        throw new IllegalStateException("国名池已用尽，放不下第 " + (index + 1) + " 个国家");
    }

    /** 从 start 起绕一圈找第一个没被用过的名字；全用过返回 null。 */
    private static String firstUnused(List<String> pool, int start, Set<String> used) {
        for (int step = 0; step < pool.size(); step++) {
            String candidate = pool.get((start + step) % pool.size());
            if (used.add(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> distinct(List<String> names) {
        return List.copyOf(new LinkedHashSet<>(names));
    }

    private static int distinctNameCount(List<Culture> cultures) {
        Set<String> all = new LinkedHashSet<>();
        for (Culture c : cultures) {
            all.addAll(c.namePrefixes());
        }
        return all.size();
    }
}
