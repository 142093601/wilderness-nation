package com.wildernessnation.statecraft.data;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.data.DataFiles;
import com.wildernessnation.statecraft.core.data.LetterData;
import com.wildernessnation.statecraft.core.data.NationData;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.persist.StateMigrator;
import com.wildernessnation.statecraft.core.persist.StateMigrations;
import com.wildernessnation.statecraft.core.persist.StateNode;
import com.wildernessnation.statecraft.core.staff.StaffCatalog;
import com.wildernessnation.statecraft.core.text.PlaceNames;
import com.wildernessnation.statecraft.core.text.TextTemplates;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;

/**
 * 内置数据文件（`data/statecraft/eras.json` / `nations.json`）的载入点。
 *
 * <p>规则（哪个字段必填、缺了用什么默认值）在 core 的 {@link DataFiles} 里，可 JUnit 覆盖；
 * 这里只负责"读文件 + 把 JSON 变成中性树"。
 *
 * <p>载入时机是 mod 构造：必须早于任何存档读取（{@code SavedData} 反序列化要用时代表）。
 * 数据包形式的热重载留给后面（`NATIONS.md` §十六），v1 先认内置资源。
 */
public final class StatecraftData {

    private static final Logger LOG = LogUtils.getLogger();

    private static final String ERAS_PATH = "/data/statecraft/eras.json";
    private static final String NATIONS_PATH = "/data/statecraft/nations.json";
    private static final String PLACES_PATH = "/data/statecraft/places.json";
    private static final String EVENTS_PATH = "/data/statecraft/events.json";
    private static final String LETTERS_PATH = "/data/statecraft/letters.json";
    private static final String BUILDINGS_PATH = "/data/statecraft/buildings.json";
    private static final String STAFF_PATH = "/data/statecraft/staff.json";

    private static EraTable eras;
    private static NationData nations;
    private static StateMigrator migrator;
    private static PlaceNames places;
    private static TextTemplates templates;
    private static LetterData letters;
    private static BuildingCatalog buildings;
    private static StaffCatalog staff;

    private StatecraftData() {}

    public static void load() {
        eras = DataFiles.eras(read(ERAS_PATH));
        nations = DataFiles.nations(read(NATIONS_PATH));
        // 生产迁移链（core 侧）：v1 是**真的发布过**的格式，测试世界里的 .dat 就是 v1
        places = DataFiles.places(read(PLACES_PATH));
        templates = DataFiles.templates(read(EVENTS_PATH));
        letters = DataFiles.letters(read(LETTERS_PATH));
        buildings = DataFiles.buildings(read(BUILDINGS_PATH));
        // 建筑与村民是**同一件事写了两份数据**（building.staffProfession ↔ staff.buildingType），
        // 所以载入时就把两边对齐一次：对不上是内容 bug，早报早好过"进了游戏才刷不出村民"
        staff = DataFiles.staff(read(STAFF_PATH)).validatedAgainst(buildings);
        migrator = StateMigrations.production();
        LOG.info("Statecraft 数据已载入：{} 个时代、{} 个文化、国名池 {} 个、国书 {} 种、{} 座建筑、{} 种村民",
                eras.size(),
                nations.cultures().size(),
                nations.cultures().stream().mapToInt(c -> c.namePrefixes().size()).sum(),
                letters.kinds().size(),
                buildings.size(),
                staff.size());
    }

    public static EraTable eras() {
        requireLoaded();
        return eras;
    }

    public static NationData nations() {
        requireLoaded();
        return nations;
    }

    public static List<Culture> cultures() {
        return nations().cultures();
    }

    public static StatecraftConfig config() {
        return nations().config();
    }

    public static PlaceNames places() {
        requireLoaded();
        return places;
    }

    public static TextTemplates templates() {
        requireLoaded();
        return templates;
    }

    /** 国书的种类定义与数值（`letters.json`）。 */
    public static LetterData letters() {
        requireLoaded();
        return letters;
    }

    /** 建筑定义表（`buildings.json`）—— 环境档位、绑定的村民职业、图纸授权都在里面。 */
    public static BuildingCatalog buildings() {
        requireLoaded();
        return buildings;
    }

    /** 村民定义表（`staff.json`），载入时已与建筑表交叉校验过。 */
    public static StaffCatalog staff() {
        requireLoaded();
        return staff;
    }

    public static StateMigrator migrator() {
        requireLoaded();
        return migrator;
    }

    private static void requireLoaded() {
        if (eras == null) {
            throw new IllegalStateException("Statecraft 数据还没载入（应在 mod 构造里调用 StatecraftData.load()）");
        }
    }

    private static StateNode read(String path) {
        try (InputStream in = StatecraftData.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("内置数据文件缺失：" + path);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonNodes.fromJson(JsonParser.parseString(json));
        } catch (IOException e) {
            throw new UncheckedIOException("读不了内置数据文件 " + path, e);
        }
    }
}
