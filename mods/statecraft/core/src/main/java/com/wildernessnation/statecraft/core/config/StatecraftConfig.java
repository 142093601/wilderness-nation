package com.wildernessnation.statecraft.core.config;

/**
 * 全部可调参数与默认值（对应 NATIONS.md 的数值表）。
 *
 * <p>设计约定：<strong>默认值就是我们要的那套</strong>，改配置只为了试平衡，
 * 不是为了"让代码跑起来"。{@link #validated()} 负责钳制，非法组合直接抛异常，
 * 绝不把非法状态写进存档。
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
        double maxPlacementRadius,
        double placementAngleStepDeg) {

    public static final int NATION_COUNT_MIN = 8;
    public static final int NATION_COUNT_MAX = 16;

    public static StatecraftConfig defaults() {
        return new StatecraftConfig(
                12, 1500, 1, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                2000.0, 12000.0, 8.0);
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
        double rmin = Math.max(minPlacementRadius, minNationSpacing);
        double rmax = Math.max(maxPlacementRadius, rmin);
        if (rmax <= 0.0 || rmin <= 0.0) {
            throw new IllegalStateException("布点半径必须 > 0：" + rmin + ".." + rmax);
        }
        double dmin = Math.min(devMin, devMax);
        double dmax = Math.max(devMin, devMax);
        return new StatecraftConfig(nations, minNationSpacing, smin, smax,
                devBase, devPerSize, Math.max(0.0, devJitter), dmin, dmax,
                stanceBase, Math.max(0.0, stanceJitter),
                rmin, rmax, Math.max(1.0, placementAngleStepDeg));
    }

    /** 测试用：把内半径当整数看，避免在断言里写一长串浮点比较。 */
    public int minPlacementRadiusAsInt() {
        return (int) Math.round(minPlacementRadius);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
