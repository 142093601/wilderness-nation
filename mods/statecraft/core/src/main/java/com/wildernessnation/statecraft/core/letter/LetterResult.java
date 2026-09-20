package com.wildernessnation.statecraft.core.letter;

import com.wildernessnation.statecraft.core.model.WorldState;

/**
 * 一次国书交互的结果（回复、递出、破约都走它）。
 *
 * <p>和 {@code DiplomacyOutcome} 同一条纪律：**前置不满足是"被拒绝"这种正常结果，不是异常**。
 * 玩家点了一封已经过期的国书，该看到一句中文解释，而不是崩掉。
 *
 * <p>{@code playerTreasuryDelta} 同样是**报出来**的：玩家侧账本属于计划 4/5，
 * core 不编一个账本出来（朝贡国书要玩家掏资源，那是这里唯一会用上它的地方）。
 *
 * @param state               处理后的状态；被拒绝时**原样返回入参**（一个字都没动）
 * @param accepted            这一步是否真的改变了什么
 * @param message             中文说明（给情报册直接显示）
 * @param playerTreasuryDelta 玩家方国库的变化（负数=要掏出去）
 */
public record LetterResult(
        WorldState state,
        boolean accepted,
        String message,
        double playerTreasuryDelta) {

    public LetterResult {
        if (state == null) {
            throw new IllegalArgumentException("LetterResult.state 不能为 null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("LetterResult.message 不能为空");
        }
        if (!Double.isFinite(playerTreasuryDelta)) {
            throw new IllegalArgumentException(
                    "LetterResult.playerTreasuryDelta 必须有限：" + playerTreasuryDelta);
        }
    }

    static LetterResult rejected(WorldState state, String message) {
        return new LetterResult(state, false, message, 0.0);
    }

    static LetterResult accepted(WorldState state, String message) {
        return new LetterResult(state, true, message, 0.0);
    }

    static LetterResult accepted(WorldState state, String message, double playerTreasuryDelta) {
        return new LetterResult(state, true, message, playerTreasuryDelta);
    }
}
