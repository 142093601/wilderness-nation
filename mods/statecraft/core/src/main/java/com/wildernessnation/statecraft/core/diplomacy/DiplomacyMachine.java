package com.wildernessnation.statecraft.core.diplomacy;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import com.wildernessnation.statecraft.core.settle.SettlementConfig;
import java.util.List;

/**
 * 外交状态机（`NATIONS.md` §11.2 的 6 个动作）。
 *
 * <h2>两条纪律</h2>
 *
 * <p><strong>① 前置被违反必须拒绝，而且状态一个字都不能动。</strong>（§十五 明确要求）
 * 拒绝是 {@link DiplomacyOutcome#accepted()} 为 false 的**正常结果**，不是异常——
 * 玩家会看到"还在打仗，谈不了结盟"这种回话。只有"国家 id 不存在"这种**调用方 bug** 才抛。
 *
 * <p><strong>② 需要掷骰的动作必须可复现。</strong>玩家动作是即时的、不推进结算时钟，
 * 所以随机序号由调用方从 {@link DiplomacyRequest#nonce()} 递进来——它不属于世界状态，
 * 而是**外部输入的一部分**（和命令一样），被事务日志记下来才能重放。
 * 骰子本身仍是 {@code hash(seed, nonce, 用途标签)}，且同一串动作里 nonce 必须各不相同。
 *
 * <h2>两条不能在这里做的检查（记清楚边界）</h2>
 *
 * <p>§11.2 写着"在情报站发起"。**"你在不在情报站旁边"是 mc 层的事实**，core 没有世界读数，
 * 所以由 mod 层在调用前把关；core 只保证规则上的前置。
 *
 * <p>**玩家侧的资源与战力不在 core 里**（`playerLedger` 属于计划 4/5）。所以：
 * 通商/朝贡给玩家带来的国库变化、求援带来的战力加成，core 只**报出来**
 * （{@link DiplomacyOutcome#playerTreasuryDelta()}、{@link DiplomacyOutcome#aidGranted()}），
 * 由调用方去记账——core 不编一个玩家账本出来。
 */
public final class DiplomacyMachine {

    private final DiplomacyConfig cfg;
    private final SettlementConfig settleCfg;

    /**
     * @param cfg       外交参数（§11.2 / §十二）
     * @param settleCfg 只用来取"停战冷却 = 几个结算周期"（§十二）——那是结算的概念，
     *                  不在这里重复定义一遍
     */
    public DiplomacyMachine(DiplomacyConfig cfg, SettlementConfig settleCfg) {
        this.cfg = cfg.validated();
        this.settleCfg = settleCfg.validated();
    }

