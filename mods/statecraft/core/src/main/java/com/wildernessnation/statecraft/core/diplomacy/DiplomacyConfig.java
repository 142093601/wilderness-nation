package com.wildernessnation.statecraft.core.diplomacy;

/**
 * 外交参数（`NATIONS.md` §11.2 / §十二）。
 *
 * <p>与 `StatecraftConfig`（生成）、`SettlementConfig`（结算）并列的第三份配置：消费者不同。
 * 标 **【规格】** 的是设计文档直接给的数；标 **【占位】** 的是设计没给、由我选的保守值
 * （都要在试玩里调，所以放配置而不是公式里）。
 *
 * <p>**没有**放在这里的是：宣战门槛（−40 / +40）在 {@code SettlementConfig} 里——
 * 那条规则属于"国家对玩家的压力"，由结算引擎跑，外交动作只是触发它。
 */
public record DiplomacyConfig(
        double allianceMinAttitude,
        double tradeMinAttitude,
        double declareWarAttitudePenalty,
        double peaceWarScoreWeight,
        double peaceWarScoreDivisor,
        double peaceRefusalAttitudePenalty,
        double tradeTreasuryGain,
        double tradeAttitudeGain,
        double tributeMinDevelopmentGap,
        double tributeAttitudeGain,
        double tributeCost,
        long tributeNonAggressionSettlements,
        double aidAttitudeWeight,
        double aidMilitaryWeight,
        double raidWarScoreStep,
        double raidHeldAttitudeGain) {

    public static DiplomacyConfig defaults() {
        return new DiplomacyConfig(
                40.0,     // allianceMinAttitude              【规格】§11.2 结盟：态度 ≥ +40
                0.0,      // tradeMinAttitude                 【规格】§11.2 通商：态度 ≥ 0
                -30.0,    // declareWarAttitudePenalty        【占位】宣战"态度大跌"
                0.30,     // peaceWarScoreWeight              【占位】§11.2 求和"按战果判定"占多大权重
                60.0,     // peaceWarScoreDivisor             【占位】战果项在这个值处饱和
                -5.0,     // peaceRefusalAttitudePenalty      【占位】被拒绝后态度更硬
                5.0,      // tradeTreasuryGain                【占位】§11.2 只说"双方 treasury 提升"
                3.0,      // tradeAttitudeGain                【规格】§十二 玩家行为 → 态度：通商 +3
                0.0,      // tributeMinDevelopmentGap         【规格】§11.2 朝贡：你方发展度高于对方
                15.0,     // tributeAttitudeGain              【占位】朝贡换来的态度
                20.0,     // tributeCost                      【占位】朝贡要付多少（玩家侧记账，core 只报数）
                4L,       // tributeNonAggressionSettlements  【占位】"不侵犯"承诺持续几个结算
                0.25,     // aidAttitudeWeight                【占位】§11.2 求援按"态度"
                0.25,     // aidMilitaryWeight                【占位】§11.2 求援按"对方军力"
                10.0,     // raidWarScoreStep                 【占位】一次袭击的战果幅度
                5.0);     // raidHeldAttitudeGain             【占位】守住/被压制时态度的变化量
    }

    public DiplomacyConfig {
        requireFinite("allianceMinAttitude", allianceMinAttitude);
        requireFinite("tradeMinAttitude", tradeMinAttitude);
        requireFinite("declareWarAttitudePenalty", declareWarAttitudePenalty);
        requireFinite("peaceWarScoreWeight", peaceWarScoreWeight);
        if (peaceWarScoreWeight < 0) {
            throw new IllegalStateException("peaceWarScoreWeight 必须 >= 0：" + peaceWarScoreWeight);
        }
        requireFinite("peaceWarScoreDivisor", peaceWarScoreDivisor);
        if (peaceWarScoreDivisor <= 0) {
            throw new IllegalStateException(
                    "peaceWarScoreDivisor 必须 > 0（要拿它做除数）：" + peaceWarScoreDivisor);
        }
        requireFinite("peaceRefusalAttitudePenalty", peaceRefusalAttitudePenalty);
        requireNonNegative("tradeTreasuryGain", tradeTreasuryGain);
        requireFinite("tradeAttitudeGain", tradeAttitudeGain);
        requireNonNegative("tributeMinDevelopmentGap", tributeMinDevelopmentGap);
        requireFinite("tributeAttitudeGain", tributeAttitudeGain);
        requireNonNegative("tributeCost", tributeCost);
        if (tributeNonAggressionSettlements < 0) {
            throw new IllegalStateException(
                    "tributeNonAggressionSettlements 不能为负：" + tributeNonAggressionSettlements);
        }
        requireNonNegative("aidAttitudeWeight", aidAttitudeWeight);
        requireNonNegative("aidMilitaryWeight", aidMilitaryWeight);
        requireNonNegative("raidWarScoreStep", raidWarScoreStep);
        requireFinite("raidHeldAttitudeGain", raidHeldAttitudeGain);
    }

    /** 构造器已校验；保留这个方法是为了与另外两份配置手感一致。 */
    public DiplomacyConfig validated() {
        return this;
    }

    private static void requireFinite(String name, double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalStateException(name + " 必须是有限数，实际 " + v);
        }
    }

    private static void requireNonNegative(String name, double v) {
        if (!(v >= 0.0)) {          // NaN 也会落到这里
            throw new IllegalStateException(name + " 必须 >= 0 且有限，实际 " + v);
        }
        requireFinite(name, v);
    }
}
