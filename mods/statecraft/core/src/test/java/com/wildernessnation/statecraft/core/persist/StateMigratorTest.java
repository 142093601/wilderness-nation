package com.wildernessnation.statecraft.core.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateMigratorTest {

    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of("赤沙", "黑岩", "铁蹄", "灰狼", "断刃", "荒骨")),
            new Culture("desert_raider", "沙盗", List.of("白岩", "烈日", "黄沙", "孤驼", "枯井", "长夜")));

    private static EraTable sixEras() {
        return new EraTable(List.of(
                new Era("landing", "落地", 0, 12, 1.0, Set.of("intel_book")),
                new Era("founding", "立国", 1, 20, 1.15, Set.of()),
                new Era("infra", "基建", 2, 25, 1.3, Set.of("intel_station")),
                new Era("expedition", "远征", 3, 25, 1.45, Set.of()),
                new Era("defense", "御敌", 4, 30, 1.6, Set.of()),
                new Era("civilization", "文明", 5, 38, 1.75, Set.of("intel_station_2"))));
    }

    private static WorldState generated(long seed, String eraId, int eraOrdinal) {
        return new WorldGenerator(StatecraftConfig.defaults(), seed)
                .generate(CULTURES, 0.0, 0.0, eraId, eraOrdinal);
    }

    @Test
    void currentVersionLoadsWithoutAnyWarning() {
        LoadOutcome out = StateMigrator.currentOnly()
                .load(StateCodec.write(generated(1L, "landing", 0)), sixEras());
        assertFalse(out.reset());
        assertEquals(List.of(), out.warnings(), "版本与时代都对上就不该有警告");
        assertEquals(generated(1L, "landing", 0), out.state().orElseThrow());
    }

    /** 缺版本号 = v0；没有 v0→v1 迁移时就只能重置国家系统——但绝不抛异常。 */
    @Test
    void missingMigrationResetsInsteadOfThrowing() {
        LoadOutcome out = StateMigrator.currentOnly().load(legacyV0(), sixEras());
        assertTrue(out.reset());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("缺少 schemaVersion 0 → 1")),
                "要说清缺哪一级：" + out.warnings());
    }

    /** 玩家装回旧版 mod：存档比代码新。绝不猜未来格式。 */
    @Test
    void newerSchemaResetsInsteadOfGuessing() {
        StateNode node = StateMigrator.putField(
                StateCodec.write(generated(1L, "landing", 0)),
                StateCodec.KEY_SCHEMA_VERSION, new StateNode.Int(99));
        LoadOutcome out = StateMigrator.currentOnly().load(node, sixEras());
        assertTrue(out.reset());
        assertTrue(out.warnings().get(0).contains("99"), out.warnings().toString());
    }

    /**
     * 迁移链走通：假迁移把 v0（字段名 {@code era}）抬到 v1，再由**生产用的真实迁移**
     * {@link StateMigrations#v1ToV2()} 抬到当前版本。
     *
     * <p>v0→v1 那级只活在测试里（生产代码从没发布过 v0）；v1→v2 是真实的 —— 测试世界里的
     * `.dat` 就是 v1。这样这条用例同时验证了"链能串起来"和"真实迁移能跑"。
     */
    @Test
    void registeredMigrationBringsLegacySaveForward() {
        StateMigrator migrator = new StateMigrator(List.of(
                new StateMigration(0, StateMigratorTest::renameEraField),
                StateMigrations.v1ToV2()));
        LoadOutcome out = migrator.load(legacyV0(), sixEras());
        assertFalse(out.reset(), out.warnings().toString());
        WorldState state = out.state().orElseThrow();
        assertEquals("infra", state.eraId());
        assertEquals(2, state.eraOrdinal());
        assertEquals(7L, state.seed());
        assertEquals(WorldState.CURRENT_SCHEMA_VERSION, state.schemaVersion());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("0 → 1")), out.warnings().toString());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("1 → 2")), out.warnings().toString());
    }

    /**
     * **真实的 v1 → v2 迁移**：v1 只有 12 个国家的字段，结算所需的字段与关系/事件都不存在。
     * 迁移必须补上"还没发生过"的默认值，并且**不能改动 v1 已有的任何内容**。
     */
    @Test
    void v1SaveMigratesToV2WithQuietDefaults() {
        LoadOutcome out = StateMigrations.production().load(legacyV1(), sixEras());
        assertFalse(out.reset(), out.warnings().toString());
        WorldState state = out.state().orElseThrow();

        assertEquals(WorldState.CURRENT_SCHEMA_VERSION, state.schemaVersion());
        assertEquals(2, state.nations().size());
        Nation first = state.nation("n0").orElseThrow();
        assertEquals("赤沙", first.name(), "v1 已有的字段一个都不许改");
        assertEquals(5, first.size());
        assertEquals(60.0, first.development(), 0.0);
        assertEquals(Nation.Status.ALIVE, first.status(), "补「还没发生过」");
        assertEquals(Nation.PartyStatus.NEUTRAL, first.partyStatus(), "补「还没发生过」");
        assertEquals(0.0, first.partyWarScore(), 0.0);
        assertEquals(0.0, first.fatigue(), 0.0);
        assertEquals(0, first.absorbedCount());

        assertEquals(1, state.relations().size(), "2 个国家 → 1 对关系");
        Relation only = state.relations().get(0);
        assertEquals(Relation.State.PEACE, only.state());
        assertEquals(0.0, only.attitude(), 0.0);
        assertEquals(0, state.events().size());
        assertEquals(0.0, state.elapsedOnlineHours(), 0.0,
                "v1 真的没记过这个数，只能从 0 开始算（迁移里唯一一处信息损失）");
    }

    /** 迁移后再写一遍，应该就是当前格式、且能原样读回来（迁完的档要能继续用）。 */
    @Test
    void migratedV1SaveRoundTripsAsCurrentVersion() {
        WorldState migrated = StateMigrations.production().load(legacyV1(), sixEras())
                .state().orElseThrow();
        assertEquals(migrated, StateCodec.read(StateCodec.write(migrated)));
    }

    /** 时代表变了也不废档：未知 eraId 按 ordinal 提示归位。 */
    @Test
    void unknownEraIdIsReanchoredWithAWarning() {
        StateNode node = StateMigrator.putField(
                StateMigrator.putField(
                        StateCodec.write(generated(1L, "landing", 0)),
                        "eraId", new StateNode.Str("era_from_a_removed_table")),
                "eraOrdinal", new StateNode.Int(3));
        LoadOutcome out = StateMigrator.currentOnly().load(node, sixEras());
        assertFalse(out.reset());
        assertEquals("expedition", out.state().orElseThrow().eraId(), "按 ordinal=3 归位");
        assertEquals(3, out.state().orElseThrow().eraOrdinal());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("不在当前时代表里")),
                out.warnings().toString());
    }

    /** eraId 与 eraOrdinal 互相矛盾时以 eraId 为准（id 是稳定标识，序号只是排序用）。 */
    @Test
    void eraOrdinalDisagreementIsFixedFromTheStableId() {
        StateNode node = StateMigrator.putField(
                StateCodec.write(generated(1L, "landing", 0)), "eraOrdinal", new StateNode.Int(5));
        LoadOutcome out = StateMigrator.currentOnly().load(node, sixEras());
        assertFalse(out.reset());
        assertEquals("landing", out.state().orElseThrow().eraId());
        assertEquals(0, out.state().orElseThrow().eraOrdinal(), "序号被 eraId 纠正为 0");
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("以 eraId 为准")),
                out.warnings().toString());
    }

    @Test
    void blankEraIdFallsBackToTheOrdinalHint() {
        StateNode node = StateMigrator.putField(
                StateMigrator.putField(
                        StateCodec.write(generated(1L, "landing", 0)),
                        "eraId", new StateNode.Str("")),
                "eraOrdinal", new StateNode.Int(4));
        LoadOutcome out = StateMigrator.currentOnly().load(node, sixEras());
        assertFalse(out.reset());
        assertEquals("defense", out.state().orElseThrow().eraId());
    }

    /** 内容损坏（不是版本问题）同样只重置国家系统。 */
    @Test
    void corruptedContentResetsWithAWarning() {
        StateNode node = StateMigrator.putField(
                StateCodec.write(generated(1L, "landing", 0)),
                "nations", new StateNode.Str("这不是数组"));
        LoadOutcome out = StateMigrator.currentOnly().load(node, sixEras());
        assertTrue(out.reset());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("重置国家系统")),
                out.warnings().toString());
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("world.nations")),
                "警告要能定位到坏字段：" + out.warnings());
    }

    /** 根节点根本不是对象（存档整个坏了）也要走重置，不能把异常抛给服务器线程。 */
    @Test
    void nonObjectRootResetsInsteadOfThrowing() {
        LoadOutcome out = StateMigrator.currentOnly().load(new StateNode.Int(42), sixEras());
        assertTrue(out.reset());
        assertFalse(out.warnings().isEmpty());
    }

    @Test
    void rejectsDuplicateMigrationStartVersions() {
        assertThrows(IllegalArgumentException.class, () -> new StateMigrator(List.of(
                new StateMigration(0, n -> n),
                new StateMigration(0, n -> n))));
    }

    @Test
    void rejectsNegativeMigrationVersionAndNullFunction() {
        assertThrows(IllegalArgumentException.class, () -> new StateMigration(-1, n -> n));
        assertThrows(NullPointerException.class, () -> new StateMigration(0, null));
    }

    @Test
    void loadOutcomeRejectsNullState() {
        assertThrows(IllegalArgumentException.class, () -> new LoadOutcome(null, List.of()));
    }

    // ---- 造一个 v0 的旧档：字段名是 `era`，而且没有 schemaVersion ----

    private static StateNode legacyV0() {
        Map<String, StateNode> f = StateNode.fields();
        f.put("seed", new StateNode.Int(7L));
        f.put("era", new StateNode.Str("infra"));
        f.put("eraOrdinal", new StateNode.Int(2));
        f.put("seq", new StateNode.Int(0L));
        f.put("nations", new StateNode.Arr(List.of()));
        return new StateNode.Obj(f);
    }

    /** v0 → v1：把 `era` 改名为 `eraId`（版本号由迁移器统一盖章）。 */
    private static StateNode renameEraField(StateNode root) {
        Map<String, StateNode> fields = StateNode.fields();
        for (Map.Entry<String, StateNode> e : root.asObj(StateCodec.ROOT).fields().entrySet()) {
            if (!e.getKey().equals("era")) {
                fields.put(e.getKey(), e.getValue());
            }
        }
        fields.put("eraId", root.field(StateCodec.ROOT, "era"));
        return new StateNode.Obj(fields);
    }

    // ---- 造一个**真实的 v1** 旧档：12 国字段齐、没有 status/fatigue/relations/events ----

    private static StateNode legacyV1() {
        Map<String, StateNode> f = StateNode.fields();
        f.put("schemaVersion", new StateNode.Int(1));
        f.put("seed", new StateNode.Int(7L));
        f.put("eraId", new StateNode.Str("infra"));
        f.put("eraOrdinal", new StateNode.Int(2));
        f.put("seq", new StateNode.Int(3L));
        f.put("nations", new StateNode.Arr(List.of(
                legacyNation("n0", "赤沙", 5, 60.0),
                legacyNation("n1", "黑岩", 3, 80.0))));
        return new StateNode.Obj(f);
    }

    private static StateNode legacyNation(String id, String name, int size, double dev) {
        Map<String, StateNode> n = StateNode.fields();
        n.put("id", new StateNode.Str(id));
        n.put("name", new StateNode.Str(name));
        n.put("cultureId", new StateNode.Str("bandit"));
        n.put("size", new StateNode.Int(size));
        n.put("development", new StateNode.Dec(dev));
        n.put("stance", new StateNode.Dec(-10.0));
        n.put("military", new StateNode.Dec(20.0));
        n.put("treasury", new StateNode.Dec(50.0));
        n.put("x", new StateNode.Dec(100.0));
        n.put("z", new StateNode.Dec(200.0));
        n.put("met", new StateNode.Bool(false));
        n.put("attitudeToParty", new StateNode.Dec(0.0));
        return new StateNode.Obj(n);
    }
}
