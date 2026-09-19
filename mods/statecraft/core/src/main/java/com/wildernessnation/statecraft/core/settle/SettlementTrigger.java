package com.wildernessnation.statecraft.core.settle;

/**
 * 结算的触发来源（`NATIONS.md` §七 的三种触发）。
 *
 * <p>第三种"玩家动作"（外交 6 动作、发起夺城）不走结算引擎——它是**即时**的，
 * 由 `diplomacy` 包直接改状态，不推进时钟。
 */
public enum SettlementTrigger {

    /** 周期小结算：每 {@code periodicHours} 小时累计在线时间一次。 */
    PERIODIC,

    /** 时代推进：主节拍，额外做"全体成长"与一封「天下大势」。 */
    ERA_ADVANCE
}
