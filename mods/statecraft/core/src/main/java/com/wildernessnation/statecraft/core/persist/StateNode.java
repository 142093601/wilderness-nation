package com.wildernessnation.statecraft.core.persist;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持久化的<strong>中性数据树</strong>：core 只认它，NBT / JSON / 任何后端都由薄层翻译。
 *
 * <p><strong>为什么不用 {@code Map<String,Object>}</strong>：那样每个读字段的地方都要自己 cast，
 * 而版本迁移最容易出的错就是类型搞混——编译器一点忙都帮不上。放一棵 sealed 树，
 * 取值失败时能直接报出字段名和期望类型。
 *
 * <p><strong>为什么 {@code Int(long)} 与 {@code Dec(double)} 分开</strong>：世界种子是 long，
 * 超过 2^53 用 double 存会<strong>静默丢精度</strong>，存档再也复现不出同一个世界。
 *
 * <p><strong>字段顺序确定</strong>：{@code Obj} 用插入序的 map 包成不可变视图
 * （注意不能用 {@code Map.copyOf}——它不保证迭代顺序），同一状态序列化出来的字节稳定，
 * 可 diff、可哈希、可当崩溃重放的输入指纹。
 */
public sealed interface StateNode {

    record Obj(Map<String, StateNode> fields) implements StateNode {
        public Obj {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    record Arr(List<StateNode> items) implements StateNode {
        public Arr {
            items = List.copyOf(items);
        }
    }

    record Str(String value) implements StateNode {
        public Str {
            if (value == null) {
                throw new IllegalArgumentException("StateNode.Str 不能为 null");
            }
        }
    }

    record Int(long value) implements StateNode {}

    record Dec(double value) implements StateNode {
        public Dec {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("StateNode.Dec 必须是有限数：" + value);
            }
        }
    }

    record Bool(boolean value) implements StateNode {}

    // ---- 便捷构造 ----

    /** 保插入序的可变字段表；交给 {@link Obj} 时会被拷成不可变。 */
    static Map<String, StateNode> fields() {
        return new LinkedHashMap<>();
    }

    // ---- 取值（失败一律抛 IllegalStateException，并把字段路径带上）----

    default Obj asObj(String path) {
        if (this instanceof Obj o) {
            return o;
        }
        throw typeError(path, "对象", this);
    }

    default Arr asArr(String path) {
        if (this instanceof Arr a) {
            return a;
        }
        throw typeError(path, "数组", this);
    }

    default String asString(String path) {
        if (this instanceof Str s) {
            return s.value();
        }
        throw typeError(path, "字符串", this);
    }

    /** 整数（short/int/long 都读得出来）。 */
    default long asLong(String path) {
        if (this instanceof Int i) {
            return i.value();
        }
        throw typeError(path, "整数", this);
    }

    /** int 字段的取值，越界时报清楚。 */
    default int asInt(String path) {
        long v = asLong(path);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalStateException(path + " 超出 int 范围：" + v);
        }
        return (int) v;
    }

    /** 小数；整数也接受（迁移时旧版本常把 0 写成整数）。 */
    default double asDouble(String path) {
        if (this instanceof Dec d) {
            return d.value();
        }
        if (this instanceof Int i) {
            return i.value();
        }
        throw typeError(path, "小数", this);
    }

    default boolean asBool(String path) {
        if (this instanceof Bool b) {
            return b.value();
        }
        throw typeError(path, "布尔", this);
    }

    /** 取对象的字段；缺字段时报出**完整路径**（父路径 + 字段名）。 */
    default StateNode field(String path, String key) {
        StateNode v = asObj(path).fields().get(key);
        if (v == null) {
            throw new IllegalStateException(path + "." + key + " 缺失");
        }
        return v;
    }

    /** 取可缺省字段；缺失返回 null。 */
    default StateNode optionalField(String path, String key) {
        return asObj(path).fields().get(key);
    }

    /** 给数组元素用的路径，如 {@code nations[3]}。 */
    static String index(String path, int i) {
        return path + "[" + i + "]";
    }

    private static IllegalStateException typeError(String path, String expected, StateNode actual) {
        return new IllegalStateException(path + " 期望" + expected + "，实际是 " + describe(actual));
    }

    private static String describe(StateNode node) {
        return switch (node) {
            case Obj o -> "对象(字段 " + o.fields().keySet() + ")";
            case Arr a -> "数组(长度 " + a.items().size() + ")";
            case Str s -> "字符串 \"" + s.value() + "\"";
            case Int i -> "整数 " + i.value();
            case Dec d -> "小数 " + d.value();
            case Bool b -> "布尔 " + b.value();
        };
    }
}
