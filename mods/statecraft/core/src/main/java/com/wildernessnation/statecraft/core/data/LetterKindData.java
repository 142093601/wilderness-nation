package com.wildernessnation.statecraft.core.data;

import com.wildernessnation.statecraft.core.letter.LetterKind;

/**
 * 一种国书的定义（`NATIONS.md` §十六 `letters/*.json`：条件、期限、接受/拒绝后果）。
 *
 * <p>**只管"这种国书长什么样、要什么价"**：期限与各项态度增减是全局配置
 * （{@link com.wildernessnation.statecraft.core.letter.LetterConfig}），条件由规则层判断，
 * 文案在 `events.json` 的模板里。三者分开，改文案不会牵动数值、改数值不用碰代码。
 *
 * @param kind        种类（{@link LetterKind} 的枚举名）
 * @param name        中文名（情报册里显示"停止在边境建造"这类）
 * @param radius      默认半径（只有"停止建造"用；其余为 0）
 * @param amount      默认要价（只有"朝贡"用；其余为 0）
 */
public record LetterKindData(
        LetterKind kind,
        String name,
        double radius,
        double amount) {

    public LetterKindData {
        if (kind == null) {
            throw new IllegalArgumentException("LetterKindData.kind 不能为 null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("LetterKindData.name 不能为空");
        }
        if (!(radius >= 0.0) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("半径必须 >= 0 且有限：" + radius);
        }
        if (!(amount >= 0.0) || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("要价必须 >= 0 且有限：" + amount);
        }
        // 和 Letter 的按种类校验对齐：该有的必须有，不该有的必须是 0
        if (kind == LetterKind.STOP_BUILDING && radius <= 0.0) {
            throw new IllegalArgumentException("停止建造类国书必须给半径");
        }
        if (kind == LetterKind.TRIBUTE && amount <= 0.0) {
            throw new IllegalArgumentException("朝贡类国书必须给要价");
        }
    }
}
