package com.wildernessnation.statecraft.world;

import com.wildernessnation.statecraft.core.building.BuildingDef;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * **玩家侧账本**（`NATIONS.md` §11.1 的追踪/深挖、§10.1 的已购图纸授权、§十二 的玩家国库）。
 *
 * <h2>为什么它在 mod 层、不在 core</h2>
 *
 * <p>core 里对应的规则**全都已经写好了**，而且都是"收事实、给判定"的形状：
 * {@code IntelBook.track/deepDive}、{@code BlueprintRules.buy/canBuild}。
 * 它们刻意**不持有**"你追着谁""你买过什么"——因为那些是**玩家侧状态**，
 * 而 core 塞一个玩家账本会让"同 seed 同输入必得同结果"变成"还得看它内部数到几"。
 *
 * <p>所以账本落在 mod 层（跟着存档走，不进 core 的 schema）：core 保持纯逻辑，
 * mod 层只负责"记下来 + 递进 core"。
 *
 * <h2>和计划 5 的关系</h2>
 *
 * <p>这就是 `DEFERRED.md` §I2 说的那个接缝。计划 5 要做的是把它接到**夺城**上
 * （玩家属地清单、资源消耗），那时帐本可能要挪进 core 或换存储格式；
 * 现在这一步的价值是：**门禁与界面终于有真实数据可读**（图纸授权不再是一句空话）。
 *
 * <p>刻意做成**不可变**的（每个 setter 返回新实例）：和 core 里的状态一个手感，
 * 也免得调用方拿到引用后忘了标记存档脏。
 */
public record PlayerLedger(
        Set<String> grantedBlueprints,
        Set<String> trackedNations,
        double treasury,
        long lastDeepDiveSeq) {

    /** 一个崭新的账本：什么都没买、什么都没追、国库 0、没深挖过。 */
    public static PlayerLedger empty() {
        return new PlayerLedger(Set.of(), Set.of(), 0.0, -1L);
    }

    public PlayerLedger {
        grantedBlueprints = grantedBlueprints == null
                ? Set.of() : Set.copyOf(new LinkedHashSet<>(grantedBlueprints));
        trackedNations = trackedNations == null
                ? Set.of() : Set.copyOf(new LinkedHashSet<>(trackedNations));
        if (!(treasury >= 0.0) || !Double.isFinite(treasury)) {
            throw new IllegalArgumentException("treasury 必须 >= 0 且有限：" + treasury);
        }
        if (lastDeepDiveSeq < -1L) {
            throw new IllegalArgumentException(
                    "lastDeepDiveSeq 只能是 -1（从未）或 >= 0 的结算序号：" + lastDeepDiveSeq);
        }
    }

    // ---- 图纸授权（§10.1）----

    public boolean ownsBlueprint(String blueprintId) {
        return blueprintId != null && grantedBlueprints.contains(blueprintId);
    }

    /** 记下买下的授权，并扣钱（钱不够时由 core 的 {@code BlueprintRules.buy} 先拦）。 */
    public PlayerLedger withPurchase(BuildingDef def, double price) {
        if (!def.authorized()) {
            return this;
        }
        Set<String> owned = new LinkedHashSet<>(grantedBlueprints);
        owned.add(def.requiredBlueprint());
        return new PlayerLedger(owned, trackedNations, Math.max(0.0, treasury - price),
                lastDeepDiveSeq);
    }

    public PlayerLedger withTreasury(double v) {
        return new PlayerLedger(grantedBlueprints, trackedNations, Math.max(0.0, v),
                lastDeepDiveSeq);
    }

    // ---- 追踪与深挖（§11.1 第三档）----

    public PlayerLedger withTracked(Set<String> v) {
        return new PlayerLedger(grantedBlueprints, v, treasury, lastDeepDiveSeq);
    }

    /**
     * 记下"这个结算已经深挖过"（§11.1：每结算 1 国）。
     *
     * <p>存的是**结算序号**而不是一个布尔或计数器：seq 一变就自动等于"额度重置"，
     * 不需要任何地方去清零（少一个会忘记复位的状态）。
     */
    public PlayerLedger withDeepDive(long seq) {
        return new PlayerLedger(grantedBlueprints, trackedNations, treasury, seq);
    }

    public PlayerLedger withTrackedAdded(String nationId) {
        Set<String> next = new LinkedHashSet<>(trackedNations);
        next.add(nationId);
        return withTracked(next);
    }

    public PlayerLedger withTrackedRemoved(String nationId) {
        Set<String> next = new LinkedHashSet<>(trackedNations);
        next.remove(nationId);
        return withTracked(next);
    }

    /** 给存档/日志看的一行摘要。 */
    public String describe() {
        return "blueprints=" + grantedBlueprints + " tracked=" + trackedNations
                + " treasury=" + String.format(java.util.Locale.ROOT, "%.0f", treasury);
    }

    /** 给 NBT 读回：列表 → 集合（保持顺序）。 */
    public static Set<String> toSet(List<String> list) {
        return list == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(list));
    }

    public static List<String> toList(Set<String> set) {
        return set == null ? List.of() : List.copyOf(set);
    }
}
