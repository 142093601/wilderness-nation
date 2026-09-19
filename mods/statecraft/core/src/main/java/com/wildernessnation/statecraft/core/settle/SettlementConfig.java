package com.wildernessnation.statecraft.core.settle;

/**
 * 结算参数（`NATIONS.md` §十二）。
 *
 * <p><strong>凡是 §十二 给了数的，这里就是那个数</strong>，javadoc 里标出出处。
 * 凡是设计<strong>没给</strong>的，一律做成这里的显式参数、在 javadoc 里标 **【占位】**——
 * 绝不在公式里偷偷写一个魔数。理由是 §十二 自己写着"默认值全部走配置"：
 * 这些数必然要在真机试玩里改，写死在公式里就得改代码重编译。
 *
 * <p>【占位】的默认值是我选的**保守值**（宁可世界安静一点，也不要第一版就天下大乱），
 * 目的只是让规则能跑通、能测；平衡在试玩里调。
 *
 * <p>与 {@code StatecraftConfig}（生成参数）分开成两个 record：消费者不同、改动的理由也不同，
 * 合成一个 30 多字段的 record 只会让每个测试都要写一长串位置参数。
 *
 * <h2>从测量里改掉的两个设计数（不是随手调的）</h2>
 *
 * <p><strong>① {@code collapseMaxDevelopment} 必须是 40，不能是 §八.4 写的 25。</strong>
 * §六 的生成公式是 {@code dev = clamp(20, 95, 95 − 6×(size−1) + rng(−8,+8))}，于是
 * "规模到 10 的国家，发展度恒在 [33,49]"——把 size 代进去可以化简成 {@code 41 + 原始抖动}。
 * 而吸收只让规模变大、发展度按规模差下降，规模封顶 10 之后就不再降。
 * **结论：全世界的 dev 下限是 33，"dev < 25" 永远不成立**，§八.4 的灭亡分支是死代码
 * （调参探针实测：20 seed × 6 时代，灭亡 0 次）。抬到 40 之后"弱国"= 已经膨胀到规模 9~10 的国家，
 * 正好就是设计要的那句"旧世崩了"。有一条用例把这个下限（33）钉住了。
 *
 * <p><strong>② {@code collapseWarScore} 必须小于 {@code absorptionWarScore}。</strong>
 * 否则被打垮的弱国在够到灭亡线之前就先被吸收掉了，灭亡分支同样不可达（构造器里有断言拦着）。
 */
