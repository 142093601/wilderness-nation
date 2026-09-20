package com.wildernessnation.statecraft.core.building;

import com.wildernessnation.statecraft.core.env.EnvironRequirement;
import com.wildernessnation.statecraft.core.env.EnvironmentRules;
import com.wildernessnation.statecraft.core.env.EnvironmentSnapshot;
import com.wildernessnation.statecraft.core.env.EnvironmentVerdict;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 一座建筑的定义（`NATIONS.md` §10.1 / §十六 的 `buildings.json`）。
 *
 * <p>落地的是那句话："**图纸是壳，模块是核**"——壳（图纸 + 锚点方块 + 环境档位）与
 * 核（绑定的村民职业 + 能力）都在这一份定义里，代码只认这份数据。
 *
 * <p><strong>core 只存 block id 字符串，不认任何 mc 类型</strong>：锚点方块、图纸资源路径
 * 在这里都是不透明的字符串，由 mod 层去解释。这样这一层仍然纯逻辑、可 JUnit。
 *
 * <h2>能力为什么挂在"档"上，而不是挂在建筑上</h2>
 *
 * <p>§11.1 里第一档与第三档的能力**不是同一批**（情报站升级之后才"可追踪 +2 · 每结算深挖 1 国"）。
 * 挂在建筑上的话，一座一级情报站也会声称自己会深挖——那正是"界面按钮在但能力没开"的来源。
 * 所以 {@code tierAbilities} 与 {@code tiers} **一一对应**，
 * {@link #abilitiesAt(int)} 取的是**累积**（第 N 档包含前面所有档的能力）。
 *
 * @param id                建筑类型标识（{@code Building.type()} 用的就是它）
 * @param name              中文名（情报册/对话里显示）
 * @param anchorBlock       锚点方块 id（就位才允许实体化，§10.1）
 * @param schematic         图纸资源路径（{@code .nbt} 的包内路径）；空串 = 还没配
 * @param tiers             **升序**的环境档位（§10.5：取最后一个被满足的档）
 * @param tierAbilities     与 {@code tiers} 一一对应的能力集合
 * @param staffProfession   这座建筑绑定的村民职业（§10.2）
 * @param requiredBlueprint 需要哪份图纸授权才能动工；空串 = 不需要授权
 * @param blueprintPrice    图纸授权的价钱（玩家侧记账，core 只报数）
 */
public record BuildingDef(
        String id,
        String name,
        String anchorBlock,
        String schematic,
        List<EnvironRequirement> tiers,
        List<Set<String>> tierAbilities,
        String staffProfession,
        String requiredBlueprint,
        double blueprintPrice) {

    public BuildingDef {
        requireText(id, "BuildingDef.id");
        requireText(name, "BuildingDef.name");
        requireText(anchorBlock, "BuildingDef.anchorBlock");
        requireText(staffProfession, "BuildingDef.staffProfession");
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("BuildingDef.tiers 不能为空：" + id);
        }
        tiers = List.copyOf(tiers);
        if (tierAbilities == null || tierAbilities.size() != tiers.size()) {
            throw new IllegalArgumentException("BuildingDef「" + id + "」的能力表必须与档位表一一对应："
                    + tiers.size() + " 档 vs " + (tierAbilities == null ? "null" : tierAbilities.size()));
        }
        List<Set<String>> abilities = new ArrayList<>(tierAbilities.size());
        for (Set<String> set : tierAbilities) {
            abilities.add(set == null ? Set.of() : Set.copyOf(set));
        }
        tierAbilities = List.copyOf(abilities);

        Set<String> tierIds = new LinkedHashSet<>();
        for (EnvironRequirement tier : tiers) {
            if (!tierIds.add(tier.tierId())) {
                throw new IllegalArgumentException(
                        "BuildingDef「" + id + "」的档位 id 重复：" + tier.tierId());
            }
        }
        schematic = schematic == null ? "" : schematic;
        requiredBlueprint = requiredBlueprint == null ? "" : requiredBlueprint;
        if (!(blueprintPrice >= 0.0) || !Double.isFinite(blueprintPrice)) {
            throw new IllegalArgumentException("blueprintPrice 必须 >= 0 且有限：" + blueprintPrice);
        }
        // 要授权的建筑必须给个价；不要授权的必须是 0 —— 防止"免费送"和"要授权但标价 0"两种笔误
        if (requiredBlueprint.isEmpty() && blueprintPrice != 0.0) {
            throw new IllegalArgumentException(
                    "BuildingDef「" + id + "」不需要授权，价钱却是 " + blueprintPrice);
        }
        if (!requiredBlueprint.isEmpty() && blueprintPrice <= 0.0) {
            throw new IllegalArgumentException(
                    "BuildingDef「" + id + "」要授权，价钱必须 > 0，实际 " + blueprintPrice);
        }
    }

    /** 最低那一档（"能不能用"），校验保证一定存在。 */
    public EnvironRequirement baseTier() {
        return tiers.get(0);
    }

    /** 最高那一档（升级链的尽头）。 */
    public EnvironRequirement topTier() {
        return tiers.get(tiers.size() - 1);
    }

    /** 这座建筑需要图纸授权吗。 */
    public boolean authorized() {
        return !requiredBlueprint.isEmpty();
    }

    public int tierCount() {
        return tiers.size();
    }

    /** 第 level 档（1 起）为止的**全部**能力。越界钳到两端。 */
    public Set<String> abilitiesAt(int level) {
        int upto = Math.max(1, Math.min(tiers.size(), level));
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < upto; i++) {
            out.addAll(tierAbilities.get(i));
        }
        return Set.copyOf(out);
    }

    /** 满级时的全部能力（内容层交叉校验用，也是"这座建筑最终能干什么"的答案）。 */
    public Set<String> abilities() {
        return abilitiesAt(tiers.size());
    }

    public boolean hasAbilityAt(int level, String ability) {
        return abilitiesAt(level).contains(ability);
    }

    /** 环境判定直接挂在定义上：档位表是它的一部分，不该在调用点各自传一遍。 */
    public EnvironmentVerdict evaluate(EnvironmentSnapshot snapshot) {
        return EnvironmentRules.evaluate(tiers, snapshot);
    }

    /**
     * 档位 id → 建筑等级（1 起）。{@code Building.level()} 存的是这个数。
     *
     * <p>找不到就返回空：那说明存档里的等级来自另一份 `buildings.json`（内容改过），
     * 是"数据换了"，不是"建筑坏了"，所以由调用方决定怎么处理（通常是按 1 档算）。
     */
    public Optional<Integer> levelOf(String tierId) {
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).tierId().equals(tierId)) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
    }

    /** 某个等级对应的档位（越界钳到两端，和 `EraTable.byOrdinal` 一个手感）。 */
    public EnvironRequirement tierForLevel(int level) {
        int idx = Math.max(0, Math.min(tiers.size() - 1, level - 1));
        return tiers.get(idx);
    }

    /** 这一档之上还有更高档吗（情报站的"对锚点用升级件"就是问这个）。 */
    public boolean canUpgradeFrom(int level) {
        return level < tiers.size();
    }

    /** 逐档的 id（给情报册/对话显示升级链）。 */
    public List<String> tierIds() {
        List<String> out = new ArrayList<>(tiers.size());
        for (EnvironRequirement tier : tiers) {
            out.add(tier.tierId());
        }
        return out;
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }
}
