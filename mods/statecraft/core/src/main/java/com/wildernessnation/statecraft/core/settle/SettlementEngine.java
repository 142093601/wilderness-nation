package com.wildernessnation.statecraft.core.settle;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.letter.LetterDefaults;
import com.wildernessnation.statecraft.core.letter.LetterIssuing;
import com.wildernessnation.statecraft.core.letter.LetterMachine;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 结算引擎（`NATIONS.md` §七 / §八 / §九）。
 *
 * <p><strong>零实体</strong>：这里不改任何方块、不生成任何军队（§八.7）。它只算数字与事件，
 * 实体化由 mod 层按事件去做。
 *
 * <p><strong>确定性</strong>：所有随机都取自 {@code hash(seed, seq, 用途标签)}，
 * 而 {@code seq} 是结算序号。于是"同 seed + 同一串输入"必然复现同一段历史——
 * 这既是可重放（事务日志，计划 3 Task 4）的地基，也是这些规则能被测试的前提。
 * 遍历顺序也必须是确定的：{@code nations} 按 id 顺序、{@code relations} 按建立顺序，
 * 两者都由 {@code WorldGenerator} 以固定顺序生成。
 *
 * <p>触发差异（§七）：
 * <table>
 *   <tr><th></th><th>周期小结算</th><th>时代推进</th></tr>
 *   <tr><td>全体成长</td><td>—</td><td>✅ 军力/国库 × 时代系数</td></tr>
 *   <tr><td>军力恢复</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>疲劳衰减</td><td>✅</td><td>—（时代推进不是"小结算"）</td></tr>
 *   <tr><td>态度回归</td><td>✅</td><td>—</td></tr>
 *   <tr><td>国家之间开战与推进</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>对玩家压力（宣战/袭击）</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>「天下大势」事件</td><td>—</td><td>✅</td></tr>
 * </table>
 */
public final class SettlementEngine {

    private final SettlementConfig cfg;
    private final StatecraftConfig gen;
    private final EraTable eras;
    private final LetterMachine letters;

    /**
     * 内容层的国书要价（`letters.json` 的 kinds）；没给就不递国书（只在测试里会这样）。
     *
     * <p>core 是**零依赖**的（连 {@code javax.annotation} 都不引入），所以这里用
     * "可为 null + javadoc"表达可选，而不是 {@code @Nullable} 注解。
     */
    private final LetterDefaults defaults;

    /**
     * @param cfg  结算参数（§十二）
     * @param gen  生成参数——**吸收后要按"发展度与规模反比"重算 dev**，那条公式属于生成期，
     *             所以引擎确实需要它，而不是重复写一份
     * @param eras 时代表（数据驱动，数量不写死）
     */
    public SettlementEngine(SettlementConfig cfg, StatecraftConfig gen, EraTable eras) {
        this(cfg, gen, eras, LetterMachine.defaults(), null);
    }

    /** 显式注入国书状态机（试玩调参时用得上；默认走 {@link LetterMachine#defaults()}）。 */
    public SettlementEngine(
            SettlementConfig cfg, StatecraftConfig gen, EraTable eras, LetterMachine letters) {
        this(cfg, gen, eras, letters, null);
    }

    /**
     * 完整构造：既给国书状态机、又给内容层要价。
     *
     * @param defaults 三种国书的半径/数额（来自 `letters.json`）；给 null 则这一局不递国书
     */
    public SettlementEngine(SettlementConfig cfg, StatecraftConfig gen, EraTable eras,
            LetterMachine letters, LetterDefaults defaults) {
        this.cfg = cfg.validated();
        this.gen = gen.validated();
        this.eras = eras;
        this.letters = letters;
        this.defaults = defaults;
    }

