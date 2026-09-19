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
 * <h2>对 §五 字段表的两处有意偏离（都在 v2 落盘之前定的，所以不需要额外迁移）</h2>
 *
 * <p><strong>① {@code warOnParty} 布尔 → {@link PartyStatus} 枚举 + 期限 + 战果。</strong>
 * §五 只给了一个布尔（"是否正与玩家方交战（含战果）"），但 §11.2 的外交动作还需要表达
 * <em>同盟</em>（结盟）与 <em>停战/不侵犯</em>（求和、朝贡）。要是再加两个布尔，
 * "既同盟又交战"这种非法组合在类型上就拦不住。枚举让四种状态**先天互斥**。
 * §五 那句"（含战果）"落在 {@link #partyWarScore} 上。
 *
 * <p><strong>② 没有 {@code traits}（尚武/富庶/记仇/开放）。</strong>§八/§十二 里没有任何规则读它；
 * "没人用的字段不写"是这份代码的纪律，等有规则用它时再加。
 *
 * <p>关于那一堆 {@code withXxx}：引擎每一步都要改一两个字段，而 record 是不可变的。
 * 每个方法只改一个字段、其余原样，并且有测试逐个断言"只动了那一个"——
 * 这是为了挡住 18 个位置参数最容易出的错（插错顺序、类型悄悄对上）。
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
        PartyStatus partyStatus,
        long partyStatusUntilSeq,
        double partyWarScore,
        double fatigue,
        int absorbedCount) {

    public enum Status {
        /** 存活（默认）。 */
        ALIVE,
        /** 灭亡（或被吸收后不复存在）。 */
        DEAD
    }

    /** 对玩家方的外交状态（§五 的 {@code warOnParty} 的扩展，见类注释）。 */
    public enum PartyStatus {
        /** 中立（默认）。 */
        NEUTRAL,
        /** 正与玩家方交战。 */
        WAR,
        /** 与玩家方同盟。 */
        ALLIANCE,
        /** 停战 / 不侵犯承诺，`partyStatusUntilSeq` 之前不成立。 */
        TRUCE
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
        if (partyStatus == null) {
            throw new IllegalArgumentException("Nation.partyStatus 不能为 null");
        }
        if (partyStatusUntilSeq < 0) {
            throw new IllegalArgumentException(
                    "Nation.partyStatusUntilSeq 不能为负：" + partyStatusUntilSeq);
        }
        if (!Double.isFinite(partyWarScore)) {
            throw new IllegalArgumentException("Nation.partyWarScore 必须是有限数：" + partyWarScore);
        }
        if (partyStatus != PartyStatus.TRUCE && partyStatusUntilSeq != 0L) {
            throw new IllegalArgumentException(
                    "只有 TRUCE 才有期限，当前 " + partyStatus + " 却带了期限 " + partyStatusUntilSeq);
        }
        requireNonNegative(fatigue, "扩张疲劳");
        if (absorbedCount < 0) {
            throw new IllegalArgumentException("Nation.absorbedCount 不能为负：" + absorbedCount);
        }
    }

    /** 开局用的默认值：存活、对玩家中立、无疲劳、没吸收过别人。 */
    public static Nation spawn(
            String id, String name, String cultureId, int size, double development,
            double stance, double military, double treasury, double x, double z) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, false, 0.0, Status.ALIVE, PartyStatus.NEUTRAL, 0L, 0.0, 0.0, 0);
    }

    public boolean alive() {
        return status == Status.ALIVE;
    }

    public boolean atWarWithParty() {
        return partyStatus == PartyStatus.WAR;
    }

    public boolean alliedWithParty() {
        return partyStatus == PartyStatus.ALLIANCE;
    }

    /** 停战/不侵犯承诺在给定结算序号下是否还有效。 */
    public boolean truceHoldsAt(long seq) {
        return partyStatus == PartyStatus.TRUCE && seq < partyStatusUntilSeq;
    }

    /** 现在能不能对玩家宣战（只看自身状态，态度与倾向的门槛在 `SettlementEngine`）。 */
    public boolean freeToDeclareWarOnParty(long seq) {
        return alive() && partyStatus == PartyStatus.NEUTRAL;
    }

    /** 到某个点的水平距离（出生点默认在原点附近时可直接比较）。 */
    public double distanceTo(double ox, double oz) {
        return Math.hypot(x - ox, z - oz);
    }

    public Nation withPosition(double nx, double nz) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                nx, nz, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, fatigue, absorbedCount);
    }

    public Nation withSize(int v) {
        return new Nation(id, name, cultureId, v, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, fatigue, absorbedCount);
    }

    public Nation withDevelopment(double v) {
        return new Nation(id, name, cultureId, size, v, stance, military, treasury,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, fatigue, absorbedCount);
    }

    public Nation withMilitary(double v) {
        return new Nation(id, name, cultureId, size, development, stance, v, treasury,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, fatigue, absorbedCount);
    }

    public Nation withTreasury(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, v,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, fatigue, absorbedCount);
    }

    public Nation withAttitudeToParty(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, v, status, partyStatus, partyStatusUntilSeq, partyWarScore, fatigue,
                absorbedCount);
    }

    public Nation withMet(boolean v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, v, attitudeToParty, status, partyStatus, partyStatusUntilSeq, partyWarScore,
                fatigue, absorbedCount);
    }

    public Nation withStatus(Status v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, v, partyStatus, partyStatusUntilSeq, partyWarScore,
                fatigue, absorbedCount);
    }

    /** 设置对玩家的外交状态；非 TRUCE 时把期限清零，免得留下过期的僵尸期限。 */
    public Nation withPartyStatus(PartyStatus v, long untilSeq) {
        long until = v == PartyStatus.TRUCE ? untilSeq : 0L;
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, v, until, partyWarScore, fatigue,
                absorbedCount);
    }

    /** 战果：正数 = 玩家方占优。 */
    public Nation withPartyWarScore(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq, v, fatigue,
                absorbedCount);
    }

    public Nation withFatigue(double v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, v, absorbedCount);
    }

    public Nation withAbsorbedCount(int v) {
        return new Nation(id, name, cultureId, size, development, stance, military, treasury,
                x, z, met, attitudeToParty, status, partyStatus, partyStatusUntilSeq,
                partyWarScore, fatigue, v);
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
