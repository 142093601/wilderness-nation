package com.wildernessnation.statecraft.core.staff;

import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.building.StaffObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把"世界看到了什么"翻译成 §10.3 的三件校验（**村民绑定的接线处**）。
 *
 * <h2>为什么这一段必须在 core</h2>
 *
 * <p>{@link StaffObservation} 只有四个布尔，是**判定结果**。如果由 mod 层自己算出
 * "它是本职村民吗""它身上记的 buildingId 对不对"，那条规则就跑到 mc 层去了——
 * 而它恰恰是"不做对会生出三个文书"的那条（§10.3），必须能在 JUnit 里钉死。
 * 所以 mod 层只报告**原始事实**（那个实体是什么职业、身上记着哪个 buildingId、锚点还在不在），
 * 判定在这里做。
 *
 * <h2>两个方向都要对</h2>
 *
 * <p>"绑定对不对"要同时看建筑要什么（{@link BuildingDef#staffProfession()}）和
 * 村民说自己是干什么的（{@link StaffDef#buildingType()}）。只看一边就会出现
 * "刷出一个文书，但这座建筑其实是仓库"这种错——它不会崩，只会安静地把功能接错。
 */
public final class StaffBinding {

    private StaffBinding() {}

    /**
     * 世界看到的那一个实体（mod 层的原始读数）。
     *
     * @param uuid         实体 UUID（字符串形式；core 不碰 {@code java.util.UUID}）
     * @param professionId 它的职业 id（mod 层从实体上读出来的，不是推断的）
     * @param buildingId   它身上记的 buildingId；没记就是空串
     */
    public record StaffIdentity(String uuid, String professionId, String buildingId) {

        public StaffIdentity {
            requireText(uuid, "StaffIdentity.uuid");
            requireText(professionId, "StaffIdentity.professionId");
            buildingId = buildingId == null ? "" : buildingId;
        }
    }

    /**
     * 把原始读数变成 §10.3 的四个布尔。
     *
     * @param def          建筑定义（要哪个职业）
     * @param buildingId   这座建筑在存档里的 id（村民身上该记的就是它）
     * @param seen         按 {@code staffUUID} 找到的那个实体；不在（死亡/被感染/被清除）就是空
     * @param anchorIntact 锚点方块还在吗（§10.4 的"建筑被拆"）
     */
    public static StaffObservation observe(
            BuildingDef def, String buildingId, Optional<StaffIdentity> seen,
            boolean anchorIntact) {
        if (def == null) {
            throw new IllegalArgumentException("BuildingDef 不能为 null");
        }
        requireText(buildingId, "buildingId");
        if (seen == null) {
            throw new IllegalArgumentException(
                    "seen 用 Optional.empty() 表示「没找到」，不要传 null");
        }
        if (seen.isEmpty()) {
            return new StaffObservation(false, false, false, anchorIntact);
        }
        StaffIdentity id = seen.get();
        return new StaffObservation(
                true,
                id.professionId().equals(def.staffProfession()),
                id.buildingId().equals(buildingId),
                anchorIntact);
    }

    /** 三件校验里**没过**的那些，逐条写成给玩家/日志看的中文（空 = 全过）。 */
    public static List<String> describeMismatch(BuildingDef def, StaffObservation obs) {
        List<String> out = new ArrayList<>();
        if (!obs.anchorIntact()) {
            out.add("锚点方块没了（建筑算被拆）");
        }
        if (!obs.staffExists()) {
            out.add("登记的" + def.name() + "村民不在了");
            return out;      // 人都没了，后两项没有意义（不然会给玩家三条自相矛盾的话）
        }
        if (!obs.rightProfession()) {
            out.add("站在那里的人不是" + def.name() + "要的「"
                    + def.staffProfession() + "」");
        }
        if (!obs.buildingIdMatches()) {
            out.add("那个村民身上记的不是这座建筑（多半是从别处挪过来的）");
        }
        return out;
    }

    /**
     * 建筑与村民定义对不对得上（内容层的交叉校验，比 {@code StaffCatalog.crossCheck} 更细一档：
     * 那条查的是"id 存不存在"，这条查的是"两边说的是不是同一件事"）。
     *
     * <p>能力是**包含**关系而不是相等：建筑满级开放的能力，村民必须全会；
     * 村民多会一点不算错（同一个文书以后也许还管别的建筑）。
     */
    public static boolean matches(BuildingDef def, StaffDef staff) {
        return def.staffProfession().equals(staff.id())
                && def.id().equals(staff.buildingType())
                && staff.abilities().containsAll(def.abilities());
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }
}
