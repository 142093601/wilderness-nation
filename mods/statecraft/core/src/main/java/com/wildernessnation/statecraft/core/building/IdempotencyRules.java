package com.wildernessnation.statecraft.core.building;

import com.wildernessnation.statecraft.core.model.Building;

/**
 * 幂等决策（`NATIONS.md` §10.3 / §10.4）。
 *
 * <p>§10.3 的原话："区块重载 / 服务器重启后：校验 `staffUUID` 存在、是本职村民、且 `buildingId` 匹配
 * → 匹配则**不生成**；不匹配则按冷却重生成"，外加一句"**同一 `seq` 不重复生成**"。
 * §10.4 补了两种损失：村民死亡（→ 停摆 + 冷却后重雇）、锚点没了（→ 登记失效，村民保留）。
 *
 * <p>判定的**顺序**是有讲究的：
 * <ol>
 *   <li><strong>锚点先判</strong>：房子都没了，"村民还在不在"就不重要了 —— 结果是"失效"而不是"重生"；
 *   <li>三件校验全过 → {@code SKIP}（这是幂等的正常路径，绝大多数调用都走这里）；
 *   <li>需要重生时再看**同一 seq** 与**冷却**：同一次结算只做一次，冷却期内排队。
 * </ol>
 */
public final class IdempotencyRules {

    private final IdempotencyConfig cfg;

    public IdempotencyRules(IdempotencyConfig cfg) {
        this.cfg = cfg.validated();
    }

    /**
     * @param building 建筑登记
     * @param observed mc 层观察到的现状
     * @param seq      当前结算序号
     * @param hours    当前累计在线小时（节流用它，**不是**用 seq）
     */
    public MaterializationDecision decide(
            Building building, StaffObservation observed, long seq, double hours) {
        if (building == null || observed == null) {
            throw new IllegalArgumentException("building 与 observed 都不能为 null");
        }
        if (seq < 0 || !(hours >= 0.0) || !Double.isFinite(hours)) {
            throw new IllegalArgumentException("seq/hours 非法：" + seq + " / " + hours);
        }

        if (!observed.anchorIntact()) {
            return MaterializationDecision.MARK_INVALID;
        }
        if (observed.bindingHealthy()) {
            return MaterializationDecision.SKIP;      // §10.3 幂等：匹配就不生成
        }
        if (building.materializedAtSeq() == seq) {
            return MaterializationDecision.SKIP;      // §10.3：同一 seq 不重复生成
        }
        if (building.everMaterialized()) {
            double cooldown = cooldownFor(observed);
            if (hours - building.materializedAtHours() < cooldown) {
                return MaterializationDecision.WAIT_COOLDOWN;
            }
        }
        return MaterializationDecision.RESPAWN;
    }

    /**
     * 冷却取哪一个：**村民不在**（死了/被清除）用 §10.4 的"重新雇用"冷却；
     * **村民在但不对**（职业错/绑定错）用 §十二 的实体化间隔 ——
     * 后者不是"损失"，只是要把这个冒牌货换掉。
     */
    private double cooldownFor(StaffObservation observed) {
        return observed.staffExists()
                ? cfg.materializationCooldownHours()
                : cfg.rehireCooldownHours();
    }
}
