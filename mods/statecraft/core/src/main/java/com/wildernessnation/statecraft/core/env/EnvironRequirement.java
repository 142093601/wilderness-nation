package com.wildernessnation.statecraft.core.env;

import java.util.ArrayList;
import java.util.List;

/**
 * 一档环境要求（`NATIONS.md` §10.5 / §十二）。
 *
 * <p>**档位与阈值来自数据**（以后是 `buildings.json`），core 不写死"三档"：
 * §10.5 只说环境决定"能不能用 / 到哪一档"，没说分几档、每档什么阈值。
 *
 * <p>默认那套（§十二）是：有屋顶 + 封闭度 ≥60% + 内部体积 ≥12 格 + 在领地内。
 *
 * @param tierId              档位标识（数据里给，如 {@code basic} / {@code upgraded}）
 * @param requireRoof         是否必须有屋顶
 * @param minEnclosurePercent 封闭度下限（0~100）
 * @param minVolume           内部体积下限（格）
 * @param requireClaim        是否必须在领地内
 */
public record EnvironRequirement(
        String tierId,
        boolean requireRoof,
        double minEnclosurePercent,
        int minVolume,
        boolean requireClaim) {

    public EnvironRequirement {
        if (tierId == null || tierId.isBlank()) {
            throw new IllegalArgumentException("EnvironRequirement.tierId 不能为空");
        }
        if (!(minEnclosurePercent >= 0.0 && minEnclosurePercent <= 100.0)
                || !Double.isFinite(minEnclosurePercent)) {
            throw new IllegalArgumentException(
                    "minEnclosurePercent 必须落在 0..100：" + minEnclosurePercent);
        }
        if (minVolume < 0) {
            throw new IllegalArgumentException("minVolume 不能为负：" + minVolume);
        }
    }

    /** §十二 的默认档（情报站用）。 */
    public static EnvironRequirement defaultBasic() {
        return new EnvironRequirement("basic", true, 60.0, 12, true);
    }

    /** 这一档**不满足**的地方，逐条写成给玩家看的中文（空列表 = 全满足）。 */
    public List<String> unmet(EnvironmentSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        if (requireRoof && !snapshot.hasRoof()) {
            reasons.add("没有屋顶");
        }
        if (snapshot.enclosurePercent() < minEnclosurePercent) {
            reasons.add(String.format(java.util.Locale.ROOT, "封闭度 %.1f%% < %.1f%%",
                    snapshot.enclosurePercent(), minEnclosurePercent));
        }
        if (snapshot.volume() < minVolume) {
            reasons.add("内部体积 " + snapshot.volume() + " < " + minVolume + " 格");
        }
        if (requireClaim && !snapshot.insideClaim()) {
            reasons.add("不在领地内");
        }
        return reasons;
    }

    public boolean satisfiedBy(EnvironmentSnapshot snapshot) {
        return unmet(snapshot).isEmpty();
    }
}
