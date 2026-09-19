package com.wildernessnation.statecraft.core.persist;

import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 读档：按 {@code schemaVersion} 选迁移链 → 归位时代表 → 解码。
 *
 * <p><strong>总原则（`NATIONS.md` §十三）：绝不让存档报废。</strong>
 * 版本太新、缺迁移、内容损坏 → 一律只<strong>重置国家系统</strong>（{@link LoadOutcome#reset()}）
 * 并留下中文警告，而不是抛异常把世界弄坏。
 *
 * <p>唯一的例外是纯 bug（传 null 之类），那属于调用方错误，直接抛。
 */
public final class StateMigrator {

    private final List<StateMigration> migrations;

    public StateMigrator(List<StateMigration> migrations) {
        Objects.requireNonNull(migrations, "迁移表不能为 null");
        List<StateMigration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(StateMigration::fromVersion));
        Set<Integer> seen = new HashSet<>();
        for (StateMigration m : sorted) {
            if (!seen.add(m.fromVersion())) {
                throw new IllegalArgumentException("同一版本只能有一级迁移，重复起点：" + m.fromVersion());
            }
        }
        this.migrations = List.copyOf(sorted);
    }

    /** 只走"当前版本"的读档（还没有历史版本可迁）。 */
    public static StateMigrator currentOnly() {
        return new StateMigrator(List.of());
    }

    public LoadOutcome load(StateNode root, EraTable eras) {
        Objects.requireNonNull(root, "存档根节点不能为 null");
        Objects.requireNonNull(eras, "时代表不能为 null");
        List<String> warnings = new ArrayList<>();
        try {
            return doLoad(root, eras, warnings);
        } catch (RuntimeException e) {
            warnings.add("存档读取失败：" + e.getMessage() + " —— 重置国家系统，世界不报废");
            return new LoadOutcome(Optional.empty(), warnings);
        }
    }

    private LoadOutcome doLoad(StateNode root, EraTable eras, List<String> warnings) {
        int storedVersion = StateCodec.schemaVersionOf(root);
        if (storedVersion > WorldState.CURRENT_SCHEMA_VERSION) {
            warnings.add("存档 schemaVersion=" + storedVersion + " 比本 mod 的 "
                    + WorldState.CURRENT_SCHEMA_VERSION + " 还新（是不是装回了旧版 mod？）"
                    + " —— 重置国家系统，世界不报废");
            return new LoadOutcome(Optional.empty(), warnings);
        }

        StateNode node = root;
        for (int v = storedVersion; v < WorldState.CURRENT_SCHEMA_VERSION; v++) {
            StateMigration step = migrationFrom(v);
            if (step == null) {
                warnings.add("缺少 schemaVersion " + v + " → " + (v + 1)
                        + " 的迁移 —— 重置国家系统，世界不报废");
                return new LoadOutcome(Optional.empty(), warnings);
            }
            node = stampVersion(step.apply().apply(node), v + 1);
            warnings.add("已迁移存档 schemaVersion " + v + " → " + (v + 1));
        }

        node = reanchorEra(node, eras, warnings);
        WorldState state = StateCodec.read(node);
        return new LoadOutcome(Optional.of(state), warnings);
    }

    private StateMigration migrationFrom(int fromVersion) {
        for (StateMigration m : migrations) {
            if (m.fromVersion() == fromVersion) {
                return m;
            }
        }
        return null;
    }

    /**
     * 时代表变了也不能废档：存档里的 {@code eraId} 不在当前表里 → 用 ordinal 提示归位；
     * {@code eraOrdinal} 与 {@code eraId} 互相矛盾 → 以 <strong>eraId 为准</strong>
     * （id 是稳定标识，序号只是排序用的，见 `NATIONS.md` §七）。
     */
    private static StateNode reanchorEra(StateNode root, EraTable eras, List<String> warnings) {
        StateNode idNode = root.optionalField(StateCodec.ROOT, "eraId");
        StateNode ordinalNode = root.optionalField(StateCodec.ROOT, "eraOrdinal");
        String storedId = idNode instanceof StateNode.Str s ? s.value() : "";
        int storedOrdinal = ordinalNode == null ? 0 : ordinalNode.asInt(StateCodec.ROOT + ".eraOrdinal");

        Era era = eras.fallbackForUnknownId(storedId, storedOrdinal);
        if (era.id().equals(storedId) && era.ordinal() == storedOrdinal) {
            return root;
        }
        if (eras.byId(storedId).isEmpty()) {
            warnings.add("存档 eraId=\"" + storedId + "\" 不在当前时代表里 → 归位到 \""
                    + era.id() + "\"（序号 " + era.ordinal() + "），不废档");
        } else {
            warnings.add("存档 eraId=\"" + storedId + "\" 与 eraOrdinal=" + storedOrdinal
                    + " 不一致 → 以 eraId 为准，序号改为 " + era.ordinal());
        }
        return putField(
                putField(root, "eraId", new StateNode.Str(era.id())),
                "eraOrdinal",
                new StateNode.Int(era.ordinal()));
    }

    /**
     * 换掉根节点的一个字段（其余原样保留，已有字段保持原位置 → 字段顺序稳定）。
     *
     * <p>刻意<strong>不提供多字段版本</strong>：那要吃一个 {@code Map}，而 {@code Map.of} 的迭代顺序
     * 是未定义的，会悄悄破坏"序列化字节稳定"这条前提。
     */
    public static StateNode putField(StateNode root, String key, StateNode value) {
        Map<String, StateNode> fields = StateNode.fields();
        fields.putAll(root.asObj(StateCodec.ROOT).fields());
        fields.put(key, value);
        return new StateNode.Obj(fields);
    }

    /** 迁移链每走完一级统一盖章，免得某一级忘了改版本号导致链断掉。 */
    private static StateNode stampVersion(StateNode root, int version) {
        return putField(root, StateCodec.KEY_SCHEMA_VERSION, new StateNode.Int(version));
    }
}
