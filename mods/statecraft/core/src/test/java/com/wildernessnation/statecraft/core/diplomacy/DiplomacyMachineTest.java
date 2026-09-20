package com.wildernessnation.statecraft.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.settle.SettlementConfig;
import com.wildernessnation.statecraft.core.settle.SettlementEngine;
import com.wildernessnation.statecraft.core.settle.SettlementInput;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiplomacyMachineTest {

    private static final SettlementConfig SETTLE = SettlementConfig.defaults();

    private static DiplomacyMachine machine() {
        return new DiplomacyMachine(DiplomacyConfig.defaults(), SETTLE);
    }

    private static Nation nation(
            double attitude, double stance, double development, boolean met,
            Nation.PartyStatus partyStatus) {
        return new Nation("n0", "赤沙", "bandit", 5, development, stance, 20.0, 50.0, 0.0, 0.0,
                met, attitude, Nation.Status.ALIVE, partyStatus, 0L, 0.0, 0.0, 0);
    }

    private static Nation neutral(double attitude) {
        return nation(attitude, 0.0, 40.0, true, Nation.PartyStatus.NEUTRAL);
    }

    private static WorldState state(Nation n) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(n), List.of(), List.of(), List.of(), 0.0);
    }

    private static DiplomacyResult run(Nation n, DiplomacyAction action, long nonce) {
        return run(n, action, nonce, 0.0);
    }

    private static DiplomacyResult run(
            Nation n, DiplomacyAction action, long nonce, double playerDevelopment) {
        WorldState s = state(n);
        return machine().apply(s, new DiplomacyRequest(action, "n0", playerDevelopment, nonce));
    }

    // ---- 前置：违反必须拒绝，而且状态一个字都不能动 ----

    @Test
    void declaringWarNeedsContactAndNoAlliance() {
        DiplomacyResult unmet = run(nation(-80.0, 0.0, 40.0, false, Nation.PartyStatus.NEUTRAL),
                DiplomacyAction.DECLARE_WAR, 0);
        assertFalse(unmet.outcome().accepted(), "没接触过就不该能宣战");
        assertTrue(unmet.outcome().reason().contains("接触"));

        DiplomacyResult allied = run(nation(-80.0, 0.0, 40.0, true, Nation.PartyStatus.ALLIANCE),
                DiplomacyAction.DECLARE_WAR, 0);
        assertFalse(allied.outcome().accepted(), "盟友不能直接宣战");
        assertTrue(allied.outcome().reason().contains("盟友"));

        DiplomacyResult again = run(nation(-80.0, 0.0, 40.0, true, Nation.PartyStatus.WAR),
                DiplomacyAction.DECLARE_WAR, 0);
        assertFalse(again.outcome().accepted(), "已经在打就别重复宣战");
    }

    /** 拒绝时**状态对象必须是同一个**——这是"拒绝不改状态"的最硬写法。 */
    @Test
    void rejectedActionsLeaveTheStateUntouched() {
        WorldState before = state(neutral(-80.0));
        DiplomacyResult result = machine().apply(before,
                DiplomacyRequest.of(DiplomacyAction.ALLIANCE, "n0", 0));
        assertFalse(result.outcome().accepted());
        assertSame(before, result.state(), "被拒绝时不该产生新状态");
        assertNotEquals(0, result.outcome().reason().length());
    }

    @Test
    void allianceNeedsEnoughAttitudeAndPeace() {
        assertFalse(run(neutral(39.9), DiplomacyAction.ALLIANCE, 0).outcome().accepted(),
                "态度差一点就不能结盟");
        assertTrue(run(neutral(40.0), DiplomacyAction.ALLIANCE, 0).outcome().accepted(),
                "态度够就能结盟");

        DiplomacyResult atWar = run(nation(90.0, 0.0, 40.0, true, Nation.PartyStatus.WAR),
                DiplomacyAction.ALLIANCE, 0);
        assertFalse(atWar.outcome().accepted(), "还在打仗，谈不了结盟");
        assertTrue(atWar.outcome().reason().contains("打仗"));
    }

    @Test
    void tradeNeedsNonNegativeAttitude() {
        assertFalse(run(neutral(-0.1), DiplomacyAction.TRADE, 0).outcome().accepted());
        assertFalse(run(nation(80.0, 0.0, 40.0, true, Nation.PartyStatus.WAR),
                DiplomacyAction.TRADE, 0).outcome().accepted(), "交战期间通不了商");
    }

    @Test
    void callForAidNeedsAnAlliance() {
        DiplomacyResult noAlly = run(neutral(90.0), DiplomacyAction.CALL_FOR_AID, 0);
        assertFalse(noAlly.outcome().accepted());
        assertTrue(noAlly.outcome().reason().contains("盟友"));
    }

    @Test
    void tributeNeedsADevelopmentAdvantage() {
        DiplomacyResult noGap = run(neutral(0.0), DiplomacyAction.TRIBUTE, 0, 40.0);
        assertFalse(noGap.outcome().accepted(), "发展度一样高，凭什么收贡");
        assertTrue(noGap.outcome().reason().contains("发展度"));

        DiplomacyResult gap = run(neutral(0.0), DiplomacyAction.TRIBUTE, 0, 70.0);
        assertTrue(gap.outcome().accepted());
    }

    @Test
    void suingForPeaceNeedsAWar() {
        DiplomacyResult peace = run(neutral(0.0), DiplomacyAction.SUE_FOR_PEACE, 0);
        assertFalse(peace.outcome().accepted());
        assertTrue(peace.outcome().reason().contains("并没有在打仗"));
    }

    @Test
    void unknownNationOrDeadNationIsHandled() {
        WorldState s = state(neutral(0.0));
        assertThrows(IllegalArgumentException.class, () -> machine().apply(s,
                DiplomacyRequest.of(DiplomacyAction.TRADE, "n9", 0)));

        Nation dead = neutral(0.0).withStatus(Nation.Status.DEAD);
        DiplomacyResult r = machine().apply(state(dead),
                DiplomacyRequest.of(DiplomacyAction.TRADE, "n0", 0));
        assertFalse(r.outcome().accepted());
        assertTrue(r.outcome().reason().contains("不存在"));
    }

    // ---- 效果 ----

    @Test
    void declaringWarGoesToWarAndSoursAttitude() {
        DiplomacyResult r = run(neutral(-20.0), DiplomacyAction.DECLARE_WAR, 0);
        assertTrue(r.outcome().accepted());
        Nation after = r.state().nation("n0").orElseThrow();
        assertEquals(Nation.PartyStatus.WAR, after.partyStatus());
        assertTrue(after.atWarWithParty());
        assertEquals(-50.0, after.attitudeToParty(), 1e-9, "-20 加上宣战惩罚 -30");
        assertEquals(1, r.state().events().size());
        assertEquals(EventTypes.WAR_DECLARED, r.state().events().get(0).type());
    }

    @Test
    void allianceTradeAndTributeApplyTheirEffects() {
        DiplomacyResult allied = run(neutral(60.0), DiplomacyAction.ALLIANCE, 0);
        assertTrue(allied.state().nation("n0").orElseThrow().alliedWithParty());

        DiplomacyResult traded = run(neutral(10.0), DiplomacyAction.TRADE, 0);
        Nation afterTrade = traded.state().nation("n0").orElseThrow();
        assertEquals(13.0, afterTrade.attitudeToParty(), 1e-9, "§十二 通商 +3");
        assertEquals(55.0, afterTrade.treasury(), 1e-9, "国库 +5（占位）");
        assertEquals(5.0, traded.outcome().playerTreasuryDelta(), 1e-9,
                "玩家侧收益也要报出来（玩家账本在计划 4/5）");
        assertTrue(traded.outcome().chance().isEmpty(), "通商不掷骰，成功率应为空");

        DiplomacyResult tribute = run(neutral(-30.0), DiplomacyAction.TRIBUTE, 0, 80.0);
        Nation afterTribute = tribute.state().nation("n0").orElseThrow();
        assertEquals(-15.0, afterTribute.attitudeToParty(), 1e-9, "-30 + 15");
        assertEquals(Nation.PartyStatus.TRUCE, afterTribute.partyStatus());
        assertEquals(3L + DiplomacyConfig.defaults().tributeNonAggressionSettlements(),
                afterTribute.partyStatusUntilSeq(), "承诺从当前结算序号往后数");
        assertTrue(tribute.outcome().playerTreasuryDelta() < 0, "朝贡是玩家付钱");
    }

    /** 求和：骰子是确定性的，所以"两种结果都能出现"是可以被断言的，不是碰运气。 */
    @Test
    void suingForPeaceCanBothSucceedAndFail() {
        Nation atWar = nation(60.0, 0.0, 40.0, true, Nation.PartyStatus.WAR)
                .withPartyWarScore(50.0);
        DiplomacyMachine m = machine();
        double chance = m.peaceChance(atWar, 80.0);
        assertTrue(chance > 0.5, "态度好 + 战果占优 → 成功率应该偏高：" + chance);

        boolean sawAccept = false;
        boolean sawRefuse = false;
        for (long nonce = 0; nonce < 40 && !(sawAccept && sawRefuse); nonce++) {
            DiplomacyResult r = run(atWar, DiplomacyAction.SUE_FOR_PEACE, nonce, 80.0);
            assertEquals(chance, r.outcome().chance().orElseThrow(), 1e-9, "报出的概率要等于公式");
            if (r.outcome().accepted()) {
                sawAccept = true;
                Nation after = r.state().nation("n0").orElseThrow();
                assertEquals(Nation.PartyStatus.TRUCE, after.partyStatus());
                assertEquals(3L + SETTLE.truceCooldownSettlements(), after.partyStatusUntilSeq());
                assertEquals(0.0, after.partyWarScore(), 0.0, "停战了战果清零");
            } else {
                sawRefuse = true;
                assertTrue(r.state().nation("n0").orElseThrow().atWarWithParty(),
                        "被拒绝就该还在打");
            }
        }
        assertTrue(sawAccept, "40 个 nonce 里应该有成功的");
        assertTrue(sawRefuse, "40 个 nonce 里应该有失败的");
    }

    @Test
    void callForAidCanBothBeGrantedAndRefused() {
        Nation ally = nation(0.0, 0.0, 40.0, true, Nation.PartyStatus.ALLIANCE);
        boolean sawGrant = false;
        boolean sawRefuse = false;
        for (long nonce = 0; nonce < 40 && !(sawGrant && sawRefuse); nonce++) {
            DiplomacyResult r = run(ally, DiplomacyAction.CALL_FOR_AID, nonce);
            if (r.outcome().aidGranted()) {
                sawGrant = true;
            } else {
                sawRefuse = true;
            }
        }
        assertTrue(sawGrant && sawRefuse, "求援成败两种结果都该出现");
    }

    // ---- 公式 ----

    /**
     * §十二 的外交成功率。文档写的 {@code (attitude−50)/200} 与 §9.1/§11.2 的有符号态度矛盾，
     * 已定案为 {@code attitude/200}（见 `PLAN-statecraft-03-settle.md` §一）。
     */
    @Test
    void peaceChanceFollowsTheDocumentedFormula() {
        DiplomacyMachine m = machine();
        // 0.5 + attitude/200 + (devYou-devThem)/200 + stance/400，无战果项
        Nation mid = nation(0.0, 0.0, 40.0, true, Nation.PartyStatus.WAR);
        assertEquals(0.5, m.peaceChance(mid, 40.0), 1e-9, "中性国家 + 同等发展度 = 0.5");
        // 0.5 + 60/200 + 20/200 = 0.9（刻意选不会撞上钳制的数）
        assertEquals(0.9, m.peaceChance(mid.withAttitudeToParty(60.0), 60.0), 1e-9);
        // 上下钳制
        assertEquals(0.95, m.peaceChance(mid.withAttitudeToParty(100.0), 100.0), 1e-9);
        assertEquals(0.05, m.peaceChance(mid.withAttitudeToParty(-100.0), -100.0), 1e-9);
        // 战果项：占优加分、落后减分，且在 ±1 处饱和
        assertEquals(m.peaceChance(mid, 40.0) + 0.30,
                m.peaceChance(mid.withPartyWarScore(60.0), 40.0), 1e-9);
        assertEquals(m.peaceChance(mid.withPartyWarScore(60.0), 40.0),
                m.peaceChance(mid.withPartyWarScore(9999.0), 40.0), 1e-9, "战果项饱和");
    }

    @Test
    void aidChanceRewardsAttitudeAndMilitary() {
        DiplomacyMachine m = machine();
        Nation weakUnfriendly = nation(0.0, 0.0, 40.0, true, Nation.PartyStatus.ALLIANCE)
                .withMilitary(0.0);
        Nation strongFriendly = nation(80.0, 0.0, 40.0, true, Nation.PartyStatus.ALLIANCE)
                .withMilitary(100.0);
        assertTrue(m.aidChance(strongFriendly) > m.aidChance(weakUnfriendly));
        assertTrue(m.aidChance(strongFriendly) <= 0.95);
        assertTrue(m.aidChance(weakUnfriendly) >= 0.05);
    }

    // ---- 战果与承诺 ----

    @Test
    void raidOutcomeMovesAttitudeMilitaryAndWarScore() {
        Nation atWar = nation(-60.0, 0.0, 40.0, true, Nation.PartyStatus.WAR);
        DiplomacyMachine m = machine();

        DiplomacyResult held = m.reportRaid(state(atWar), "n0", true);
        Nation afterHeld = held.state().nation("n0").orElseThrow();
        assertTrue(afterHeld.military() < 20.0, "守住了 → 它军力受损");
        assertTrue(afterHeld.treasury() < 50.0);
        assertEquals(-55.0, afterHeld.attitudeToParty(), 1e-9, "态度回升");
        assertEquals(10.0, afterHeld.partyWarScore(), 1e-9, "战果向玩家倾斜");

        DiplomacyResult lost = m.reportRaid(state(atWar), "n0", false);
        Nation afterLost = lost.state().nation("n0").orElseThrow();
        assertEquals(20.0, afterLost.military(), 1e-9, "被压制 → 它毫发无损");
        assertEquals(-65.0, afterLost.attitudeToParty(), 1e-9, "态度更硬");
        assertEquals(-10.0, afterLost.partyWarScore(), 1e-9);
    }

    @Test
    void raidReportRequiresAnActualWar() {
        DiplomacyResult r = machine().reportRaid(state(neutral(0.0)), "n0", true);
        assertFalse(r.outcome().accepted());
        assertTrue(r.outcome().reason().contains("交战"));
    }

    /**
     * 「朝贡」换来的不侵犯承诺必须**真的挡住宣战**，而且到点自动失效——
     * 否则那句话术就只是聊天记录里的一行。
     */
    @Test
    void tributeNonAggressionActuallyBlocksWarAndExpires() {
        // 又恨又凶：本来一定会打
        Nation hostile = nation(-80.0, 60.0, 30.0, true, Nation.PartyStatus.NEUTRAL);
        SettlementEngine engine = new SettlementEngine(SETTLE, StatecraftConfig.defaults(), eras());
        assertTrue(engine.willDeclareWarOnPlayer(hostile, 3L), "前提：本来够条件宣战");

        WorldState afterTribute = machine()
                .apply(state(hostile), new DiplomacyRequest(DiplomacyAction.TRIBUTE, "n0", 90.0, 0))
                .state();
        Nation protectedNation = afterTribute.nation("n0").orElseThrow();
        assertEquals(Nation.PartyStatus.TRUCE, protectedNation.partyStatus());
        long until = protectedNation.partyStatusUntilSeq();

        assertFalse(engine.willDeclareWarOnPlayer(protectedNation, 3L), "承诺期内不该宣战");
        assertFalse(engine.willDeclareWarOnPlayer(protectedNation, until - 1));
        assertTrue(protectedNation.truceHoldsAt(until - 1));

        // 结算推进到期限之后：承诺自动失效
        WorldState s = afterTribute;
        while (s.seq() < until) {
            s = engine.settle(s, SettlementInput.periodic(SETTLE));
        }
        Nation expired = s.nation("n0").orElseThrow();
        assertFalse(expired.truceHoldsAt(s.seq()), "到点了承诺就不该还在");
        // 一次结算里"承诺到期"排在"对玩家施压"之前，所以它一过期就立刻翻脸 ——
        // 这正是不侵犯承诺的意义：买的是那段时间，不是它的态度。
        assertEquals(Nation.PartyStatus.WAR, expired.partyStatus(),
                "承诺一过期，态度还是 -65、倾向还是 60，它就立刻宣战了");
        assertTrue(s.events().stream().anyMatch(e -> e.type().equals(EventTypes.WAR_DECLARED)));
    }

    /** 承诺到期后如果别的条件不成立，应该干净地回到中立（而不是卡在 TRUCE）。 */
    @Test
    void expiredTruceFallsBackToNeutralWhenNothingElseTriggers() {
        Nation mild = nation(-80.0, 0.0, 30.0, true, Nation.PartyStatus.NEUTRAL);   // 倾向 0 → 不会宣战
        WorldState withTruce = machine()
                .apply(state(mild), new DiplomacyRequest(DiplomacyAction.TRIBUTE, "n0", 90.0, 0))
                .state();
        long until = withTruce.nation("n0").orElseThrow().partyStatusUntilSeq();

        SettlementEngine engine = new SettlementEngine(SETTLE, StatecraftConfig.defaults(), eras());
        WorldState s = withTruce;
        while (s.seq() < until) {
            s = engine.settle(s, SettlementInput.periodic(SETTLE));
        }
        Nation after = s.nation("n0").orElseThrow();
        assertEquals(Nation.PartyStatus.NEUTRAL, after.partyStatus());
        assertEquals(0L, after.partyStatusUntilSeq(), "回到中立要顺手把期限清零");
    }

    /** 停战冷却：求和成功后要等够结算次数才能再打。 */
    @Test
    void truceFromPeaceLastsTheConfiguredCooldown() {
        Nation atWar = nation(90.0, 0.0, 30.0, true, Nation.PartyStatus.WAR);
        WorldState withTruce = null;
        for (long nonce = 0; nonce < 40 && withTruce == null; nonce++) {
            DiplomacyResult r = run(atWar, DiplomacyAction.SUE_FOR_PEACE, nonce, 80.0);
            if (r.outcome().accepted()) {
                withTruce = r.state();
            }
        }
        assertTrue(withTruce != null, "得先有一次成功的求和");

        SettlementEngine engine = new SettlementEngine(SETTLE, StatecraftConfig.defaults(), eras());
        long until = withTruce.nation("n0").orElseThrow().partyStatusUntilSeq();
        WorldState s = withTruce;
        while (s.seq() < until - 1) {
            s = engine.settle(s, SettlementInput.periodic(SETTLE));
        }
        assertTrue(s.nation("n0").orElseThrow().truceHoldsAt(s.seq()),
                "冷却期还没到，停战仍然有效");
        s = engine.settle(s, SettlementInput.periodic(SETTLE));
        assertEquals(Nation.PartyStatus.NEUTRAL, s.nation("n0").orElseThrow().partyStatus());
    }

    // ---- 确定性 ----

    /** 同 seed + 同 nonce → 同一结果；nonce 不同则骰子不同。 */
    @Test
    void sameSeedAndNonceGiveTheSameOutcome() {
        Nation atWar = nation(1.0, 0.0, 44.0, true, Nation.PartyStatus.WAR);
        for (long nonce = 0; nonce < 10; nonce++) {
            DiplomacyResult a = run(atWar, DiplomacyAction.SUE_FOR_PEACE, nonce, 50.0);
            DiplomacyResult b = run(atWar, DiplomacyAction.SUE_FOR_PEACE, nonce, 50.0);
            assertEquals(a.state().nations(), b.state().nations(), "nonce " + nonce + " 结果不一致");
            assertEquals(a.outcome().accepted(), b.outcome().accepted());
        }
        // 换个 seed 就必须换个历史
        DiplomacyMachine m = machine();
        WorldState other = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 43L, "landing", 0, 3L,
                List.of(atWar), List.of(), List.of(), List.of(), 0.0);
        boolean differs = false;
        for (long nonce = 0; nonce < 20 && !differs; nonce++) {
            boolean withSeed42 = m.apply(state(atWar),
                    new DiplomacyRequest(DiplomacyAction.SUE_FOR_PEACE, "n0", 50.0, nonce))
                    .outcome().accepted();
            boolean withSeed43 = m.apply(other,
                    new DiplomacyRequest(DiplomacyAction.SUE_FOR_PEACE, "n0", 50.0, nonce))
                    .outcome().accepted();
            differs = withSeed42 != withSeed43;
        }
        assertTrue(differs, "不同 seed 总该在某些 nonce 上掷出不同结果");
    }

    private static EraTable eras() {
        return new EraTable(List.of(
                new Era("landing", "落地", 0, 12, 1.00, Set.of()),
                new Era("founding", "立国", 1, 20, 1.15, Set.of())));
    }
}