    public WorldState settle(WorldState state, SettlementInput input) {
        long seq = state.seq() + 1;
        double hours = state.elapsedOnlineHours() + input.elapsedHoursDelta();
        boolean eraAdvance = input.trigger() == SettlementTrigger.ERA_ADVANCE;
        DeterministicRandom rng = new DeterministicRandom(state.seed());

        // 国书要先算：到期与承诺了结都改态度，而"态度 → 宣战门槛"就在本拍稍后。
        // §11.2 那句"一次结算里'承诺到期'排在'对玩家施压'之前"说的就是这件事：
        // 期限一过它就可能立刻翻脸——买的是那段时间，不是它的态度。
        WorldState pre = letters.expireOverdue(state, seq);
        pre = letters.settlePledges(pre, seq);

        Map<String, Nation> work = new LinkedHashMap<>();
        for (Nation n : pre.nations()) {
            work.put(n.id(), n);
        }
        List<Relation> relations = new ArrayList<>(pre.relations());
        List<Event> events = new ArrayList<>();

        Era era = eras.currentFor(hours);

        if (eraAdvance) {
            applyGrowth(work, era.coefficient());
        }
        applyMilitaryRecovery(work);
        if (!eraAdvance) {
            applyFatigueDecay(work);
            applyAttitudeRegression(work, relations, rng, seq);
        }
        applyTruceExpiry(work, relations, seq);
        advanceWars(work, relations, rng, seq, events);
        applyPressureOnPlayer(work, rng, seq, events);
        if (eraAdvance) {
            events.add(eraSummary(seq, era, work, relations));
        }

        List<Nation> nations = new ArrayList<>(state.nations().size());
        for (Nation original : state.nations()) {
            nations.add(work.get(original.id()));
        }
        WorldState settled = new WorldState(
                WorldState.CURRENT_SCHEMA_VERSION, pre.seed(), pre.eraId(), pre.eraOrdinal(),
                pre.seq(), nations, relations, pre.events(), pre.letters(), pre.buildings(),
                pre.elapsedOnlineHours()).withEventsAdded(events)
                .withClock(hours, era.id(), era.ordinal(), seq);

        // 国书**最后递**：它读的是这一拍刚算完的态度与战果（"打完这一仗它才来要东西"），
        // 所以必须排在压力之后。
        return issueLetters(settled, letters);
    }

    /**
     * 这一拍谁要递国书（§9.4 的触发侧）。
     *
     * <p>{@link LetterIssuing} 只回答"它想不想递、递哪种"；"这一拍最多递几封"
     * 是节流（§十二 的同类做法），也在这里。
     *
     * <p><b>顺序必须确定</b>：按 {@code nations[]} 的顺序取，所以同一个存档每次都挑中同一批国家
     * —— 不然"同 seed 同输入必得同结果"就在这一行上破了。
     *
     * @param letters 已经绑定好内容层默认要价的国书状态机（半径/数额来自 `letters.json`）
     */
    private WorldState issueLetters(WorldState state, LetterMachine letters) {
        if (defaults == null) {
            return state;            // 没给内容层默认值就不递（构造器保证只在测试里发生）
        }
        long budget = letters.config().maxIssuesPerSettlement();
        if (budget <= 0) {
            return state;
        }
        LetterIssuing issuing = new LetterIssuing(letters.config());
        WorldState next = state;
        for (Nation n : state.nations()) {
            if (budget <= 0) {
                break;
            }
            Optional<LetterIssuing.Proposal> proposal = issuing.consider(next, n);
            if (proposal.isEmpty()) {
                continue;
            }
            budget--;
            next = letters.propose(next, n.id(), proposal.get().kind(),
                    proposal.get().targetNationId(), defaults);
        }
        return next;
    }

    // ---- §七「全体成长」 ----

    /**
     * 时代推进时的全体成长。
     *
     * <p>**刻意只动军力与国库**，不碰 {@code size} 与 {@code development}：后两者由
     * "吸收 → 规模变大 → 发展度按反比公式重算"驱动，如果时代推进也顺手改它们，
     * §八 那条最关键的因果（打赢了反而变落后）就被搅浑了。
     *
     * <p>【占位】读法：设计只写了"全体成长"和时代有 {@code coefficient}，没说什么增长。
     * 这里取"军力与国库 × 时代系数"，是最保守、也最容易在试玩里改的一种。
     */
    private static void applyGrowth(Map<String, Nation> work, double coefficient) {
        for (Map.Entry<String, Nation> e : work.entrySet()) {
            Nation n = e.getValue();
            if (!n.alive()) {
                continue;
            }
            e.setValue(n.withMilitary(n.military() * coefficient)
                    .withTreasury(n.treasury() * coefficient));
        }
    }

    // ---- §八.6 军力恢复（受疲劳拖累） ----

