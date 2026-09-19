package com.wildernessnation.statecraft.core.building;

/**
 * 实体化节流与重雇冷却的参数（`NATIONS.md` §十二 + §10.4）。
 *
 * @param materializationCooldownHours §十二「两次间隔 **≥5 分钟**」。
 *                                     **换算说明**：core 的时钟是"累计在线小时"（§七 的口径），
 *                                     所以 5 分钟 = {@code 5/60} 小时 —— 不能用结算序号去比（那是 2 小时一步）。
 * @param maxPerSettlement             §十二「同一次结算最多 **1 处**」
 * @param rehireCooldownHours          §10.4「村民死亡 → 冷却后可**重新雇用**」。
 *                                     设计**没给**这个冷却 → 【占位】
 */
public record IdempotencyConfig(
        double materializationCooldownHours,
        int maxPerSettlement,
        double rehireCooldownHours) {

    public static IdempotencyConfig defaults() {
        return new IdempotencyConfig(5.0 / 60.0, 1, 1.0);
    }

    public IdempotencyConfig {
        if (!(materializationCooldownHours >= 0.0) || !Double.isFinite(materializationCooldownHours)) {
            throw new IllegalArgumentException(
                    "materializationCooldownHours 必须 >= 0 且有限：" + materializationCooldownHours);
        }
        if (maxPerSettlement < 0) {
            throw new IllegalArgumentException("maxPerSettlement 不能为负：" + maxPerSettlement);
        }
        if (!(rehireCooldownHours >= 0.0) || !Double.isFinite(rehireCooldownHours)) {
            throw new IllegalArgumentException(
                    "rehireCooldownHours 必须 >= 0 且有限：" + rehireCooldownHours);
        }
    }

    public IdempotencyConfig validated() {
        return this;
    }
}
