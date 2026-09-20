package com.wildernessnation.statecraft.core.building;

/**
 * 建筑能力的词表（`NATIONS.md` §十一 的能力项）。
 *
 * <p>和 {@code EventTypes} 一样刻意用 {@code String} 常量而不是枚举：
 * 能力名会出现在**数据文件**里（`buildings.json` / `staff.json`），
 * 做成枚举的话，旧版 mod 读到新版数据里多加的一项能力就只能崩；
 * 用非空字符串 + 这张词表，未知能力**原样带着走**，不认它就是不认它。
 *
 * <p>词表里没有"某座建筑的专属能力"：能力描述的是**玩家能做什么**，
 * 而不是"这是哪座建筑"。所以情报站和升级后的情报站共用同一批名字，
 * 差别在于它们各自声明了哪些（`buildings.json` 决定）。
 */
public final class BuildingAbility {

    /** 看已接触国家的基本信息、读未读事件、回复国书（§11.1 第一档）。 */
    public static final String INTEL_BOOK = "intel_book";

    /** 遣使发起外交动作（§11.1 第二档）。 */
    public static final String DIPLOMACY = "diplomacy";

    /** 宣战 / 朝贡必须在这里发起（§11.2，全服公告）。 */
    public static final String DECLARATION = "declaration";

    /** 追踪国家（§11.1 第三档，上限 +2）。 */
    public static final String TRACK_NATIONS = "track_nations";

    /** 显示各国正在进行的战争与同盟（§11.1 第三档）。 */
    public static final String SEE_WARS = "see_wars";

    /** 每结算可深挖 1 国详报（§11.1 第三档）。 */
    public static final String DEEP_DIVE = "deep_dive";

    /** 建筑等级 +1（"对锚点用升级件"，§11.1 第三档）。 */
    public static final String UPGRADE = "upgrade";

    private BuildingAbility() {}
}
