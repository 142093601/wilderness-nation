package com.wildernessnation.statecraft.core.letter;

import com.wildernessnation.statecraft.core.diplomacy.DiplomacyConfig;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 国书状态机（`NATIONS.md` §9.4）。
 *
 * <h2>国书是什么</h2>
 *
 * <p>国家递来的**可交互事件**（条件 + 期限）："停止在边境建造""共同讨伐 X 国""朝贡 N 资源换互不侵犯"。
 * 三种共用一个 {@link Letter} record（按种类校验字段），生命周期也共用一套：
 * {@code OPEN → ACCEPTED / REFUSED / EXPIRED / BREACHED}。
 *
 * <h2>本类管的四件事</h2>
 *
 * <ol>
 *   <li><strong>递出</strong>（{@link #propose}）—— 给一封 {@code OPEN} 的国书定下期限；</li>
 *   <li><strong>回复</strong>（{@link #answer}）—— 接受就结算该种类独有的后果，拒绝就只掉态度；</li>
 *   <li><strong>到期</strong>（{@link #expireOverdue}）—— §9.4 的原话"忽略=拒绝"，所以掉态度；</li>
 *   <li><strong>承诺</strong>（{@link #settlePledges} / {@link #reportBuildingNear}）——
 *       接受「停止建造」换来的是一段**真的会管住你的**承诺期，到期履约 +10，被逮到破约 −25。</li>
 * </ol>
 *
 * <h2>两条不能在这里做的检查（和其它规则类一样记清楚边界）</h2>
 *
 * <p><strong>① "他到底有没有在边境建东西"只有世界知道。</strong>core 没有方块读数，
 * 所以由 mod 层把"离它 D 格内置了方块"这件事**报进来**（{@link #reportBuildingNear}），
 * core 只判断"这算不算破了某封在效期内的承诺"。
 *
 * <p><strong>② 玩家侧的资源不在 core 里</strong>（{@code playerLedger} 属于计划 4/5）。
 * 所以朝贡要掏多少，core 只**报出来**（{@link LetterResult#playerTreasuryDelta()}），
 * 由调用方去记账——core 不编一个玩家账本出来。
 *
 * <h2>确定性</h2>
 *
 * <p>国书的每一步都是**结算驱动**或**玩家显式动作**，没有一处掷骰：
 * 后果完全由"哪封信 + 当前 seq + 配置"决定。所以这里不需要 nonce，
 * 重放时同一串输入必然得到同一段国书往来。
 */
public final class LetterMachine {

    private final LetterConfig cfg;
    private final DiplomacyConfig diplomacy;

    /**
     * @param cfg       国书参数（§9.4 / §十二）
     * @param diplomacy 只借"朝贡换来的不侵犯承诺有多长"这一项（§11.2）——
     *                  它属于朝贡这个动作，不在这里重新定义一遍，免得两个数各改各的
     */
    public LetterMachine(LetterConfig cfg, DiplomacyConfig diplomacy) {
        this.cfg = cfg.validated();
        this.diplomacy = diplomacy.validated();
    }

    /** 只用默认参数的便捷构造（结算引擎的默认路径走它）。 */
    public static LetterMachine defaults() {
        return new LetterMachine(LetterConfig.defaults(), DiplomacyConfig.defaults());
    }

    /** 这份配置（结算引擎要拿它问"这一拍最多递几封"）。 */
    public LetterConfig config() {
        return cfg;
    }

    // ---- 递出 ----

    /**
     * 递出一封国书（{@code OPEN}，期限 = 现在 + {@code deadlineSettlements}）。
     *
     * <p>幂等由调用方保证"同一触发只递一封"：这里**不主动去重**（同一个国家在不同局面下
     * 完全可以先后递两封同类国书，去重会让"它又来了"这种叙事消失）。id 由
     * "序号 + 国家 + 种类"合成，真撞上了就加后缀，绝不静默覆盖。
     */
    public WorldState propose(
            WorldState state, String nationId, LetterKind kind,
            String targetNationId, double stopBuildRadius, double amount) {
        Nation nation = requireNation(state, nationId);
        if (!nation.alive()) {
            throw new IllegalArgumentException("这个国家已经不存在了，递不了国书：" + nationId);
        }
        long issuedAt = state.seq();
        long deadline = issuedAt + cfg.deadlineSettlements();
        Letter letter = new Letter(uniqueId(state, nationId, kind), nationId, kind,
                targetNationId == null ? "" : targetNationId, stopBuildRadius, amount,
                issuedAt, deadline, LetterState.OPEN, 0L);
        return state.withLetters(concat(state.letters(), letter))
                .withEventsAdded(List.of(letterEvent(state, letter, "letter_" + kindSuffix(kind))));
    }

    /**
     * 按**内容层的默认要价**递出一封国书（半径/资源数额来自 `letters.json`）。
     *
     * <p>为什么不让调用方自己从数据里取半径：那条"哪种国书要什么"的映射
     * （只有停止建造有半径、只有朝贡有钱）是 {@link Letter} 自己的按种类校验，属于规则。
     * 让每个调用点各写一遍，迟早有一处写成"给共同讨伐填半径"然后被构造器拒掉。
     */
    public WorldState propose(
            WorldState state, String nationId, LetterKind kind, String targetNationId,
            LetterDefaults defaults) {
        return propose(state, nationId, kind, targetNationId,
                defaults.radiusFor(kind), defaults.amountFor(kind));
    }

    /** 递出时的参数（"它要求你做什么"），渲染模板要按这个顺序取词。 */
    public static List<String> proposalParams(WorldState state, Letter letter) {        String name = state.nation(letter.fromNationId()).map(Nation::name).orElse("某国");
        List<String> out = new ArrayList<>(2);
        out.add(name);
        switch (letter.kind()) {
            case STOP_BUILDING -> out.add(trimNumber(letter.stopBuildRadius()));
            case JOINT_WAR -> out.add(state.nation(letter.targetNationId())
                    .map(Nation::name).orElse("某国"));
            case TRIBUTE -> out.add(trimNumber(letter.amount()));
        }
        return out;
    }

    // ---- 回复 ----

    /**
     * 玩家回复一封国书。
     *
     * <p>前置：**这封信还在（{@code OPEN}）**。已经回过的再点一次 → 拒绝（状态原样），
     * 不是异常：玩家会同时看到"你已经回过它了"。
     *
     * <p>接受时按种类结算后果（见各类的私有方法）；拒绝时只有一条——态度掉
     * {@link LetterConfig#refusalAttitudePenalty()}，没有别的副作用（不撤军、不改关系）。
     */
    public LetterResult answer(WorldState state, String letterId, boolean accept) {
        Letter letter = state.letters().stream()
                .filter(l -> l.id().equals(letterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有这封国书：" + letterId));
        if (letter.state() != LetterState.OPEN) {
            return LetterResult.rejected(state, "你已经回过这封国书了（" + stateLabel(letter) + "）");
        }
        Nation nation = state.nation(letter.fromNationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "国书来自一个不存在的国家：" + letter.fromNationId()));
        if (!nation.alive()) {
            // 递信的人自己没了：这封国书自动作废，且**不掉态度**（对一个死人掉态度没意义）
            Letter voided = letter.withState(LetterState.EXPIRED);
            return LetterResult.accepted(state.withLetter(voided),
                    nation.name() + "已经不在了，这封国书作废");
        }
        return accept
                ? accept(state, letter, nation)
                : refuse(state, letter, nation);
    }

    private LetterResult refuse(WorldState state, Letter letter, Nation nation) {
        Nation updated = nation.withAttitudeToParty(
                clampAttitude(nation.attitudeToParty() + cfg.refusalAttitudePenalty()));
        Letter answered = letter.withState(LetterState.REFUSED);
        WorldState next = state.withNation(updated).withLetter(answered)
                .withEventsAdded(List.of(letterEvent(state, letter, "letter_refused")));
        return LetterResult.accepted(next, "你拒绝了「" + nation.name() + "」的国书（它的态度 "
                + oneDecimal(cfg.refusalAttitudePenalty()) + "）");
    }

    private LetterResult accept(WorldState state, Letter letter, Nation nation) {
        return switch (letter.kind()) {
            case TRIBUTE -> acceptTribute(state, letter, nation);
            case JOINT_WAR -> acceptJointWar(state, letter, nation);
            case STOP_BUILDING -> acceptStopBuilding(state, letter, nation);
        };
    }

    /**
     * 接受朝贡：**玩家掏资源**，换来"不侵犯"承诺。
     *
     * <p>用的是 §11.2「朝贡」那套语义（{@code PartyStatus.TRUCE} + 期限），
     * 因为对玩家来说两件事的效果是同一件：一段时间内它不打你。
     */
    private LetterResult acceptTribute(WorldState state, Letter letter, Nation nation) {
        long until = state.seq() + diplomacy.tributeNonAggressionSettlements();
        Nation updated = nation
                .withTreasury(nation.treasury() + letter.amount())
                .withAttitudeToParty(clampAttitude(
                        nation.attitudeToParty() + cfg.tributeAcceptAttitudeGain()))
                .withPartyStatus(Nation.PartyStatus.TRUCE, until)
                .withPartyWarScore(0.0);
        Letter answered = letter.withState(LetterState.ACCEPTED);
        WorldState next = state.withNation(updated).withLetter(answered)
                .withEventsAdded(List.of(letterEvent(state, letter, "letter_accepted")));
        return LetterResult.accepted(next,
                "你向「" + nation.name() + "」付了 " + trimNumber(letter.amount())
                        + "，它承诺到第 " + until + " 次结算为止不动你们",
                -letter.amount());
    }

    /**
     * 接受共同讨伐：**你和它一起与目标国开战**。
     *
     * <p>三处都要动，少一处这个动作就是假的：
     * <ul>
     *   <li>目标国对玩家的 {@code partyStatus → WAR}（不然"你们在打仗"这条根本不成立）；</li>
     *   <li>它与目标国的 {@code Relation → WAR}（不然它只是嘴上答应）；</li>
     *   <li>态度 +10（§十二 完成国书）。</li>
     * </ul>
     * 已经在跟目标国打的话不重复开战、但**照样给态度**——你确实答应了它。
     */
    private LetterResult acceptJointWar(WorldState state, Letter letter, Nation nation) {
        Nation target = state.nation(letter.targetNationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "共同讨伐的目标国不存在：" + letter.targetNationId()));
        if (!target.alive() || target.id().equals(nation.id())) {
            return LetterResult.rejected(state,
                    "共同讨伐的目标（" + target.name() + "）已经不在了，这封国书没有意义");
        }

        Nation requester = nation.withAttitudeToParty(clampAttitude(
                nation.attitudeToParty() + cfg.jointWarAttitudeGain()));
        Nation dragged = target.atWarWithParty()
                ? target
                : target.withPartyStatus(Nation.PartyStatus.WAR, 0L).withPartyWarScore(0.0);

        List<Relation> relations = new ArrayList<>(state.relations());
        for (int k = 0; k < relations.size(); k++) {
            Relation r = relations.get(k);
            if (r.connects(requester.id(), dragged.id())
                    && r.state() != Relation.State.WAR) {
                relations.set(k, r.withState(Relation.State.WAR).withWarScore(0.0)
                        .withLosingStreak(0));
            }
        }

        Letter answered = letter.withState(LetterState.ACCEPTED);
        WorldState next = state.withNation(requester).withNation(dragged)
                .withRelations(relations).withLetter(answered)
                .withEventsAdded(List.of(letterEvent(state, letter, "letter_accepted")));
        return LetterResult.accepted(next, "你答应与「" + requester.name() + "」共同讨伐「"
                + dragged.name() + "」，你们现在与它处于战争状态");
    }

    /**
     * 接受停止建造：立下一段真的有效的承诺期。
     *
     * <p>为什么接受不当场给满 +10：§十二 写的是"**完成**国书 +10"，
     * 而"停止建造"这类国书的完成只能等到承诺期结束才看得出来。所以这里先给一点
     * （{@link LetterConfig#stopBuildAcceptAttitudeGain()}），履约时再给满。
     */
    private LetterResult acceptStopBuilding(WorldState state, Letter letter, Nation nation) {
        long until = state.seq() + cfg.stopBuildPromiseSettlements();
        Nation updated = nation.withAttitudeToParty(clampAttitude(
                nation.attitudeToParty() + cfg.stopBuildAcceptAttitudeGain()));
        Letter answered = letter.withState(LetterState.ACCEPTED, until);
        WorldState next = state.withNation(updated).withLetter(answered)
                .withEventsAdded(List.of(letterEvent(state, letter, "letter_accepted")));
        return LetterResult.accepted(next, "你答应了「" + nation.name() + "」："
                + trimNumber(letter.stopBuildRadius()) + " 格内不再动土，到第 " + until
                + " 次结算为止");
    }

    // ---- 到期与承诺 ----

    /**
     * 把过了期限还没回的国书转成 {@code EXPIRED}（§七 的周期小结算里跑）。
     *
     * <p>§9.4 的原话是"忽略=拒绝"，所以**态度照掉**，而且比明着拒绝掉得更多
     * （{@link LetterConfig#ignoreAttitudePenalty()}）：不吭声是最难看的回应。
     */
    public WorldState expireOverdue(WorldState state) {
        return expireOverdue(state, state.seq());
    }

    /**
     * 带显式结算序号的版本：结算引擎要在"**推进之后**的序号"上判到期。
     *
     * <p>为什么不能用入参状态里的 seq：引擎是在把 seq 从 8 推到 9 的那一拍里跑的。
     * 一封期限为 8 的国书，玩家在 seq=8 时还能回（`8 > 8` 不成立），
     * 所以它该在**推进到 9 的那一拍**过期。若拿旧序号去判，它会白白多活一格，
     * 玩家就能在期限外继续回复——期限形同虚设。
     */
    public WorldState expireOverdue(WorldState state, long seq) {
        List<Letter> letters = state.letters();
        List<Letter> out = new ArrayList<>(letters.size());
        List<Event> events = new ArrayList<>();
        List<Nation> updated = null;
        boolean changed = false;
        for (Letter l : letters) {
            if (!l.overdueAt(seq)) {
                out.add(l);
                continue;
            }
            changed = true;
            out.add(l.withState(LetterState.EXPIRED));
            events.add(letterEvent(state, seq, l, "letter_expired"));
            Optional<Nation> n = state.nation(l.fromNationId());
            if (n.isPresent() && n.get().alive()) {
                if (updated == null) {
                    updated = new ArrayList<>(state.nations());
                }
                replaceAttitude(updated, n.get(), cfg.ignoreAttitudePenalty());
            }
        }
        if (!changed) {
            return state;
        }
        WorldState next = state.withLetters(out);
        if (updated != null) {
            next = next.withNations(updated);
        }
        return next.withEventsAdded(events);
    }

    /**
     * 承诺到期 → 履约完成：态度 +10（§十二"完成国书 +10"），承诺清账。
     *
     * <p>清账是必须的：不清的话 {@link Letter#promiseInForceAt} 会永远为真，
     * 玩家在第 100 次结算还会因为第 3 次结算答应过的事被追责。
     */
    public WorldState settlePledges(WorldState state) {
        return settlePledges(state, state.seq());
    }

    /** 带显式结算序号的版本（理由同 {@link #expireOverdue(WorldState, long)}）。 */
    public WorldState settlePledges(WorldState state, long seq) {
        List<Letter> out = new ArrayList<>(state.letters().size());
        List<Event> events = new ArrayList<>();
        List<Nation> updated = null;
        boolean changed = false;
        for (Letter l : state.letters()) {
            if (!l.promiseFulfilledAt(seq)) {
                out.add(l);
                continue;
            }
            changed = true;
            out.add(l.withState(LetterState.ACCEPTED, 0L));
            events.add(letterEvent(state, seq, l, "letter_kept"));
            Optional<Nation> n = state.nation(l.fromNationId());
            if (n.isPresent() && n.get().alive()) {
                if (updated == null) {
                    updated = new ArrayList<>(state.nations());
                }
                replaceAttitude(updated, n.get(), cfg.stopBuildKeptAttitudeGain());
            }
        }
        if (!changed) {
            return state;
        }
        WorldState next = state.withLetters(out);
        if (updated != null) {
            next = next.withNations(updated);
        }
        return next.withEventsAdded(events);
    }

    /**
     * mod 层报告："这个国家 D 格内出现了一次动土"。
     *
     * <p>判断全在 core：只有当那封「停止建造」国书**正在承诺期内**、而且这次动土
     * 落在它给的半径里，才算破约。其余情况（没国书、已过期、离得远）都是"不构成破约"，
     * 返回 {@code accepted=false} 且**状态一个字不动**——调用方拿这个返回值决定要不要发警告。
     *
     * @param distance 到该国都城（或它划定的边界）的水平距离
     */
    public LetterResult reportBuildingNear(WorldState state, String nationId, double distance) {
        requireNation(state, nationId);
        if (!(distance >= 0.0) || !Double.isFinite(distance)) {
            throw new IllegalArgumentException("距离必须 >= 0 且有限：" + distance);
        }
        List<Letter> out = new ArrayList<>(state.letters().size());
        List<Event> events = new ArrayList<>();
        List<Letter> breached = new ArrayList<>();
        for (Letter l : state.letters()) {
            boolean hit = l.fromNationId().equals(nationId)
                    && l.kind() == LetterKind.STOP_BUILDING
                    && l.promiseInForceAt(state.seq())
                    && distance <= l.stopBuildRadius();
            if (!hit) {
                out.add(l);
                continue;
            }
            out.add(l.withState(LetterState.BREACHED, 0L));
            events.add(letterEvent(state, l, "letter_breached"));
            breached.add(l);
        }
        if (breached.isEmpty()) {
            return LetterResult.rejected(state, "这次动土不涉及任何在效期内的承诺");
        }

        Nation nation = state.nation(nationId).orElseThrow();
        Nation updated = nation.withAttitudeToParty(clampAttitude(
                nation.attitudeToParty() + cfg.stopBuildBreachAttitudePenalty()));
        WorldState next = state.withLetters(out).withNation(updated).withEventsAdded(events);
        return LetterResult.accepted(next, "你在「" + nation.name() + "」划定的 "
                + trimNumber(breached.get(0).stopBuildRadius()) + " 格内动了土，承诺作废（它的态度 "
                + oneDecimal(cfg.stopBuildBreachAttitudePenalty()) + "）");
    }

    // ---- 查询 ----

    /** 还没有回复的国书（情报册里的"待回条目"，§9.4 的 v1 形态）。 */
    public static List<Letter> openLetters(WorldState state) {
        List<Letter> out = new ArrayList<>();
        for (Letter l : state.letters()) {
            if (l.state() == LetterState.OPEN) {
                out.add(l);
            }
        }
        return out;
    }

    // ---- 内部 ----

    private static List<Letter> concat(List<Letter> letters, Letter extra) {
        List<Letter> out = new ArrayList<>(letters.size() + 1);
        out.addAll(letters);
        out.add(extra);
        return out;
    }

    private static String uniqueId(WorldState state, String nationId, LetterKind kind) {
        String base = "L" + state.seq() + "-" + nationId + "-" + kind.name();
        String id = base;
        int n = 2;
        while (containsLetter(state, id)) {
            id = base + "#" + n;
            n++;
        }
        return id;
    }

    private static boolean containsLetter(WorldState state, String id) {
        for (Letter l : state.letters()) {
            if (l.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static Event letterEvent(WorldState state, Letter letter, String textKey) {
        return letterEvent(state, state.seq(), letter, textKey);
    }

    private static Event letterEvent(WorldState state, long seq, Letter letter, String textKey) {
        return new Event(seq, EventTypes.LETTER,
                List.of(Event.PARTY_PLAYER, letter.fromNationId()),
                textKey, proposalParams(state, letter), true, false);
    }

    private static String kindSuffix(LetterKind kind) {
        return switch (kind) {
            case STOP_BUILDING -> "stop_building";
            case JOINT_WAR -> "joint_war";
            case TRIBUTE -> "tribute";
        };
    }

    private static String stateLabel(Letter letter) {
        return switch (letter.state()) {
            case OPEN -> "待回";
            case ACCEPTED -> "已接受";
            case REFUSED -> "已拒绝";
            case EXPIRED -> "已过期";
            case BREACHED -> "已破约";
        };
    }

    private static Nation requireNation(WorldState state, String nationId) {
        return state.nation(nationId)
                .orElseThrow(() -> new IllegalArgumentException("没有这个国家：" + nationId));
    }

    /** 就地改一个国家的态度（列表已经是副本，可以直接替换）。 */
    private static void replaceAttitude(List<Nation> nations, Nation target, double delta) {
        for (int i = 0; i < nations.size(); i++) {
            if (nations.get(i).id().equals(target.id())) {
                nations.set(i, target.withAttitudeToParty(
                        clampAttitude(target.attitudeToParty() + delta)));
                return;
            }
        }
    }

    private static double clampAttitude(double v) {
        return Math.max(-100.0, Math.min(100.0, v));
    }

    private static String oneDecimal(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** 数值渲染：整数值不要写成 "64.0"（国书是给玩家看的）。 */
    private static String trimNumber(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return oneDecimal(v);
    }
}
