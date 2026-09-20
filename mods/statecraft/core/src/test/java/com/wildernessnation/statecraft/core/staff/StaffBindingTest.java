package com.wildernessnation.statecraft.core.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.building.BuildingAbility;
import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.building.StaffObservation;
import com.wildernessnation.statecraft.core.env.EnvironRequirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 村民绑定（§10.3 的三件校验）与两张表的交叉校验。
 *
 * <p>这是"**不做对会生出三个文书**"（§10.3）那条的落点：
 * mod 层只报告"那个实体是什么职业、身上记着哪个 buildingId、锚点还在不在"，
 * 判定必须在 core，才能被这些用例钉死。
 */
class StaffBindingTest {

    private static BuildingDef station(double price) {
        return new BuildingDef("intel_station", "情报站", "minecraft:lectern", "",
                List.of(new EnvironRequirement("basic", true, 60.0, 12, true),
                        new EnvironRequirement("upgraded", true, 75.0, 40, true)),
                List.of(Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY),
                        Set.of(BuildingAbility.DEEP_DIVE)),
                "scribe", price > 0 ? "intel_station" : "", price);
    }

    private static StaffDef scribe() {
        return new StaffDef("scribe", "文书", "intel_station",
                List.of("陈守简", "姚无咎"), "statecraft:scribe", "要听哪一桩？",
                Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY,
                        BuildingAbility.DEEP_DIVE));
    }

    // ---- 观察 → 三件校验 ----

    @Test
    void aHealthyBindingPassesAllThreeChecks() {
        StaffObservation obs = StaffBinding.observe(station(64.0), "b1",
                Optional.of(new StaffBinding.StaffIdentity("uuid-1", "scribe", "b1")), true);
        assertEquals(StaffObservation.healthy(), obs);
        assertTrue(obs.bindingHealthy());
        assertTrue(StaffBinding.describeMismatch(station(64.0), obs).isEmpty(),
                "全过的时候不该有任何中文抱怨");
    }

    @Test
    void wrongProfessionIsCaughtByCoreNotByTheModLayer() {
        BuildingDef def = station(64.0);
        StaffObservation obs = StaffBinding.observe(def, "b1",
                Optional.of(new StaffBinding.StaffIdentity("uuid-1", "farmer", "b1")), true);
        assertFalse(obs.rightProfession(), "站在那儿的不是文书");
        assertTrue(obs.staffExists(), "人在，只是职业不对——这两种要分开，处理方式不同");
        assertTrue(obs.buildingIdMatches());
        List<String> why = StaffBinding.describeMismatch(def, obs);
        assertEquals(1, why.size());
        assertTrue(why.get(0).contains("scribe"), why.get(0));
    }

    @Test
    void aStaffCarryingAnotherBuildingIdIsCaught() {
        BuildingDef def = station(64.0);
        StaffObservation obs = StaffBinding.observe(def, "b1",
                Optional.of(new StaffBinding.StaffIdentity("uuid-1", "scribe", "b2")), true);
        assertTrue(obs.rightProfession());
        assertFalse(obs.buildingIdMatches(), "多半是从别处挪过来的");
        assertTrue(StaffBinding.describeMismatch(def, obs).get(0).contains("记的不是这座建筑"));
    }

    @Test
    void aMissingStaffGivesOnlyOneReasonNotThreeContradictoryOnes() {
        BuildingDef def = station(64.0);
        StaffObservation obs = StaffBinding.observe(def, "b1", Optional.empty(), true);
        assertEquals(StaffObservation.staffGone(), obs);
        List<String> why = StaffBinding.describeMismatch(def, obs);
        assertEquals(1, why.size(), "人都没了还说「职业不对」就是自相矛盾：" + why);
        assertTrue(why.get(0).contains("不在了"), why.get(0));
    }

    @Test
    void aGoneAnchorIsReportedFirstAndSeparately() {
        BuildingDef def = station(64.0);
        StaffObservation obs = StaffBinding.observe(def, "b1",
                Optional.of(new StaffBinding.StaffIdentity("uuid-1", "scribe", "b1")), false);
        assertFalse(obs.anchorIntact());
        assertTrue(obs.bindingHealthy(), "§10.4：建筑被拆登记为失效，但村民本身没问题");
        assertEquals("锚点方块没了（建筑算被拆）", StaffBinding.describeMismatch(def, obs).get(0));
    }

    @Test
    void observeRejectsNullOrBlankArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> StaffBinding.observe(null, "b1", Optional.empty(), true));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBinding.observe(station(64.0), "  ", Optional.empty(), true));
        assertThrows(IllegalArgumentException.class,
                () -> StaffBinding.observe(station(64.0), "b1", null, true));
        assertThrows(IllegalArgumentException.class,
                () -> new StaffBinding.StaffIdentity("", "scribe", "b1"));
    }

    // ---- 两张表交叉校验 ----

    @Test
    void buildingAndStaffTablesMustAgree() {
        BuildingDef def = station(64.0);
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        byId.put(def.id(), def);
        BuildingCatalog buildings = new BuildingCatalog(byId);
        Map<String, StaffDef> staffById = new LinkedHashMap<>();
        staffById.put("scribe", scribe());

        List<String> problems = new StaffCatalog(staffById).crossCheck(buildings);
        assertTrue(problems.isEmpty(), "对齐的数据不该报问题：" + problems);
        assertEquals(1, new StaffCatalog(staffById).validatedAgainst(buildings).size());
    }

    @Test
    void aBuildingWhoseProfessionIsMissingIsReported() {
        BuildingDef def = station(64.0);
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        byId.put(def.id(), def);
        StaffCatalog staff = new StaffCatalog(Map.of("farmer", new StaffDef(
                "farmer", "农夫", "farm", List.of("甲"), "", "……", Set.of())));

        List<String> problems = staff.crossCheck(new BuildingCatalog(byId));
        assertEquals(2, problems.size(),
                "一个方向报「职业缺失」，另一个报「农夫没落脚处」：" + problems);
        assertTrue(problems.get(0).contains("scribe"), problems.get(0));
        assertTrue(problems.get(1).contains("farm"), problems.get(1));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> staff.validatedAgainst(new BuildingCatalog(byId)));
        assertTrue(e.getMessage().contains("对不上"), e.getMessage());
    }

    /** 建筑满级开放的能力，村民必须全会（不然会出现"按钮在但没人应答"）。 */
    @Test
    void staffMustCoverEveryAbilityTheBuildingUnlocks() {
        BuildingDef def = station(64.0);
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        byId.put(def.id(), def);
        StaffCatalog staff = new StaffCatalog(Map.of("scribe", new StaffDef(
                "scribe", "文书", "intel_station", List.of("甲"), "", "……",
                Set.of(BuildingAbility.INTEL_BOOK))));

        List<String> problems = staff.crossCheck(new BuildingCatalog(byId));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("deep_dive"), problems.get(0));
        assertTrue(problems.get(0).contains("不会"), problems.get(0));
    }

    /** 村民多会一点不算错：同一个文书以后也许还管别的建筑。 */
    @Test
    void extraStaffAbilitiesAreFine() {
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        byId.put("intel_station", station(64.0));
        StaffDef multi = new StaffDef("scribe", "文书", "intel_station", List.of("甲"), "", "……",
                Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY,
                        BuildingAbility.DEEP_DIVE, "quartermaster"));
        assertTrue(new StaffCatalog(Map.of("scribe", multi))
                .crossCheck(new BuildingCatalog(byId)).isEmpty());
        assertTrue(StaffBinding.matches(station(64.0), multi));
    }

    @Test
    void aStaffPointingAtANonexistentBuildingIsReported() {
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        byId.put("intel_station", station(64.0));
        List<String> problems = new StaffCatalog(Map.of("scribe", scribe()))
                .crossCheck(new BuildingCatalog(byId));
        assertTrue(problems.isEmpty(), problems.toString());

        StaffDef ghost = new StaffDef("scribe", "文书", "warehouse", List.of("甲"), "", "……",
                Set.of());
        List<String> ghostProblems = new StaffCatalog(Map.of("scribe", ghost))
                .crossCheck(new BuildingCatalog(byId));
        assertTrue(ghostProblems.get(0).contains("warehouse"), ghostProblems.get(0));
    }

    // ---- StaffDef 自身的规则 ----

    @Test
    void namesAreDrawnByIndexNotAtRandom() {
        StaffDef def = scribe();
        assertEquals(2, def.nameCount());
        assertEquals("陈守简", def.nameAt(0));
        assertEquals("姚无咎", def.nameAt(1));
        assertEquals("陈守简", def.nameAt(2), "取模循环");
        assertEquals("姚无咎", def.nameAt(-1), "负下标也要落回池子里");
    }

    @Test
    void duplicateOrEmptyNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StaffDef("scribe", "文书", "intel_station", List.of("甲", "甲"),
                        "", "……", Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StaffDef("scribe", "文书", "intel_station", List.of(),
                        "", "……", Set.of()));
    }

    @Test
    void catalogRejectsDuplicatesAndAnswersReverseLookups() {
        StaffDef a = scribe();
        Map<String, StaffDef> byId = new LinkedHashMap<>();
        byId.put(a.id(), a);
        StaffCatalog catalog = new StaffCatalog(byId);
        assertEquals(1, catalog.size());
        assertEquals("文书", catalog.require("scribe").name());
        assertThrows(IllegalArgumentException.class, () -> catalog.require("nobody"));
        assertTrue(catalog.byId(null).isEmpty());

        Map<String, BuildingDef> buildings = new LinkedHashMap<>();
        buildings.put("intel_station", station(64.0));
        assertEquals(1, catalog.buildingsOf("scribe", new BuildingCatalog(buildings)).size());
        assertTrue(catalog.buildingsOf("nobody", new BuildingCatalog(buildings)).isEmpty());
    }
}
