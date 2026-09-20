package com.wildernessnation.statecraft.core.building;

import com.wildernessnation.statecraft.core.model.Building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 实体化节流（`NATIONS.md` §十二："同一次结算最多 1 处；两次间隔 ≥5 分钟；**其余排队**"）。
 *
 * <p>"排队"不需要一个真的队列对象：候选建筑还在登记表里躺着，下次结算会重新过一遍
 * {@link IdempotencyRules}，冷却到了自然会被选中。所以这里只回答"**这一次结算允许哪几处**"。
 *
 * <p>选谁先：按建筑 id 升序 —— 确定性的（同 seed 同输入必得同顺序），
 * 而不是"看 HashMap 迭代顺序"那种偶发行为。
 */
public final class MaterializationThrottle {

    private final IdempotencyConfig cfg;

    public MaterializationThrottle(IdempotencyConfig cfg) {
        this.cfg = cfg.validated();
    }

    /**
     * @param all                世界上**所有**建筑登记（用来找"上一次实体化"是多久以前 —— 间隔是全局限的）
     * @param eligible           这一步已经通过 {@link IdempotencyRules} 判定为 RESPAWN 的建筑
     * @param hours              当前累计在线小时
     * @param alreadyThisSettle  本次结算已经实体化了几处（同一次结算要封顶）
     * @return 允许在本次结算实体化的建筑（最多 {@code maxPerSettlement} 处，按 id 升序）
     */
    public List<Building> select(
            List<Building> all, List<Building> eligible, double hours, int alreadyThisSettle) {
        if (all == null || eligible == null) {
            throw new IllegalArgumentException("all 与 eligible 都不能为 null");
        }
        if (!(hours >= 0.0) || !Double.isFinite(hours) || alreadyThisSettle < 0) {
            throw new IllegalArgumentException("hours/alreadyThisSettle 非法：" + hours + " / "
                    + alreadyThisSettle);
        }
        int budget = cfg.maxPerSettlement() - alreadyThisSettle;
        if (budget <= 0 || eligible.isEmpty()) {
            return List.of();
        }
        if (withinGlobalInterval(all, hours)) {
            return List.of();
        }
        List<Building> sorted = new ArrayList<>(eligible);
        sorted.sort(Comparator.comparing(Building::id));
        return List.copyOf(sorted.subList(0, Math.min(budget, sorted.size())));
    }

    /** 全局间隔：离"上一次实体化"不足 §十二 的间隔就先不做（"其余排队"）。 */
    private boolean withinGlobalInterval(List<Building> all, double hours) {
        double last = -1.0;
        for (Building b : all) {
            if (b.everMaterialized()) {
                last = Math.max(last, b.materializedAtHours());
            }
        }
        return last >= 0.0 && hours - last < cfg.materializationCooldownHours();
    }
}
