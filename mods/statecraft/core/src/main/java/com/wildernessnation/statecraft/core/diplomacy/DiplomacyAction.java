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
    TRIBUTE;

    /**
     * 这个动作必须在**情报站**发起吗（`NATIONS.md` §11.2 的"在情报站发起（全服公告）"）。
     *
     * <p>只有宣战与朝贡要：它们**公告天下**——打谁、给谁进贡，整服都得知道。
     * 其余四个动作只是你和对方之间的往来，情报册上就能做。
     *
     * <p>为什么放在枚举上而不是写在 {@code IntelTier} 里：这是**动作自己的属性**
     * （"要不要当面说"），不是档位的能力。放在档位那边的话，
     * 以后加一个"也要公告"的新动作就得改两处，而有一处一定会被忘掉。
     */
    public boolean needsStation() {
        return this == DECLARE_WAR || this == TRIBUTE;
    }
}
