package com.wildernessnation.statecraft.core.settle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.letter.LetterKind;
import com.wildernessnation.statecraft.core.letter.LetterMachine;
import com.wildernessnation.statecraft.core.letter.LetterState;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementEngineTest {

    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of("赤沙", "黑岩", "铁蹄", "灰狼", "断刃", "荒骨")),
            new Culture("desert_raider", "沙盗", List.of("白岩", "烈日", "黄沙", "孤驼", "枯井", "长夜")));

    private static EraTable sixEras() {
        return new EraTable(List.of(
                new Era("landing", "落地", 0, 12, 1.00, Set.of()),
                new Era("founding", "立国", 1, 20, 1.15, Set.of()),
                new Era("infra", "基建", 2, 25, 1.30, Set.of()),
                new Era("expedition", "远征", 3, 25, 1.45, Set.of()),
                new Era("defense", "御敌", 4, 30, 1.60, Set.of()),
                new Era("civilization", "文明", 5, 38, 1.75, Set.of())));
    }

    private static SettlementEngine engine(SettlementConfig cfg) {
        return new SettlementEngine(cfg, StatecraftConfig.defaults(), sixEras());
    }

    /**
     * 只改关心的几个旋钮，其余一律用默认值——免得每个用例都要写 23 个位置参数。
     *
     * @param collapseMaxDevelopment 灭亡的"弱国"门槛。默认 40 是为了**可达**
     *     （§六 的公式让所有国家的 dev 下限是 33）；要验证"dev 边界"本身时
     *     就显式传 §八.4 写的 25。
     */
    private static SettlementConfig cfg(
            double warChanceBase, double warScoreStep, double collapseWarScore,
            double absorptionWarScore, double raidChance, double collapseMaxDevelopment) {
        SettlementConfig d = SettlementConfig.defaults();
        return new SettlementConfig(
                d.periodicHours(), d.militaryRecoveryPerSize(), d.fatigueDecayPerPeriod(),
                d.fatiguePowerFactor(), d.fatigueWarChanceFactor(),
                d.attitudeRegressionMin(), d.attitudeRegressionMax(),
                warChanceBase, d.warDeclarationAttitude(), d.warDeclarationStance(), raidChance,
                d.raidScaleBase(), d.raidScaleDivisor(), d.raidScaleCap(), warScoreStep,
                collapseWarScore, collapseMaxDevelopment, d.collapseLosingStreak(),
                absorptionWarScore, d.absorptionSizeFactor(), d.absorptionSizeCap(),
                d.truceCooldownSettlements(), d.maxTransactionsPerSettle());
    }

    private static SettlementConfig cfg(
            double warChanceBase, double warScoreStep, double collapseWarScore,
            double absorptionWarScore, double raidChance) {
        return cfg(warChanceBase, warScoreStep, collapseWarScore, absorptionWarScore, raidChance,
                SettlementConfig.defaults().collapseMaxDevelopment());
    }

    /** 不自动开战（战争结果要逐条控制时都用它，再手工把关系设成 WAR）。 */
    private static SettlementConfig noWar() {
        return cfg(0.0, 12.0, 40.0, 50.0, 0.0);
    }

    private static Nation nation(String id, int size, double military, double stance, double dev) {
        return Nation.spawn(id, "国" + id, "bandit", size, dev, stance, military, 50.0, 0.0, 0.0);
    }

    /** 两个国家、关系**已经处于交战**——这样每次 settle 都恰好推进一步战争。 */
    private static WorldState atWar(Nation a, Nation b) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 0L,
                List.of(a, b),
                List.of(new Relation(a.id(), b.id(), 0.0, Relation.State.WAR, 0.0, 0L, 0)),
                List.of(), List.of(), List.of(), 0.0);
    }

    private static WorldState peaceful(Nation a, Nation b) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 0L,
                List.of(a, b), List.of(Relation.peace(a.id(), b.id())), List.of(), List.of(), List.of(), 0.0);
    }

    private static WorldState single(Nation n) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 7L, "landing", 0, 0L,
                List.of(n), List.of(), List.of(), List.of(), List.of(), 0.0);
    }

    private static long count(WorldState s, String type) {
        return s.events().stream().filter(e -> e.type().equals(type)).count();
    }

    private static boolean has(WorldState s, String type) {
        return count(s, type) > 0;
    }

    // ---- 时钟与时代 ----

    @Test
    void periodicSettlementAdvancesClockAndSeq() {
        SettlementConfig c = SettlementConfig.defaults();
        WorldState before = new WorldGenerator(StatecraftConfig.defaults(), 1L)
                .generate(CULTURES, 0.0, 0.0, "landing", 0);
        WorldState after = engine(c).settle(before, SettlementInput.periodic(c));

        assertEquals(c.periodicHours(), after.elapsedOnlineHours(), 0.0);
        assertEquals(before.seq() + 1, after.seq());
        assertEquals("landing", after.eraId(), "2 小时还没到下一个时代");
        assertEquals(before.nations().size(), after.nations().size(), "结算不增删国家");
    }

    @Test
    void eraAdvanceMovesTheEraAndEmitsTheSummary() {
        SettlementConfig c = SettlementConfig.defaults();
        WorldState before = new WorldGenerator(StatecraftConfig.defaults(), 1L)
                .generate(CULTURES, 0.0, 0.0, "landing", 0);
        WorldState after = engine(c).settle(before, SettlementInput.eraAdvance(12.0));

        assertEquals("founding", after.eraId(), "走完 12 小时进入第二个时代");
        assertEquals(1, after.eraOrdinal());
        assertTrue(has(after, EventTypes.ERA_SUMMARY), "时代推进要给一封「天下大势」");
        Event summary = after.events().stream()
                .filter(e -> e.type().equals(EventTypes.ERA_SUMMARY)).findFirst().orElseThrow();
        assertEquals("founding", summary.params().get(0), "事件里带 eraId（文本渲染留给计划 4）");
    }

    @Test
    void eraAdvanceGrowsMilitaryAndTreasuryButNotSizeOrDevelopment() {
        // 第二时代系数 1.15
        SettlementEngine e = new SettlementEngine(
                cfg(0.0, 12.0, 40.0, 50.0, 0.0), StatecraftConfig.defaults(), sixEras());
        WorldState after = e.settle(peaceful(nation("n0", 5, 20.0, 0.0, 60.0),
                nation("n1", 5, 20.0, 0.0, 60.0)), SettlementInput.eraAdvance(20.0));

        Nation n0 = after.nation("n0").orElseThrow();
        assertTrue(n0.military() > 20.0, "时代推进让军力成长：" + n0.military());
        assertTrue(n0.treasury() > 50.0, "国库也成长：" + n0.treasury());
        assertEquals(5, n0.size(), "size 不归时代管（它由吸收驱动）");
        assertEquals(60.0, n0.development(), 0.0, "dev 不归时代管（它由反比公式驱动）");
    }

    // ---- §十二 态度回归 / 疲劳 ----

    @Test
    void attitudeRegressesTowardZero() {
        SettlementConfig c = SettlementConfig.defaults();   // 每小结算向 0 靠 1~2 点
        WorldState after = engine(noWar()).settle(
                peaceful(nation("n0", 5, 20.0, 0.0, 60.0).withAttitudeToParty(-100.0),
                        nation("n1", 5, 20.0, 0.0, 60.0).withAttitudeToParty(80.0)),
                SettlementInput.periodic(c));

        double attA = after.nation("n0").orElseThrow().attitudeToParty();
        double attB = after.nation("n1").orElseThrow().attitudeToParty();
        assertTrue(attA >= -99.0 && attA <= -98.0, "朝 0 靠 1~2 点：" + attA);
        assertTrue(attB >= 78.0 && attB <= 79.0, "朝 0 靠 1~2 点：" + attB);
        // 国家之间的关系态度也一起回归
        assertEquals(0.0, after.relations().get(0).attitude(), 0.0);
    }

    @Test
    void fatigueDecaysAndSlowsMilitaryRecovery() {
        SettlementConfig c = SettlementConfig.defaults();
        Nation fresh = nation("n0", 4, 20.0, 0.0, 60.0);
        Nation tired = nation("n1", 4, 20.0, 0.0, 60.0).withFatigue(2.0);
        WorldState after = engine(noWar()).settle(peaceful(fresh, tired), SettlementInput.periodic(c));

        double freshGain = after.nation("n0").orElseThrow().military() - 20.0;
        double tiredGain = after.nation("n1").orElseThrow().military() - 20.0;
        assertTrue(tiredGain < freshGain,
                "疲劳拖慢恢复：疲惫国 +" + tiredGain + " vs 新国 +" + freshGain);
        assertEquals(1.9, after.nation("n1").orElseThrow().fatigue(), 1e-9, "每小结算 −0.1");
    }

    // ---- §八 战争：灭亡 / 吸收 ----

    @Test
    void absorptionGrowsTheWinnerAndLowersItsDevelopment() {
        // 战力差距极大 → 胜者确定；一步就过吸收阈值
        SettlementConfig c = cfg(0.0, 100.0, 10.0, 50.0, 0.0);
        WorldState after = engine(c).settle(
                atWar(nation("n0", 2, 1000.0, 0.0, 89.0), nation("n1", 6, 1.0, 0.0, 65.0)),
                SettlementInput.periodic(c));

        assertTrue(has(after, EventTypes.ABSORBED), "应该走吸收：" + after.events());
        Nation winner = after.nation("n0").orElseThrow();
        assertFalse(after.nation("n1").orElseThrow().alive(), "败者灭亡");

        assertEquals(2 + (int) Math.ceil(6 * 0.5), winner.size(), "size += ceil(败者size × 0.5)");
        // 规模变大 → 反比公式重算 → 发展度**下降**（设计要的"打赢了反而变落后"）
        assertTrue(winner.development() < 89.0, "吸收后发展度必须下降：" + winner.development());
        assertEquals(1.0, winner.fatigue(), 1e-9, "吸收 +1 疲劳");
        assertEquals(1, winner.absorbedCount());
    }

    @Test
    void absorptionIsCappedAtSizeTen() {
        SettlementConfig c = cfg(0.0, 100.0, 10.0, 50.0, 0.0);
        WorldState after = engine(c).settle(
                atWar(nation("n0", 9, 1000.0, 0.0, 40.0), nation("n1", 10, 1.0, 0.0, 20.0)),
                SettlementInput.periodic(c));
        assertTrue(has(after, EventTypes.ABSORBED));
        assertEquals(10, after.nation("n0").orElseThrow().size(),
                "9 + ceil(10×0.5)=14 → 被上限 10 截住");
    }

    /** §八.4 灭亡要**同时**满足：战果落后超阈值 + 本来弱（dev < 25）+ 连输 ≥3 步。 */
    @Test
    void collapseNeedsAllThreeConditions() {
        SettlementConfig c = cfg(0.0, 100.0, 60.0, 1000.0, 0.0, 25.0);   // 上限用文档写的 25
        SettlementEngine e = engine(c);

        // dev = 24（弱）：连输第 1、2 步都不该灭，第 3 步才灭
        WorldState s = atWar(nation("n0", 2, 1000.0, 0.0, 89.0), nation("n1", 4, 1.0, 0.0, 24.0));
        s = e.settle(s, SettlementInput.periodic(c));
        assertFalse(has(s, EventTypes.COLLAPSE), "只输一步不该灭亡");
        assertEquals(1, s.relations().get(0).losingStreak());
        s = e.settle(s, SettlementInput.periodic(c));
        assertFalse(has(s, EventTypes.COLLAPSE), "连输两步还不该灭亡（要 ≥3）");
        s = e.settle(s, SettlementInput.periodic(c));
        assertTrue(has(s, EventTypes.COLLAPSE), "连输三步 + 弱国 → 灭亡：" + s.events());
        assertFalse(s.nation("n1").orElseThrow().alive());

        // dev = 25（不满足 dev < 25）：连输再多也不灭
        SettlementEngine e2 = engine(c);
        WorldState healthy = atWar(nation("n0", 2, 1000.0, 0.0, 89.0), nation("n1", 4, 1.0, 0.0, 25.0));
        for (int i = 0; i < 4; i++) {
            healthy = e2.settle(healthy, SettlementInput.periodic(c));
        }
        assertFalse(has(healthy, EventTypes.COLLAPSE),
                "dev=25 不满足 dev<25，不该灭亡：" + healthy.events());
        assertTrue(healthy.nation("n1").orElseThrow().alive());
        assertTrue(healthy.relations().get(0).losingStreak() >= 3);
    }

    @Test
    void warScoreGrowsStepByStepAndRecordsTheLosingStreak() {
        SettlementConfig c = cfg(0.0, 30.0, 900.0, 1000.0, 0.0);   // 阈值都不可达 → 一直打
        SettlementEngine e = engine(c);
        WorldState s = e.settle(
                atWar(nation("n0", 2, 1000.0, 0.0, 89.0), nation("n1", 4, 1.0, 0.0, 60.0)),
                SettlementInput.periodic(c));
        Relation r1 = s.relations().get(0);
        assertEquals(Relation.State.WAR, r1.state());
        assertEquals(30.0, r1.warScore(), 1e-9, "a 一直赢 → warScore 正数增大");
        assertEquals(1, r1.losingStreak());

        WorldState s2 = e.settle(s, SettlementInput.periodic(c));
        Relation r2 = s2.relations().get(0);
        assertEquals(60.0, r2.warScore(), 1e-9);
        assertEquals(2, r2.losingStreak(), "同一个败者 → 连败 +1");
    }

    @Test
    void zeroWarChanceKeepsTheWorldPeaceful() {
        SettlementConfig c = noWar();
        SettlementEngine e = engine(c);
        WorldState s = new WorldGenerator(StatecraftConfig.defaults(), 3L)
                .generate(CULTURES, 0.0, 0.0, "landing", 0);
        for (int i = 0; i < 5; i++) {
            s = e.settle(s, SettlementInput.periodic(c));
        }
        assertTrue(s.relations().stream().allMatch(r -> r.state() == Relation.State.PEACE),
                "开战概率 0 时不该有任何战争");
        assertEquals(0, count(s, EventTypes.WAR_DECLARED));
    }

    @Test
    void warIsDeclaredWhenTheRollAllowsIt() {
        SettlementConfig c = cfg(1.0, 12.0, 40.0, 50.0, 0.0);
        WorldState after = engine(c).settle(
                peaceful(nation("n0", 5, 20.0, 30.0, 60.0), nation("n1", 5, 20.0, 30.0, 60.0)),
                SettlementInput.periodic(c));
        assertTrue(has(after, EventTypes.WAR_DECLARED), "概率 1 → 必然宣战：" + after.events());
        assertEquals(Relation.State.WAR, after.relations().get(0).state());
    }

    @Test
    void deadNationStopsParticipating() {
        SettlementConfig c = cfg(0.0, 100.0, 10.0, 50.0, 0.0);
        SettlementEngine e = engine(c);
        WorldState s = e.settle(
                atWar(nation("n0", 2, 1000.0, 0.0, 89.0), nation("n1", 4, 1.0, 0.0, 20.0)),
                SettlementInput.periodic(c));
        Nation dead = s.nation("n1").orElseThrow();
        assertFalse(dead.alive());

        double militaryBefore = dead.military();
        WorldState again = e.settle(s, SettlementInput.periodic(c));
        assertEquals(militaryBefore, again.nation("n1").orElseThrow().military(), 0.0,
                "灭亡的国家不再恢复军力");
        assertEquals(Relation.State.PEACE, again.relations().get(0).state(), "战争随一方灭亡而收尾");
    }

    // ---- §九 对玩家的压力 ----

    /** 边界必须能直接问：一次结算里"态度回归"先跑，卡在 −40 的国家会漂回 −38 而失去资格。 */
    @Test
    void playerWarGateUsesAttitudeAndStance() {
        SettlementEngine e = engine(SettlementConfig.defaults());
        assertTrue(e.willDeclareWarOnPlayer(
                nation("n0", 5, 20.0, 40.0, 60.0).withAttitudeToParty(-40.0), 0L), "刚好到门槛");
        assertFalse(e.willDeclareWarOnPlayer(
                nation("n0", 5, 20.0, 40.0, 60.0).withAttitudeToParty(-39.9), 0L), "差一点就不够恨");
        assertFalse(e.willDeclareWarOnPlayer(
                nation("n0", 5, 20.0, 39.9, 60.0).withAttitudeToParty(-80.0), 0L), "倾向不够凶");
        assertFalse(e.willDeclareWarOnPlayer(
                nation("n0", 5, 20.0, 80.0, 60.0).withAttitudeToParty(-80.0)
                        .withPartyStatus(Nation.PartyStatus.WAR, 0L), 0L), "已经在打就不再重复宣战");
    }

    @Test
    void hostileNationDeclaresWarOnThePlayerInThePipeline() {
        SettlementConfig c = noWar();
        Nation hostile = nation("n0", 5, 20.0, 50.0, 60.0).withAttitudeToParty(-80.0);
        WorldState after = engine(c).settle(single(hostile), SettlementInput.periodic(c));
        assertTrue(after.nation("n0").orElseThrow().atWarWithParty(), "够恨就该宣战");
        assertTrue(has(after, EventTypes.WAR_DECLARED));
    }

    @Test
    void raidIsEmittedOnlyWhenAtWarWithThePlayer() {
        Nation hostile = nation("n0", 6, 120.0, 50.0, 40.0)
                .withAttitudeToParty(-80.0).withPartyStatus(Nation.PartyStatus.WAR, 0L);
        SettlementConfig c = cfg(0.0, 12.0, 40.0, 50.0, 1.0);      // 袭击概率 1
        WorldState after = engine(c).settle(single(hostile), SettlementInput.periodic(c));
        assertTrue(has(after, EventTypes.RAID), "已宣战 + 概率 1 → 必须有袭击：" + after.events());
        Event raid = after.events().stream()
                .filter(e -> e.type().equals(EventTypes.RAID)).findFirst().orElseThrow();
        assertEquals(Event.PARTY_PLAYER, raid.parties().get(0));
        assertEquals("6", raid.params().get(1), "袭击规模 = 2 + floor(120/25) = 6");
    }

    @Test
    void peacefulNationNeverRaids() {
        SettlementConfig c = cfg(0.0, 12.0, 40.0, 50.0, 1.0);
        WorldState after = engine(c).settle(
                single(nation("n0", 6, 120.0, 0.0, 40.0).withAttitudeToParty(50.0)),
                SettlementInput.periodic(c));
        assertFalse(has(after, EventTypes.RAID), "没宣战就不会来打你：" + after.events());
    }

    @Test
    void raidScaleFollowsTheFormulaAndTheCap() {
        SettlementEngine e = engine(SettlementConfig.defaults());
        assertEquals(2, e.raidScale(nation("n0", 1, 0.0, 0.0, 50.0)));
        assertEquals(6, e.raidScale(nation("n0", 1, 120.0, 0.0, 50.0)));
        assertEquals(24, e.raidScale(nation("n0", 1, 100000.0, 0.0, 50.0)), "上限 24");
    }

    // ---- §9.4 国书：到期与承诺要在结算里真的跑起来 ----

    /**
     * 国书到期必须由**结算**推动，而不是只能靠手调 {@code LetterMachine}。
     *
     * <p>这条覆盖的是"接线"：{@code expireOverdue} 用的是**推进之后**的 seq
     * （期限 3 的国书该在把 seq 推到 4 的那一拍过期）。接错成旧 seq 的话，
     * 国书会白白多活一格，玩家能在期限外继续回复——这里就会红。
     */
    @Test
    void settlementExpiresOverdueLettersAndKeepsTheEvent() {
        SettlementConfig c = SettlementConfig.defaults();
        LetterMachine letters = LetterMachine.defaults();
        WorldState s = letters.propose(single(nation("n0", 6, 0.0, 0.0, 50.0)),
                "n0", LetterKind.TRIBUTE, "", 0.0, 120.0);
        long deadline = s.letters().get(0).deadlineSeq();

        SettlementEngine e = new SettlementEngine(c, StatecraftConfig.defaults(), sixEras(),
                letters);
        WorldState ticked = s;
        while (ticked.seq() <= deadline) {
            ticked = e.settle(ticked, SettlementInput.periodic(c));
        }

        assertEquals(LetterState.EXPIRED, ticked.letters().get(0).state(),
                "过了期限还没回 = 拒绝（§9.4）");
        assertTrue(ticked.events().stream().anyMatch(
                        ev -> "letter_expired".equals(ev.textKey())),
                "到期要留下事件，情报册才有东西可显示：" + ticked.events());
        assertTrue(ticked.nation("n0").orElseThrow().attitudeToParty()
                        < s.nation("n0").orElseThrow().attitudeToParty(),
                "忽略掉的态度要比原来低");
    }

    /** 承诺在结算里自动了结（清了账就不会被反复追责）。 */
    @Test
    void settlementFulfilsAMaturedPromise() {
        SettlementConfig c = SettlementConfig.defaults();
        LetterMachine letters = LetterMachine.defaults();
        WorldState s = letters.propose(single(nation("n0", 6, 0.0, 0.0, 50.0)),
                "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0);
        s = letters.answer(s, s.letters().get(0).id(), true).state();
        long until = s.letters().get(0).promiseUntilSeq();

        SettlementEngine e = new SettlementEngine(c, StatecraftConfig.defaults(), sixEras(),
                letters);
        WorldState ticked = s;
        while (ticked.seq() < until) {
            ticked = e.settle(ticked, SettlementInput.periodic(c));
        }
        ticked = e.settle(ticked, SettlementInput.periodic(c));

        assertEquals(0L, ticked.letters().get(0).promiseUntilSeq(), "履约后要清账");
        assertTrue(ticked.events().stream().anyMatch(ev -> "letter_kept".equals(ev.textKey())),
                "履约要留下事件：" + ticked.events());
    }

    // ---- 确定性 ----

    /**
     * 同 seed + 同一串输入 → 逐字段相同。
     *
     * <p>这条是"崩溃后从上一个完整结算重放"的地基（计划 3 Task 4），
     * 也是所有平衡结论能复现的前提。所以跑 40 个 seed、每个 6 个时代。
     */
    @Test
    void sameSeedAndInputsReplayIdentically() {
        SettlementConfig c = SettlementConfig.defaults();
        EraTable eras = sixEras();
        for (long seed = 0; seed < 40; seed++) {
            WorldState start = new WorldGenerator(StatecraftConfig.defaults(), seed)
                    .generate(CULTURES, 0.0, 0.0, "landing", 0);
            assertEquals(replay(start, c, eras), replay(start, c, eras), "seed " + seed + " 重放不一致");
        }
    }

    /** 6 个时代，每个时代里两次小结算 + 一次时代推进。 */
    private static WorldState replay(WorldState start, SettlementConfig c, EraTable eras) {
        SettlementEngine e = new SettlementEngine(c, StatecraftConfig.defaults(), eras);
        WorldState s = start;
        for (int ordinal = 0; ordinal < eras.size(); ordinal++) {
            s = e.settle(s, SettlementInput.periodic(c));
            s = e.settle(s, SettlementInput.periodic(c));
            s = e.settle(s, SettlementInput.eraAdvance(eras.byOrdinal(ordinal).durationHours()));
        }
        return s;
    }

    /** 不同 seed 必须走出不同的历史（否则"确定性"就成了"全都一样"）。 */
    @Test
    void differentSeedsDiverge() {
        SettlementConfig c = SettlementConfig.defaults();
        EraTable eras = sixEras();
        WorldState a = replay(new WorldGenerator(StatecraftConfig.defaults(), 11L)
                .generate(CULTURES, 0.0, 0.0, "landing", 0), c, eras);
        WorldState b = replay(new WorldGenerator(StatecraftConfig.defaults(), 12L)
                .generate(CULTURES, 0.0, 0.0, "landing", 0), c, eras);
        assertNotEquals(a.nations(), b.nations());
    }

    /** 长跑不炸、世界不会被推平、事件表不会无限长。 */
    @Test
    void sixErasLeaveAWorldStanding() {
        SettlementConfig c = SettlementConfig.defaults();
        EraTable eras = sixEras();
        for (long seed = 0; seed < 12; seed++) {
            WorldState start = new WorldGenerator(StatecraftConfig.defaults(), seed)
                    .generate(CULTURES, 0.0, 0.0, "landing", 0);
            WorldState end = replay(start, c, eras);
            long living = end.livingNations().size();
            assertTrue(living >= 1, "seed " + seed + " 把世界打空了");
            assertTrue(living <= start.nations().size());
            assertTrue(end.events().size() <= WorldState.MAX_EVENTS);
            assertEquals(WorldState.CURRENT_SCHEMA_VERSION, end.schemaVersion());
            assertEquals(eras.size() * 3L, end.seq(), "每个时代 3 次结算");
        }
    }

    /**
     * 设计里唯一的防雪球机制是"吸收 → 规模变大 → 发展度按反比下降"外加扩张疲劳。
     *
     * <p>刻意**不**断言一个"最大国不超过 X"的数值阈值：那个数是试玩里调出来的，
     * 现在挑一个数字写进断言只会变成一条假的"已验证"。这里钉的是机制本身。
     */
    @Test
    void absorbersAlwaysLoseDevelopment() {
        SettlementConfig c = SettlementConfig.defaults();
        EraTable eras = sixEras();
        boolean sawAbsorber = false;
        for (long seed = 0; seed < 12; seed++) {
            WorldState start = new WorldGenerator(StatecraftConfig.defaults(), seed)
                    .generate(CULTURES, 0.0, 0.0, "landing", 0);
            WorldState end = replay(start, c, eras);
            for (Nation before : start.nations()) {
                Nation after = end.nation(before.id()).orElseThrow();
                if (after.absorbedCount() == 0) {
                    continue;
                }
                sawAbsorber = true;
                assertTrue(after.development() <= before.development(),
                        "吸收过别人的国家发展度不该反而更高：" + before.development()
                                + " → " + after.development());
                assertTrue(after.size() >= before.size());
            }
        }
        assertTrue(sawAbsorber, "前提：默认参数下 12 个 seed × 6 个时代里应该出现过吸收，"
                + "否则这条用例是空跑");
    }

    /**
     * **灭亡分支必须真的会触发**。
     *
     * <p>这条是从测量里长出来的绊线：2026-09-19 用调参探针量到过
     * "默认参数 + 20 seed × 6 时代 → 灭亡 0 次"，原因是 §八.4 的 {@code dev < 25}
     * 在 §六 的生成公式下**不可达**（规模到 10 的国家发展度恒在 [33,49]）。
     * 把阈值抬到 40 之后才有灭亡。这里钉住"默认参数下确实出现灭亡"，
     * 免得以后有人把开战概率调低、或把阈值改回 25，让这条分支又悄悄变成死代码。
     */
    @Test
    void collapseActuallyHappensWithDefaultParameters() {
        SettlementConfig c = SettlementConfig.defaults();
        EraTable eras = sixEras();
        int collapsed = 0;
        for (long seed = 0; seed < 20; seed++) {
            WorldState start = new WorldGenerator(StatecraftConfig.defaults(), seed)
                    .generate(CULTURES, 0.0, 0.0, "landing", 0);
            collapsed += count(replay(start, c, eras), EventTypes.COLLAPSE);
        }
        // 确定性：这 20 个 seed 是固定的，所以这个数字不会抖
        assertTrue(collapsed > 0, "默认参数下 20 seed × 6 时代一次灭亡都没有 —— "
                + "多半是灭亡条件又变得不可达了（历史上踩过：dev<25 永远不成立）");
    }

    /**
     * 把"为什么灭亡阈值是 40 而不是文档写的 25"钉成一条可执行的算术。
     *
     * <p>{@code dev = clamp(20, 95, 95 − 6×(size−1) + jitter)}，代入 size=10 得
     * {@code 41 + jitter}，而抖动范围是 ±8 → **规模 10 的国家发展度恒在 [33,49]**。
     * 吸收只让规模变大，规模封顶 10 之后发展度就不再降，所以全世界的 dev 下限是 33。
     * 如果以后有人改了 {@code devBase}/{@code devPerSize}，这条会先红，逼他重新算灭亡阈值。
     */
    @Test
    void developmentFloorForSizeTenLocksTheCollapseThreshold() {
        StatecraftConfig gen = StatecraftConfig.defaults();
        assertEquals(33.0, WorldGenerator.developmentFor(10, -gen.devJitter(), gen), 0.0);
        assertEquals(49.0, WorldGenerator.developmentFor(10, gen.devJitter(), gen), 0.0);
        assertTrue(SettlementConfig.defaults().collapseMaxDevelopment() > 33.0,
                "灭亡的 dev 阈值必须高过 33，否则 §八.4 永远不成立");
    }

    @Test
    void configRejectsACollapseThresholdThatWouldMakeCollapseUnreachable() {
        SettlementConfig d = SettlementConfig.defaults();
        // collapse >= absorption：被打垮的弱国在够到灭亡线之前先被吸收，灭亡分支成死代码
        assertThrows(IllegalStateException.class, () -> new SettlementConfig(
                d.periodicHours(), d.militaryRecoveryPerSize(), d.fatigueDecayPerPeriod(),
                d.fatiguePowerFactor(), d.fatigueWarChanceFactor(),
                d.attitudeRegressionMin(), d.attitudeRegressionMax(), d.warChanceBase(),
                d.warDeclarationAttitude(), d.warDeclarationStance(), d.raidChance(),
                d.raidScaleBase(), d.raidScaleDivisor(), d.raidScaleCap(), d.warScoreStep(),
                60.0, d.collapseMaxDevelopment(), d.collapseLosingStreak(),
                50.0, d.absorptionSizeFactor(), d.absorptionSizeCap(),
                d.truceCooldownSettlements(), d.maxTransactionsPerSettle()));
    }
}
