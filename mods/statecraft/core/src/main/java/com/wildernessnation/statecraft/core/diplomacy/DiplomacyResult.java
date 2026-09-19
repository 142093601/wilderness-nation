package com.wildernessnation.statecraft.core.diplomacy;

import com.wildernessnation.statecraft.core.model.WorldState;

/**
 * 外交动作的结果：新状态 + 这次动作的判定说明。
 *
 * <p>为什么把两者一起返回而不是"改完让你自己读状态"：**被拒绝**也是一种结果，
 * 而拒绝时状态一个字都不能变。把状态和判定捆在一起返回，调用方就没法把"拒绝"
 * 误当成"已经改过了"（这正是一次真实事故的形状：自动化"以为"自己做了什么）。
 */
public record DiplomacyResult(WorldState state, DiplomacyOutcome outcome) {

    public DiplomacyResult {
        if (state == null) {
            throw new IllegalArgumentException("DiplomacyResult.state 不能为 null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("DiplomacyResult.outcome 不能为 null");
        }
    }
}