    private void applyMilitaryRecovery(Map<String, Nation> work) {
        for (Map.Entry<String, Nation> e : work.entrySet()) {
            Nation n = e.getValue();
            if (!n.alive()) {
                continue;
            }
            double base = Math.ceil(n.size() * cfg.militaryRecoveryPerSize());
            double penalty = Math.pow(cfg.fatiguePowerFactor(), n.fatigue());
            e.setValue(n.withMilitary(n.military() + base * penalty));
        }
    }

    /** §十二：扩张疲劳每小结算 −0.1，最低 0。 */
    private void applyFatigueDecay(Map<String, Nation> work) {
        for (Map.Entry<String, Nation> e : work.entrySet()) {
            Nation n = e.getValue();
            if (n.fatigue() <= 0.0) {
                continue;
            }
            e.setValue(n.withFatigue(Math.max(0.0, n.fatigue() - cfg.fatigueDecayPerPeriod())));
        }
    }

    /** §十二：态度每小结算向 0 靠 1~2 点（对玩家的态度与国家之间的态度都算）。 */
    private void applyAttitudeRegression(
            Map<String, Nation> work, List<Relation> relations, DeterministicRandom rng, long seq) {
        int i = 0;
        for (Map.Entry<String, Nation> e : work.entrySet()) {
            Nation n = e.getValue();
            i++;
            e.setValue(n.withAttitudeToParty(
                    regress(n.attitudeToParty(), rng, seq, "attitude#" + i)));
        }
        for (int k = 0; k < relations.size(); k++) {
            Relation r = relations.get(k);
            relations.set(k, r.withAttitude(regress(r.attitude(), rng, seq, "relAttitude#" + k)));
        }
    }

    private double regress(double value, DeterministicRandom rng, long seq, String tag) {
        if (value == 0.0) {
            return 0.0;
        }
        double step = rng.rangeDouble(seq, tag, cfg.attitudeRegressionMin(),
                cfg.attitudeRegressionMax());
        double next = value > 0 ? value - step : value + step;
        // 不许穿过 0（"向 0 靠"不是"反向"）
        return value > 0 ? Math.max(0.0, next) : Math.min(0.0, next);
    }

    /** 停战到期自动回到和平（国家 ↔ 玩家、国家 ↔ 国家两处都要）。 */
    private void applyTruceExpiry(
            Map<String, Nation> work, List<Relation> relations, long seq) {
        for (Map.Entry<String, Nation> e : work.entrySet()) {
            Nation n = e.getValue();
            if (n.partyStatus() == Nation.PartyStatus.TRUCE && seq >= n.partyStatusUntilSeq()) {
                e.setValue(n.withPartyStatus(Nation.PartyStatus.NEUTRAL, 0L));
            }
        }
        for (int k = 0; k < relations.size(); k++) {
            Relation r = relations.get(k);
            if (r.state() == Relation.State.TRUCE && seq >= r.truceUntilSeq()) {
                relations.set(k, r.backToPeace());
            }
        }
    }

    // ---- §八 国家之间的战争 ----

    private void advanceWars(
            Map<String, Nation> work, List<Relation> relations, DeterministicRandom rng, long seq,
            List<Event> events) {
        int budget = cfg.maxTransactionsPerSettle();
        for (int k = 0; k < relations.size(); k++) {
            Relation r = relations.get(k);
            Nation a = work.get(r.a());
            Nation b = work.get(r.b());

            // 有一方已经没了 → 这段关系收尾（战争无法继续，但关系本身留着当史料）
            if (a == null || b == null || !a.alive() || !b.alive()) {
                if (r.state() != Relation.State.PEACE) {
                    relations.set(k, r.backToPeace());
                }
                continue;
            }
            if (r.state() == Relation.State.ALLIANCE || r.state() == Relation.State.TRUCE) {
                continue;
            }

            if (r.state() == Relation.State.PEACE) {
                if (budget <= 0) {
                    continue;
                }
                if (rollWar(a, b, rng, seq, k)) {
                    budget--;
                    relations.set(k, r.withState(Relation.State.WAR).withWarScore(0.0)
                            .withLosingStreak(0));
                    events.add(new Event(seq, EventTypes.WAR_DECLARED, List.of(a.id(), b.id()),
                            "war_declared", List.of(a.name(), b.name()), true, false));
                }
                continue;
            }

            // state == WAR：推进一步
            if (budget <= 0) {
                continue;
            }
            budget--;
            stepWar(work, relations, k, r, a, b, rng, seq, events);
        }
    }

