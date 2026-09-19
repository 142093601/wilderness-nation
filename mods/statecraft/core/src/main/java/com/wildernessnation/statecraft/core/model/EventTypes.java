package com.wildernessnation.statecraft.core.model;

/**
 * 事件的类型词表（`NATIONS.md` §五 列了 14 种）。
 *
 * <p>刻意用 {@code String} 常量而不是枚举：存档里的类型名来自"当时的代码"，
 * 如果做成枚举，旧版 mod 读到新版写的事件名就只能崩或重置——这跟 §十三"不让存档报废"直接冲突。
 * 用非空字符串 + 这张词表，未知类型也能原样带着走。
 */
public final class EventTypes {

    public static final String WAR_DECLARED = "war_declared";
    public static final String BATTLE = "battle";
    public static final String ABSORBED = "absorbed";
    public static final String ALLIANCE = "alliance";
    public static final String TRUCE = "truce";
    public static final String TRADE = "trade";
    public static final String COLLAPSE = "collapse";
    public static final String RAID = "raid";
    public static final String LETTER = "letter";
    public static final String PLAYER_ACTION = "player_action";
    public static final String SIEGE_STARTED = "siege_started";
    public static final String CITY_TAKEN = "city_taken";
    public static final String STAFF_LOST = "staff_lost";
    public static final String ERA_SUMMARY = "era_summary";

    private EventTypes() {}
}
