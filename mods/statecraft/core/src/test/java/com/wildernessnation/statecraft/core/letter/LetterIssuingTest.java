package com.wildernessnation.statecraft.core.letter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "什么时候该有人递国书"（§9.4 的触发侧）。
 *
 * <p>这一层是 2026-09-20 接线时发现的空洞：{@link LetterMachine} 只管"递出之后怎么走"，
 * **没有任何地方会调用它** —— 也就是说 §9.4 的国书在游戏里永远不会出现。
 * 所以这些用例钉的是"国书真的会出现、而且不会刷屏"。
 */
class LetterIssuingTest {

    private static final LetterConfig CFG = LetterConfig.defaults();
    private static final LetterIssuing ISSUING = new LetterIssuing(CFG);

    private static Nation nation(String id, Nation.PartyStatus status, double attitude,
            double warScore) {
        return new Nation(id, id, "bandit", 5, 40.0, 30.0, 20.0, 50.0, 0.0, 0.0,
                true, attitude, Nation.Status.ALIVE, status, 0L, warScore, 0.0, 0);
    }

    private static WorldState state(long seq, Nation... nations) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, seq,
                List.of(nations), List.of(), List.of(), List.of(), List.of(), 0.0);
    }

    // ---- 四档判断 ----

    @Test
    void anUnmetNationNeverSendsAnything() {
        Nation unmet = new Nation("n0", "赤沙", "bandit", 5, 40.0, 30.0, 20.0, 50.0, 0.0, 0.0,
                false, -80.0, Nation.Status.ALIVE, Nation.PartyStatus.NEUTRAL, 0L, 0.0, 0.0, 0);
        assertTrue(ISSUING.consider(state(3L, unmet), unmet).isEmpty(),
                "没接触过就不该收到它的国书（你甚至不知道它存在）");
    }

    @Test
    void aDeadNationSendsNothing() {
        Nation dead = nation("n0", Nation.PartyStatus.NEUTRAL, -80.0, 0.0)
                .withStatus(Nation.Status.DEAD);
        assertTrue(ISSUING.consider(state(3L, dead), dead).isEmpty());
    }

    @Test
    void aVeryAngryNationAsksYouToStopBuilding() {
        Nation angry = nation("n0", Nation.PartyStatus.NEUTRAL,
                CFG.issueAttitudeThreshold() - 1, 0.0);
        LetterIssuing.Proposal p = ISSUING.consider(state(3L, angry), angry).orElseThrow();
        assertEquals(LetterKind.STOP_BUILDING, p.kind());
        assertTrue(p.targetNationId().isEmpty(), "停止建造不带目标国");
        assertTrue(p.reason().contains("态度"), p.reason());
    }

    /** 态度还没低到门槛就不该来要东西（不然每国每拍一封）。 */
    @Test
    void aMildlyAnnoyedNationStaysQuiet() {
        Nation mild = nation("n0", Nation.PartyStatus.NEUTRAL,
                CFG.issueAttitudeThreshold() + 1, 0.0);
        assertTrue(ISSUING.consider(state(3L, mild), mild).isEmpty());
    }

    @Test
    void aWinningEnemyDemandsTribute() {
        Nation winning = nation("n0", Nation.PartyStatus.WAR, -60.0, -20.0);
        LetterIssuing.Proposal p = ISSUING.consider(state(3L, winning), winning).orElseThrow();
        assertEquals(LetterKind.TRIBUTE, p.kind(), "它占优（战果为负）才会来索贡");
        assertTrue(p.reason().contains("占优"), p.reason());
    }

    /** 打输的一方不来要东西 —— 那时候它该求和，不是索贡。 */
    @Test
    void aLosingEnemyDoesNotDemandTribute() {
        Nation losing = nation("n0", Nation.PartyStatus.WAR, -60.0, 20.0);
        assertTrue(ISSUING.consider(state(3L, losing), losing).isEmpty());
    }

    @Test
    void aFriendlyNationMayInviteYouToAJointWar() {
        Nation asker = nation("n0", Nation.PartyStatus.NEUTRAL, 10.0, 0.0);
        Nation enemy = nation("n1", Nation.PartyStatus.NEUTRAL, 0.0, 0.0);
        WorldState s = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(asker, enemy),
                List.of(new Relation("n0", "n1", CFG.jointWarTargetAttitude() - 1, Relation.State.PEACE,
                        0.0, 0L, 0)),
                List.of(), List.of(), List.of(), 0.0);

        LetterIssuing.Proposal p = ISSUING.consider(s, asker).orElseThrow();
        assertEquals(LetterKind.JOINT_WAR, p.kind());
        assertEquals("n1", p.targetNationId(), "共同讨伐必须带上目标国");
    }

    /** 它自己跟你关系就不好时不会来拉你入伙（"不讨厌你"是前置）。 */
    @Test
    void aNationThatDislikesYouDoesNotInviteYou() {
        Nation asker = nation("n0", Nation.PartyStatus.NEUTRAL, -5.0, 0.0);   // 低于 jointWarMinAttitude
        Nation enemy = nation("n1", Nation.PartyStatus.NEUTRAL, 0.0, 0.0);
        WorldState s = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(asker, enemy),
                List.of(new Relation("n0", "n1", -80.0, Relation.State.PEACE, 0.0, 0L, 0)),
                List.of(), List.of(), List.of(), 0.0);
        assertTrue(ISSUING.consider(s, asker).isEmpty());
    }

    /** 已经是同盟的邻居不算"仇家"。 */
    @Test
    void anAllyIsNotAJointWarTarget() {
        Nation asker = nation("n0", Nation.PartyStatus.NEUTRAL, 10.0, 0.0);
        Nation ally = nation("n1", Nation.PartyStatus.NEUTRAL, 0.0, 0.0);
        WorldState s = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(asker, ally),
                List.of(new Relation("n0", "n1", -80.0, Relation.State.ALLIANCE, 0.0, 0L, 0)),
                List.of(), List.of(), List.of(), 0.0);
        assertTrue(ISSUING.consider(s, asker).isEmpty());
    }

    /** 已经没了的目标国不能被拉进共同讨伐。 */
    @Test
    void aDeadTargetIsNotProposed() {
        Nation asker = nation("n0", Nation.PartyStatus.NEUTRAL, 10.0, 0.0);
        Nation dead = nation("n1", Nation.PartyStatus.NEUTRAL, 0.0, 0.0)
                .withStatus(Nation.Status.DEAD);
        WorldState s = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(asker, dead),
                List.of(new Relation("n0", "n1", -80.0, Relation.State.PEACE, 0.0, 0L, 0)),
                List.of(), List.of(), List.of(), 0.0);
        assertTrue(ISSUING.consider(s, asker).isEmpty());
    }

    // ---- 不许刷屏 ----

    @Test
    void anOpenLetterBlocksANewOne() {
        Nation angry = nation("n0", Nation.PartyStatus.NEUTRAL, -80.0, 0.0);
        Letter open = new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                1L, 9L, LetterState.OPEN, 0L);
        WorldState s = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(angry), List.of(), List.of(), List.of(open), List.of(), 0.0);
        assertTrue(ISSUING.consider(s, angry).isEmpty(), "手上还有一封待回的就别再递");
    }

    @Test
    void aRecentLetterBlocksANewOneUntilTheCooldownPasses() {
        Nation angry = nation("n0", Nation.PartyStatus.NEUTRAL, -80.0, 0.0);
        Letter answered = new Letter("L1", "n0", LetterKind.STOP_BUILDING, "", 64.0, 0.0,
                1L, 4L, LetterState.REFUSED, 0L);

        WorldState fresh = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0,
                2L, List.of(angry), List.of(), List.of(), List.of(answered), List.of(), 0.0);
        assertTrue(ISSUING.consider(fresh, angry).isEmpty(), "刚拒绝完不该立刻又来一封");

        long past = 1L + CFG.issueCooldownSettlements();
        WorldState later = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0,
                past, List.of(angry), List.of(), List.of(), List.of(answered), List.of(), 0.0);
        assertFalse(ISSUING.consider(later, angry).isEmpty(), "冷却过了就可以再来");
    }

    /** 顺序确定：同一个世界状态，问两次得到同一个结论。 */
    @Test
    void theDecisionIsDeterministic() {
        Nation asker = nation("n0", Nation.PartyStatus.NEUTRAL, 10.0, 0.0);
        Nation enemy = nation("n1", Nation.PartyStatus.NEUTRAL, 0.0, 0.0);
        WorldState s = new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 3L,
                List.of(asker, enemy),
                List.of(new Relation("n0", "n1", -80.0, Relation.State.PEACE, 0.0, 0L, 0)),
                List.of(), List.of(), List.of(), 0.0);
        assertEquals(ISSUING.consider(s, asker), ISSUING.consider(s, asker));
    }

    // ---- 配置与默认值 ----

    @Test
    void theDefaultIssuingNumbersAreCoherent() {
        assertTrue(CFG.issueAttitudeThreshold() < 0, "总得有点不满才来提要求");
        assertTrue(CFG.issueCooldownSettlements() > 0, "没有冷却就会刷屏");
        assertTrue(CFG.maxIssuesPerSettlement() >= 1, "一封都不递等于这个功能不存在");
        assertTrue(CFG.jointWarTargetAttitude() < CFG.jointWarMinAttitude(),
                "对第三国的恨必须比对你的态度更狠，才谈得上拉你入伙");
    }

    @Test
    void configRejectsAnIssuingThresholdThatIsTooSoft() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LetterConfig(3L, -5.0, -8.0, 10.0, 10.0, 5.0, 6L, 10.0, -25.0,
                        10.0, 4L, 1L, 0.0, -50.0));
        assertTrue(e.getMessage().contains("issueAttitudeThreshold"), e.getMessage());
    }

    @Test
    void defaultsDeriveTheRightNumbersPerKind() {
        LetterDefaults d = new LetterDefaults(64.0, 120.0);
        assertEquals(64.0, d.radiusFor(LetterKind.STOP_BUILDING), 0.0);
        assertEquals(0.0, d.radiusFor(LetterKind.TRIBUTE), 0.0, "只有停止建造有半径");
        assertEquals(120.0, d.amountFor(LetterKind.TRIBUTE), 0.0);
        assertEquals(0.0, d.amountFor(LetterKind.JOINT_WAR), 0.0, "只有朝贡要东西");
        assertThrows(IllegalArgumentException.class, () -> new LetterDefaults(0.0, 120.0));
        assertThrows(IllegalArgumentException.class, () -> new LetterDefaults(64.0, 0.0));
    }

    @Test
    void aProposalValidatesItsOwnShape() {
        assertThrows(IllegalArgumentException.class,
                () -> new LetterIssuing.Proposal(LetterKind.JOINT_WAR, "", "拉你入伙"));
        assertThrows(IllegalArgumentException.class,
                () -> new LetterIssuing.Proposal(LetterKind.TRIBUTE, "n1", "索贡"));
        assertThrows(IllegalArgumentException.class,
                () -> new LetterIssuing.Proposal(null, "", "理由"));
        assertThrows(IllegalArgumentException.class,
                () -> new LetterIssuing.Proposal(LetterKind.TRIBUTE, "", " "));
    }
}
