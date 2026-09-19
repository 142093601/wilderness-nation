package com.wildernessnation.statecraft.core.config;

/**
 * 全部可调参数与默认值（对应 NATIONS.md 第十二节）。
 *
 * <p>设计约定：<strong>默认值就是我们要的那套</strong>，改配置只为了试平衡。
 * {@link #validated()} 负责把非法组合挡在门口：钳制、或直接抛异常。
 *
 * <p><strong>绝不把非法状态写下去</strong>——所以这里对每个 double 都要 finite 检查，
 * 并且 dev 必须落在 Nation 允许的 [0,100] 内（否则错误会晚到"生成深处"才爆，很难查）。
 */
public record StatecraftConfig(
        int nationCount,
        int minNationSpacing,
        int sizeMin,
        int sizeMax,
        double devBase,
        double devPerSize,
        double devJitter,
        double devMin,
        double devMax,
        double stanceBase,
        double stanceJitter,
        double minPlacementRadius,
        double maxPlacementRadius) {

    public static final int NATION_COUNT_MIN = 8;
    public static final int NATION_COUNT_MAX = 16;

    public static StatecraftConfig defaults() {
        return new StatecraftConfig(
                12, 1500, 1, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                2000.0, 12000.0);
    }

    public StatecraftConfig validated() {
        int nations = clamp(nationCount, NATION_COUNT_MIN, NATION_COUNT_MAX);

        if (minNationSpacing <= 0) {
            throw new IllegalStateException("minNationSpacing 必须 > 0，实际 " + minNationSpacing);
        }
        int smin = Math.max(1, sizeMin);
        int smax = Math.min(10, sizeMax);
        if (smax < smin) {
            throw new IllegalStateException("sizeMax 不能小于 sizeMin：" + smin + ".." + smax);
        }

        requireFinite("devBase", devBase);
        requireFinite("devPerSize", devPerSize);
        requireFinite("devJitter", devJitter);
        requireFinite("devMin", devMin);
        requireFinite("devMax", devMax);
        requireFinite("stanceBase", stanceBase);
        requireFinite("stanceJitter", stanceJitter);
        requireFinite("minPlacementRadius", minPlacementRadius);
        requireFinite("maxPlacementRadius", maxPlacementRadius);

        if (devPerSize < 0.0) {
            throw new IllegalStateException("devPerSize 必须 >= 0（规模越大发展度越低）：" + devPerSize);
        }
        // dev 必须落在 Nation 允许的区间内，否则 Nation 构造器会晚一步炸
        double dmin = clampDouble(Math.min(devMin, devMax), 0.0, 100.0);
        double dmax = clampDouble(Math.max(devMin, devMax), 0.0, 100.0);

        double rmin = Math.max(minPlacementRadius, minNationSpacing);
        double rmax = Math.max(maxPlacementRadius, rmin);
        if (rmin <= 0.0) {
            throw new IllegalStateException("布点半径必须 > 0：" + rmin + ".." + rmax);
        }
        // 薄环带会让布点必然失败（报错还会指向错误的原因），所以在校验期就拦下
        double ringArea = Math.PI * (rmax * rmax - rmin * rmin);
        double exclusion = Math.PI * (double) minNationSpacing * minNationSpacing;
        if (rmax - rmin < minNationSpacing) {
            throw new IllegalStateException(String.format(
                    "布点环带太薄：rmin=%.0f rmax=%.0f，而国家间距要求 %d —— "
                            + "请让 maxPlacementRadius 至少比 minPlacementRadius 大一个间距",
                    rmin, rmax, minNationSpacing));
        }
        if (exclusion > 0 && ringArea / exclusion < nations * 2.0) {
            throw new IllegalStateException(String.format(
                    "布点几何不可行：环带面积 %.3g / 排他面积 %.3g = %.1f，少于 %d 个国家的 2 倍余量",
                    ringArea, exclusion, ringArea / exclusion, nations));
        }

        return new StatecraftConfig(nations, minNationSpacing, smin, smax,
                devBase, devPerSize, Math.max(0.0, devJitter), dmin, dmax,
                stanceBase, Math.max(0.0, stanceJitter), rmin, rmax);
    }

    private static void requireFinite(String name, double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalStateException(name + " 必须是有限数，实际 " + v);
        }
    }

    private static double clampDouble(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