    private boolean rollWar(Nation a, Nation b, DeterministicRandom rng, long seq, int k) {
        double stanceTerm = (a.stance() + b.stance()) / 200.0;      // 两国都好战 → 更容易打
        double fatigueTerm = Math.pow(cfg.fatigueWarChanceFactor(), a.fatigue() + b.fatigue());
        double chance = Math.max(0.0, Math.min(1.0, cfg.warChanceBase() * (1.0 + stanceTerm) * fatigueTerm));
        return rng.nextDouble(seq, "warRoll#" + k) < chance;
    }

    /** §八.2~§八.5：一步战争 → 可能只是战果变化，也可能灭亡或吸收。 */
    private void stepWar(
            Map<String, Nation> work, List<Relation> relations, int index, Relation r,
            Nation a, Nation b, DeterministicRandom rng, long seq, List<Event> events) {
        double powerA = power(a, rng, seq, "powerA#" + index);
        double powerB = power(b, rng, seq, "powerB#" + index);
        boolean aWins = powerA >= powerB;

        double score = r.warScore() + (aWins ? cfg.warScoreStep() : -cfg.warScoreStep());
        String loserId = aWins ? b.id() : a.id();
        // 上一次占劣的是谁：warScore < 0 表示 a 占劣
        String previousLoser = r.warScore() < 0 ? a.id() : b.id();
        int streak = loserId.equals(previousLoser) ? r.losingStreak() + 1 : 1;

        Nation winner = aWins ? a : b;
        Nation loser = aWins ? b : a;
        double margin = Math.abs(score);

        if (margin >= cfg.collapseWarScore()
                && loser.development() < cfg.collapseMaxDevelopment()
                && streak >= cfg.collapseLosingStreak()) {
            // §八.4 灭亡：势力被打垮、而且本来就是个弱国
            work.put(loser.id(), loser.withStatus(Nation.Status.DEAD));
            relations.set(index, r.backToPeace());
            events.add(new Event(seq, EventTypes.COLLAPSE, List.of(loser.id(), winner.id()),
                    "collapse", List.of(loser.name(), winner.name()), true, false));
            return;
        }

        if (margin >= cfg.absorptionWarScore()) {
            // §八.5 吸收：胜者变大 → dev 按反比公式重算（于是"打赢了反而变落后"）
            int newSize = Math.min(cfg.absorptionSizeCap(),
                    winner.size() + (int) Math.ceil(loser.size() * cfg.absorptionSizeFactor()));
            double newDev = absorptionDevelopment(winner, newSize);
            work.put(winner.id(), winner.withSize(newSize).withDevelopment(newDev)
                    .withFatigue(winner.fatigue() + 1.0)
                    .withAbsorbedCount(winner.absorbedCount() + 1));
            work.put(loser.id(), loser.withStatus(Nation.Status.DEAD));
            relations.set(index, r.backToPeace());
            events.add(new Event(seq, EventTypes.ABSORBED, List.of(winner.id(), loser.id()),
                    "absorbed", List.of(winner.name(), loser.name(), String.valueOf(newSize)),
                    true, false));
            return;
        }

        relations.set(index, r.withWarScore(score).withLosingStreak(streak));
        events.add(new Event(seq, EventTypes.BATTLE, List.of(winner.id(), loser.id()), "battle",
                List.of(winner.name(), loser.name()), true, false));
    }

    /**
     * 吸收后的发展度：**沿用该国原有的抖动，只按规模差扣**。
     *
     * <p>§八.5 只写了"dev 按反比公式重算"。如果照 §六 的公式**重新摇一次抖动**，
     * "打赢了反而变落后"这条设计意图就会**被抖动推翻**：规模 +4 只让基准掉 24 点，
     * 而抖动范围是 ±8，重摇完全可能把发展度摇得更高（实测：规模已到上限 10 时尤其明显，
     * 那时规模一点都不变、发展度却能上下跳）。所以这里把它实现成
     * "只吃规模那部分的线性变化"——抖动是国家的"性格"，不该因为打了一仗就重新抽签。
     *
     * <p>副作用是好的：这一段不再消耗随机数，重放更稳。
     */
    private double absorptionDevelopment(Nation winner, int newSize) {
        double raw = winner.development() - gen.devPerSize() * (newSize - winner.size());
        return Math.max(gen.devMin(), Math.min(gen.devMax(), raw));
    }