public record SettlementConfig(
        double periodicHours,
        double militaryRecoveryPerSize,
        double fatigueDecayPerPeriod,
        double fatiguePowerFactor,
        double fatigueWarChanceFactor,
        double attitudeRegressionMin,
        double attitudeRegressionMax,
        double warChanceBase,
        double warDeclarationAttitude,
        double warDeclarationStance,
        double raidChance,
        double raidScaleBase,
        double raidScaleDivisor,
        int raidScaleCap,
        double warScoreStep,
        double collapseWarScore,
        double collapseMaxDevelopment,
        int collapseLosingStreak,
        double absorptionWarScore,
        double absorptionSizeFactor,
        int absorptionSizeCap,
        long truceCooldownSettlements,
        int maxTransactionsPerSettle) {

    public static SettlementConfig defaults() {
        return new SettlementConfig(
                2.0,      // periodicHours            §十二 周期小结算 = 每 2 小时累计在线
                0.5,      // militaryRecoveryPerSize   【占位】每小结算 +ceil(size × 0.5)
                0.1,      // fatigueDecayPerPeriod     §十二 扩张疲劳每小结算 −0.1
                0.85,     // fatiguePowerFactor        §八.2/§八.6 疲劳每点 ×0.85（战力与恢复都用它）
                1.15,     // fatigueWarChanceFactor    §八.6 疲劳每点使被宣战概率 ×1.15
                1.0,      // attitudeRegressionMin     §十二 态度回归"向 0 靠 1~2 点"
                2.0,      // attitudeRegressionMax
                0.06,     // warChanceBase             【占位】国家之间每小结算的基础开战概率
                -40.0,    // warDeclarationAttitude    §9.1 玩家被宣战门槛：attitude ≤ −40
                40.0,     // warDeclarationStance      §9.1 且 stance ≥ +40
                0.5,      // raidChance                【占位】已宣战的国家每小结算发动袭击的概率
                2.0,      // raidScaleBase             §十二 袭击规模 = 2 + floor(military/25)
                25.0,     // raidScaleDivisor
                24,       // raidScaleCap              §十二 上限 24
                12.0,     // warScoreStep              【占位】每步的战果幅度
                40.0,     // collapseWarScore          【占位】§八.4 "warScore 落后超阈值"
                40.0,     // collapseMaxDevelopment    §八.4 写的是 25 —— **实测不可达**，见下方说明
                3,        // collapseLosingStreak      §八.4 连输 ≥3 步
                50.0,     // absorptionWarScore        【占位】§八.5 没给吸收的触发条件
                0.5,      // absorptionSizeFactor      §八.5 size += ceil(败者size × 0.5)
                10,       // absorptionSizeCap         §八.5 上限 10
                1L,       // truceCooldownSettlements  §十二 停战冷却 = 1 个结算周期
                2);       // maxTransactionsPerSettle  【占位】每小结算最多处理几对（性能与观感）
    }

    public SettlementConfig {
        requireFinite("periodicHours", periodicHours);
        if (periodicHours <= 0) {
            throw new IllegalStateException("periodicHours 必须 > 0：" + periodicHours);
        }
        requireNonNegative("militaryRecoveryPerSize", militaryRecoveryPerSize);
        requireNonNegative("fatigueDecayPerPeriod", fatigueDecayPerPeriod);
        requireFinite("fatiguePowerFactor", fatiguePowerFactor);
        if (fatiguePowerFactor <= 0 || fatiguePowerFactor > 1) {
            throw new IllegalStateException("fatiguePowerFactor 必须落在 (0,1]：" + fatiguePowerFactor);
        }
        requireFinite("fatigueWarChanceFactor", fatigueWarChanceFactor);
        if (fatigueWarChanceFactor < 1) {
            throw new IllegalStateException(
                    "fatigueWarChanceFactor 应该 >= 1（疲劳让国家更好战）：" + fatigueWarChanceFactor);
        }
        requireNonNegative("attitudeRegressionMin", attitudeRegressionMin);
        requireNonNegative("attitudeRegressionMax", attitudeRegressionMax);
        if (attitudeRegressionMax < attitudeRegressionMin) {
            throw new IllegalStateException("attitudeRegressionMax 不能小于 Min："
                    + attitudeRegressionMin + ".." + attitudeRegressionMax);
        }
        requireFinite("warChanceBase", warChanceBase);
        if (warChanceBase < 0 || warChanceBase > 1) {
            throw new IllegalStateException("warChanceBase 必须落在 [0,1]：" + warChanceBase);
        }
        requireFinite("warDeclarationAttitude", warDeclarationAttitude);
        requireFinite("warDeclarationStance", warDeclarationStance);
        requireFinite("raidChance", raidChance);
        if (raidChance < 0 || raidChance > 1) {
            throw new IllegalStateException("raidChance 必须落在 [0,1]：" + raidChance);
        }
        requireNonNegative("raidScaleBase", raidScaleBase);
        requireFinite("raidScaleDivisor", raidScaleDivisor);
        if (raidScaleDivisor <= 0) {
            throw new IllegalStateException("raidScaleDivisor 必须 > 0：" + raidScaleDivisor);
        }
        if (raidScaleCap < 1) {
            throw new IllegalStateException("raidScaleCap 必须 >= 1：" + raidScaleCap);
        }
        requireNonNegative("warScoreStep", warScoreStep);
        requireNonNegative("collapseWarScore", collapseWarScore);
        requireNonNegative("collapseMaxDevelopment", collapseMaxDevelopment);
        if (collapseLosingStreak < 1) {
            throw new IllegalStateException("collapseLosingStreak 必须 >= 1：" + collapseLosingStreak);
        }
        requireNonNegative("absorptionWarScore", absorptionWarScore);
        // 这条不是审美：**灭亡阈值必须严格小于吸收阈值**，否则一个被打垮的弱国永远不会"灭亡"——
        // 它在够到灭亡线之前就先被吸收掉了，§八.4 的整条分支成了死代码。
        // 2026-09-19 用调参探针量出来过（60/50 时 20 seed × 6 时代里灭亡 = 0 次）。
        if (!(collapseWarScore < absorptionWarScore)) {
            throw new IllegalStateException("collapseWarScore 必须 < absorptionWarScore，"
                    + "否则灭亡分支永远不可达（吸收会先触发）："
                    + collapseWarScore + " vs " + absorptionWarScore);
        }
        requireFinite("absorptionSizeFactor", absorptionSizeFactor);
        if (absorptionSizeFactor < 0) {
            throw new IllegalStateException("absorptionSizeFactor 必须 >= 0：" + absorptionSizeFactor);
        }
        if (absorptionSizeCap < 1 || absorptionSizeCap > 10) {
            throw new IllegalStateException("absorptionSizeCap 必须落在 1..10：" + absorptionSizeCap);
        }
        if (truceCooldownSettlements < 0) {
            throw new IllegalStateException(
                    "truceCooldownSettlements 不能为负：" + truceCooldownSettlements);
        }
        if (maxTransactionsPerSettle < 0) {
            throw new IllegalStateException(
                    "maxTransactionsPerSettle 不能为负：" + maxTransactionsPerSettle);
        }
    }

    public SettlementConfig validated() {
        return this;   // 构造器已经校验过了；保留这个方法是为了与 StatecraftConfig 手感一致
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
