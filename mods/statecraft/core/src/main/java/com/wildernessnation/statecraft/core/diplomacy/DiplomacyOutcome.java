package com.wildernessnation.statecraft.core.diplomacy;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 一次外交动作的结果。
 *
 * @param accepted            是否成立（前置不满足时为 false，**不抛异常**——§十五 要求"前置被违反必须拒绝"，
 *                            而"被拒绝"是玩家会看到的一种正常结果，不是 bug）
 * @param reason              中文说明（成立时为"已结盟"这类，拒绝时为"还在打仗，谈不了结盟"这类）
 * @param chance              如果这个动作需要掷骰，这里是成功率；不需要掷骰时为空
 * @param playerTreasuryDelta 玩家方国库的变化。**玩家侧账本属于计划 4/5**，
 *                            所以 core 只把这个数**报出来**，由调用方去记（通商/朝贡都要用）
 * @param aidGranted          求援是否真的得到驰援（其他动作恒为 false）
 */
public record DiplomacyOutcome(
        boolean accepted,
        String reason,
        OptionalDouble chance,
        double playerTreasuryDelta,
        boolean aidGranted) {

    public DiplomacyOutcome {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("DiplomacyOutcome.reason 不能为空");
        }
        if (chance == null) {
            chance = OptionalDouble.empty();
        }
        if (!Double.isFinite(playerTreasuryDelta)) {
            throw new IllegalArgumentException(
                    "DiplomacyOutcome.playerTreasuryDelta 必须有限：" + playerTreasuryDelta);
        }
    }

    static DiplomacyOutcome rejected(String reason) {
        return new DiplomacyOutcome(false, reason, OptionalDouble.empty(), 0.0, false);
    }

    static DiplomacyOutcome accepted(String reason) {
        return new DiplomacyOutcome(true, reason, OptionalDouble.empty(), 0.0, false);
    }

    static DiplomacyOutcome accepted(
            String reason, double chance, double playerTreasuryDelta, boolean aidGranted) {
        return new DiplomacyOutcome(true, reason, OptionalDouble.of(chance), playerTreasuryDelta,
                aidGranted);
    }

    /** 必成、但带玩家侧代价/收益的动作（通商、朝贡）。 */
    static DiplomacyOutcome accepted(String reason, double playerTreasuryDelta) {
        return new DiplomacyOutcome(true, reason, OptionalDouble.empty(), playerTreasuryDelta,
                false);
    }

    /** 被拒绝时也带上骰子信息的版本（求和失败时用得上：让玩家知道差多少）。 */
    static DiplomacyOutcome refused(String reason, double chance, double playerTreasuryDelta) {
        return new DiplomacyOutcome(false, reason, OptionalDouble.of(chance), playerTreasuryDelta,
                false);
    }
}
