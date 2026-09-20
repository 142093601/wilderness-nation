package com.wildernessnation.statecraft.core.intel;

/**
 * 一次情报能力的判定（追踪 / 深挖 / 读消息）。
 *
 * <p>和 {@code LetterResult}、{@code BlueprintVerdict} 同一条纪律：
 * **前置不满足是"被拒绝"这种正常结果，不是异常**——界面要显示的是"要升级情报站"，
 * 不是一段堆栈。core 只判规则，不改状态（追踪清单在玩家账本里，计划 4/5）。
 *
 * @param allowed 成立吗
 * @param reason  中文说明（直接给界面显示）
 */
public record IntelVerdict(boolean allowed, String reason) {

    public IntelVerdict {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("IntelVerdict.reason 不能为空");
        }
    }

    static IntelVerdict accepted(String reason) {
        return new IntelVerdict(true, reason);
    }

    static IntelVerdict rejected(String reason) {
        return new IntelVerdict(false, reason);
    }
}
