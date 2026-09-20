package com.wildernessnation.statecraft.core.letter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.diplomacy.DiplomacyConfig;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 国书状态机（§9.4）的测试。
 *
 * <p>三件事必须被钉住：
 * <ol>
 *   <li><strong>拒绝不动状态</strong>——和外交动作同一条纪律，用 {@code assertSame} 写死；</li>
 *   <li><strong>三种国书的后果各不相同</strong>——朝贡掏钱换太平、共同讨伐真的开战、
 *       停止建造立下会到期的承诺。写成"接受就 +态度"那种统一处理，这个功能就白做了；</li>
 *   <li><strong>承诺会了结</strong>——履约 +10、破约 −25、过期作废，三条都不能留下"永远有效"的尾巴。</li>
 * </ol>
 */
class LetterMachineTest {

    private static final LetterConfig CFG = LetterConfig.defaults();

    private static LetterMachine machine() {
        return new LetterMachine(CFG, DiplomacyConfig.defaults());
    }

    private static Nation nation(String id, String name, double attitude) {
        return new Nation(id, name, "bandit", 5, 40.0, 0.0, 20.0, 50.0, 0.0, 0.0,
                true, attitude, Nation.Status.ALIVE, Nation.PartyStatus.NEUTRAL, 0L, 0.0, 0.0, 0);
    }

