package com.wildernessnation.statecraft.core.diplomacy;

/**
 * 玩家方的外交动作（`NATIONS.md` §11.2 的 6 个）。
 *
 * <p>这里的"动作"是**玩家发起**的那些。国家主动递来的国书（§9.4）是另一回事，
 * 属于计划 4 的情报站/国书。
 */
public enum DiplomacyAction {

    /** 宣战：进入 war，可发起夺城，它也会来打你们。 */
    DECLARE_WAR,

    /** 求和：按战果判定成败，成功转 truce（冷却 1 个结算周期）。 */
    SUE_FOR_PEACE,

    /** 结盟：转 alliance，互相在战争中提升彼此战力。 */
    ALLIANCE,

    /** 通商：双方国库提升，态度回升。 */
    TRADE,

    /** 求援：处于 alliance 时，按对方军力与态度决定是否驰援。 */
    CALL_FOR_AID,

    /** 朝贡：支付资源换态度与"不侵犯"承诺。 */
    TRIBUTE
}
