package com.wildernessnation.statecraft.core.persist;

import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link WorldState} ↔ {@link StateNode} 的编解码。
 *
 * <p>字段名与 {@code NATIONS.md} §五 一致（`schemaVersion` / `seed` / `eraId` / `eraOrdinal` /
 * `seq` / `nations[]`），这样薄层翻译成 NBT 时不需要再起一套名字。
 *
 * <p>读的时候<strong>不放过任何异常</strong>：类型不对、字段缺失、数值越界一律抛
 * （错误信息带完整路径，例如 {@code world.nations[3].size}），由 {@link StateMigrator} 决定
 * "重置国家系统"还是往上抛。
 */
public final class StateCodec {

    public static final String ROOT = "world";
    public static final String KEY_SCHEMA_VERSION = "schemaVersion";

    private StateCodec() {}

    public static StateNode write(WorldState state) {
        Map<String, StateNode> root = StateNode.fields();
        root.put(KEY_SCHEMA_VERSION, new StateNode.Int(state.schemaVersion()));
        root.put("seed", new StateNode.Int(state.seed()));
        root.put("eraId", new StateNode.Str(state.eraId()));
        root.put("eraOrdinal", new StateNode.Int(state.eraOrdinal()));
        root.put("seq", new StateNode.Int(state.seq()));
        List<StateNode> nations = new ArrayList<>();
        for (Nation n : state.nations()) {
            nations.add(writeNation(n));
        }
        root.put("nations", new StateNode.Arr(nations));
        return new StateNode.Obj(root);
    }

    private static StateNode writeNation(Nation n) {
        Map<String, StateNode> f = StateNode.fields();
        f.put("id", new StateNode.Str(n.id()));
        f.put("name", new StateNode.Str(n.name()));
        f.put("cultureId", new StateNode.Str(n.cultureId()));
        f.put("size", new StateNode.Int(n.size()));
        f.put("development", new StateNode.Dec(n.development()));
        f.put("stance", new StateNode.Dec(n.stance()));
        f.put("military", new StateNode.Dec(n.military()));
        f.put("treasury", new StateNode.Dec(n.treasury()));
        f.put("x", new StateNode.Dec(n.x()));
        f.put("z", new StateNode.Dec(n.z()));
        f.put("met", new StateNode.Bool(n.met()));
        f.put("attitudeToParty", new StateNode.Dec(n.attitudeToParty()));
        return new StateNode.Obj(f);
    }

    public static WorldState read(StateNode root) {
        String p = ROOT;
        int schemaVersion = root.field(p, KEY_SCHEMA_VERSION).asInt(p + "." + KEY_SCHEMA_VERSION);
        long seed = root.field(p, "seed").asLong(p + ".seed");
        String eraId = root.field(p, "eraId").asString(p + ".eraId");
        int eraOrdinal = root.field(p, "eraOrdinal").asInt(p + ".eraOrdinal");
        long seq = root.field(p, "seq").asLong(p + ".seq");

        StateNode nationsNode = root.field(p, "nations");
        List<StateNode> items = nationsNode.asArr(p + ".nations").items();
        List<Nation> nations = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            nations.add(readNation(items.get(i), StateNode.index(p + ".nations", i)));
        }
        return new WorldState(schemaVersion, seed, nations, eraId, eraOrdinal, seq);
    }

    private static Nation readNation(StateNode node, String p) {
        return new Nation(
                node.field(p, "id").asString(p + ".id"),
                node.field(p, "name").asString(p + ".name"),
                node.field(p, "cultureId").asString(p + ".cultureId"),
                node.field(p, "size").asInt(p + ".size"),
                node.field(p, "development").asDouble(p + ".development"),
                node.field(p, "stance").asDouble(p + ".stance"),
                node.field(p, "military").asDouble(p + ".military"),
                node.field(p, "treasury").asDouble(p + ".treasury"),
                node.field(p, "x").asDouble(p + ".x"),
                node.field(p, "z").asDouble(p + ".z"),
                node.field(p, "met").asBool(p + ".met"),
                node.field(p, "attitudeToParty").asDouble(p + ".attitudeToParty"));
    }

    /**
     * 读存档格式版本；<strong>字段缺失按 0 处理</strong>。
     *
     * <p>缺版本号 = 本字段存在之前写的档，也就是 v0 —— 这个语义必须在迁移器里统一，所以放在这里。
     */
    public static int schemaVersionOf(StateNode root) {
        StateNode v = root.optionalField(ROOT, KEY_SCHEMA_VERSION);
        if (v == null) {
            return 0;
        }
        return v.asInt(ROOT + "." + KEY_SCHEMA_VERSION);
    }
}
