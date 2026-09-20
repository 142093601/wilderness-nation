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
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.persist.StateHash;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementLogTest {

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

    private static SettlementEngine engine() {
        return new SettlementEngine(
                SettlementConfig.defaults(), StatecraftConfig.defaults(), sixEras());
    }

    private static WorldState start(long seed) {
        return new WorldGenerator(StatecraftConfig.defaults(), seed)
                .generate(CULTURES, 0.0, 0.0, "landing", 0);
    }

    /** 一个"会话"：跑若干步，边跑边记日志。返回 [最终状态, 日志]。 */
    private record Session(WorldState end, SettlementLog log) {}

    private static Session run(WorldState start, SettlementEngine engine, int steps) {
        WorldState s = start;
        SettlementLog log = SettlementLog.empty();
        for (int i = 0; i < steps; i++) {
            SettlementInput input = i % 3 == 2
                    ? SettlementInput.eraAdvance(sixEras().byOrdinal(s.eraOrdinal()).durationHours())
                    : SettlementInput.periodic(SettlementConfig.defaults());
            log = log.append(s, input);
            s = engine.settle(s, input);
        }
        return new Session(s, log);
    }

    // ---- 重放 ----

    /** 核心承诺：拿同样的 seed + 日志里的输入，能把历史**一模一样**地重算出来。 */
    @Test
    void replayReproducesTheSameHistory() {
        SettlementEngine engine = engine();
        for (long seed = 0; seed < 8; seed++) {
            WorldState initial = start(seed);
            Session session = run(initial, engine, 18);

            ReplayResult replayed = session.log().replay(initial, engine);
            assertTrue(replayed.ok(), "seed " + seed + " 重放失败：" + replayed.describe());
            assertEquals(18, replayed.stepsApplied());
            assertEquals(session.end(), replayed.state(), "seed " + seed + " 重放结果不一致");
            assertEquals(StateHash.of(session.end()), StateHash.of(replayed.state()));
        }
    }

    /** 「从上一个完整结算重放」：拿中途的快照 + 之后的日志，一样能接上。 */
    @Test
    void replayFromAMidwaySnapshotAlsoWorks() {
        SettlementEngine engine = engine();
        WorldState initial = start(5L);
        SettlementLog log = SettlementLog.empty();
        WorldState s = initial;
        WorldState snapshot = null;
        for (int i = 0; i < 15; i++) {
            if (i == 10) {
                snapshot = s;
            }
            SettlementInput input = SettlementInput.periodic(SettlementConfig.defaults());
            log = log.append(s, input);
            s = engine.settle(s, input);
        }
        assertTrue(snapshot != null);

        ReplayResult replayed = log.since(snapshot.seq()).replay(snapshot, engine);
        assertTrue(replayed.ok(), replayed.describe());
        assertEquals(5, replayed.stepsApplied(), "快照之后的 5 步");
        assertEquals(s, replayed.state());
    }

    @Test
    void verifyAlsoCatchesATamperedLastStep() {
        SettlementEngine engine = engine();
        WorldState initial = start(9L);
        Session session = run(initial, engine, 9);

        // 篡改**最后一条**的输入：中间步骤全都对得上，只有最终结果会不一样
        List<SettlementLogEntry> entries = new ArrayList<>(session.log().entries());
        SettlementLogEntry last = entries.get(entries.size() - 1);
        entries.set(entries.size() - 1,
                new SettlementLogEntry(last.seq(), last.trigger(),
                        last.hoursDelta() + 4.0, last.inputHash()));
        SettlementLog tampered = new SettlementLog(entries);

        assertTrue(tampered.replay(initial, engine).ok(),
                "只改最后一条时，逐步校验发现不了（这正是 replay 单独不够用的原因）");
        ReplayResult verified = tampered.verify(initial, engine, session.end());
        assertFalse(verified.ok(), "verify 必须能抓到最后一条被篡改");
        assertEquals(last.seq(), verified.divergedAtSeq().orElseThrow());
    }

    @Test
    void replayStopsWhenTheLogDoesNotBelongToThisState() {
        SettlementEngine engine = engine();
        Session session = run(start(3L), engine, 10);

        // 拿另一条世界线的状态当起点：第一步的输入哈希就对不上
        ReplayResult foreign = session.log().replay(start(4L), engine);
        assertFalse(foreign.ok());
        assertEquals(0, foreign.stepsApplied());
        assertEquals(session.log().entries().get(0).seq(), foreign.divergedAtSeq().orElseThrow());
        assertTrue(foreign.describe().contains("分歧"));
    }

    @Test
    void replayStopsAtTheFirstDivergentInputValue() {
        SettlementEngine engine = engine();
        WorldState initial = start(11L);
        Session session = run(initial, engine, 12);

        // 把第 6 步的小时数改掉：那一步本身能跑，但它算出来的状态与第 7 步记的哈希对不上
        List<SettlementLogEntry> entries = new ArrayList<>(session.log().entries());
        SettlementLogEntry six = entries.get(5);
        entries.set(5, new SettlementLogEntry(six.seq(), six.trigger(),
                six.hoursDelta() + 2.0, six.inputHash()));
        SettlementLog tampered = new SettlementLog(entries);

        ReplayResult replayed = tampered.replay(initial, engine);
        assertFalse(replayed.ok());
        // 篡改的是第 6 步：它自己跑得下去（哈希对得上），要到**第 7 步**才发现状态已经不是那条线了
        assertEquals(7, replayed.divergedAtSeq().orElseThrow(), "分歧在第 7 步才暴露");
        assertEquals(6, replayed.stepsApplied(), "第 6 步照样被应用了 —— 这正说明「只校验当前步」不够");
    }

    // ---- 日志本身 ----

    @Test
    void appendRecordsTheStateBeforeTheSettlement() {
        SettlementEngine engine = engine();
        WorldState initial = start(1L);
        SettlementLog log = SettlementLog.empty().append(initial,
                SettlementInput.periodic(SettlementConfig.defaults()));

        SettlementLogEntry entry = log.lastEntry();
        assertEquals(initial.seq() + 1, entry.seq());
        assertEquals(SettlementTrigger.PERIODIC, entry.trigger());
        assertEquals(StateHash.of(initial), entry.inputHash());

        // 再记一步，哈希必须换成"上一步之后"的状态
        WorldState after = engine.settle(initial, entry.input());
        SettlementLog two = log.append(after, SettlementInput.periodic(SettlementConfig.defaults()));
        assertEquals(StateHash.of(after), two.lastEntry().inputHash());
        assertNotEquals(entry.inputHash(), two.lastEntry().inputHash());
    }

    @Test
    void entriesMustBeStrictlyIncreasingInSeq() {
        SettlementLogEntry a = new SettlementLogEntry(5L, SettlementTrigger.PERIODIC, 2.0, 1L);
        SettlementLogEntry b = new SettlementLogEntry(5L, SettlementTrigger.PERIODIC, 2.0, 2L);
        assertThrows(IllegalArgumentException.class, () -> new SettlementLog(List.of(a, b)));
        assertThrows(IllegalArgumentException.class, () -> new SettlementLog(List.of(b, a)));
    }

    @Test
    void logRoundTripsThroughTheNeutralTree() {
        SettlementEngine engine = engine();
        Session session = run(start(2L), engine, 7);
        SettlementLog back = SettlementLog.fromNode(session.log().toNode());
        assertEquals(session.log().entries(), back.entries());

        ReplayResult replayed = back.replay(start(2L), engine);
        assertTrue(replayed.ok(), replayed.describe());
        assertEquals(session.end(), replayed.state());
    }

    @Test
    void logKeepsOnlyTheMostRecentEntries() {
        List<SettlementLogEntry> many = new ArrayList<>();
        for (int i = 1; i <= SettlementLog.MAX_ENTRIES + 20; i++) {
            many.add(new SettlementLogEntry(i, SettlementTrigger.PERIODIC, 2.0, i));
        }
        SettlementLog trimmed = new SettlementLog(many);
        assertEquals(SettlementLog.MAX_ENTRIES, trimmed.size());
        assertEquals(21L, trimmed.entries().get(0).seq(), "丢的是最旧的");
    }

    @Test
    void emptyLogHandlesTheEdgeCases() {
        SettlementLog empty = SettlementLog.empty();
        assertTrue(empty.isEmpty());
        assertThrows(IllegalStateException.class, empty::lastEntry);
        WorldState s = start(1L);
        assertTrue(empty.replay(s, engine()).ok(), "空日志重放就是原状态");
        assertTrue(empty.since(0).isEmpty());
    }

    /** 哈希本身：同状态同值、可跨进程复现（这里用固定期望值钉住算法不再变）。 */
    @Test
    void stateHashIsStableAndSensitive() {
        WorldState s = start(7L);
        assertEquals(StateHash.of(s), StateHash.of(start(7L)), "同 seed 同输入 → 同哈希");
        assertNotEquals(StateHash.of(s), StateHash.of(start(8L)), "换 seed 必须换哈希");

        // 钉住具体值：算法一旦改了（哪怕只是标签顺序），这条会红，提醒"存档里的哈希要重新对齐"
        assertEquals(-4151940155576746970L, StateHash.of(start(7L)),
                "状态哈希变了：要么算法改了，要么 schema 加了字段 —— 两者都会让存档里的 "
                        + "inputHash 全部失效，必须有意为之（换 schema 时事务日志要一起清掉）");
    }
}