    /** §八.2：{@code 战力 = military × (1 + stance/200) × 疲劳惩罚 × rng(0.85, 1.15)}。 */
    private double power(Nation n, DeterministicRandom rng, long seq, String tag) {
        double fatiguePenalty = Math.pow(cfg.fatiguePowerFactor(), n.fatigue());
        double luck = rng.rangeDouble(seq, tag, 0.85, 1.15);
        return Math.max(0.0, n.military() * (1.0 + n.stance() / 200.0) * fatiguePenalty * luck);
    }

    // ---- §九 国家对玩家的压力 ----

    /**
     * §9.1 的宣战门槛：{@code attitude ≤ −40 且 stance ≥ +40}，**且"不侵犯承诺"已过期**。
     *
     * <p>公开出来是有意的：**一次结算里"态度回归"先跑**，所以刚好卡在 −40 的国家会在同一拍里
     * 漂回 −38 而失去资格——边界值在整条流水线上根本观察不到。要有能直接问"这个国家够不够凶"
     * 的地方，边界才测得了（mod 层以后要用它做情报站的提示）。
     *
     * <p>【占位】§9.1 还写了"还要看发展度差与你们最近的扩张速度"：发展度差需要**玩家方的发展度**，
     * 扩张速度需要**你的领地**——两者都在 `playerLedger` / `domains` 里，属于计划 4/5。
     * 现在只实现态度与倾向这两项，缺的那两项等有数据源再接（不猜一个替代公式）。
     *
     * <p>「朝贡」换来的不侵犯承诺（`nonAggressionUntilSeq`）在这里生效：
     * 承诺期内不论多恨都不会宣战——这是 §11.2 那句"'不侵犯'承诺"的实际含义。
     */
    public boolean willDeclareWarOnPlayer(Nation n, long seq) {
        return n.alive()
                && n.freeToDeclareWarOnParty(seq)
                && n.attitudeToParty() <= cfg.warDeclarationAttitude()
                && n.stance() >= cfg.warDeclarationStance();
    }

    private void applyPressureOnPlayer(
            Map<String, Nation> work, DeterministicRandom rng, long seq, List<Event> events) {
        int i = 0;
        for (Map.Entry<String, Nation> e : work.entrySet()) {
            Nation n = e.getValue();
            i++;
            if (willDeclareWarOnPlayer(n, seq)) {
                e.setValue(n.withPartyStatus(Nation.PartyStatus.WAR, 0L));
                events.add(new Event(seq, EventTypes.WAR_DECLARED, List.of(n.id(),
                        Event.PARTY_PLAYER), "war_declared_player", List.of(n.name()), true, false));
                continue;
            }
            if (n.atWarWithParty() && rng.nextDouble(seq, "raidRoll#" + i) < cfg.raidChance()) {
                int scale = raidScale(n);
                events.add(new Event(seq, EventTypes.RAID, List.of(Event.PARTY_PLAYER, n.id()),
                        "raid", List.of(n.name(), String.valueOf(scale)), true, false));
            }
        }
    }

    /** §十二：袭击规模 = 2 + floor(military/25)，上限 24。 */
    public int raidScale(Nation n) {
        double raw = cfg.raidScaleBase() + Math.floor(n.military() / cfg.raidScaleDivisor());
        return (int) Math.min(cfg.raidScaleCap(), raw);
    }

    /** §七：时代推进时的一封「天下大势」（文本渲染在计划 4，这里只给结构化参数）。 */
    private Event eraSummary(long seq, Era era, Map<String, Nation> work, List<Relation> relations) {
        long living = work.values().stream().filter(Nation::alive).count();
        long wars = relations.stream().filter(r -> r.state() == Relation.State.WAR).count();
        long dead = work.size() - living;
        return new Event(seq, EventTypes.ERA_SUMMARY, List.of(Event.PARTY_PLAYER), "era_summary",
                List.of(era.id(), era.name(), String.valueOf(living), String.valueOf(dead),
                        String.valueOf(wars)),
                true, false);
    }
}
