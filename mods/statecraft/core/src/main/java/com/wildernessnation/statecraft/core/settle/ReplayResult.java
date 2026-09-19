package com.wildernessnation.statecraft.core.settle;

import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.OptionalLong;

/**
 * 重放的结果。
 *
 * @param state         重放到的状态（可能在分歧处停下）
 * @param stepsApplied  实际成功应用了几步
 * @param divergedAtSeq 在第几步发现"日志与状态对不上"；{@link OptionalLong#empty()} 表示一路对上了
 */
public record ReplayResult(WorldState state, int stepsApplied, OptionalLong divergedAtSeq) {

    public ReplayResult {
        if (state == null) {
            throw new IllegalArgumentException("ReplayResult.state 不能为 null");
        }
        if (divergedAtSeq == null) {
            divergedAtSeq = OptionalLong.empty();
        }
    }

    public boolean ok() {
        return divergedAtSeq.isEmpty();
    }

    /** 中文说明，直接可以进日志/回话。 */
    public String describe() {
        return ok()
                ? "重放成功：应用了 " + stepsApplied + " 步，结果与日志一致"
                : "重放在第 " + divergedAtSeq.getAsLong() + " 步发现分歧（已应用 " + stepsApplied
                        + " 步）—— 日志与状态不是同一段历史";
    }
}
