package com.wildernessnation.statecraft.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.env.EnvironRequirement;
import com.wildernessnation.statecraft.core.env.EnvironmentSnapshot;
import com.wildernessnation.statecraft.core.env.EnvironmentVerdict;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 建筑定义（`buildings.json` 的 core 侧类型）。
 *
 * <p>重点钉三件事：
 * <ol>
 *   <li><strong>能力挂在档上</strong>——一级情报站不能声称自己会深挖（§11.1 的第三档能力）；</li>
 *   <li><strong>档位顺序就是语义</strong>——§10.5 取"最后一个被满足的档"，所以顺序不能被排序弄丢；</li>
 *   <li><strong>授权与价钱必须成对</strong>——"要授权但标价 0""不要授权却标了价"都是内容写错。</li>
 * </ol>
 */
class BuildingDefTest {

    private static EnvironRequirement tier(String id, double enclosure, int volume) {
        return new EnvironRequirement(id, true, enclosure, volume, true);
    }

    /** 情报站：basic 给外交，upgraded 才给追踪/深挖。 */
    private static BuildingDef station() {
        return new BuildingDef("intel_station", "情报站", "minecraft:lectern",
                "statecraft:intel_station",
                List.of(tier("basic", 60.0, 12), tier("upgraded", 75.0, 40)),
                List.of(Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY,
                                BuildingAbility.DECLARATION),
                        Set.of(BuildingAbility.TRACK_NATIONS, BuildingAbility.SEE_WARS,
                                BuildingAbility.DEEP_DIVE, BuildingAbility.UPGRADE)),
                "scribe", "intel_station", 64.0);
    }

    @Test
    void abilitiesAreCumulativeAndLevelDependent() {
        BuildingDef def = station();
        assertEquals(Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY,
                        BuildingAbility.DECLARATION),
                def.abilitiesAt(1), "一级只有第一档的能力");
        assertFalse(def.hasAbilityAt(1, BuildingAbility.DEEP_DIVE),
                "一级情报站不能深挖——这正是「界面按钮在但能力没开」的来源");
        assertTrue(def.hasAbilityAt(2, BuildingAbility.DEEP_DIVE));
        assertTrue(def.abilities().containsAll(def.abilitiesAt(1)), "高档必须包含低档的全部能力");
        assertEquals(7, def.abilities().size());
        assertEquals(def.abilities(), def.abilitiesAt(99), "越界钳到最高档");
        assertEquals(def.abilitiesAt(1), def.abilitiesAt(0), "越界钳到最低档");
    }

    @Test
    void tierOrderIsTheSemanticsAndIsPreserved() {
        BuildingDef def = station();
        assertEquals(List.of("basic", "upgraded"), def.tierIds());
        assertEquals("basic", def.baseTier().tierId());
        assertEquals("upgraded", def.topTier().tierId());
        assertEquals(2, def.tierCount());
        assertEquals(1, def.levelOf("basic").orElseThrow());
        assertEquals(2, def.levelOf("upgraded").orElseThrow());
        assertEquals(java.util.Optional.empty(), def.levelOf("传说"),
                "存档里的等级来自旧数据时要能给空，由调用方决定怎么处理");
        assertEquals("upgraded", def.tierForLevel(2).tierId());
        assertEquals("upgraded", def.tierForLevel(999).tierId(), "越界钳到最高档");
        assertTrue(def.canUpgradeFrom(1));
        assertFalse(def.canUpgradeFrom(2));
    }

    /** 环境判定挂在定义上：档位表是它的一部分。 */
    @Test
    void evaluatePicksTheHighestSatisfiedTier() {
        BuildingDef def = station();
        EnvironmentVerdict small = def.evaluate(
                new EnvironmentSnapshot(true, 70.0, 20, true));
        assertEquals("basic", small.usableTierId().orElseThrow());
        assertEquals("upgraded", small.nextTierId().orElseThrow(), "下一步该补的就是升级条件");

        EnvironmentVerdict big = def.evaluate(
                new EnvironmentSnapshot(true, 80.0, 50, true));
        assertEquals("upgraded", big.usableTierId().orElseThrow());
        assertTrue(big.nextTierId().isEmpty(), "满了就没有下一档");
        assertTrue(big.unmet().isEmpty());
    }

    @Test
    void abilityTableMustMatchTheTierTable() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new BuildingDef("x", "X", "minecraft:stone", "",
                        List.of(tier("basic", 60.0, 12)), List.of(),
                        "scribe", "", 0.0));
        assertTrue(e.getMessage().contains("一一对应"), e.getMessage());
    }

    @Test
    void authorizationAndPriceMustAgree() {
        // 要授权却不标价 → 白送
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingDef("x", "X", "minecraft:stone", "",
                        List.of(tier("basic", 60.0, 12)), List.of(Set.of()),
                        "scribe", "x_blueprint", 0.0));
        // 不要授权却标了价 → 玩家会被收一笔不存在的钱
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingDef("x", "X", "minecraft:stone", "",
                        List.of(tier("basic", 60.0, 12)), List.of(Set.of()),
                        "scribe", "", 10.0));
        assertTrue(station().authorized());
    }

    @Test
    void duplicateTierIdsAndEmptyTierTablesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingDef("x", "X", "minecraft:stone", "",
                        List.of(tier("basic", 60.0, 12), tier("basic", 70.0, 20)),
                        List.of(Set.of(), Set.of()), "scribe", "", 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingDef("x", "X", "minecraft:stone", "",
                        List.of(), List.of(), "scribe", "", 0.0));
    }

    // ---- 目录 ----

    @Test
    void catalogKeepsFileOrderAndRejectsDuplicates() {
        BuildingDef a = new BuildingDef("a", "甲", "minecraft:stone", "", List.of(tier("basic", 0, 0)),
                List.of(Set.of()), "scribe", "", 0.0);
        BuildingDef b = new BuildingDef("b", "乙", "minecraft:stone", "", List.of(tier("basic", 0, 0)),
                List.of(Set.of()), "scribe", "b_blueprint", 5.0);
        Map<String, BuildingDef> byId = new java.util.LinkedHashMap<>();
        byId.put("a", a);
        byId.put("b", b);
        BuildingCatalog catalog = new BuildingCatalog(byId);

        assertEquals(List.of("a", "b"), catalog.all().stream().map(BuildingDef::id).toList(),
                "顺序必须等于文件顺序（不然同一份数据两次跑出两种顺序）");
        assertEquals(a, catalog.require("a"));
        assertEquals(List.of("b"), catalog.authorized().stream().map(BuildingDef::id).toList(),
                "建筑工的货架上只有要授权的那些");
        assertThrows(IllegalArgumentException.class, () -> catalog.require("c"));
        assertTrue(catalog.byId(null).isEmpty(), "null 查 id 不该抛");
        assertThrows(IllegalArgumentException.class,
                () -> new BuildingCatalog(Map.of()));
    }
}
