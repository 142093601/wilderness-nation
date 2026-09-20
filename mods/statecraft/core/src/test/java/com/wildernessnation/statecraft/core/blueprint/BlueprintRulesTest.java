package com.wildernessnation.statecraft.core.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.env.EnvironRequirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 图纸授权（§10.1 的"建筑工 NPC 出售「图纸授权」"）。
 *
 * <p>这条门禁是"**建造不外包 / 每一块砖都是玩家自己放的**"之外的另一半保证：
 * 玩家不能跳过内容层直接搜到图纸就动工，也不能在没买授权时先打出壳再补票。
 */
class BlueprintRulesTest {

    private static BuildingDef building(String id, String blueprint, double price) {
        return new BuildingDef(id, id, "minecraft:stone", "statecraft:" + id,
                List.of(new EnvironRequirement("basic", true, 60.0, 12, true)),
                List.of(Set.of()), "scribe", blueprint, price);
    }

    private static BuildingCatalog catalog() {
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        byId.put("intel_station", building("intel_station", "intel_station", 64.0));
        byId.put("warehouse", building("warehouse", "warehouse", 32.0));
        byId.put("free_hut", building("free_hut", "", 0.0));
        return new BuildingCatalog(byId);
    }

    @Test
    void buyingNeedsResourcesAndOnlyOnce() {
        BuildingDef def = building("intel_station", "intel_station", 64.0);

        BlueprintVerdict poor = BlueprintRules.buy(def, false, 10.0);
        assertFalse(poor.allowed());
        assertTrue(poor.reason().contains("资源不够"), poor.reason());
        assertEquals(0.0, poor.playerTreasuryDelta(), 1e-9, "被拒绝时不该扣钱");

        BlueprintVerdict owned = BlueprintRules.buy(def, true, 1000.0);
        assertFalse(owned.allowed(), "买过就别再收一次钱");

        BlueprintVerdict ok = BlueprintRules.buy(def, false, 100.0);
        assertTrue(ok.allowed());
        assertEquals(-64.0, ok.playerTreasuryDelta(), 1e-9, "要扣多少必须报出来（账本在调用方）");
        assertEquals("intel_station", ok.blueprintId());
    }

    /** 不需要授权的建筑永远通过——门禁必须由数据决定，不能写死"所有建筑都要授权"。 */
    @Test
    void buildingsWithoutABlueprintAreNeverBlocked() {
        BuildingDef free = building("free_hut", "", 0.0);
        assertFalse(free.authorized());
        assertTrue(BlueprintRules.canBuild(free, false).allowed(),
                "§10.6 说后续建筑是「只加数据」，它们不该被授权门槛卡住");
        assertFalse(BlueprintRules.buy(free, false, 0.0).allowed(),
                "但也不能买一份不存在的授权");
        assertTrue(BlueprintRules.buy(free, false, 0.0).reason().contains("不需要图纸授权"));
    }

    @Test
    void buildingRequiresTheAuthorizationToHaveBeenBought() {
        BuildingDef def = building("intel_station", "intel_station", 64.0);
        BlueprintVerdict blocked = BlueprintRules.canBuild(def, false);
        assertFalse(blocked.allowed());
        assertTrue(blocked.reason().contains("建筑工"), blocked.reason());
        assertTrue(BlueprintRules.canBuild(def, true).allowed());
    }

    @Test
    void theShelfListsOnlyUnauthorizedBuildingsInFileOrder() {
        List<String> shelf = BlueprintRules.shelf(catalog(), Set.of()).stream()
                .map(BuildingDef::id).toList();
        assertEquals(List.of("intel_station", "warehouse"), shelf,
                "货架上是「要授权且还没买」的那些，顺序 = buildings.json 顺序");
        assertFalse(shelf.contains("free_hut"), "不需要授权的建筑不上货架——没东西可卖");

        List<String> afterBuying = BlueprintRules.shelf(catalog(), Set.of("intel_station")).stream()
                .map(BuildingDef::id).toList();
        assertEquals(List.of("warehouse"), afterBuying, "买过的从货架上撤下");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintRules.buy(null, false, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintRules.buy(building("x", "x", 1.0), false, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> BlueprintRules.shelf(null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> BlueprintRules.canBuild(null, true));
    }

    @Test
    void aVerdictCannotBeRejectedAndChargeThePlayerAtTheSameTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintVerdict(false, "x", "x", "不行", -5.0));
    }
}
