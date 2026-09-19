package com.wildernessnation.statecraft.core.model;

/**
 * 国家（不可变快照）。
 *
 * <p>坐标是<strong>抽象位置</strong>，不等于"已经放了建筑"——实体化由 mod 层负责。
 *
 * <p>范围校验一律写成 <code>!(v >= lo && v &lt;= hi)</code> 而不是
 * <code>v &lt; lo || v &gt; hi</code>：后者对 <strong>NaN</strong> 两个比较都是 false，
 * 于是 NaN 会静默通过（2026-09-19 独立审查实测到过）。
 *
 * <p><strong>为什么没有 {@code traits}</strong>：§八/§十二 里没有任何规则读它。
 * "没人用的字段不写"是这份代码的纪律，等有规则用它时再加（那时正好走一次存档迁移）。
 *
 * <p>关于那一堆 {@code withXxx}：引擎每一步都要改一两个字段，而 record 是不可变的。
 * 每个方法只改一个字段、其余原样，并且有测试逐个断言"只动了那一个"——
 * 这是为了挡住 16 个位置参数最容易出的错（插错顺序、类型悄悄对上）。
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
        double attitudeToParty,
        Status status,
        boolean warOnParty,
        double fatigue,
        int absorbedCount) {

    public enum Status {
        /** 存活（默认）。 */
        ALIVE,
        /** 灭亡（或被吸收后不复存在）。 */
        DEAD
    }

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
        if (status == null) {
            throw new IllegalArgumentException("Nation.status 不能为 null");
        }
        requireNonNegative(fatigue, "扩张疲劳");
        if (absorbedCount < 0) {
            throw new IllegalArgumentException("Nation.absorbedCount 不能为负：" + absorbedCount);
        }
    }

    /** 开局用的默认值：存活、未与玩家交战、无疲劳、没吸收过别人。 */
    public static Nation spawn(
            String id, String name, String cultureId, int size, double development,
            double stance, double military, double treasury, double x, double z) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, false, 0.0, Status.ALIVE, false, 0.0, 0);
    }

    public boolean alive() {
        return status == Status.ALIVE;
    }

    /** 到某个点的水平距离（出生点默认在原点附近时可直接比较）。 */
    public double distanceTo(double ox, double oz) {
        return Math.hypot(x - ox, z - oz);
    }

    public Nation withPosition(double nx, double nz) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                nx, nz, met, attitudeToParty, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withSize(int v) {
        return new Nation(id, name, cultureId, v, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withDevelopment(double v) {
        return new Nation(id, name, cultureId, size, v, stance, military, treasury,
                x, z, met, attitudeToParty, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withMilitary(double v) {
        return new Nation(id, name, cultureId, size, development, stance, v, treasury,
                x, z, met, attitudeToParty, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withTreasury(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, v,
                x, z, met, attitudeToParty, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withAttitudeToParty(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, v, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withMet(boolean v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, v, attitudeToParty, status, warOnParty, fatigue, absorbedCount);
    }

    public Nation withStatus(Status v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, v, warOnParty, fatigue, absorbedCount);
    }

    public Nation withWarOnParty(boolean v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, v, fatigue, absorbedCount);
    }

    public Nation withFatigue(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, warOnParty, v, absorbedCount);
    }

    public Nation withAbsorbedCount(int v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, warOnParty, fatigue, v);
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
