package com.wildernessnation.statecraft.core.diplomacy;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.List;

/**
 * **玩家行为 → 国家的态度**（`NATIONS.md` §十二 的那张表）。
 *
 * <table>
 *   <tr><th>行为</th><th>每次</th><th>周期上限</th></tr>
 *   <tr><td>击杀其单位</td><td>−1</td><td>−15</td></tr>
 *   <tr><td><b>在都城 64 格内放置方块</b></td><td>−2</td><td>−10</td></tr>
 *   <tr><td>通商</td><td>+3</td><td>—（在 {@code DiplomacyMachine.trade} 里）</td></tr>
 *   <tr><td>完成国书</td><td>+10</td><td>—（在 {@code LetterMachine} 里）</td></tr>
 * </table>
 *
 * <h2>为什么这一层必须存在</h2>
 *
 * <p>2026-09-20 接线时发现：**没有任何东西会拉低国家对玩家的态度**。
 * 生成时态度是 0，每拍"态度回归"又把它拉向 0 —— 于是 §9.1 的宣战门槛（态度 ≤ −40）
 * 永远踩不到、§9.4 的国书也只剩"手动宣战再求和"那一条人为路径。
 * 这张表是**压力的唯一来源**，少了它整条"国家与玩家"的链就是死的。
 *
 * <h2>周期上限怎么记账</h2>
 *
 * <p>不另存计数器：**从事件日志里数**（同一 `seq` 里已经记了几条同类事件）。
 * 理由和别处一样 —— 事件日志本来就是那份账（§十二 的"归档 ≠ 丢失"），
 * 另存一份就会出现"两处不一致"。代价是每周期要扫一遍事件（上限 500 条，可忽略）。
 *
 * <h2>只做"放置方块"这一条</h2>
 *
 * <p>§十二 还有"击杀其单位 −1"。那一条要**知道被打死的单位属于哪个国家**，
 * 而 HYW 单位与国家之间还没有任何映射（那是 M0/计划 5 的事）。所以这里**不写**
 * 那条规则的半成品 —— 等有映射时再加，一次做对。
 */
public final class PlayerActions {

    /** §十二：在都城多少格内放置方块算挑衅。 */
    public static final double CAPITAL_RADIUS = 64.0;

    /** §十二：每放置一次方块的态度代价。 */
    public static final double BUILD_ATTITUDE_STEP = -2.0;

    /** §十二：一个结算周期内"放置方块"累计能扣到多少。 */
    public static final double BUILD_ATTITUDE_CAP = -10.0;

    /** 记进事件日志用的模板键（周期上限就是靠数它记账的，所以**不能改**）。 */
    public static final String BUILD_TEXT_KEY = "build_near_capital";

    private PlayerActions() {}

    /**
     * 报告一次"玩家在 (x,z) 放置了方块"。
     *
     * <p>调用方（mod 层）只报告**事实**：谁在哪儿放了方块。落在谁的都城 64 格内、
     * 这次还能扣多少（周期上限），都在这里判 —— 这样"上限"这条规则能被 JUnit 钉死。
     *
     * @param seq 当前结算序号（周期上限按它分桶）
     * @return 处理后的状态 + 这次真的被扣了态度的国家
     */
    public static Reaction buildingNear(WorldState state, double x, double z, long seq) {
        if (state == null) {
            throw new IllegalArgumentException("WorldState 不能为 null");
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("位置必须是有限数：" + x + "," + z);
        }
        if (seq < 0) {
            throw new IllegalArgumentException("seq 不能为负：" + seq);
        }

        List<Nation> nations = new ArrayList<>(state.nations());
        List<Event> events = new ArrayList<>();
        List<String> affected = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < nations.size(); i++) {
            Nation n = nations.get(i);
            if (!n.alive() || n.distanceTo(x, z) > CAPITAL_RADIUS) {
                continue;
            }
            double already = penaltySoFar(state, n.id(), seq);
            // 这个周期还剩多少额度（负数；扣满时正好是 0）
            double remaining = BUILD_ATTITUDE_CAP - already;
            if (remaining >= 0.0) {
                continue;                       // §十二 的周期上限到了：本周期不再扣
            }
            // 两个都是负数，取**更轻**的那个（一次最多扣 2，但也别超过剩余额度）。
            // ⚠️ 这里曾经写成 `Math.max(0.0, Math.min(STEP, remaining))`，两个错叠在一起：
            //   ① min(−2, −10) = −10（拿反了，该是 max）；
            //   ② 外层的 max(0, ·) 把合法的负数步长夹成 0 → **一次都扣不动**。
            // 两条都被 `PlayerActionsTest` 当场抓住。
            double step = Math.max(BUILD_ATTITUDE_STEP, remaining);
            nations.set(i, n.withAttitudeToParty(clampAttitude(n.attitudeToParty() + step)));
            events.add(new Event(seq, EventTypes.PLAYER_ACTION,
                    List.of(Event.PARTY_PLAYER, n.id()), BUILD_TEXT_KEY,
                    List.of(n.name()), true, false));
            affected.add(n.id());
            changed = true;
        }
        if (!changed) {
            return new Reaction(state, List.of());
        }
        WorldState next = state.withNations(nations);
        // 事件照样要记（它是周期上限的账），但**只有真的扣了态度**才算"有影响"
        return new Reaction(next.withEventsAdded(events), List.copyOf(affected));
    }

    /**
     * 这个国家的都城在不在这个位置附近（给 mod 层做"要不要上报"的预判，
     * 免得每个方块都走一遍完整的状态更新）。
     */
    public static List<String> capitalsNear(WorldState state, double x, double z) {
        List<String> out = new ArrayList<>();
        for (Nation n : state.nations()) {
            if (n.alive() && n.distanceTo(x, z) <= CAPITAL_RADIUS) {
                out.add(n.id());
            }
        }
        return out;
    }

    /** 这个周期在这个国家身上已经扣了多少（负数；用来算"还能不能再扣"）。 */
    public static double penaltySoFar(WorldState state, String nationId, long seq) {
        double sum = 0.0;
        for (Event e : state.events()) {
            if (e.seq() != seq || !BUILD_TEXT_KEY.equals(e.textKey())) {
                continue;
            }
            if (e.parties().contains(nationId)) {
                sum += BUILD_ATTITUDE_STEP;
            }
        }
        return sum;
    }

    private static double clampAttitude(double v) {
        return Math.max(-100.0, Math.min(100.0, v));
    }

    /**
     * 一次"玩家动了土"的处理结果。
     *
     * @param state    处理后的状态（没人被影响时**原样返回入参**）
     * @param affected 这次真的被扣了态度的国家 id
     */
    public record Reaction(WorldState state, List<String> affected) {

        public Reaction {
            if (state == null) {
                throw new IllegalArgumentException("Reaction.state 不能为 null");
            }
            affected = affected == null ? List.of() : List.copyOf(affected);
        }

        public boolean changed() {
            return !affected.isEmpty();
        }
    }
}
