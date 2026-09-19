package com.wildernessnation.statecraft.core.settle;

/**
 * 一次结算的输入（`NATIONS.md` §七：**按累计在线时间，不在线不推进**）。
 *
 * <p>{@code elapsedHoursDelta} 是这段时间**真的在线**了多少小时——调用方（mod 层）负责
 * 只在有人在线时累加。core 不做"服务器开着的时长"这种判断，那一层没有世界读数。
 *
 * @param trigger            触发来源
 * @param elapsedHoursDelta  本次结算代表多少累计在线小时（必须 > 0）
 */
public record SettlementInput(SettlementTrigger trigger, double elapsedHoursDelta) {

    public SettlementInput {
        if (trigger == null) {
            throw new IllegalArgumentException("SettlementInput.trigger 不能为 null");
        }
        if (!(elapsedHoursDelta > 0.0) || !Double.isFinite(elapsedHoursDelta)) {
            throw new IllegalArgumentException(
                    "SettlementInput.elapsedHoursDelta 必须 > 0 且有限：" + elapsedHoursDelta);
        }
    }

    /** 一次标准的周期小结算。 */
    public static SettlementInput periodic(SettlementConfig cfg) {
        return new SettlementInput(SettlementTrigger.PERIODIC, cfg.periodicHours());
    }

    /** 一次时代推进；{@code hours} 是当前时代实际走过的小时数。 */
    public static SettlementInput eraAdvance(double hours) {
        return new SettlementInput(SettlementTrigger.ERA_ADVANCE, hours);
    }
}
