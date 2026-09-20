package com.wildernessnation.statecraft.core.letter;

import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.Optional;

/**
 * **什么时候该有人递国书**（`NATIONS.md` §9.4 的触发侧）。
 *
 * <p>这一层是纯函数：给定一个国家与此刻的世界状态，回答"它这次要不要递、递哪一种"。
 * 没有随机数、没有世界读数 —— 所以"国书什么时候出现"能被 JUnit 钉死。
 *
 * <h2>为什么必须有这一层</h2>
 *
 * <p>{@link LetterMachine} 只管"递出之后怎么走"，它自己**不会**被谁调用。
 * 少了这一层，§9.4 的国书在游戏里永远不会出现（2026-09-20 接线时发现的空洞）。
 *
 * <h2>四档判断（顺序即优先级）</h2>
 *
 * <ol>
 *   <li><b>在打仗、而且它占优</b> → {@link LetterKind#TRIBUTE}：趁势要东西换太平；</li>
 *   <li><b>没在打仗、但恨你（态度低于门槛）</b> → {@link LetterKind#STOP_BUILDING}：
 *       §9.4 的"停止在边境建造"；</li>
 *   <li><b>它有个仇家、而又不讨厌你</b> → {@link LetterKind#JOINT_WAR}：拉你一起打；</li>
 *   <li>其余 → 不递。</li>
 * </ol>
 *
 * <p>【占位】§9.4 只给了三种国书的**语义**，没给触发条件。这里取的是"每个国家自己身上
 * 看得出来的信号"（态度、战果、对第三国的态度），刻意不引入玩家侧的扩张速度 ——
 * 那个数据在计划 5 的账本里，现在没有（不猜一个替代信号）。
 */
public final class LetterIssuing {

    private final LetterConfig cfg;

    public LetterIssuing(LetterConfig cfg) {
        this.cfg = cfg.validated();
    }

    /**
     * 它这次要递什么（空 = 不递）。
     *
     * @param state  当前世界（要给 JOINT_WAR 挑目标国）
     * @param nation 候选国家
     */
    public Optional<Proposal> consider(WorldState state, Nation nation) {
        if (nation == null || !nation.alive() || !nation.met()) {
            return Optional.empty();                    // 没接触过就谈不上递国书
        }
        if (hasOpenLetter(state, nation.id()) || coolingDown(state, nation.id())) {
            return Optional.empty();                    // 已经有一封待回的 / 刚递过，别刷屏
        }

        if (nation.atWarWithParty()) {
            // 它正占优 → 趁势索贡。战果为负 = 玩家方占劣（§五：正数=玩家方占优）
            if (nation.partyWarScore() < 0.0) {
                return Optional.of(new Proposal(LetterKind.TRIBUTE, "",
                        "它在战事上占优，趁势索贡"));
            }
            return Optional.empty();                    // 打输了就不来要东西了
        }
        if (nation.attitudeToParty() <= cfg.issueAttitudeThreshold()) {
            return Optional.of(new Proposal(LetterKind.STOP_BUILDING, "",
                    "它对你们的扩张很不满（态度 " + oneDecimal(nation.attitudeToParty()) + "）"));
        }
        Optional<String> enemy = hatedNeighbour(state, nation);
        if (enemy.isPresent() && nation.attitudeToParty() >= cfg.jointWarMinAttitude()) {
            return Optional.of(new Proposal(LetterKind.JOINT_WAR, enemy.get(),
                    "它想打「" + state.nation(enemy.get()).map(Nation::name).orElse(enemy.get())
                            + "」，来拉你们入伙"));
        }
        return Optional.empty();
    }

    /** 同一个国家刚递过就别再递（否则每两小时来一封，很快就变成刷屏）。 */
    private boolean coolingDown(WorldState state, String nationId) {
        long latest = Long.MIN_VALUE;
        for (Letter l : state.letters()) {
            if (l.fromNationId().equals(nationId)) {
                latest = Math.max(latest, l.issuedAtSeq());
            }
        }
        if (latest == Long.MIN_VALUE) {
            return false;
        }
        return state.seq() - latest < cfg.issueCooldownSettlements();
    }

    private static boolean hasOpenLetter(WorldState state, String nationId) {
        for (Letter l : state.letters()) {
            if (l.fromNationId().equals(nationId) && l.state() == LetterState.OPEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 它有没有一个"恨到想打"的邻居。
     *
     * <p>按关系表顺序取**第一个**够恨的 —— 顺序确定（关系表由生成器以固定顺序产出），
     * 所以同一个存档每次都挑中同一个目标。
     */
    private Optional<String> hatedNeighbour(WorldState state, Nation nation) {
        for (Relation r : state.relations()) {
            boolean mine = r.a().equals(nation.id()) || r.b().equals(nation.id());
            if (!mine || r.state() == Relation.State.ALLIANCE) {
                continue;
            }
            String other = r.a().equals(nation.id()) ? r.b() : r.a();
            Nation enemy = state.nation(other).orElse(null);
            if (enemy == null || !enemy.alive()) {
                continue;
            }
            if (r.attitude() <= cfg.jointWarTargetAttitude()) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    private static String oneDecimal(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /**
     * 一次"该递什么"的结论。
     *
     * @param kind           种类
     * @param targetNationId 只有共同讨伐用（其余为空串）
     * @param reason         为什么（写进事件/日志，便于事后搞清楚它当时在想什么）
     */
    public record Proposal(LetterKind kind, String targetNationId, String reason) {

        public Proposal {
            if (kind == null) {
                throw new IllegalArgumentException("Proposal.kind 不能为 null");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Proposal.reason 不能为空");
            }
            targetNationId = targetNationId == null ? "" : targetNationId;
            if (kind == LetterKind.JOINT_WAR && targetNationId.isBlank()) {
                throw new IllegalArgumentException("共同讨伐必须给目标国");
            }
            if (kind != LetterKind.JOINT_WAR && !targetNationId.isBlank()) {
                throw new IllegalArgumentException(kind + " 不该带目标国：" + targetNationId);
            }
        }
    }
}
