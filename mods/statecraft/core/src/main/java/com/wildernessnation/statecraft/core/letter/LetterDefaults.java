package com.wildernessnation.statecraft.core.letter;

/**
 * 三种国书**内容层**的要价/半径（`letters.json` 里的 `kinds`）。
 *
 * <p>为什么和 {@link LetterConfig} 分开：那一份是**数值旋钮**（期限、各项态度增减），
 * 是调平衡用的；这一份是**内容**（"要 64 格""要 120 资源"），改它等于改游戏内容。
 * 两者都会在试玩里动，但动的理由不同，混在一起以后没人分得清哪个是哪个。
 *
 * <p>为什么由调用方（mod 层）从数据文件构造、而不是 core 自己读：
 * core 里没有 JSON 解析（零依赖是硬纪律），而且"哪座建筑要什么价"本来就该在数据里。
 *
 * @param stopBuildRadius 停止建造类国书的半径（格）
 * @param tributeAmount   朝贡类国书的要价
 */
public record LetterDefaults(double stopBuildRadius, double tributeAmount) {

    public LetterDefaults {
        if (!(stopBuildRadius > 0.0) || !Double.isFinite(stopBuildRadius)) {
            throw new IllegalArgumentException(
                    "stopBuildRadius 必须 > 0 且有限（国书总得划个范围）：" + stopBuildRadius);
        }
        if (!(tributeAmount > 0.0) || !Double.isFinite(tributeAmount)) {
            throw new IllegalArgumentException(
                    "tributeAmount 必须 > 0 且有限（国书总得要点东西）：" + tributeAmount);
        }
    }

    /** 只给"停止建造"用；其余种类没有半径（与 {@link Letter} 的按种类校验对齐）。 */
    public double radiusFor(LetterKind kind) {
        return kind == LetterKind.STOP_BUILDING ? stopBuildRadius : 0.0;
    }

    /** 只给"朝贡"用。 */
    public double amountFor(LetterKind kind) {
        return kind == LetterKind.TRIBUTE ? tributeAmount : 0.0;
    }
}
