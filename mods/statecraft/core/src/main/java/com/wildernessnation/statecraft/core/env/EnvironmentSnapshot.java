package com.wildernessnation.statecraft.core.env;

/**
 * 建筑环境快照（`NATIONS.md` §10.5）：**由 mc 层量出来的四个读数**，规则层只看它。
 *
 * <h2>这个类型是"不做形状检测"的结构性保证</h2>
 *
 * §10.5 的硬要求是"**软性判定，不做形状检测**"（挪一块砖就失效、跨区块误判、性能差、
 * 与"地标手建"的自由冲突）。所以这里**刻意只有四个数**：有没有屋顶、封闭度、内部体积、在不在领地内。
 *
 * <p><strong>没有方块列表、没有坐标、没有形状描述</strong> —— 于是规则层根本没有材料去做形状检测。
 * 这条不是靠注释提醒、也不是靠自觉：谁想加形状判定，会先撞上"这里根本没这个数据"。
 * 有一条用例把这个字段集合钉死了（加了新字段就会红，逼人先写清楚为什么）。
 *
 * @param hasRoof          头顶有没有遮挡
 * @param enclosurePercent 封闭度 0~100（由 mc 层量）
 * @param volume           内部可用体积（格）
 * @param insideClaim      是否在领地内
 */
public record EnvironmentSnapshot(
        boolean hasRoof,
        double enclosurePercent,
        int volume,
        boolean insideClaim) {

    public EnvironmentSnapshot {
        if (!(enclosurePercent >= 0.0 && enclosurePercent <= 100.0)
                || !Double.isFinite(enclosurePercent)) {      // NaN 也会落进来
            throw new IllegalArgumentException(
                    "enclosurePercent 必须落在 0..100：" + enclosurePercent);
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume 不能为负：" + volume);
        }
    }

    /** 给玩家看的一行读数（拒绝理由里也用它）。 */
    public String describe() {
        return String.format(java.util.Locale.ROOT,
                "屋顶=%s 封闭度=%.1f%% 体积=%d 领地=%s",
                hasRoof ? "有" : "无", enclosurePercent, volume, insideClaim ? "内" : "外");
    }
}