    /** 两个国家 + 一段和平关系，seq=3（国书的期限从这一拍往后数）。 */
    private static WorldState state(double attitude) {
        Nation a = nation("n0", "赤沙", attitude);
        Nation b = nation("n1", "白岩", 0.0);
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(a, b), List.of(Relation.peace("n0", "n1")), List.of(),
                List.of(), List.of(), 0.0);
    }

    private static double attitude(WorldState s, String id) {
        return s.nation(id).orElseThrow().attitudeToParty();
    }

    private static Letter only(WorldState s) {
        assertEquals(1, s.letters().size(), "这一步应该正好有一封国书");
        return s.letters().get(0);
    }

    /** 递一封该种类的国书（要价/半径/目标国都按数据文件里的默认值），返回新状态。 */
    private static WorldState proposeOnly(WorldState s, LetterKind kind) {
        double radius = kind == LetterKind.STOP_BUILDING ? 64.0 : 0.0;
        double amount = kind == LetterKind.TRIBUTE ? 120.0 : 0.0;
        String target = kind == LetterKind.JOINT_WAR ? "n1" : "";
        return machine().propose(s, "n0", kind, target, radius, amount);
    }

    // ---- 递出 ----

    @Test
    void proposingSetsAnOpenLetterAndADeadline() {
        WorldState s = machine().propose(state(0.0), "n0", LetterKind.STOP_BUILDING,
                "", 64.0, 0.0);
        Letter l = only(s);
        assertEquals(LetterState.OPEN, l.state());
        assertEquals(3L, l.issuedAtSeq());
        assertEquals(3L + CFG.deadlineSettlements(), l.deadlineSeq(), "期限 = 递出序号 + 配置值");
        assertEquals(0L, l.promiseUntilSeq(), "还没接受，谈不上承诺");
        assertEquals("L3-n0-STOP_BUILDING", l.id());
        assertTrue(l.overdueAt(3L + CFG.deadlineSettlements() + 1), "过了期限才算逾期");

        Event e = s.events().get(s.events().size() - 1);
        assertEquals(EventTypes.LETTER, e.type(), "国书要留一个事件，情报册才有东西可显示");
        assertEquals("letter_stop_building", e.textKey());
        assertEquals(List.of("赤沙", "64"), e.params(), "半径取整数，不写成 64.0");
        assertTrue(e.visible());
        assertFalse(e.read(), "刚递来的国书是未读的");
    }

    /** 同一个国家可以先后递两封同类国书，但 id 不能撞（撞了就是静默覆盖历史）。 */
    @Test
    void aSecondLetterOfTheSameKindGetsItsOwnId() {
        WorldState s = state(0.0);
        s = machine().propose(s, "n0", LetterKind.TRIBUTE, "", 0.0, 120.0);
        s = machine().propose(s, "n0", LetterKind.TRIBUTE, "", 0.0, 200.0);
        assertEquals(2, s.letters().size());
        assertNotEquals(s.letters().get(0).id(), s.letters().get(1).id());
        assertEquals(List.of(LetterState.OPEN, LetterState.OPEN), LetterMachine.openLetters(s)
                .stream().map(Letter::state).toList());
    }

    @Test
    void proposingRejectsUnknownAndDeadNations() {
        WorldState s = state(0.0);
        assertThrows(IllegalArgumentException.class,
                () -> machine().propose(s, "n9", LetterKind.TRIBUTE, "", 0.0, 120.0));

        WorldState dead = s.withNation(s.nation("n0").orElseThrow()
                .withStatus(Nation.Status.DEAD));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> machine().propose(dead, "n0", LetterKind.TRIBUTE, "", 0.0, 120.0));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }

    // ---- 回复：前置被违反必须拒绝，而且状态一个字都不能动 ----

    @Test
    void answeringAnUnknownLetterThrows() {
        WorldState s = state(0.0);
        assertThrows(IllegalArgumentException.class, () -> machine().answer(s, "L404", true));
    }

    @Test
    void answeringTwiceIsRefusedWithoutTouchingTheState() {
        WorldState s = proposeOnly(state(0.0), LetterKind.TRIBUTE);
        WorldState answered = machine().answer(s, s.letters().get(0).id(), true).state();

        LetterResult again = machine().answer(answered, answered.letters().get(0).id(), true);
        assertFalse(again.accepted());
        assertSame(answered, again.state(), "重复回复必须原样返回（一个字都不能动）");
        assertTrue(again.message().contains("已经回过"), again.message());
    }

    @Test
    void refusingOnlyCostsAttitude() {
        WorldState s = proposeOnly(state(0.0), LetterKind.TRIBUTE);
        LetterResult r = machine().answer(s, s.letters().get(0).id(), false);

        assertTrue(r.accepted());
        assertEquals(LetterState.REFUSED, only(r.state()).state());
        assertEquals(CFG.refusalAttitudePenalty(), attitude(r.state(), "n0"), 1e-9);
        assertEquals(0.0, r.playerTreasuryDelta(), 1e-9, "拒绝不该掏钱");
        assertEquals(50.0, r.state().nation("n0").orElseThrow().treasury(), 1e-9,
                "拒绝不该动它的国库");
        assertEquals(Nation.PartyStatus.NEUTRAL,
                r.state().nation("n0").orElseThrow().partyStatus(), "拒绝不给太平");
    }

    // ---- 接受：三种国书的后果各不相同 ----

    @Test
    void acceptingTributePaysAndBuysNonAggression() {
        WorldState s = proposeOnly(state(0.0), LetterKind.TRIBUTE);
        LetterResult r = machine().answer(s, s.letters().get(0).id(), true);

        assertTrue(r.accepted());
        assertEquals(LetterState.ACCEPTED, only(r.state()).state());
        assertEquals(-120.0, r.playerTreasuryDelta(), 1e-9, "玩家要掏的数目必须报出来");
        assertEquals(170.0, r.state().nation("n0").orElseThrow().treasury(), 1e-9,
                "贡品进的是它的国库");
        assertEquals(CFG.tributeAcceptAttitudeGain(), attitude(r.state(), "n0"), 1e-9);

        Nation n = r.state().nation("n0").orElseThrow();
        assertEquals(Nation.PartyStatus.TRUCE, n.partyStatus(), "朝贡换的就是不侵犯");
        assertEquals(3L + DiplomacyConfig.defaults().tributeNonAggressionSettlements(),
                n.partyStatusUntilSeq(),
                "承诺长度借用 §11.2 朝贡那一个数，不在这里另立一份");
        assertTrue(n.truceHoldsAt(4L));
    }

    @Test
    void acceptingJointWarActuallyStartsTheWar() {
        WorldState s = proposeOnly(state(0.0), LetterKind.JOINT_WAR);
        LetterResult r = machine().answer(s, s.letters().get(0).id(), true);

        assertTrue(r.accepted());
        assertEquals(CFG.jointWarAttitudeGain(), attitude(r.state(), "n0"), 1e-9);
        assertTrue(r.state().nation("n1").orElseThrow().atWarWithParty(),
                "目标国必须真的和玩家进入战争——不然这个动作只是嘴上答应");
        assertEquals(Relation.State.WAR,
                r.state().relation("n0", "n1").orElseThrow().state(),
                "要求方自己也得跟目标国开战");
        assertEquals(0.0, r.state().relation("n0", "n1").orElseThrow().warScore(), 1e-9);
    }

    /** 已经在跟目标国打的情况：不重复开战，但**照样给态度**（你确实答应了）。 */
    @Test
    void acceptingJointWarAgainstAnExistingEnemyDoesNotResetTheWarScore() {
        WorldState s = state(0.0);
        s = s.withNation(s.nation("n1").orElseThrow()
                        .withPartyStatus(Nation.PartyStatus.WAR, 0L)
                        .withPartyWarScore(35.0))
                .withRelations(List.of(Relation.peace("n0", "n1")
                        .withState(Relation.State.WAR).withWarScore(35.0)));
        s = proposeOnly(s, LetterKind.JOINT_WAR);
        LetterResult r = machine().answer(s, s.letters().get(0).id(), true);

        assertEquals(35.0, r.state().nation("n1").orElseThrow().partyWarScore(), 1e-9,
                "已经打了一半的战果不能被清零");
        assertEquals(35.0, r.state().relation("n0", "n1").orElseThrow().warScore(), 1e-9);
        assertEquals(CFG.jointWarAttitudeGain(), attitude(r.state(), "n0"), 1e-9);
    }

    /** 目标国已经没了：拒绝，而且**状态一个字不动**（这封国书没有意义了）。 */
    @Test
    void acceptingJointWarAgainstADeadNationIsRefused() {
        WorldState s = state(0.0);
        s = s.withNation(s.nation("n1").orElseThrow().withStatus(Nation.Status.DEAD));
        s = proposeOnly(s, LetterKind.JOINT_WAR);
        LetterResult r = machine().answer(s, s.letters().get(0).id(), true);
        assertFalse(r.accepted());
        assertSame(s, r.state());
    }

    @Test
    void acceptingStopBuildingCreatesARealPromiseWindow() {
        WorldState s = proposeOnly(state(0.0), LetterKind.STOP_BUILDING);
        LetterResult r = machine().answer(s, s.letters().get(0).id(), true);

        assertTrue(r.accepted());
        Letter l = only(r.state());
        assertEquals(LetterState.ACCEPTED, l.state());
        assertEquals(3L + CFG.stopBuildPromiseSettlements(), l.promiseUntilSeq());
        assertTrue(l.promiseInForceAt(4L), "刚答应的承诺必须马上生效");
        assertFalse(l.promiseFulfilledAt(4L));
        assertEquals(CFG.stopBuildAcceptAttitudeGain(), attitude(r.state(), "n0"), 1e-9,
                "先给一点面子，履约的 +10 要等期限到");
    }

    // ---- 承诺：履约 / 破约 / 了结 ----

    @Test
    void keepingThePromisePaysTheCompletionBonusOnce() {
        WorldState s = proposeOnly(state(0.0), LetterKind.STOP_BUILDING);
        s = machine().answer(s, s.letters().get(0).id(), true).state();

        long until = only(s).promiseUntilSeq();
        WorldState beforeDue = machine().settlePledges(s, until - 1);
        assertSame(s, beforeDue, "还没到点就不该有任何变化");
        assertEquals(CFG.stopBuildAcceptAttitudeGain(), attitude(beforeDue, "n0"), 1e-9);

        WorldState atDue = machine().settlePledges(s, until);
        assertEquals(CFG.stopBuildAcceptAttitudeGain() + CFG.stopBuildKeptAttitudeGain(),
                attitude(atDue, "n0"), 1e-9,
                "§十二的「完成国书 +10」是在答应那一笔之上再给的");
        assertEquals(0L, only(atDue).promiseUntilSeq(), "履约后承诺要清账");
        assertEquals(LetterState.ACCEPTED, only(atDue).state());
        assertEquals("letter_kept", atDue.events().get(atDue.events().size() - 1).textKey());

        // 再跑一次不能重复给分（这是"清账"最实际的用处）
        WorldState again = machine().settlePledges(atDue, until + 5);
        assertSame(atDue, again, "承诺清了就不该再被结算一次");
    }

    @Test
    void buildingInsideTheRadiusBreaksThePromise() {
        WorldState s = proposeOnly(state(0.0), LetterKind.STOP_BUILDING);
        s = machine().answer(s, s.letters().get(0).id(), true).state();
        long until = only(s).promiseUntilSeq();

        WorldState inWindow = s.withClock(0.0, "landing", 0, until - 1);
        LetterResult r = machine().reportBuildingNear(inWindow, "n0", 63.0);

        assertTrue(r.accepted(), "在半径内动土就是破约");
        assertEquals(LetterState.BREACHED, only(r.state()).state());
        assertEquals(0L, only(r.state()).promiseUntilSeq());
        assertEquals(CFG.stopBuildAcceptAttitudeGain() + CFG.stopBuildBreachAttitudePenalty(),
                attitude(r.state(), "n0"), 1e-9,
                "破约那一笔盖在「答应」那一笔之上");
        assertEquals("letter_breached", r.state().events().get(
                r.state().events().size() - 1).textKey());
        assertFalse(only(r.state()).promiseInForceAt(until - 1), "破约之后承诺就没了");
    }

    @Test
    void buildingOutsideTheRadiusOrAfterTheWindowIsNotABreach() {
        WorldState s = proposeOnly(state(0.0), LetterKind.STOP_BUILDING);
        s = machine().answer(s, s.letters().get(0).id(), true).state();
        long until = only(s).promiseUntilSeq();
        WorldState inWindow = s.withClock(0.0, "landing", 0, until - 1);

        LetterResult far = machine().reportBuildingNear(inWindow, "n0", 65.0);
        assertFalse(far.accepted(), "半径外不算");
        assertSame(inWindow, far.state());

        LetterResult other = machine().reportBuildingNear(inWindow, "n1", 1.0);
        assertFalse(other.accepted(), "别的国家没有对你提这个要求");
        assertSame(inWindow, other.state());

        WorldState afterWindow = inWindow.withClock(0.0, "landing", 0, until);
        LetterResult late = machine().reportBuildingNear(afterWindow, "n0", 1.0);
        assertFalse(late.accepted(), "承诺过期之后再建就不算破约");
        assertSame(afterWindow, late.state());
    }

    @Test
    void reportingRejectsNegativeDistances() {
        WorldState s = state(0.0);
        assertThrows(IllegalArgumentException.class,
                () -> machine().reportBuildingNear(s, "n0", -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> machine().reportBuildingNear(s, "n9", 1.0));
    }

    // ---- 到期（§9.4：忽略 = 拒绝） ----

    @Test
    void overdueLettersExpireAndCostAttitude() {
        WorldState s = proposeOnly(state(0.0), LetterKind.TRIBUTE);
        long deadline = only(s).deadlineSeq();

        assertSame(s, machine().expireOverdue(s, deadline), "期限那一拍还能回");
        WorldState expired = machine().expireOverdue(s, deadline + 1);

        assertEquals(LetterState.EXPIRED, only(expired).state());
        assertEquals(CFG.ignoreAttitudePenalty(), attitude(expired, "n0"), 1e-9,
                "忽略掉的态度比明着拒绝更狠");
        assertTrue(CFG.ignoreAttitudePenalty() < CFG.refusalAttitudePenalty());
        assertEquals("letter_expired",
                expired.events().get(expired.events().size() - 1).textKey());
        assertEquals(0.0, expired.nation("n0").orElseThrow().treasury() - 50.0, 1e-9);

        // 已经过期之后再结算不该重复扣
        assertSame(expired, machine().expireOverdue(expired, deadline + 2));
    }

    @Test
    void answeringAnExpiredLetterIsRefused() {
        WorldState s = proposeOnly(state(0.0), LetterKind.TRIBUTE);
        WorldState expired = machine().expireOverdue(s, only(s).deadlineSeq() + 1);
        LetterResult r = machine().answer(expired, only(expired).id(), true);
        assertFalse(r.accepted(), "过期就是拒绝，不能再接受");
        assertSame(expired, r.state());
    }

    /** 递信的国家自己没了：作废，但**不掉态度**（对一个没了的东西掉态度没意义）。 */
    @Test
    void aLetterFromADeadNationIsVoidedWithoutCost() {
        WorldState s = proposeOnly(state(0.0), LetterKind.TRIBUTE);
        WorldState dead = s.withNation(s.nation("n0").orElseThrow()
                .withStatus(Nation.Status.DEAD));
        LetterResult r = machine().answer(dead, only(dead).id(), true);

        assertTrue(r.accepted());
        assertEquals(LetterState.EXPIRED, only(r.state()).state());
        assertEquals(0.0, attitude(r.state(), "n0"), 1e-9, "不作废就别掉态度");
        assertEquals(0.0, r.playerTreasuryDelta(), 1e-9);
    }

    // ---- 确定性 ----

    /** 同一串输入必得同一段国书往来（这是事务日志重放的地基）。 */
    @Test
    void theWholeExchangeIsDeterministic() {
        WorldState s = state(0.0);
        WorldState once = machine().answer(machine().propose(s, "n0", LetterKind.STOP_BUILDING,
                "", 64.0, 0.0), "L3-n0-STOP_BUILDING", true).state();
        WorldState twice = machine().answer(machine().propose(s, "n0", LetterKind.STOP_BUILDING,
                "", 64.0, 0.0), "L3-n0-STOP_BUILDING", true).state();
        assertEquals(once, twice);

        WorldState breachedOnce = machine().reportBuildingNear(
                once.withClock(0.0, "landing", 0, 5L), "n0", 10.0).state();
        WorldState breachedTwice = machine().reportBuildingNear(
                twice.withClock(0.0, "landing", 0, 5L), "n0", 10.0).state();
        assertEquals(breachedOnce, breachedTwice);
    }

    // ---- 数据文件 ----

    @Test
    void theDefaultConfigIsCoherent() {
        assertTrue(CFG.deadlineSettlements() > 0, "国书总得给个回话的期限");
        assertTrue(CFG.stopBuildPromiseSettlements() > 0);
        assertTrue(CFG.ignoreAttitudePenalty() <= CFG.refusalAttitudePenalty(),
                "不吭声不该比明说拒绝更划算");
        assertTrue(CFG.stopBuildBreachAttitudePenalty() < CFG.stopBuildKeptAttitudeGain(),
                "破约必须比履约惨，否则最优解是先答应再照建");
        assertTrue(CFG.stopBuildKeptAttitudeGain() > 0, "§十二：完成国书 +10");
    }

    @Test
    void configRejectsAPromiseThatEndsImmediately() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LetterConfig(3L, -5.0, -8.0, 10.0, 10.0, 5.0, 0L, 10.0, -25.0));
        assertTrue(e.getMessage().contains("承诺期"), e.getMessage());
    }

    @Test
    void configRejectsABreachThatIsCheaperThanRefusing() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LetterConfig(3L, -5.0, -8.0, 10.0, 10.0, 5.0, 6L, 10.0, -1.0));
        assertTrue(e.getMessage().contains("破约"), e.getMessage());
    }

    // ---- Letter 自身的校验 ----

    @Test
    void onlyStopBuildingLettersCarryAPromise() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Letter("L1", "n0", LetterKind.TRIBUTE, "", 0.0, 120.0,
                        3L, 5L, LetterState.ACCEPTED, 9L));
        assertTrue(e.getMessage().contains("停止建造"), e.getMessage());
    }

    @Test
    void aPromiseRequiresAnAcceptedStateAndAFutureDeadline() {
        assertThrows(IllegalArgumentException.class,
                () -> new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                        3L, 5L, LetterState.OPEN, 9L));
        assertThrows(IllegalArgumentException.class,
                () -> new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                        3L, 5L, LetterState.BREACHED, 9L));
        assertThrows(IllegalArgumentException.class,
                () -> new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                        3L, 5L, LetterState.ACCEPTED, 3L), "承诺期必须晚于递出时间");
        assertThrows(IllegalArgumentException.class,
                () -> new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                        3L, 5L, LetterState.ACCEPTED, -1L));
    }

    /** 破约之后那封信不能再回到"承诺中"（状态是单向的）。 */
    @Test
    void aBreachedLetterNoLongerPromises() {
        Letter l = new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                3L, 5L, LetterState.BREACHED, 0L);
        assertFalse(l.promiseInForceAt(4L));
        assertFalse(l.promiseFulfilledAt(9L));
        assertTrue(l.answered());
        assertFalse(l.overdueAt(9L), "已经有结论的信不该再被当成逾期");
    }

    @Test
    void openLettersOnlyListsTheUnansweredOnes() {
        WorldState s = state(0.0);
        s = machine().propose(s, "n0", LetterKind.TRIBUTE, "", 0.0, 120.0);
        s = machine().propose(s, "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0);
        s = machine().answer(s, s.letters().get(0).id(), false).state();

        List<Letter> open = LetterMachine.openLetters(s);
        assertEquals(1, open.size());
        assertEquals(LetterKind.STOP_BUILDING, open.get(0).kind());
        assertEquals(Optional.empty(), s.nation("n9"));
    }
}
