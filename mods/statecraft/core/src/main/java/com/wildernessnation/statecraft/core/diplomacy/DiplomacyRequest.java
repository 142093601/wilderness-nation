package com.wildernessnation.statecraft.core.diplomacy;

/**
 * 一次外交请求。
 *
 * @param action             要做的动作
 * @param nationId           对象国家
 * @param playerDevelopment  **玩家方的发展度**。§十二 的外交成功率里有 {@code (devYou − devThem)} 项，
 *                           而 `playerLedger`（玩家侧账本）属于计划 4/5——所以这个数由**调用方**递进来，
 *                           core 不自己编一个。现在传 0 就等于"这一项不参与"，将来接了账本再传真值。
 * @param nonce              **玩家动作的随机来源**，由调用方给（通常用世界刻或自增计数），必须 >= 0。
 *                           为什么不让 core 自己数：玩家动作是外部输入，和命令一样——
 *                           只有把它记进事务日志，"同 seed 同输入必得同结果"才成立。
 *                           **同一串动作里 nonce 必须各不相同**，否则两次求和会掷出同一个骰子。
 */
public record DiplomacyRequest(
        DiplomacyAction action, String nationId, double playerDevelopment, long nonce) {

    public DiplomacyRequest {
        if (action == null) {
            throw new IllegalArgumentException("DiplomacyRequest.action 不能为 null");
        }
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("DiplomacyRequest.nationId 不能为空");
        }
        if (!Double.isFinite(playerDevelopment)) {
            throw new IllegalArgumentException(
                    "DiplomacyRequest.playerDevelopment 必须是有限数：" + playerDevelopment);
        }
        if (nonce < 0) {
            throw new IllegalArgumentException("DiplomacyRequest.nonce 不能为负：" + nonce);
        }
    }

    /** 玩家发展度按 0（那一项不参与）的便捷构造，nonce 仍要自己给。 */
    public static DiplomacyRequest of(DiplomacyAction action, String nationId, long nonce) {
        return new DiplomacyRequest(action, nationId, 0.0, nonce);
    }
}
