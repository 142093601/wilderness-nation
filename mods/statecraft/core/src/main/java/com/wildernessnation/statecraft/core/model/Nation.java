package com.wildernessnation.statecraft.core.model;

/**
 * 一个国家（不可变快照）。
 *
 * <p>坐标是<strong>抽象位置</strong>，不等于"已经放了建筑"——实体化由 mod 层负责。
 *
 * <p>范围校验一律写成 <code>!(v >= lo && v &lt;= hi)</code> 而不是
 * <code>v &lt; lo || v &gt; hi</code>：后者对 <strong>NaN</strong> 两个比较都是 false，
 * 于是 NaN 会静默通过（2026-09-19 独立审查实测到过）。
 */
public record Nation(
        String id,
        String name,
        String cultureId,
        int size,
        double development,
        double stance,
        double military,
        double treasury,
        double x,
        double z,
        boolean met,
        double attitudeToParty) {

    public Nation {
        requireText(id, "Nation.id");
        requireText(name, "Nation.name");
        requireText(cultureId, "Nation.cultureId");
        if (size < 1 || size > 10) {
            throw new IllegalArgumentException("规模必须 1..10：" + size);
        }
        requireRange(development, 0.0, 100.0, "发展度");
        requireRange(stance, -100.0, 100.0, "政治倾向");
        requireRange(attitudeToParty, -100.0, 100.0, "态度");
        requireNonNegative(military, "军力");
        requireNonNegative(treasury, "国库");
        requireFinite(x, "坐标 x");
        requireFinite(z, "坐标 z");
    }

    /** 到某个点的水平距离（出生点默认在原点附近时可直接比较）。 */
    public double distanceTo(double ox, double oz) {
        return Math.hypot(x - ox, z - oz);
    }

    public Nation withPosition(double nx, double nz) {
        return new Nation(id, name, cultureId, size, development, stance,
                military, treasury, nx, nz, met, attitudeToParty);
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }

    private static void requireFinite(double v, String what) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(what + " 必须是有限数，实际 " + v);
        }
    }

    private static void requireRange(double v, double lo, double hi, String what) {
        if (!(v >= lo && v <= hi)) {   // NaN 也会落到这里
            throw new IllegalArgumentException(what + " 必须 " + lo + ".." + hi + "，实际 " + v);
        }
    }

    private static void requireNonNegative(double v, String what) {
        if (!(v >= 0.0)) {             // NaN 也会落到这里
            throw new IllegalArgumentException(what + " 必须 >= 0 且有限，实际 " + v);
        }
        requireFinite(v, what);
    }
}
