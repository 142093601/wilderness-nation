package com.wildernessnation.statecraft.core.letter;

/**
 * 一封国书（`NATIONS.md` §9.4）。
 *
 * <p>三种国书要的字段不一样，但**共用一个 record + 按种类校验**：
 * 分三个类型会让"情报册里一张列表"变复杂，而它们的生命周期（待回 → 接受/拒绝/过期）是同一套。
 *
 * @param id             标识
 * @param fromNationId   谁递的
 * @param kind           种类
 * @param targetNationId 只有"共同讨伐"用；其余为空串
 * @param stopBuildRadius 只有"停止建造"用（格）；其余为 0
 * @param amount         只有"朝贡"用（玩家要付多少）；其余为 0
 * @param issuedAtSeq    递出的结算序号
 * @param deadlineSeq    期限（到这个序号还没回 = 拒绝）
 * @param state          当前状态
 * @param promiseUntilSeq 承诺有效期（只有「停止在边境建造」接受后 > 0）；0 = 不构成承诺或承诺已了结
 */
public record Letter(
        String id,
        String fromNationId,
        LetterKind kind,
        String targetNationId,
        double stopBuildRadius,
        double amount,
        long issuedAtSeq,
        long deadlineSeq,
        LetterState state,
        long promiseUntilSeq) {

    public Letter {
        requireText(id, "Letter.id");
        requireText(fromNationId, "Letter.fromNationId");
        if (kind == null) {
            throw new IllegalArgumentException("Letter.kind 不能为 null");
        }
        if (state == null) {
            throw new IllegalArgumentException("Letter.state 不能为 null");
        }
        if (issuedAtSeq < 0 || deadlineSeq < 0) {
            throw new IllegalArgumentException(
                    "结算序号不能为负：" + issuedAtSeq + " / " + deadlineSeq);
        }
        if (deadlineSeq < issuedAtSeq) {
            throw new IllegalArgumentException(
                    "期限不能早于递出时间：" + issuedAtSeq + " → " + deadlineSeq);
        }
        if (!(stopBuildRadius >= 0.0) || !Double.isFinite(stopBuildRadius)) {
            throw new IllegalArgumentException("stopBuildRadius 非法：" + stopBuildRadius);
        }
        if (!(amount >= 0.0) || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount 非法：" + amount);
        }
        targetNationId = targetNationId == null ? "" : targetNationId;

        if (promiseUntilSeq < 0) {
            throw new IllegalArgumentException("承诺期限不能为负：" + promiseUntilSeq);
        }
        // 只有"停止建造"能产生承诺：别种国书填了它，就是调用方记错了账
        if (promiseUntilSeq != 0L && kind != LetterKind.STOP_BUILDING) {
            throw new IllegalArgumentException(
                    "只有停止建造类国书才有承诺期，" + kind + " 却带了 " + promiseUntilSeq);
        }
        if (promiseUntilSeq != 0L && promiseUntilSeq <= issuedAtSeq) {
            throw new IllegalArgumentException(
                    "承诺期必须晚于递出时间：" + issuedAtSeq + " → " + promiseUntilSeq);
        }
        // 承诺还活着的时候，这封信不能是"没回"的：承诺是接受的结果
        if (promiseUntilSeq != 0L && state != LetterState.ACCEPTED) {
            throw new IllegalArgumentException(
                    "只有已接受的国书才有承诺期，当前状态 " + state);
        }

        // 按种类校验：不该填的字段填了，就是数据写错了（早报早好）
        switch (kind) {
            case STOP_BUILDING -> {
                if (stopBuildRadius <= 0.0) {
                    throw new IllegalArgumentException("停止建造类国书必须给半径");
                }
                requireEmpty(targetNationId, "停止建造类国书不该有目标国");
                requireZero(amount, "停止建造类国书不该有资源数额");
            }
            case JOINT_WAR -> {
                requireText(targetNationId, "共同讨伐类国书必须有目标国");
                requireZero(stopBuildRadius, "共同讨伐类国书不该有半径");
                requireZero(amount, "共同讨伐类国书不该有资源数额");
            }
            case TRIBUTE -> {
                if (amount <= 0.0) {
                    throw new IllegalArgumentException("朝贡类国书必须给资源数额");
                }
                requireEmpty(targetNationId, "朝贡类国书不该有目标国");
                requireZero(stopBuildRadius, "朝贡类国书不该有半径");
            }
        }
    }

    /** 还没回过，而且期限到了 —— 该转成 EXPIRED（§9.4：忽略=拒绝）。 */
    public boolean overdueAt(long seq) {
        return state == LetterState.OPEN && seq > deadlineSeq;
    }

    public boolean answered() {
        return state != LetterState.OPEN;
    }

    /**
     * 这封信在当前结算序号下**正在生效的承诺**（没有就是 0）。
     *
     * <p>破约与履约都会把 {@code promiseUntilSeq} 清零，所以"还在承诺中"= 三个条件同时成立。
     */
    public boolean promiseInForceAt(long seq) {
        return state == LetterState.ACCEPTED && promiseUntilSeq > 0 && seq < promiseUntilSeq;
    }

    /** 承诺期到了、而且没破约 → 该结算成"履约完成"。 */
    public boolean promiseFulfilledAt(long seq) {
        return state == LetterState.ACCEPTED && promiseUntilSeq > 0 && seq >= promiseUntilSeq;
    }

    public Letter withState(LetterState v) {
        return new Letter(id, fromNationId, kind, targetNationId, stopBuildRadius, amount,
                issuedAtSeq, deadlineSeq, v, promiseUntilSeq);
    }

    /** 换状态并同时了结承诺（接受时立约、破约/履约时清账都走它）。 */
    public Letter withState(LetterState v, long newPromiseUntilSeq) {
        return new Letter(id, fromNationId, kind, targetNationId, stopBuildRadius, amount,
                issuedAtSeq, deadlineSeq, v, newPromiseUntilSeq);
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }

    private static void requireEmpty(String v, String what) {
        if (v != null && !v.isBlank()) {
            throw new IllegalArgumentException(what + "，却填了：" + v);
        }
    }

    private static void requireZero(double v, String what) {
        if (v != 0.0) {
            throw new IllegalArgumentException(what + "，却是 " + v);
        }
    }
}