    public DiplomacyResult apply(WorldState state, DiplomacyRequest request) {
        Nation nation = state.nation(request.nationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "没有这个国家：" + request.nationId()));
        if (!nation.alive()) {
            return reject(state, "这个国家已经不存在了");
        }
        long nonce = request.nonce();
        DeterministicRandom rng = new DeterministicRandom(state.seed());
        return switch (request.action()) {
            case DECLARE_WAR -> declareWar(state, nation, nonce);
            case SUE_FOR_PEACE -> sueForPeace(state, nation, request, rng, nonce);
            case ALLIANCE -> alliance(state, nation, nonce);
            case TRADE -> trade(state, nation, nonce);
            case CALL_FOR_AID -> callForAid(state, nation, rng, nonce);
            case TRIBUTE -> tribute(state, nation, request, nonce);
        };
    }

    /**
     * mod 层报告一次袭击的结果（§9.3）。
     *
     * <p>为什么由外面报进来：**"你们守住没有"只有游戏里知道**（守军打没打光、建筑掉没掉）。
     * core 只能算"压力"和"后来怎么走"，所以这里提供一个明确的入口，
     * 顺便让 §11.2「求和」里的"战果"项真的会有值。
     *
     * @param playerHeld 玩家方守住了 → 该国军力/国库受损、态度回升、战果向玩家倾斜
     */
    public DiplomacyResult reportRaid(WorldState state, String nationId, boolean playerHeld) {
        Nation nation = state.nation(nationId)
                .orElseThrow(() -> new IllegalArgumentException("没有这个国家：" + nationId));
        if (!nation.atWarWithParty()) {
            return reject(state, "它并没有和你们交战，谈不上战果");
        }
        Nation updated = playerHeld
                ? nation.withMilitary(Math.max(0.0, nation.military() * 0.90))
                        .withTreasury(Math.max(0.0, nation.treasury() * 0.90))
                        .withAttitudeToParty(clampAttitude(nation.attitudeToParty()
                                + cfg.raidHeldAttitudeGain()))
                        .withPartyWarScore(nation.partyWarScore() + cfg.raidWarScoreStep())
                : nation
                        .withAttitudeToParty(clampAttitude(nation.attitudeToParty()
                                - cfg.raidHeldAttitudeGain()))
                        .withPartyWarScore(nation.partyWarScore() - cfg.raidWarScoreStep());
        String reason = playerHeld ? "你们守住了，它的军力与国库受损" : "你们被压制了，它的态度更硬";
        Event event = new Event(state.seq(), EventTypes.RAID,
                List.of(Event.PARTY_PLAYER, nation.id()),
                playerHeld ? "raid_held" : "raid_lost", List.of(nation.name()), true, false);
        WorldState next = state.withNation(updated).withEventsAdded(List.of(event));
        return new DiplomacyResult(next, DiplomacyOutcome.accepted(reason));
    }

    // ---- 6 个动作 ----

    /** 宣战：前置"已接触 + 非同盟"（§11.2）；情报站那条由 mod 层把关。 */
    private DiplomacyResult declareWar(WorldState state, Nation nation, long nonce) {
        if (!nation.met()) {
            return reject(state, "你还没接触过它，先拿到情报再说");
        }
        if (nation.alliedWithParty()) {
            return reject(state, "它是你们的盟友，得先解除同盟");
        }
        if (nation.atWarWithParty()) {
            return reject(state, "你们已经在打仗了");
        }
        Nation updated = nation.withPartyStatus(Nation.PartyStatus.WAR, 0L)
                .withPartyWarScore(0.0)
                .withAttitudeToParty(clampAttitude(
                        nation.attitudeToParty() + cfg.declareWarAttitudePenalty()));
        Event event = new Event(state.seq(), EventTypes.WAR_DECLARED,
                List.of(Event.PARTY_PLAYER, nation.id()), "war_declared_by_player",
                List.of(nation.name()), true, false);
        return accept(state, updated, event, nonce, "已宣战：" + nation.name());
    }

    /** 求和：前置"处于 war"；成败按战果与态度掷骰（§11.2 + §十二 的外交成功率）。 */
    private DiplomacyResult sueForPeace(
            WorldState state, Nation nation, DiplomacyRequest request, DeterministicRandom rng,
            long nonce) {
        if (!nation.atWarWithParty()) {
            return reject(state, "你们并没有在打仗");
        }
        double chance = peaceChance(nation, request.playerDevelopment());
        boolean accepted = rng.nextDouble(nonce, "sueForPeace") < chance;
        if (!accepted) {
            Nation updated = nation.withAttitudeToParty(clampAttitude(
                    nation.attitudeToParty() + cfg.peaceRefusalAttitudePenalty()));
            Event event = new Event(state.seq(), EventTypes.PLAYER_ACTION,
                    List.of(Event.PARTY_PLAYER, nation.id()), "peace_refused",
                    List.of(nation.name()), true, false);
            WorldState next = state.withNation(updated).withEventsAdded(List.of(event))
                    ;
            return new DiplomacyResult(next, DiplomacyOutcome.refused(
                    "它拒绝了求和（成功率 " + percent(chance) + "）", chance, 0.0));
        }
        long until = state.seq() + settleCfg.truceCooldownSettlements();
        Nation updated = nation.withPartyStatus(Nation.PartyStatus.TRUCE, until)
                .withPartyWarScore(0.0);
        Event event = new Event(state.seq(), EventTypes.TRUCE,
                List.of(Event.PARTY_PLAYER, nation.id()), "peace_made",
                List.of(nation.name()), true, false);
        // 求和是掷过骰的，所以**成功也要把概率报出来**（不然调用方无法解释"为什么这次成了"）
        WorldState next = state.withNation(updated).withEventsAdded(List.of(event));
        return new DiplomacyResult(next, DiplomacyOutcome.accepted(
                "求和成功（成功率 " + percent(chance) + "），停战到第 " + until + " 次结算",
                chance, 0.0, false));
    }

    /** 结盟：前置"态度 ≥ +40 且非战争"（§11.2）。 */
    private DiplomacyResult alliance(WorldState state, Nation nation, long nonce) {
        if (nation.atWarWithParty()) {
            return reject(state, "还在打仗，谈不了结盟");
        }
        if (nation.alliedWithParty()) {
            return reject(state, "你们已经是盟友了");
        }
        if (nation.attitudeToParty() < cfg.allianceMinAttitude()) {
            return reject(state, "它还不够信你（态度 " + oneDecimal(nation.attitudeToParty())
                    + "，需要 " + oneDecimal(cfg.allianceMinAttitude()) + "）");
        }
        Nation updated = nation.withPartyStatus(Nation.PartyStatus.ALLIANCE, 0L);
        Event event = new Event(state.seq(), EventTypes.ALLIANCE,
                List.of(Event.PARTY_PLAYER, nation.id()), "alliance_made",
                List.of(nation.name()), true, false);
        return accept(state, updated, event, nonce, "已与 " + nation.name() + " 结盟");
    }

    /** 通商：前置"态度 ≥ 0 且非战争"（§11.2）；态度 +3 来自 §十二 的"玩家行为 → 态度"。 */
    private DiplomacyResult trade(WorldState state, Nation nation, long nonce) {
        if (nation.atWarWithParty()) {
            return reject(state, "正在交战，通不了商");
        }
        if (nation.attitudeToParty() < cfg.tradeMinAttitude()) {
            return reject(state, "它对你们没有好感（态度 "
                    + oneDecimal(nation.attitudeToParty()) + "）");
        }
        Nation updated = nation
                .withTreasury(nation.treasury() + cfg.tradeTreasuryGain())
                .withAttitudeToParty(clampAttitude(
                        nation.attitudeToParty() + cfg.tradeAttitudeGain()));
        Event event = new Event(state.seq(), EventTypes.TRADE,
                List.of(Event.PARTY_PLAYER, nation.id()), "trade_made",
                List.of(nation.name()), true, false);
        WorldState next = state.withNation(updated).withEventsAdded(List.of(event));
        return new DiplomacyResult(next,
                DiplomacyOutcome.accepted("与 " + nation.name() + " 通商成功",
                        cfg.tradeTreasuryGain()));
    }

    /** 求援：前置"处于 alliance"；成败按对方军力与态度（§11.2）。 */
    private DiplomacyResult callForAid(
            WorldState state, Nation nation, DeterministicRandom rng, long nonce) {
        if (!nation.alliedWithParty()) {
            return reject(state, "它不是你们的盟友，没理由来援");
        }
        double chance = aidChance(nation);
        boolean granted = rng.nextDouble(nonce, "callForAid") < chance;
        Event event = new Event(state.seq(), EventTypes.PLAYER_ACTION,
                List.of(Event.PARTY_PLAYER, nation.id()),
                granted ? "aid_granted" : "aid_refused", List.of(nation.name()), true, false);
        WorldState next = state.withEventsAdded(List.of(event));
        String reason = granted
                ? nation.name() + " 答应驰援（成功率 " + percent(chance) + "）"
                : nation.name() + " 推脱了（成功率 " + percent(chance) + "）";
        return new DiplomacyResult(next, DiplomacyOutcome.accepted(reason, chance, 0.0, granted));
    }

    /** 朝贡：前置"你方发展度高于对方"（§11.2）；换态度 + "不侵犯"承诺。 */
    private DiplomacyResult tribute(
            WorldState state, Nation nation, DiplomacyRequest request, long nonce) {
        double gap = request.playerDevelopment() - nation.development();
        if (gap <= cfg.tributeMinDevelopmentGap()) {
            return reject(state, "你方的发展度并没有高于它（差 "
                    + oneDecimal(gap) + "），它凭什么收你的贡");
        }
        long until = state.seq() + cfg.tributeNonAggressionSettlements();
        Nation updated = nation
                .withAttitudeToParty(clampAttitude(
                        nation.attitudeToParty() + cfg.tributeAttitudeGain()))
                .withPartyStatus(Nation.PartyStatus.TRUCE, until)
                .withPartyWarScore(0.0);
        Event event = new Event(state.seq(), EventTypes.PLAYER_ACTION,
                List.of(Event.PARTY_PLAYER, nation.id()), "tribute_paid",
                List.of(nation.name()), true, false);
        WorldState next = state.withNation(updated).withEventsAdded(List.of(event));
        return new DiplomacyResult(next,
                DiplomacyOutcome.accepted("已向 " + nation.name() + " 朝贡，换到不侵犯承诺（到第 "
                        + until + " 次结算）", -cfg.tributeCost()));
    }

    // ---- 公式 ----

    /**
     * §十二 的外交成功率，用于「求和」。
     *
     * <pre>p = clamp(0.05, 0.95, 0.5 + attitude/200 + (devYou − devThem)/200 + stance/400)
     *      + 战果项</pre>
     *
     * <p>文档写的是 {@code (attitude−50)/200}，那只有在态度是 0~100 时才对；而 §9.1 的
     * {@code attitude ≤ −40} 与 §11.2 的 {@code 态度 ≥ +40} 都要求**有符号**态度 —— 已定案用
     * {@code attitude/200}（见 `PLAN-statecraft-03-settle.md` §一）。
     *
     * <p>§11.2 说求和"按战果判定成败"，而 §十二 的公式里没有战果项，所以这里加一项：
     * {@code peaceWarScoreWeight × clamp(partyWarScore / peaceWarScoreDivisor, −1, 1)}。权重与除数都是【占位】。
     */
    public double peaceChance(Nation nation, double playerDevelopment) {
        double base = 0.5
                + nation.attitudeToParty() / 200.0
                + (playerDevelopment - nation.development()) / 200.0
                + nation.stance() / 400.0;
        double warTerm = cfg.peaceWarScoreWeight()
                * clamp(nation.partyWarScore() / cfg.peaceWarScoreDivisor(), -1.0, 1.0);
        return clamp(base + warTerm, 0.05, 0.95);
    }

    /**
     * 「求援」的成败（§11.2 "按对方军力与态度决定"）。
     *
     * <pre>p = clamp(0.05, 0.95, 0.5 + aidAttitudeWeight×attitude/100
     *      + aidMilitaryWeight×(military/100 − 0.5))</pre>
     *
     * <p>【占位】§11.2 只说"按军力与态度"，没给式子。这样写是为了让两项都**线性可解释**：
     * 态度 +100 最多加 {@code aidAttitudeWeight}，军力 100 相对 50 的"中位"各加减一半权重。
     */
    public double aidChance(Nation nation) {
        double base = 0.5
                + cfg.aidAttitudeWeight() * (nation.attitudeToParty() / 100.0)
                + cfg.aidMilitaryWeight() * (nation.military() / 100.0 - 0.5);
        return clamp(base, 0.05, 0.95);
    }

    // ---- 小工具 ----

    private DiplomacyResult reject(WorldState state, String reason) {
        return new DiplomacyResult(state, DiplomacyOutcome.rejected(reason));
    }

    private static DiplomacyResult accept(
            WorldState state, Nation updated, Event event, long nonce, String reason) {
        WorldState next = state.withNation(updated).withEventsAdded(List.of(event));
        return new DiplomacyResult(next, DiplomacyOutcome.accepted(reason));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 态度必须落在 Nation 允许的 −100..100 里，否则构造器会拒（宁可在这里夹好）。 */
    private static double clampAttitude(double v) {
        return clamp(v, -100.0, 100.0);
    }

    private static String percent(double chance) {
        return Math.round(chance * 100) + "%";
    }

    private static String oneDecimal(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
