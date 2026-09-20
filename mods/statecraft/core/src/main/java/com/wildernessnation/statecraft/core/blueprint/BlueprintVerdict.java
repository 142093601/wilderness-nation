package com.wildernessnation.statecraft.core.blueprint;

import com.wildernessnation.statecraft.core.building.BuildingDef;

/**
 * 一次图纸授权判定（买 / 动工）。
 *
 * <p>和 {@code DiplomacyOutcome}、{@code LetterResult} 同一条纪律：
 * **前置不满足是"被拒绝"这种正常结果，不是异常**——玩家会看到一句中文解释。
 *
 * @param allowed             成立吗
 * @param buildingId          判的是哪座建筑
 * @param blueprintId         涉及的授权 id（不需要授权时为空串）
 * @param reason              中文说明（直接给对话框显示）
 * @param playerTreasuryDelta 玩家方资源的净变化（买下时是负数）；core 只报数，账本在调用方
 */
public record BlueprintVerdict(
        boolean allowed,
        String buildingId,
        String blueprintId,
        String reason,
        double playerTreasuryDelta) {

    public BlueprintVerdict {
        requireText(buildingId, "BlueprintVerdict.buildingId");
        requireText(reason, "BlueprintVerdict.reason");
        blueprintId = blueprintId == null ? "" : blueprintId;
        if (!Double.isFinite(playerTreasuryDelta)) {
            throw new IllegalArgumentException(
                    "BlueprintVerdict.playerTreasuryDelta 必须有限：" + playerTreasuryDelta);
        }
        if (!allowed && playerTreasuryDelta != 0.0) {
            throw new IllegalArgumentException("被拒绝的判定不该带资源变化：" + playerTreasuryDelta);
        }
    }

    static BlueprintVerdict granted(BuildingDef def, String reason, double delta) {
        return new BlueprintVerdict(true, def.id(), def.requiredBlueprint(), reason, delta);
    }

    static BlueprintVerdict rejected(BuildingDef def, String reason) {
        return new BlueprintVerdict(false, def.id(), def.requiredBlueprint(), reason, 0.0);
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }
}
