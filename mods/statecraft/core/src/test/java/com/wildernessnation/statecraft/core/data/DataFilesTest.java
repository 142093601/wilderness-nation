package com.wildernessnation.statecraft.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.building.BuildingAbility;
import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.env.EnvironRequirement;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.letter.LetterConfig;
import com.wildernessnation.statecraft.core.letter.LetterKind;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.persist.StateNode;
import com.wildernessnation.statecraft.core.staff.StaffCatalog;
import com.wildernessnation.statecraft.core.staff.StaffDef;
import com.wildernessnation.statecraft.core.text.PlaceNames;
import com.wildernessnation.statecraft.core.text.TextTemplates;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataFilesTest {

    // ---- eras.json（根是数组）----

    @Test
    void readsAnEraTableOfAnyLength() {
        // 3 条与 9 条都读得出来："代码里不出现 6 个时代"靠的就是这个
        assertEquals(3, DataFiles.eras(eras(3)).size());
        assertEquals(9, DataFiles.eras(eras(9)).size());
        EraTable table = DataFiles.eras(eras(6));
        assertEquals(6, table.size());
        assertEquals("e0", table.byOrdinal(0).id());
        assertEquals("e5", table.currentFor(9999.0).id());
    }

    @Test
    void readsNameDurationCoefficientAndUnlocks() {
        Map<String, StateNode> era = StateNode.fields();
        era.put("id", new StateNode.Str("landing"));
        era.put("name", new StateNode.Str("落地"));
        era.put("ordinal", new StateNode.Int(0));
        era.put("durationHours", new StateNode.Int(12));
        era.put("coefficient", new StateNode.Dec(1.25));
        era.put("unlocks", new StateNode.Arr(List.of(
                new StateNode.Str("intel_book"), new StateNode.Str("intel_station"))));
        EraTable table = DataFiles.eras(new StateNode.Arr(List.of(new StateNode.Obj(era))));

        assertEquals("落地", table.byOrdinal(0).name());
        assertEquals(12, table.byOrdinal(0).durationHours());
        assertEquals(1.25, table.byOrdinal(0).coefficient(), 0.0);
        assertTrue(table.isUnlocked("intel_station", table.byOrdinal(0)));
    }

    @Test
    void eraNameAndCoefficientAndUnlocksAreOptional() {
        Map<String, StateNode> era = StateNode.fields();
        era.put("id", new StateNode.Str("landing"));
        era.put("ordinal", new StateNode.Int(0));
        era.put("durationHours", new StateNode.Int(12));
        EraTable table = DataFiles.eras(new StateNode.Arr(List.of(new StateNode.Obj(era))));

        assertEquals("landing", table.byOrdinal(0).name(), "没写 name 就退回 id");
        assertEquals(1.0, table.byOrdinal(0).coefficient(), 0.0);
        assertEquals(Set.of(), table.byOrdinal(0).unlocks());
    }

    @Test
    void erasMissingRequiredFieldsFailWithTheFieldPath() {
        Map<String, StateNode> era = StateNode.fields();
        era.put("id", new StateNode.Str("landing"));
        era.put("ordinal", new StateNode.Int(0));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataFiles.eras(new StateNode.Arr(List.of(new StateNode.Obj(era)))));
        assertTrue(e.getMessage().contains("eras.json[0].durationHours"),
                "报错要指到具体文件的具体字段：" + e.getMessage());
    }

    @Test
    void emptyEraArrayIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> DataFiles.eras(new StateNode.Arr(List.of())));
    }

    /** 序号不连续由 EraTable 拦下（它才是唯一的权威）。 */
    @Test
    void nonContiguousEraOrdinalsAreRejectedByTheTable() {
        Map<String, StateNode> a = StateNode.fields();
        a.put("id", new StateNode.Str("a"));
        a.put("ordinal", new StateNode.Int(0));
        a.put("durationHours", new StateNode.Int(10));
        Map<String, StateNode> b = StateNode.fields();
        b.put("id", new StateNode.Str("b"));
        b.put("ordinal", new StateNode.Int(2));
        b.put("durationHours", new StateNode.Int(10));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.eras(new StateNode.Arr(List.of(
                        new StateNode.Obj(a), new StateNode.Obj(b)))));
    }

    // ---- nations.json（根是对象）----

    @Test
    void readsCulturesInFileOrder() {
        NationData data = DataFiles.nations(nations(2, 6));
        assertEquals(2, data.cultures().size());
        assertEquals("c0", data.cultures().get(0).id());
        assertEquals("c1", data.cultures().get(1).id());
        assertEquals(6, data.cultures().get(0).namePrefixes().size());
        assertEquals("c0_0", data.cultures().get(0).namePrefixes().get(0));
    }

    @Test
    void cultureDisplayNameIsOptional() {
        NationData data = DataFiles.nations(nations(1, 3));
        assertEquals("c0", data.cultures().get(0).displayName(), "没写 displayName 就退回 id");
    }

    @Test
    void missingConfigBlockMeansDefaults() {
        assertEquals(StatecraftConfig.defaults(), DataFiles.nations(nations(2, 6)).config(),
                "配置块缺失时必须等于代码里的默认值（默认值只有一个来源）");
    }

    @Test
    void partialConfigOverridesOnlyTheFieldsItWrites() {
        StatecraftConfig d = StatecraftConfig.defaults();
        Map<String, StateNode> config = StateNode.fields();
        config.put("nationCount", new StateNode.Int(16));
        NationData data = DataFiles.nations(withConfig(nations(1, 20), config));

        assertEquals(16, data.config().nationCount());
        assertEquals(d.minNationSpacing(), data.config().minNationSpacing(), "没写的字段保持默认");
        assertEquals(d.devBase(), data.config().devBase(), 0.0);
    }

    @Test
    void configIntegersMustNotBeWrittenAsDecimals() {
        Map<String, StateNode> config = StateNode.fields();
        config.put("nationCount", new StateNode.Dec(12.0));   // 写成 12.0 就是小数
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataFiles.nations(withConfig(nations(1, 20), config)));
        assertTrue(e.getMessage().contains("nations.json.config.nationCount"),
                "报错要说清是哪一行写错了：" + e.getMessage());
    }

    @Test
    void emptyCultureListIsRejected() {
        Map<String, StateNode> root = StateNode.fields();
        root.put("cultures", new StateNode.Arr(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.nations(new StateNode.Obj(root)));
    }

    // ---- 造树工具 ----

    private static StateNode eras(int count) {
        List<StateNode> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, StateNode> era = StateNode.fields();
            era.put("id", new StateNode.Str("e" + i));
            era.put("ordinal", new StateNode.Int(i));
            era.put("durationHours", new StateNode.Int(10 + i));
            out.add(new StateNode.Obj(era));
        }
        return new StateNode.Arr(out);
    }

    private static StateNode nations(int cultureCount, int namesEach) {
        List<StateNode> cultures = new java.util.ArrayList<>();
        for (int c = 0; c < cultureCount; c++) {
            List<StateNode> names = new java.util.ArrayList<>();
            for (int n = 0; n < namesEach; n++) {
                names.add(new StateNode.Str("c" + c + "_" + n));
            }
            Map<String, StateNode> culture = StateNode.fields();
            culture.put("id", new StateNode.Str("c" + c));
            culture.put("names", new StateNode.Arr(names));
            cultures.add(new StateNode.Obj(culture));
        }
        Map<String, StateNode> root = StateNode.fields();
        root.put("cultures", new StateNode.Arr(cultures));
        return new StateNode.Obj(root);
    }

    private static StateNode withConfig(StateNode nationsRoot, Map<String, StateNode> config) {
        Map<String, StateNode> root = StateNode.fields();
        root.putAll(nationsRoot.asObj("nations.json").fields());
        root.put("config", new StateNode.Obj(config));
        return new StateNode.Obj(root);
    }

    @Test
    void readCultureNamesAreUsableByTheGenerator() {
        NationData data = DataFiles.nations(nations(2, 12));
        List<Culture> cultures = data.cultures();
        assertEquals(24, cultures.stream().mapToInt(c -> c.namePrefixes().size()).sum());
    }

    // ---- places.json / events.json（计划 4 的文案层）----

    @Test
    void readsPlacePoolsAndTemplatesFromData() {
        java.util.Map<String, StateNode> places = StateNode.fields();
        places.put("town", new StateNode.Arr(List.of(
                new StateNode.Str("石口"), new StateNode.Str("枯井镇"))));
        PlaceNames pool = DataFiles.places(new StateNode.Obj(places));
        assertTrue(pool.has(PlaceNames.TOWN));
        assertEquals(1, pool.kinds().size(), "这里只放了一种池子（town）");

        java.util.Map<String, StateNode> events = StateNode.fields();
        events.put("battle", new StateNode.Str("{0}攻破{1}边镇『{p:town}』"));
        TextTemplates templates = DataFiles.templates(new StateNode.Obj(events));
        assertTrue(templates.has("battle"));
        assertEquals("{0}攻破{1}边镇『{p:town}』", templates.require("battle"));
    }

    @Test
    void placePoolAndTemplateTablesRejectBadData() {
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.places(new StateNode.Obj(StateNode.fields())));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.templates(new StateNode.Obj(StateNode.fields())));
        // 模板内容不能是空白
        java.util.Map<String, StateNode> blank = StateNode.fields();
        blank.put("x", new StateNode.Str("   "));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.templates(new StateNode.Obj(blank)));
    }

    // ---- letters.json（§十六：国书的条件、期限、接受/拒绝后果）----

    @Test
    void readsTheThreeLetterKindsAndTheirPrices() {
        LetterData data = DataFiles.letters(letters(null));
        assertEquals(3, data.kinds().size());
        assertEquals(List.of(LetterKind.STOP_BUILDING, LetterKind.JOINT_WAR,
                        LetterKind.TRIBUTE),
                data.ordered().stream().map(LetterKindData::kind).toList(),
                "遍历顺序按枚举声明，不能跟着 JSON 里的顺序漂");
        assertEquals(64.0, data.of(LetterKind.STOP_BUILDING).radius(), 0.0);
        assertEquals(0.0, data.of(LetterKind.STOP_BUILDING).amount(), 0.0);
        assertEquals(120.0, data.of(LetterKind.TRIBUTE).amount(), 0.0);
        assertEquals("停止在边境建造", data.of(LetterKind.STOP_BUILDING).name());
    }

    @Test
    void missingLetterConfigBlockMeansDefaults() {
        assertEquals(LetterConfig.defaults(), DataFiles.letters(letters(null)).config(),
                "配置块缺失时必须等于代码里的默认值（默认值只有一个来源）");
    }

    @Test
    void partialLetterConfigOverridesOnlyTheFieldsItWrites() {
        LetterConfig d = LetterConfig.defaults();
        Map<String, StateNode> config = StateNode.fields();
        config.put("deadlineSettlements", new StateNode.Int(5));
        LetterData data = DataFiles.letters(letters(config));

        assertEquals(5L, data.config().deadlineSettlements());
        assertEquals(d.ignoreAttitudePenalty(), data.config().ignoreAttitudePenalty(), 0.0,
                "没写的字段保持默认");
        assertEquals(d.stopBuildPromiseSettlements(), data.config().stopBuildPromiseSettlements());
    }

    @Test
    void aLetterKindMissingFromTheFileIsRejected() {
        LetterData full = DataFiles.letters(letters(null));
        List<StateNode> two = new java.util.ArrayList<>(
                letters(null).asObj("letters.json").field("letters.json", "kinds")
                        .asArr("letters.json.kinds").items().subList(0, 2));
        Map<String, StateNode> root = StateNode.fields();
        root.put("kinds", new StateNode.Arr(two));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataFiles.letters(new StateNode.Obj(root)));
        assertTrue(e.getMessage().contains("TRIBUTE"),
                "缺哪一种要说出来，不能静默地少一种国书：" + e.getMessage());
        assertEquals(3, full.kinds().size());
    }

    @Test
    void unknownLetterKindNamesFailWithTheLegalValues() {
        Map<String, StateNode> bad = StateNode.fields();
        bad.put("kind", new StateNode.Str("CRUSADE"));
        Map<String, StateNode> root = StateNode.fields();
        root.put("kinds", new StateNode.Arr(List.of(new StateNode.Obj(bad))));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataFiles.letters(new StateNode.Obj(root)));
        assertTrue(e.getMessage().contains("CRUSADE"), e.getMessage());
        assertTrue(e.getMessage().contains("STOP_BUILDING"), "要列出合法值：" + e.getMessage());
    }

    /** 该要价的没要价、不该要价的要了价，都算内容写错（和 {@code Letter} 的校验对齐）。 */
    @Test
    void letterKindPricesMustBeCoherent() {
        assertThrows(IllegalArgumentException.class,
                () -> new LetterKindData(LetterKind.STOP_BUILDING, "停止建造", 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new LetterKindData(LetterKind.TRIBUTE, "朝贡", 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new LetterKindData(LetterKind.JOINT_WAR, "共同讨伐", 0.0, -1.0));
    }

    private static StateNode letters(Map<String, StateNode> config) {
        List<StateNode> kinds = new java.util.ArrayList<>();
        kinds.add(letterKind("STOP_BUILDING", "停止在边境建造", 64.0, 0.0));
        kinds.add(letterKind("JOINT_WAR", "共同讨伐", 0.0, 0.0));
        kinds.add(letterKind("TRIBUTE", "朝贡换互不侵犯", 0.0, 120.0));
        Map<String, StateNode> root = StateNode.fields();
        root.put("kinds", new StateNode.Arr(kinds));
        if (config != null) {
            root.put("config", new StateNode.Obj(config));
        }
        return new StateNode.Obj(root);
    }

    private static StateNode letterKind(String kind, String name, double radius, double amount) {
        Map<String, StateNode> f = StateNode.fields();
        f.put("kind", new StateNode.Str(kind));
        f.put("name", new StateNode.Str(name));
        if (radius != 0.0) {
            f.put("radius", new StateNode.Dec(radius));
        }
        if (amount != 0.0) {
            f.put("amount", new StateNode.Dec(amount));
        }
        return new StateNode.Obj(f);
    }

    // ---- buildings.json（§10.1/§10.5/§11.1）----

    @Test
    void readsBuildingsWithTheirTiersAndPerTierAbilities() {
        BuildingCatalog catalog = DataFiles.buildings(buildings());
        assertEquals(1, catalog.size());
        BuildingDef def = catalog.require("intel_station");

        assertEquals("情报站", def.name());
        assertEquals("minecraft:lectern", def.anchorBlock());
        assertEquals("scribe", def.staffProfession());
        assertEquals(64.0, def.blueprintPrice(), 0.0);
        assertEquals(List.of("basic", "upgraded"), def.tierIds(), "档位顺序必须等于文件顺序");
        assertEquals(Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY),
                def.abilitiesAt(1));
        assertEquals(Set.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY,
                        BuildingAbility.DEEP_DIVE),
                def.abilitiesAt(2),
                "第二档写的是「这一档新增什么」，读出来是累积的（升级过的情报站当然还会外交）");
        assertEquals(60.0, def.baseTier().minEnclosurePercent(), 0.0);
        assertEquals(40, def.topTier().minVolume());
    }

    @Test
    void buildingTierFieldsHaveSafeDefaults() {
        Map<String, StateNode> tier = StateNode.fields();
        tier.put("tierId", new StateNode.Str("basic"));
        Map<String, StateNode> building = StateNode.fields();
        building.put("id", new StateNode.Str("hut"));
        building.put("anchorBlock", new StateNode.Str("minecraft:stone"));
        building.put("staffProfession", new StateNode.Str("scribe"));
        building.put("tiers", new StateNode.Arr(List.of(new StateNode.Obj(tier))));
        Map<String, StateNode> root = StateNode.fields();
        root.put("buildings", new StateNode.Arr(List.of(new StateNode.Obj(building))));

        BuildingDef def = DataFiles.buildings(new StateNode.Obj(root)).require("hut");
        assertEquals("hut", def.name(), "没写 name 就退回 id");
        assertEquals(EnvironRequirement.defaultBasic().minEnclosurePercent(),
                def.baseTier().minEnclosurePercent(), 0.0,
                "没写的环境阈值必须等于 §十二 的默认档（默认值只有一个来源）");
        assertEquals(12, def.baseTier().minVolume());
        assertFalse(def.authorized(), "没写 requiredBlueprint = 不需要授权");
        assertTrue(def.abilities().isEmpty(), "没写 abilities = 这个档没有能力");
    }

    @Test
    void buildingsMissingRequiredFieldsFailWithTheFieldPath() {
        Map<String, StateNode> building = StateNode.fields();
        building.put("id", new StateNode.Str("hut"));
        Map<String, StateNode> root = StateNode.fields();
        root.put("buildings", new StateNode.Arr(List.of(new StateNode.Obj(building))));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataFiles.buildings(new StateNode.Obj(root)));
        assertTrue(e.getMessage().contains("buildings.json.buildings[0].anchorBlock"),
                "报错要指到具体字段：" + e.getMessage());
    }

    @Test
    void duplicateBuildingIdsAreRejectedAtLoad() {
        Map<String, StateNode> root = StateNode.fields();
        root.put("buildings", new StateNode.Arr(List.of(
                buildings().asObj("buildings.json").field("buildings.json", "buildings")
                        .asArr("buildings.json.buildings").items().get(0),
                buildings().asObj("buildings.json").field("buildings.json", "buildings")
                        .asArr("buildings.json.buildings").items().get(0))));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataFiles.buildings(new StateNode.Obj(root)));
        assertTrue(e.getMessage().contains("两次"), e.getMessage());
    }

    // ---- staff.json（§10.2）----

    @Test
    void readsStaffAndCrossChecksAgainstTheBuildings() {
        StaffCatalog staff = DataFiles.staff(staff());
        assertEquals(1, staff.size());
        StaffDef scribe = staff.require("scribe");
        assertEquals("文书", scribe.name());
        assertEquals("intel_station", scribe.buildingType());
        assertEquals(List.of("陈守简", "姚无咎"), scribe.names());
        assertEquals("陈守简", scribe.nameAt(0));

        List<String> problems = staff.crossCheck(DataFiles.buildings(buildings()));
        assertTrue(problems.isEmpty(), "这两份数据必须对得上：" + problems);
    }

    @Test
    void staffAbilitiesAndNamesAreOptionalButValidated() {
        Map<String, StateNode> staff0 = StateNode.fields();
        staff0.put("id", new StateNode.Str("scribe"));
        staff0.put("buildingType", new StateNode.Str("intel_station"));
        staff0.put("names", new StateNode.Arr(List.of(new StateNode.Str("甲"))));
        Map<String, StateNode> root = StateNode.fields();
        root.put("professions", new StateNode.Arr(List.of(new StateNode.Obj(staff0))));

        StaffDef def = DataFiles.staff(new StateNode.Obj(root)).require("scribe");
        assertEquals("scribe", def.name(), "没写 name 就退回 id");
        assertEquals("……", def.greeting(), "没写问候语给一个最不打扰的默认值");
        assertTrue(def.abilities().isEmpty());
    }

    @Test
    void staffWithoutNamesIsRejected() {
        Map<String, StateNode> staff0 = StateNode.fields();
        staff0.put("id", new StateNode.Str("scribe"));
        staff0.put("buildingType", new StateNode.Str("intel_station"));
        staff0.put("names", new StateNode.Arr(List.of()));
        Map<String, StateNode> root = StateNode.fields();
        root.put("professions", new StateNode.Arr(List.of(new StateNode.Obj(staff0))));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.staff(new StateNode.Obj(root)));
    }

    private static StateNode buildings() {
        List<StateNode> tiers = new java.util.ArrayList<>();
        tiers.add(tier("basic", 60.0, 12,
                List.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY)));
        tiers.add(tier("upgraded", 75.0, 40, List.of(BuildingAbility.DEEP_DIVE)));
        Map<String, StateNode> def = StateNode.fields();
        def.put("id", new StateNode.Str("intel_station"));
        def.put("name", new StateNode.Str("情报站"));
        def.put("anchorBlock", new StateNode.Str("minecraft:lectern"));
        def.put("schematic", new StateNode.Str("statecraft:intel_station"));
        def.put("staffProfession", new StateNode.Str("scribe"));
        def.put("requiredBlueprint", new StateNode.Str("intel_station"));
        def.put("blueprintPrice", new StateNode.Int(64));
        def.put("tiers", new StateNode.Arr(tiers));
        Map<String, StateNode> root = StateNode.fields();
        root.put("buildings", new StateNode.Arr(List.of(new StateNode.Obj(def))));
        return new StateNode.Obj(root);
    }

    private static StateNode tier(String id, double enclosure, int volume, List<String> abilities) {
        Map<String, StateNode> f = StateNode.fields();
        f.put("tierId", new StateNode.Str(id));
        f.put("requireRoof", new StateNode.Bool(true));
        f.put("minEnclosurePercent", new StateNode.Dec(enclosure));
        f.put("minVolume", new StateNode.Int(volume));
        f.put("requireClaim", new StateNode.Bool(true));
        List<StateNode> abs = new java.util.ArrayList<>();
        for (String a : abilities) {
            abs.add(new StateNode.Str(a));
        }
        f.put("abilities", new StateNode.Arr(abs));
        return new StateNode.Obj(f);
    }

    private static StateNode staff() {
        Map<String, StateNode> scribe = StateNode.fields();
        scribe.put("id", new StateNode.Str("scribe"));
        scribe.put("name", new StateNode.Str("文书"));
        scribe.put("buildingType", new StateNode.Str("intel_station"));
        scribe.put("names", new StateNode.Arr(
                List.of(new StateNode.Str("陈守简"), new StateNode.Str("姚无咎"))));
        List<StateNode> abs = new java.util.ArrayList<>();
        for (String a : List.of(BuildingAbility.INTEL_BOOK, BuildingAbility.DIPLOMACY,
                BuildingAbility.DEEP_DIVE)) {
            abs.add(new StateNode.Str(a));
        }
        scribe.put("abilities", new StateNode.Arr(abs));
        Map<String, StateNode> root = StateNode.fields();
        root.put("professions", new StateNode.Arr(List.of(new StateNode.Obj(scribe))));
        return new StateNode.Obj(root);
    }
}

