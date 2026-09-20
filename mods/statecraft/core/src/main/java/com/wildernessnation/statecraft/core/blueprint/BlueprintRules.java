package com.wildernessnation.statecraft.core.blueprint;

import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 图纸授权（`NATIONS.md` §10.1 的"建筑工 NPC 出售「图纸授权」"）。
 *
 * <h2>这条链路为什么必须被规则管住</h2>
 *
 * <pre>
 * 建筑工 NPC（卖授权）→ 玩家买到 → 用图纸加农炮打出壳（真实材料消耗）
 *    → 锚点方块就位 + 环境成立 → 幂等生成绑定村民
 * </pre>
 *
 * <p>"没买授权就放不了"是这条链路的**唯一门槛**：它把"由谁决定建什么"交回给内容层
 * （`buildings.json` 里 {@code requiredBlueprint} + {@code blueprintPrice}），
 * 而不是让玩家从 JEI 里直接搜到图纸就跳过一切。
 *
 * <h2>core 只判"够不够格"，不记账</h2>
 *
 * <p>**玩家侧的资源与已购清单不在 core 里**（`playerLedger` 属于计划 4/5，和外交那一套同一个理由）。
 * 所以这里收的是调用方递进来的两个事实："买过没有""钱包里有多少"，
 * 输出的是 {@link BlueprintVerdict}——**买得起就报出要扣多少，由调用方去记**。
 */
public final class BlueprintRules {

    private BlueprintRules() {}

    /**
     * 能不能买这份授权。
     *
     * @param def            建筑定义（它决定要哪份授权、多少钱）
     * @param alreadyGranted 这份授权已经买过了吗
     * @param playerTreasury 玩家方现有资源（core 不持有它，只拿来比价）
     */
    public static BlueprintVerdict buy(
            BuildingDef def, boolean alreadyGranted, double playerTreasury) {
        if (def == null) {
            throw new IllegalArgumentException("BuildingDef 不能为 null");
        }
        if (!Double.isFinite(playerTreasury)) {
            throw new IllegalArgumentException("playerTreasury 必须是有限数：" + playerTreasury);
        }
        if (!def.authorized()) {
            return BlueprintVerdict.rejected(def, "「" + def.name() + "」不需要图纸授权，直接动工就行");
        }
        if (alreadyGranted) {
            return BlueprintVerdict.rejected(def, "你已经买过「" + def.name() + "」的图纸授权了");
        }
        if (playerTreasury < def.blueprintPrice()) {
            return BlueprintVerdict.rejected(def, "资源不够：「" + def.name() + "」的授权要 "
                    + trim(def.blueprintPrice()) + "，你只有 " + trim(playerTreasury));
        }
        return BlueprintVerdict.granted(def, "买下了「" + def.name() + "」的图纸授权",
                -def.blueprintPrice());
    }

    /**
     * 现在能不能把这座建筑的壳打出来（图纸加农炮开火前问这一句）。
     *
     * <p>不需要授权的建筑永远通过——**这一条很重要**：§10.6 说 v1 之后"其余是加数据"，
     * 那些后续建筑也许根本不该被授权门槛卡住，所以门禁必须由数据决定，不能写死"所有建筑都要授权"。
     */
    public static BlueprintVerdict canBuild(BuildingDef def, boolean granted) {
        if (def == null) {
            throw new IllegalArgumentException("BuildingDef 不能为 null");
        }
        if (!def.authorized()) {
            return BlueprintVerdict.granted(def, "「" + def.name() + "」不需要图纸授权", 0.0);
        }
        if (!granted) {
            return BlueprintVerdict.rejected(def, "还没有「" + def.name()
                    + "」的图纸授权，先去找建筑工买（要 " + trim(def.blueprintPrice()) + "）");
        }
        return BlueprintVerdict.granted(def, "可以动工：" + def.name(), 0.0);
    }

    /**
     * 建筑工 NPC 的货架（还没买过的那些）。
     *
     * <p>顺序 = `buildings.json` 的顺序，所以同一个存档每次打开看到的是同一个货架
     * （"同 seed 同输入必得同结果"在界面上也成立）。
     */
    public static List<BuildingDef> shelf(BuildingCatalog catalog, Set<String> granted) {
        if (catalog == null) {
            throw new IllegalArgumentException("BuildingCatalog 不能为 null");
        }
        Set<String> owned = granted == null ? Set.of() : new LinkedHashSet<>(granted);
        List<BuildingDef> out = new ArrayList<>();
        for (BuildingDef def : catalog.authorized()) {
            if (!owned.contains(def.requiredBlueprint())) {
                out.add(def);
            }
        }
        return out;
    }

    private static String trim(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
