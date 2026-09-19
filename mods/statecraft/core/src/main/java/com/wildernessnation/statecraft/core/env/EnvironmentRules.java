package com.wildernessnation.statecraft.core.env;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 环境判定（`NATIONS.md` §10.5）。
 *
 * <p>**纯函数**：只吃 {@link EnvironmentSnapshot}（世界读数）与档位要求表，输出 {@link EnvironmentVerdict}。
 * 没有世界、没有方块、没有随机 —— 所以它能在 JUnit 里被钉死。
 *
 * <h2>档位怎么取</h2>
 * 档位按**升序**传入（数据侧保证，通常是 `buildings.json`）。判定规则只有一句：
 * **取最后一个被满足的档**。
 *
 * <p>刻意**不**假设"档位必须嵌套"、也不去校验它：档位语义是内容层的事，
 * 万一数据写成非嵌套的，结果只是"档位选得怪"，不会崩、也不会静默算错别的什么。
 * 真正必须成立的两件事才校验：**表不能为空**、**档位 id 不能重复**（那是数据错误，早报早好）。
 */
public final class EnvironmentRules {

    private EnvironmentRules() {}

    public static EnvironmentVerdict evaluate(
            List<EnvironRequirement> tiers, EnvironmentSnapshot snapshot) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("档位要求表不能为空");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("EnvironmentSnapshot 不能为 null");
        }
        Set<String> seen = new HashSet<>();
        for (EnvironRequirement tier : tiers) {
            if (!seen.add(tier.tierId())) {
                throw new IllegalArgumentException("档位 id 重复：" + tier.tierId());
            }
        }

        String usableTier = null;
        EnvironRequirement nextTier = null;
        for (EnvironRequirement tier : tiers) {
            if (nextTier == null && !tier.satisfiedBy(snapshot)) {
                nextTier = tier;                 // 第一个没满足的档 = 下一步该补的东西
            }
            if (nextTier == null) {
                usableTier = tier.tierId();       // 到这里为止都满足 → 它暂时是可用档
            }
        }

        List<String> unmet = nextTier == null ? List.of() : new ArrayList<>(nextTier.unmet(snapshot));
        return new EnvironmentVerdict(
                Optional.ofNullable(usableTier),
                Optional.ofNullable(nextTier == null ? null : nextTier.tierId()),
                unmet);
    }
}
