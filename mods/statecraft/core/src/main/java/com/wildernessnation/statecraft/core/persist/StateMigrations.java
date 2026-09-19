package com.wildernessnation.statecraft.core.persist;

import com.wildernessnation.statecraft.core.model.Relation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 生产用的迁移链。
 *
 * <p>这里**不是**死代码：v1 是**真的发布过**的格式 —— 测试世界 `b5v3` 里那份
 * `saves/b5v3/data/statecraft.dat` 就是 v1（`schemaVersion=1`）。所以 v1→v2 这一级
 * 会有真实的旧档走它，而不是"为不存在的旧版本写迁移"。
 *
 * <p>加字段的流程固定为三步：改 {@code WorldState}/{@code Nation} → {@code CURRENT_SCHEMA_VERSION + 1}
 * → 在这里补一级迁移。{@link StateMigrator} 每走完一级会统一盖章版本号，迁移函数只管搬字段。
 */
public final class StateMigrations {

    private StateMigrations() {}

    /** 生产迁移链。 */
    public static StateMigrator production() {
        return new StateMigrator(List.of(v1ToV2()));
    }

    /**
     * v1 → v2：计划 3 给国家加了结算所需的字段，并引入关系与事件。
     *
     * <p>补的东西**必须都是"没有任何副作用"的默认值**，否则"迁移"就变成了"凭旧档猜历史"：
     * <ul>
     *   <li>国家：{@code status=ALIVE}、{@code partyStatus=NEUTRAL}、{@code fatigue=0}、
     *       {@code absorbedCount=0}（v1 时期还没有战争、外交与吸收，所以这些就是"还没发生过"）</li>
     *   <li>关系：所有国家两两之间 {@code PEACE}、态度 0（v1 时期国家之间没有任何互动）</li>
     *   <li>事件：空表（v1 不产生事件）</li>
     *   <li>{@code elapsedOnlineHours=0} —— 这个值 v1 **真的没记过**，
     *       所以只能说"从 0 开始算"。这是迁移里唯一一处信息损失，写进注释以免以后误判</li>
     * </ul>
     */
    public static StateMigration v1ToV2() {
        return new StateMigration(1, StateMigrations::addSettlementFields);
    }

    private static StateNode addSettlementFields(StateNode root) {
        Map<String, StateNode> fields = StateNode.fields();
        fields.putAll(root.asObj(StateCodec.ROOT).fields());

        List<String> ids = new ArrayList<>();
        StateNode nationsNode = root.optionalField(StateCodec.ROOT, "nations");
        if (nationsNode instanceof StateNode.Arr nations) {
            List<StateNode> upgraded = new ArrayList<>(nations.items().size());
            for (StateNode item : nations.items()) {
                ids.add(item.field("nations[]", "id").asString("nations[].id"));
                upgraded.add(upgradeNation(item));
            }
            fields.put("nations", new StateNode.Arr(upgraded));
        }

        List<StateNode> relations = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                relations.add(peaceRelation(ids.get(i), ids.get(j)));
            }
        }
        fields.put("relations", new StateNode.Arr(relations));
        fields.put("events", new StateNode.Arr(List.of()));
        fields.put("elapsedOnlineHours", new StateNode.Dec(0.0));
        return new StateNode.Obj(fields);
    }

    private static StateNode upgradeNation(StateNode nation) {
        Map<String, StateNode> f = StateNode.fields();
        f.putAll(nation.asObj("nations[]").fields());
        f.put("status", new StateNode.Str(com.wildernessnation.statecraft.core.model.Nation.Status.ALIVE.name()));
        f.put("partyStatus", new StateNode.Str(
                com.wildernessnation.statecraft.core.model.Nation.PartyStatus.NEUTRAL.name()));
        f.put("partyStatusUntilSeq", new StateNode.Int(0));
        f.put("partyWarScore", new StateNode.Dec(0.0));
        f.put("fatigue", new StateNode.Dec(0.0));
        f.put("absorbedCount", new StateNode.Int(0));
        return new StateNode.Obj(f);
    }

    private static StateNode peaceRelation(String a, String b) {
        Map<String, StateNode> f = StateNode.fields();
        f.put("a", new StateNode.Str(a));
        f.put("b", new StateNode.Str(b));
        f.put("attitude", new StateNode.Dec(0.0));
        f.put("state", new StateNode.Str(Relation.State.PEACE.name()));
        f.put("warScore", new StateNode.Dec(0.0));
        f.put("truceUntilSeq", new StateNode.Int(0));
        f.put("losingStreak", new StateNode.Int(0));
        return new StateNode.Obj(f);
    }
}
