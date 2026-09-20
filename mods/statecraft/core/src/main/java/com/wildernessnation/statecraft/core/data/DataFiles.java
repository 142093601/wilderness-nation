package com.wildernessnation.statecraft.core.data;

import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.env.EnvironRequirement;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.letter.LetterConfig;
import com.wildernessnation.statecraft.core.letter.LetterDefaults;
import com.wildernessnation.statecraft.core.letter.LetterKind;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.persist.StateNode;
import com.wildernessnation.statecraft.core.staff.StaffCatalog;
import com.wildernessnation.statecraft.core.staff.StaffDef;
import com.wildernessnation.statecraft.core.text.PlaceNames;
import com.wildernessnation.statecraft.core.text.TextTemplates;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据文件（`eras.json` / `nations.json` / `places.json` / `events.json`）→ core 类型的读取规则。
 *
 * <p><strong>为什么读数据这一步在 core 而不是 mod 层</strong>：JSON 本身由 mod 层用 Gson 翻成
 * {@link StateNode}（那是机械翻译），而"哪个字段必填、缺了用什么默认值、算不算非法"是<strong>规则</strong>，
 * 规则要能被 JUnit 覆盖，所以放这里。
 *
 * <p>**"代码里不出现 6 个时代"**（`NATIONS.md` §七）就是靠这个文件守住的：时代数量、名称、时长、
 * 系数、解锁项全部来自 JSON。
 *
 * <p>默认值策略：文件里<strong>没写</strong>的字段一律退回 {@link StatecraftConfig#defaults()}，
 * 于是"默认值"只有一个来源（代码），数据文件只做覆盖——不会出现两处默认值互相漂移。
 */
public final class DataFiles {

    private DataFiles() {}

    /** `eras.json`：根就是一个数组。 */
    public static EraTable eras(StateNode root) {
        List<StateNode> items = root.asArr("eras.json").items();
        if (items.isEmpty()) {
            throw new IllegalStateException("eras.json 不能为空数组");
        }
        List<Era> eras = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            eras.add(readEra(items.get(i), StateNode.index("eras.json", i)));
        }
        return new EraTable(eras);
    }

    private static Era readEra(StateNode node, String path) {
        String id = node.field(path, "id").asString(path + ".id");
        StateNode nameNode = node.optionalField(path, "name");
        String name = nameNode == null ? id : nameNode.asString(path + ".name");
        int ordinal = node.field(path, "ordinal").asInt(path + ".ordinal");
        int durationHours = node.field(path, "durationHours").asInt(path + ".durationHours");
        StateNode coeffNode = node.optionalField(path, "coefficient");
        double coefficient = coeffNode == null ? 1.0 : coeffNode.asDouble(path + ".coefficient");
        Set<String> unlocks = readUnlocks(node, path);
        return new Era(id, name, ordinal, durationHours, coefficient, unlocks);
    }

    private static Set<String> readUnlocks(StateNode node, String path) {
        StateNode unlocksNode = node.optionalField(path, "unlocks");
        if (unlocksNode == null) {
            return Set.of();
        }
        List<StateNode> items = unlocksNode.asArr(path + ".unlocks").items();
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            out.add(items.get(i).asString(StateNode.index(path + ".unlocks", i)));
        }
        return out;
    }

    /** `places.json`：根是 `{ "capital": [...], "town": [...], … }`。 */
    public static PlaceNames places(StateNode root) {
        return PlaceNames.fromNode(root);
    }

    /** `events.json`：根是 `{ "<textKey>": "<模板>", … }`。 */
    public static TextTemplates templates(StateNode root) {
        return TextTemplates.fromNode(root);
    }

    /** `nations.json`：根是对象 `{ config?, cultures[] }`。 */
    public static NationData nations(StateNode root) {
        StateNode culturesNode = root.field("nations.json", "cultures");
        List<StateNode> items = culturesNode.asArr("nations.json.cultures").items();
        List<Culture> cultures = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            cultures.add(readCulture(items.get(i), StateNode.index("nations.json.cultures", i)));
        }
        return new NationData(cultures, readConfig(root));
    }

    private static Culture readCulture(StateNode node, String path) {
        String id = node.field(path, "id").asString(path + ".id");
        StateNode displayNode = node.optionalField(path, "displayName");
        String displayName = displayNode == null ? id : displayNode.asString(path + ".displayName");
        List<StateNode> names = node.field(path, "names").asArr(path + ".names").items();
        List<String> pool = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            pool.add(names.get(i).asString(StateNode.index(path + ".names", i)));
        }
        return new Culture(id, displayName, pool);
    }

    /** 配置块整个可缺省；缺省时等于 {@link StatecraftConfig#defaults()}。 */
    private static StatecraftConfig readConfig(StateNode root) {
        StateNode cfgNode = root.optionalField("nations.json", "config");
        if (cfgNode == null) {
            return StatecraftConfig.defaults();
        }
        StatecraftConfig d = StatecraftConfig.defaults();
        return new StatecraftConfig(
                intOr(cfgNode, "nationCount", d.nationCount()),
                intOr(cfgNode, "minNationSpacing", d.minNationSpacing()),
                intOr(cfgNode, "sizeMin", d.sizeMin()),
                intOr(cfgNode, "sizeMax", d.sizeMax()),
                doubleOr(cfgNode, "devBase", d.devBase()),
                doubleOr(cfgNode, "devPerSize", d.devPerSize()),
                doubleOr(cfgNode, "devJitter", d.devJitter()),
                doubleOr(cfgNode, "devMin", d.devMin()),
                doubleOr(cfgNode, "devMax", d.devMax()),
                doubleOr(cfgNode, "stanceBase", d.stanceBase()),
                doubleOr(cfgNode, "stanceJitter", d.stanceJitter()),
                doubleOr(cfgNode, "minPlacementRadius", d.minPlacementRadius()),
                doubleOr(cfgNode, "maxPlacementRadius", d.maxPlacementRadius()));
    }

    private static int intOr(StateNode obj, String key, int fallback) {
        StateNode v = obj.optionalField("nations.json.config", key);
        return v == null ? fallback : v.asInt("nations.json.config." + key);
    }

    private static double doubleOr(StateNode obj, String key, double fallback) {
        StateNode v = obj.optionalField("nations.json.config", key);
        return v == null ? fallback : v.asDouble("nations.json.config." + key);
    }


    // ---- letters.json ----

    /**
     * `letters.json`：根是 `{ "config": {...}, "kinds": [ { "kind": "STOP_BUILDING", ... } ] }`。
     *
     * <p>三种国书**必须齐全**：缺一种不是"少个内容"，而是"那种国书永远不会出现"——
     * 这种静默的缺失最难查，所以在读的时候就报错（{@link LetterData} 的构造器里）。
     */
    public static LetterData letters(StateNode root) {
        Map<LetterKind, LetterKindData> kinds = new LinkedHashMap<>();
        List<StateNode> items = root.field("letters.json", "kinds")
                .asArr("letters.json.kinds").items();
        for (int i = 0; i < items.size(); i++) {
            String path = StateNode.index("letters.json.kinds", i);
            LetterKindData data = readLetterKind(items.get(i), path);
            if (kinds.put(data.kind(), data) != null) {
                throw new IllegalStateException("letters.json 里这种国书定义了两次：" + data.kind());
            }
        }
        return new LetterData(kinds, readLetterConfig(root));
    }

    private static LetterKindData readLetterKind(StateNode node, String path) {
        String raw = node.field(path, "kind").asString(path + ".kind");
        LetterKind kind;
        try {
            kind = LetterKind.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            StringBuilder legal = new StringBuilder();
            for (LetterKind k : LetterKind.values()) {
                legal.append(legal.isEmpty() ? "" : " / ").append(k.name());
            }
            throw new IllegalStateException("letters.json 里有不认识的国书种类「" + raw
                    + "」，合法值：" + legal, e);
        }
        StateNode nameNode = node.optionalField(path, "name");
        String name = nameNode == null ? kind.name() : nameNode.asString(path + ".name");
        return new LetterKindData(kind, name,
                doubleOr(node, path, "radius", 0.0),
                doubleOr(node, path, "amount", 0.0));
    }

    /** 配置块整个可缺省；缺省时等于 {@link LetterConfig#defaults()}。 */
    private static LetterConfig readLetterConfig(StateNode root) {
        StateNode cfg = root.optionalField("letters.json", "config");
        if (cfg == null) {
            return LetterConfig.defaults();
        }
        String p = "letters.json.config";
        LetterConfig d = LetterConfig.defaults();
        return new LetterConfig(
                longOr(cfg, p, "deadlineSettlements", d.deadlineSettlements()),
                doubleOr(cfg, p, "refusalAttitudePenalty", d.refusalAttitudePenalty()),
                doubleOr(cfg, p, "ignoreAttitudePenalty", d.ignoreAttitudePenalty()),
                doubleOr(cfg, p, "tributeAcceptAttitudeGain", d.tributeAcceptAttitudeGain()),
                doubleOr(cfg, p, "jointWarAttitudeGain", d.jointWarAttitudeGain()),
                doubleOr(cfg, p, "stopBuildAcceptAttitudeGain", d.stopBuildAcceptAttitudeGain()),
                longOr(cfg, p, "stopBuildPromiseSettlements", d.stopBuildPromiseSettlements()),
                doubleOr(cfg, p, "stopBuildKeptAttitudeGain", d.stopBuildKeptAttitudeGain()),
                doubleOr(cfg, p, "stopBuildBreachAttitudePenalty",
                        d.stopBuildBreachAttitudePenalty()),
                doubleOr(cfg, p, "issueAttitudeThreshold", d.issueAttitudeThreshold()),
                longOr(cfg, p, "issueCooldownSettlements", d.issueCooldownSettlements()),
                longOr(cfg, p, "maxIssuesPerSettlement", d.maxIssuesPerSettlement()),
                doubleOr(cfg, p, "jointWarMinAttitude", d.jointWarMinAttitude()),
                doubleOr(cfg, p, "jointWarTargetAttitude", d.jointWarTargetAttitude()));
    }

    /**
     * `letters.json` 里三种国书的**要价/半径**（`LetterDefaults` 只装"停止建造的半径"与
     * "朝贡的数额"两项，因为只有这两种需要）。
     */
    public static LetterDefaults letterDefaults(LetterData data) {
        return new LetterDefaults(
                data.of(LetterKind.STOP_BUILDING).radius(),
                data.of(LetterKind.TRIBUTE).amount());
    }

    // ---- buildings.json / staff.json ----

    /**
     * `buildings.json`：根是 `{ "buildings": [ {...} ] }`。
     *
     * <p>档位**按文件顺序**读（§10.5 的判定是"取最后一个被满足的档"，所以顺序就是语义）。
     * 每个档位上还可以带一份 `abilities`，它和档位一一对应（§11.1 的第三档能力）。
     */
    public static BuildingCatalog buildings(StateNode root) {
        Map<String, BuildingDef> byId = new LinkedHashMap<>();
        List<StateNode> items = root.field("buildings.json", "buildings")
                .asArr("buildings.json.buildings").items();
        for (int i = 0; i < items.size(); i++) {
            String path = StateNode.index("buildings.json.buildings", i);
            BuildingDef def = readBuilding(items.get(i), path);
            if (byId.put(def.id(), def) != null) {
                throw new IllegalStateException("buildings.json 里这座建筑定义了两次：" + def.id());
            }
        }
        return new BuildingCatalog(byId);
    }

    private static BuildingDef readBuilding(StateNode node, String path) {
        String id = node.field(path, "id").asString(path + ".id");
        String name = stringOr(node, path, "name", id);
        String anchor = node.field(path, "anchorBlock").asString(path + ".anchorBlock");
        String schematic = stringOr(node, path, "schematic", "");
        String profession = node.field(path, "staffProfession")
                .asString(path + ".staffProfession");
        String blueprint = stringOr(node, path, "requiredBlueprint", "");
        double price = doubleOr(node, path, "blueprintPrice", 0.0);

        List<StateNode> tierNodes = node.field(path, "tiers").asArr(path + ".tiers").items();
        List<EnvironRequirement> tiers = new ArrayList<>(tierNodes.size());
        List<Set<String>> abilities = new ArrayList<>(tierNodes.size());
        for (int i = 0; i < tierNodes.size(); i++) {
            String tp = StateNode.index(path + ".tiers", i);
            tiers.add(readTier(tierNodes.get(i), tp));
            abilities.add(readStringSet(tierNodes.get(i), tp, "abilities"));
        }
        return new BuildingDef(id, name, anchor, schematic, tiers, abilities, profession,
                blueprint, price);
    }

    private static EnvironRequirement readTier(StateNode node, String path) {
        String tierId = stringOr(node, path, "tierId", "basic");
        return new EnvironRequirement(
                tierId,
                boolOr(node, path, "requireRoof", true),
                doubleOr(node, path, "minEnclosurePercent", 60.0),
                intOr(node, path, "minVolume", 12),
                boolOr(node, path, "requireClaim", true));
    }

    /** `staff.json`：根是 `{ "professions": [ {...} ] }`。 */
    public static StaffCatalog staff(StateNode root) {
        Map<String, StaffDef> byId = new LinkedHashMap<>();
        List<StateNode> items = root.field("staff.json", "professions")
                .asArr("staff.json.professions").items();
        for (int i = 0; i < items.size(); i++) {
            String path = StateNode.index("staff.json.professions", i);
            StaffDef def = readStaff(items.get(i), path);
            if (byId.put(def.id(), def) != null) {
                throw new IllegalStateException("staff.json 里这个职业定义了两次：" + def.id());
            }
        }
        return new StaffCatalog(byId);
    }

    private static StaffDef readStaff(StateNode node, String path) {
        String id = node.field(path, "id").asString(path + ".id");
        String name = stringOr(node, path, "name", id);
        String building = node.field(path, "buildingType").asString(path + ".buildingType");
        String appearance = stringOr(node, path, "appearance", "");
        String greeting = stringOr(node, path, "greeting", "……");
        List<StateNode> names = node.field(path, "names").asArr(path + ".names").items();
        List<String> pool = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            pool.add(names.get(i).asString(StateNode.index(path + ".names", i)));
        }
        return new StaffDef(id, name, building, pool, appearance, greeting,
                readStringSet(node, path, "abilities"));
    }

    private static Set<String> readStringSet(StateNode node, String path, String key) {
        StateNode arr = node.optionalField(path, key);
        if (arr == null) {
            return Set.of();
        }
        List<StateNode> items = arr.asArr(path + "." + key).items();
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            out.add(items.get(i).asString(StateNode.index(path + "." + key, i)));
        }
        return out;
    }

    private static String stringOr(StateNode obj, String path, String key, String fallback) {
        StateNode v = obj.optionalField(path, key);
        return v == null ? fallback : v.asString(path + "." + key);
    }

    private static boolean boolOr(StateNode obj, String path, String key, boolean fallback) {
        StateNode v = obj.optionalField(path, key);
        return v == null ? fallback : v.asBool(path + "." + key);
    }

    private static int intOr(StateNode obj, String path, String key, int fallback) {
        StateNode v = obj.optionalField(path, key);
        return v == null ? fallback : v.asInt(path + "." + key);
    }

    private static double doubleOr(StateNode obj, String path, String key, double fallback) {
        StateNode v = obj.optionalField(path, key);
        return v == null ? fallback : v.asDouble(path + "." + key);
    }

    private static long longOr(StateNode obj, String path, String key, long fallback) {
        StateNode v = obj.optionalField(path, key);
        return v == null ? fallback : v.asLong(path + "." + key);
    }
}
