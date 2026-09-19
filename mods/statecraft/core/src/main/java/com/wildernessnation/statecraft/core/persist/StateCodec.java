package com.wildernessnation.statecraft.core.persist;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link WorldState} ↔ {@link StateNode} 的编解码。
 *
 * <p>字段名与顺序都照 `NATIONS.md` §五（`schemaVersion` / `seed` / `eraId` / `eraOrdinal` /
 * `seq` / `nations[]` / `relations[]` / `events[]`），末尾多一个 {@code elapsedOnlineHours}
 * （文档没列，但 §七 的累计在线时间要有地方攒）。
 *
 * <p>读的时候<strong>不放过任何异常</strong>：类型不对、字段缺失、枚举名不认识一律抛
 * （错误信息带完整路径，例如 {@code world.nations[3].size}），由 {@link StateMigrator} 决定
 * "重置国家系统"还是往上抛。
 *
 * <p>枚举按**名字**存（`"WAR"` 而不是序号）：序号会随枚举顺序调整而错位，
 * 那是最难查的一类存档 bug。
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

        List<StateNode> relations = new ArrayList<>();
        for (Relation r : state.relations()) {
            relations.add(writeRelation(r));
        }
        root.put("relations", new StateNode.Arr(relations));

        List<StateNode> events = new ArrayList<>();
        for (Event e : state.events()) {
            events.add(writeEvent(e));
        }
        root.put("events", new StateNode.Arr(events));

        root.put("elapsedOnlineHours", new StateNode.Dec(state.elapsedOnlineHours()));
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
        f.put("status", new StateNode.Str(n.status().name()));
        f.put("warOnParty", new StateNode.Bool(n.warOnParty()));
        f.put("fatigue", new StateNode.Dec(n.fatigue()));
        f.put("absorbedCount", new StateNode.Int(n.absorbedCount()));
        return new StateNode.Obj(f);
    }

    private static StateNode writeRelation(Relation r) {
        Map<String, StateNode> f = StateNode.fields();
        f.put("a", new StateNode.Str(r.a()));
        f.put("b", new StateNode.Str(r.b()));
        f.put("attitude", new StateNode.Dec(r.attitude()));
        f.put("state", new StateNode.Str(r.state().name()));
        f.put("warScore", new StateNode.Dec(r.warScore()));
        f.put("truceUntilSeq", new StateNode.Int(r.truceUntilSeq()));
        f.put("losingStreak", new StateNode.Int(r.losingStreak()));
        return new StateNode.Obj(f);
    }

    private static StateNode writeEvent(Event e) {
        Map<String, StateNode> f = StateNode.fields();
        f.put("seq", new StateNode.Int(e.seq()));
        f.put("type", new StateNode.Str(e.type()));
        f.put("textKey", new StateNode.Str(e.textKey()));
        f.put("parties", strings(e.parties()));
        f.put("params", strings(e.params()));
        f.put("visible", new StateNode.Bool(e.visible()));
        f.put("read", new StateNode.Bool(e.read()));
        return new StateNode.Obj(f);
    }

    private static StateNode strings(List<String> values) {
        List<StateNode> out = new ArrayList<>(values.size());
        for (String v : values) {
            out.add(new StateNode.Str(v));
        }
        return new StateNode.Arr(out);
    }

    public static WorldState read(StateNode root) {
        String p = ROOT;
        int schemaVersion = root.field(p, KEY_SCHEMA_VERSION).asInt(p + "." + KEY_SCHEMA_VERSION);
        long seed = root.field(p, "seed").asLong(p + ".seed");
        String eraId = root.field(p, "eraId").asString(p + ".eraId");
        int eraOrdinal = root.field(p, "eraOrdinal").asInt(p + ".eraOrdinal");
        long seq = root.field(p, "seq").asLong(p + ".seq");

        List<Nation> nations = new ArrayList<>();
        List<StateNode> nationNodes = root.field(p, "nations").asArr(p + ".nations").items();
        for (int i = 0; i < nationNodes.size(); i++) {
            nations.add(readNation(nationNodes.get(i), StateNode.index(p + ".nations", i)));
        }

        List<Relation> relations = new ArrayList<>();
        List<StateNode> relationNodes = root.field(p, "relations").asArr(p + ".relations").items();
        for (int i = 0; i < relationNodes.size(); i++) {
            relations.add(readRelation(relationNodes.get(i), StateNode.index(p + ".relations", i)));
        }

        List<Event> events = new ArrayList<>();
        List<StateNode> eventNodes = root.field(p, "events").asArr(p + ".events").items();
        for (int i = 0; i < eventNodes.size(); i++) {
            events.add(readEvent(eventNodes.get(i), StateNode.index(p + ".events", i)));
        }

        double hours = root.field(p, "elapsedOnlineHours").asDouble(p + ".elapsedOnlineHours");
        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations, relations,
                events, hours);
    }

    static Nation readNation(StateNode node, String p) {
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
                node.field(p, "attitudeToParty").asDouble(p + ".attitudeToParty"),
                readEnum(Nation.Status.class, node.field(p, "status").asString(p + ".status"),
                        p + ".status"),
                node.field(p, "warOnParty").asBool(p + ".warOnParty"),
                node.field(p, "fatigue").asDouble(p + ".fatigue"),
                node.field(p, "absorbedCount").asInt(p + ".absorbedCount"));
    }

    private static Relation readRelation(StateNode node, String p) {
        return new Relation(
                node.field(p, "a").asString(p + ".a"),
                node.field(p, "b").asString(p + ".b"),
                node.field(p, "attitude").asDouble(p + ".attitude"),
                readEnum(Relation.State.class, node.field(p, "state").asString(p + ".state"),
                        p + ".state"),
                node.field(p, "warScore").asDouble(p + ".warScore"),
                node.field(p, "truceUntilSeq").asLong(p + ".truceUntilSeq"),
                node.field(p, "losingStreak").asInt(p + ".losingStreak"));
    }

    private static Event readEvent(StateNode node, String p) {
        return new Event(
                node.field(p, "seq").asLong(p + ".seq"),
                node.field(p, "type").asString(p + ".type"),
                readStrings(node.field(p, "parties"), p + ".parties"),
                node.field(p, "textKey").asString(p + ".textKey"),
                readStrings(node.field(p, "params"), p + ".params"),
                node.field(p, "visible").asBool(p + ".visible"),
                node.field(p, "read").asBool(p + ".read"));
    }

    private static List<String> readStrings(StateNode node, String p) {
        List<StateNode> items = node.asArr(p).items();
        List<String> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            out.add(items.get(i).asString(StateNode.index(p, i)));
        }
        return out;
    }

    /** 枚举按名字读；不认识的名字要报出**全部合法值**，否则存档日志里只有一句"没有这个枚举"。 */
    private static <E extends Enum<E>> E readEnum(Class<E> type, String name, String path) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        throw new IllegalStateException(path + " 不是合法的 " + type.getSimpleName() + "：" + name
                + "（合法值：" + Arrays.toString(type.getEnumConstants()) + "）");
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
