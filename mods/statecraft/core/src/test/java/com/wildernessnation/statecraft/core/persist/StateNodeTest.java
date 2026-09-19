package com.wildernessnation.statecraft.core.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StateNodeTest {

    @Test
    void objAndArrAreImmutableCopies() {
        Map<String, StateNode> mutable = StateNode.fields();
        mutable.put("a", new StateNode.Int(1));
        StateNode.Obj obj = new StateNode.Obj(mutable);
        mutable.put("b", new StateNode.Int(2));
        assertEquals(1, obj.fields().size(), "外部改动不能影响已建好的节点");
        assertThrows(UnsupportedOperationException.class, () -> obj.fields().put("c", new StateNode.Int(3)));

        List<StateNode> items = new ArrayList<>();
        items.add(new StateNode.Int(1));
        StateNode.Arr arr = new StateNode.Arr(items);
        items.add(new StateNode.Int(2));
        assertEquals(1, arr.items().size());
        assertThrows(UnsupportedOperationException.class, () -> arr.items().add(new StateNode.Int(3)));
    }

    /**
     * 字段顺序必须稳定：{@code Map.copyOf} 不保证迭代顺序，用它会让同一份状态的
     * 序列化字节时好时坏 —— 那就没法 diff、没法当崩溃重放的指纹。
     */
    @Test
    void objKeepsInsertionOrder() {
        Map<String, StateNode> f = StateNode.fields();
        for (String k : List.of("z", "a", "m", "b")) {
            f.put(k, new StateNode.Int(0));
        }
        assertEquals(List.of("z", "a", "m", "b"), List.copyOf(new StateNode.Obj(f).fields().keySet()));
    }

    @Test
    void typeErrorsMentionThePathAndTheActualType() {
        StateNode.Obj obj = obj("size", new StateNode.Str("五"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> obj.field("world.nations[3]", "size").asInt("world.nations[3].size"));
        assertTrue(e.getMessage().contains("world.nations[3].size"), "要带完整路径：" + e.getMessage());
        assertTrue(e.getMessage().contains("整数"), "要说期望类型：" + e.getMessage());
        assertTrue(e.getMessage().contains("五"), "要带上实际值：" + e.getMessage());
    }

    @Test
    void missingFieldAndWrongContainerBothFailLoudly() {
        StateNode.Obj obj = obj("size", new StateNode.Int(1));
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> obj.field("world", "stance"));
        assertTrue(missing.getMessage().contains("stance"));

        IllegalStateException notObj = assertThrows(IllegalStateException.class,
                () -> new StateNode.Int(1).field("world", "x"));
        assertTrue(notObj.getMessage().contains("对象"));
    }

    @Test
    void optionalFieldReturnsNullWhenMissing() {
        StateNode.Obj obj = obj("size", new StateNode.Int(1));
        assertNull(obj.optionalField("world", "schemaVersion"));
        assertEquals(1, obj.optionalField("world", "size").asInt("world.size"));
    }

    /** 整数与小数分开：世界种子是 long，用 double 存会静默丢精度。 */
    @Test
    void intAndDecAreDifferentTypes() {
        assertThrows(IllegalStateException.class, () -> new StateNode.Dec(1.0).asLong("seed"));
        assertThrows(IllegalStateException.class, () -> new StateNode.Int(1).asString("id"));
        // 但小数可以容忍整数（旧版本常把 0.0 写成 0）
        assertEquals(0.0, new StateNode.Int(0).asDouble("dev"), 0.0);
        assertEquals(2.5, new StateNode.Dec(2.5).asDouble("dev"), 0.0);
    }

    @Test
    void asIntRejectsValuesBeyondIntRange() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new StateNode.Int(5_000_000_000L).asInt("ordinal"));
        assertTrue(e.getMessage().contains("int"), e.getMessage());
    }

    @Test
    void rejectsNonFiniteDecimalsAndNullStrings() {
        assertThrows(IllegalArgumentException.class, () -> new StateNode.Dec(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new StateNode.Dec(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new StateNode.Str(null));
    }

    @Test
    void indexBuildsReadablePaths() {
        assertEquals("world.nations[7]", StateNode.index("world.nations", 7));
        assertNotEquals("world.nations", StateNode.index("world.nations", 0));
        assertInstanceOf(StateNode.Obj.class, obj("a", new StateNode.Int(1)));
    }

    private static StateNode.Obj obj(String key, StateNode value) {
        Map<String, StateNode> f = StateNode.fields();
        f.put(key, value);
        return new StateNode.Obj(f);
    }
}
