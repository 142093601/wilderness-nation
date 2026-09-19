package com.wildernessnation.statecraft.core.building;

/**
 * mc 层观察到的"这座建筑现在到底怎么样了"（`NATIONS.md` §10.3 的校验三件套 + §10.4 的锚点）。
 *
 * <p>规则层只吃这四个布尔 —— **不碰 UUID、不碰职业枚举、不碰世界**。
 * "这个 UUID 在不在""它是本职村民吗""它身上记的 buildingId 对不对"都由 mc 层判好再递进来，
 * 这样 §10.3 那套幂等规则就能在 JUnit 里被钉死。
 *
 * @param staffExists      登记的 {@code staffUUID} 对应的实体还在不在
 * @param rightProfession  它是这座建筑要的那种村民吗
 * @param buildingIdMatches 它身上记的 buildingId 与这座建筑一致吗
 * @param anchorIntact     锚点还在吗（§10.4：建筑被拆 → 登记为失效，但村民保留）
 */
public record StaffObservation(
        boolean staffExists,
        boolean rightProfession,
        boolean buildingIdMatches,
        boolean anchorIntact) {

    /** 一切正常：村民在、职业对、绑定对。 */
    public static StaffObservation healthy() {
        return new StaffObservation(true, true, true, true);
    }

    /** 锚点没了。 */
    public static StaffObservation anchorGone() {
        return new StaffObservation(false, false, false, false);
    }

    /** 村民不在（死亡/被感染/被清除）。 */
    public static StaffObservation staffGone() {
        return new StaffObservation(false, false, false, true);
    }

    /** §10.3 的三件校验是否全部通过（锚点单独判，因为它决定"失效"而不是"重生"）。 */
    public boolean bindingHealthy() {
        return staffExists && rightProfession && buildingIdMatches;
    }
}
