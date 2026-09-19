package com.wildernessnation.statecraft.core.settle;

import com.wildernessnation.statecraft.core.persist.StateHash;
import com.wildernessnation.statecraft.core.persist.StateNode;
import java.util.Map;

/**
 * 事务日志的一条（`NATIONS.md` §十三）。
 *
 * <p>记的是"**这一步的输入**"，不是"这一步的结果"——结果可以由输入 + seed 重算出来，
 * 那是确定性的意义所在。所以一条只需要：结算序号、触发方式、代表多少累计在线小时、
 * 以及**动手之前那个状态的哈希**（用来确认日志没被错配/篡改）。
 *
 * @param seq            这一步算完之后的结算序号（也就是动手前 seq + 1）
 * @param trigger        触发方式
 * @param hoursDelta     这一步代表多少累计在线小时
 * @param inputHash      动手之前那个状态的哈希（{@link StateHash#of}）
 */
public record SettlementLogEntry(
        long seq, SettlementTrigger trigger, double hoursDelta, long inputHash) {

    public static final String KEY_SEQ = "seq";
    public static final String KEY_TRIGGER = "trigger";
    public static final String KEY_HOURS = "hoursDelta";
    public static final String KEY_HASH = "inputHash";

    public SettlementLogEntry {
        if (seq < 1) {
            throw new IllegalArgumentException("SettlementLogEntry.seq 必须 >= 1：" + seq);
        }
        if (trigger == null) {
            throw new IllegalArgumentException("SettlementLogEntry.trigger 不能为 null");
        }
        if (!(hoursDelta > 0.0) || !Double.isFinite(hoursDelta)) {
            throw new IllegalArgumentException(
                    "SettlementLogEntry.hoursDelta 必须 > 0 且有限：" + hoursDelta);
        }
    }

    public SettlementInput input() {
        return new SettlementInput(trigger, hoursDelta);
    }

    /** 存成中性树（薄层再翻成 NBT），字段名与 §十三 的说法对齐。 */
    public StateNode toNode() {
        Map<String, StateNode> f = StateNode.fields();
        f.put(KEY_SEQ, new StateNode.Int(seq));
        f.put(KEY_TRIGGER, new StateNode.Str(trigger.name()));
        f.put(KEY_HOURS, new StateNode.Dec(hoursDelta));
        f.put(KEY_HASH, new StateNode.Int(inputHash));
        return new StateNode.Obj(f);
    }

    public static SettlementLogEntry fromNode(StateNode node) {
        String path = "logEntry";
        String triggerName = node.field(path, KEY_TRIGGER).asString(path + "." + KEY_TRIGGER);
        SettlementTrigger trigger;
        try {
            trigger = SettlementTrigger.valueOf(triggerName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(path + "." + KEY_TRIGGER
                    + " 不是合法取值：" + triggerName, e);
        }
        return new SettlementLogEntry(
                node.field(path, KEY_SEQ).asLong(path + "." + KEY_SEQ),
                trigger,
                node.field(path, KEY_HOURS).asDouble(path + "." + KEY_HOURS),
                node.field(path, KEY_HASH).asLong(path + "." + KEY_HASH));
    }
}
