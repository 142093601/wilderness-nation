package com.wildernessnation.statecraft.core.letter;

/**
 * 国书参数（`NATIONS.md` §9.4 / §十二）。
 *
 * <p>与 `StatecraftConfig`（生成）、`SettlementConfig`（结算）、`DiplomacyConfig`（外交动作）
 * 并列的第四份配置：**消费者是国书状态机**。放配置而不是写进公式的原因和其他几份一样——
 * 这些数都得在试玩里调。
 *
 * <p>标 **【规格】** 的是设计文档直接给的（§十二 的"完成国书 +10"、"忽略=拒绝"）；
 * 标 **【占位】** 的是文档没给、由我选的保守值。
 *
 * <p>**没有**放这里的是：朝贡换来的不侵犯承诺有多长（那是 §11.2 朝贡动作的参数，
 * 在 {@link com.wildernessnation.statecraft.core.diplomacy.DiplomacyConfig} 里）——
 * 玩家主动朝贡与国家递来朝贡国书是两件事，参数各归各的，但**语义共用**
 * （都是 {@code PartyStatus.TRUCE} + 期限），所以国书接受朝贡时借用那个数。
 */
public record LetterConfig(
        long deadlineSettlements,
        double refusalAttitudePenalty,
        double ignoreAttitudePenalty,
        double tributeAcceptAttitudeGain,
        double jointWarAttitudeGain,
        double stopBuildAcceptAttitudeGain,
        long stopBuildPromiseSettlements,
        double stopBuildKeptAttitudeGain,
        double stopBuildBreachAttitudePenalty) {

    public static LetterConfig defaults() {
        return new LetterConfig(
                3L,      // deadlineSettlements                【占位】递出后几个结算内必须回（2 小时/结算 → 约 6 小时）
                -5.0,    // refusalAttitudePenalty             【占位】明确拒绝
                -8.0,    // ignoreAttitudePenalty              【占位】不吭声比明说更难看（§9.4 把两者都算拒绝）
                10.0,    // tributeAcceptAttitudeGain          【占位】收下贡品
                10.0,    // jointWarAttitudeGain               【规格】§十二 玩家行为 → 态度：完成国书 +10
                5.0,     // stopBuildAcceptAttitudeGain        【占位】答应收敛，先给一点面子
                6L,      // stopBuildPromiseSettlements        【占位】承诺管几个结算（2 小时/结算 → 约 12 小时）
                10.0,    // stopBuildKeptAttitudeGain          【规格】§十二 完成国书 +10（履约了才算完成）
                -25.0);  // stopBuildBreachAttitudePenalty     【占位】当面答应背后照建，比拒绝更伤
    }

    public LetterConfig {
        if (deadlineSettlements < 0) {
            throw new IllegalStateException("deadlineSettlements 不能为负：" + deadlineSettlements);
        }
        requireFinite("refusalAttitudePenalty", refusalAttitudePenalty);
        requireFinite("ignoreAttitudePenalty", ignoreAttitudePenalty);
        requireFinite("tributeAcceptAttitudeGain", tributeAcceptAttitudeGain);
        requireFinite("jointWarAttitudeGain", jointWarAttitudeGain);
        requireFinite("stopBuildAcceptAttitudeGain", stopBuildAcceptAttitudeGain);
        if (stopBuildPromiseSettlements <= 0) {
            throw new IllegalStateException(
                    "stopBuildPromiseSettlements 必须 > 0（承诺期是「接受」的全部意义）："
                            + stopBuildPromiseSettlements);
        }
        requireFinite("stopBuildKeptAttitudeGain", stopBuildKeptAttitudeGain);
        requireFinite("stopBuildBreachAttitudePenalty", stopBuildBreachAttitudePenalty);
        // 破约不能比拒绝更划算，否则最优解是"先答应再照建"
        if (stopBuildBreachAttitudePenalty > refusalAttitudePenalty) {
            throw new IllegalStateException("破约(" + stopBuildBreachAttitudePenalty
                    + ")不该比直接拒绝(" + refusalAttitudePenalty + ")代价更小");
        }
    }

    /** 构造器已校验；保留这个方法是为了与另外三份配置手感一致。 */
    public LetterConfig validated() {
        return this;
    }

    private static void requireFinite(String name, double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalStateException(name + " 必须是有限数，实际 " + v);
        }
    }
}
