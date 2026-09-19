package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 两个国家之间的关系（`NATIONS.md` §五）。
 *
 * <p>{@code a} / {@code b} 是国家 id。**刻意不做规范化排序**：谁打谁、谁向谁求和会影响后续叙事，
 * 把一对关系硬压成"无序对"会丢掉这层信息。
 *
 * <p>存成列表而不是 `Map`：`n=12` 也只有 66 对，而"用两个 id 拼一个 map key"在 NBT/JSON 里
 * 既难读又容易出错（分隔符撞上 id 里本来就有的字符）。查一对关系用 {@link WorldState#relation}。
 *
 * <p>{@code warScore} 的符号约定：**正数 = a 占优**。{@code losingStreak} 记"当前占劣的那一方
 * 已经连输了几步"——§八.4 的灭亡条件要"连输 ≥3 步"，而它不属于 §五 列的字段，
 * 这里是**有意追加**（当时 v2 还没写出过任何存档，所以直接并进 v2，不必再加一级迁移）。
 */
public record Relation(
        String a,
        String b,
        double attitude,
        State state,
        double warScore,
        long truceUntilSeq,
        int losingStreak) {

    public enum State {
        /** 和平（默认）。 */
        PEACE,
        /** 交战。 */
        WAR,
        /** 同盟。 */
        ALLIANCE,
        /** 停战（`truceUntilSeq` 之前不能再打）。 */
        TRUCE
    }

    public Relation {
        requireText(a, "Relation.a");
        requireText(b, "Relation.b");
        if (a.equals(b)) {
            throw new IllegalArgumentException("国家不能和自己有关系：" + a);
        }
        requireRange(attitude, -100.0, 100.0, "Relation.attitude");
        if (state == null) {
            throw new IllegalArgumentException("Relation.state 不能为 null");
        }
        requireFinite(warScore, "Relation.warScore");
        if (truceUntilSeq < 0) {
            throw new IllegalArgumentException("Relation.truceUntilSeq 不能为负：" + truceUntilSeq);
        }
        if (losingStreak < 0) {
            throw new IllegalArgumentException("Relation.losingStreak 不能为负：" + losingStreak);
        }
    }

    /** 停战（全零）关系，用作新建关系的默认值。 */
    public static Relation peace(String a, String b) {
        return new Relation(a, b, 0.0, State.PEACE, 0.0, 0L, 0);
    }

    /** 是否是"这一段"关系（无视双方顺序）。 */
    public boolean connects(String left, String right) {
        return (a.equals(left) && b.equals(right)) || (a.equals(right) && b.equals(left));
    }

    public Relation withAttitude(double v) {
        return new Relation(a, b, v, state, warScore, truceUntilSeq, losingStreak);
    }

    public Relation withState(State v) {
        return new Relation(a, b, attitude, v, warScore, truceUntilSeq, losingStreak);
    }

    public Relation withWarScore(double v) {
        return new Relation(a, b, attitude, state, v, truceUntilSeq, losingStreak);
    }

    public Relation withTruceUntilSeq(long v) {
        return new Relation(a, b, attitude, state, warScore, v, losingStreak);
    }

    public Relation withLosingStreak(int v) {
        return new Relation(a, b, attitude, state, warScore, truceUntilSeq, v);
    }

    /** 回到和平：战果与连败计数一起清零（战果属于那场战争，战争结束就该消失）。 */
    public Relation backToPeace() {
        return new Relation(a, b, attitude, State.PEACE, 0.0, truceUntilSeq, 0);
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
}
