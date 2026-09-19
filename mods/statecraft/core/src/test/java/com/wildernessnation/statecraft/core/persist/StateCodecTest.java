package com.wildernessnation.statecraft.core.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StateCodecTest {

    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of("赤沙", "黑岩", "铁蹄", "灰狼", "断刃", "荒骨")),
            new Culture("desert_raider", "沙盗", List.of("白岩", "烈日", "黄沙", "孤驼", "枯井", "长夜")));

    private static WorldState generated(long seed) {
        return new WorldGenerator(StatecraftConfig.defaults(), seed)
                .generate(CULTURES, 0.0, 0.0, "landing", 0);
    }

    /** 往返必须逐字段相等，40 个 seed 都算上（不能只测一个例子）。 */
    @Test
    void roundTripIsFieldByFieldIdentical() {
        for (long seed = 0; seed < 40; seed++) {
            WorldState original = generated(seed);
            WorldState restored = StateCodec.read(StateCodec.write(original));
            assertEquals(original, restored, "seed " + seed + " 往返不相等");
            assertEquals(original.nations(), restored.nations(), "seed " + seed + " 国家表不相等");
        }
    }

    /**
     * 世界种子超过 2^53 时 double 存不下 —— 这条用例的存在就是为了挡住"把 long 塞进 double"。
     */
    @Test
    void worldSeedKeepsFullLongPrecision() {
        long seed = 9_007_199_254_740_993L;              // 2^53 + 1
        assertTrue((long) (double) seed != seed, "前提：这个 seed 用 double 存会丢精度");

        WorldState restored = StateCodec.read(StateCodec.write(generated(seed)));
        assertEquals(seed, restored.seed(), "long seed 丢精度了");
    }

    @Test
    void writesTheFieldNamesTheDesignUses() {
        Map<String, StateNode> fields = StateCodec.write(generated(1L)).asObj("world").fields();
        assertEquals(
                List.of("schemaVersion", "seed", "eraId", "eraOrdinal", "seq", "nations"),
                List.copyOf(fields.keySet()),
                "根字段名与顺序要和 NATIONS.md §五 一致");

        Map<String, StateNode> nation =
                ((StateNode.Arr) fields.get("nations")).items().get(0).asObj("nations[0]").fields();
        assertEquals(
                List.of("id", "name", "cultureId", "size", "development", "stance",
                        "military", "treasury", "x", "z", "met", "attitudeToParty"),
                List.copyOf(nation.keySet()));
    }

    @Test
    void writesAStampedCurrentVersion() {
        assertEquals(WorldState.CURRENT_SCHEMA_VERSION,
                StateCodec.schemaVersionOf(StateCodec.write(generated(2L))));
    }

    /** 缺版本号 = 本字段存在之前写的档 = v0，这个语义由编解码层统一。 */
    @Test
    void missingSchemaVersionReadsAsZero() {
        Map<String, StateNode> f = StateNode.fields();
        f.put("seed", new StateNode.Int(1L));
        assertEquals(0, StateCodec.schemaVersionOf(new StateNode.Obj(f)));
    }

    @Test
    void missingFieldErrorCarriesTheFullPath() {
        StateNode root = StateCodec.write(generated(3L));
        Map<String, StateNode> f = StateNode.fields();
        f.putAll(root.asObj("world").fields());
        StateNode.Arr nations = (StateNode.Arr) f.get("nations");
        StateNode.Obj third = nations.items().get(3).asObj("nations[3]");
        Map<String, StateNode> damaged = StateNode.fields();
        for (Map.Entry<String, StateNode> e : third.fields().entrySet()) {
            if (!e.getKey().equals("size")) {
                damaged.put(e.getKey(), e.getValue());
            }
        }
        f.put("nations", new StateNode.Arr(List.of(new StateNode.Obj(damaged))));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> StateCodec.read(new StateNode.Obj(f)));
        assertTrue(e.getMessage().contains("world.nations[0].size"),
                "报错要指出是哪个国家的哪个字段：" + e.getMessage());
    }

    /** 数值越界不在编解码层拦（那是 Nation 校验的事），但要确保它**不会被静默接受**。 */
    @Test
    void outOfRangeNationIsStillRejectedOnRead() {
        StateNode root = StateCodec.write(generated(4L));
        Map<String, StateNode> f = StateNode.fields();
        f.putAll(root.asObj("world").fields());
        StateNode.Arr nations = (StateNode.Arr) f.get("nations");
        Map<String, StateNode> first = StateNode.fields();
        first.putAll(nations.items().get(0).asObj("nations[0]").fields());
        first.put("size", new StateNode.Int(99));
        f.put("nations", new StateNode.Arr(List.of(new StateNode.Obj(first))));

        assertThrows(IllegalArgumentException.class, () -> StateCodec.read(new StateNode.Obj(f)));
    }

    @Test
    void emptyNationListRoundTrips() {
        WorldState empty = new WorldState(
                WorldState.CURRENT_SCHEMA_VERSION, 5L, List.of(), "landing", 0, 0L);
        assertEquals(empty, StateCodec.read(StateCodec.write(empty)));
    }

    @Test
    void nationFieldsSurviveIncludingMetAndAttitude() {
        Nation met = new Nation("n7", "赤沙", "bandit", 9, 12.5, 47.5, 28.0, 26.25,
                -1234.5, 9876.25, true, -55.5);
        WorldState state = new WorldState(
                WorldState.CURRENT_SCHEMA_VERSION, -1L, List.of(met), "infra", 2, 41L);
        assertEquals(state, StateCodec.read(StateCodec.write(state)));
    }
}
