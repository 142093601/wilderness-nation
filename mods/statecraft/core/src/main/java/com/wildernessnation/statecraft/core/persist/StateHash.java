package com.wildernessnation.statecraft.core.persist;

import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.Map;

/**
 * 世界状态的稳定哈希（64 位 FNV-1a）。
 *
 * <p>它的用途只有一个：**事务日志里的"输入哈希"**（§十三）。重放时逐步校验
 * "日志记的那一步，输入确实是我手上这个状态"，一旦对不上就说明日志或状态被人动过 / 版本漂移了，
 * 应该停下来而不是继续算出一段假历史。
 *
 * <h2>为什么直接用序列化后的树来算</h2>
 * 哈希的对象就是**会被写进存档的那份内容**（{@link StateCodec#write}）——
 * 这样"哈希一致"和"存档一致"是同一件事，不会出现"哈希说一样、存出来不一样"。
 *
 * <h2>为什么不用 {@code Objects.hash} / {@code String.hashCode}</h2>
 * 那两个不保证跨 JVM 稳定，而这个值要跟着存档一起长期存在。
 * 这里显式按字符、按 {@code doubleToLongBits} 混合，跨平台跨版本都得到同一个数。
 */
public final class StateHash {

    private static final long OFFSET = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private StateHash() {}

    public static long of(WorldState state) {
        return hash(StateCodec.write(state));
    }

    public static long of(StateNode node) {
        return hash(node);
    }

    private static long hash(StateNode node) {
        long h = OFFSET;
        return switch (node) {
            case StateNode.Obj o -> {
                h = mix(h, 1);
                for (Map.Entry<String, StateNode> e : o.fields().entrySet()) {
                    h = mixString(h, e.getKey());
                    h = mix(h, hash(e.getValue()));
                }
                yield h;
            }
            case StateNode.Arr a -> {
                h = mix(h, 2);
                h = mix(h, a.items().size());
                for (StateNode item : a.items()) {
                    h = mix(h, hash(item));
                }
                yield h;
            }
            case StateNode.Str s -> mixString(mix(h, 3), s.value());
            case StateNode.Int i -> mix(mix(h, 4), i.value());
            case StateNode.Dec d -> mix(mix(h, 5), Double.doubleToLongBits(d.value()));
            case StateNode.Bool b -> mix(mix(h, 6), b.value() ? 1 : 0);
        };
    }

    /** 按字符混合，不经过任何字符集编码 —— 编码一变结果就变，那就不叫稳定了。 */
    private static long mixString(long h, String s) {
        long out = mix(h, s.length());
        for (int i = 0; i < s.length(); i++) {
            out = mix(out, s.charAt(i));
        }
        return out;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * PRIME;
    }

    /** 给日志用的十六进制形式（调试时看这个比看十进制舒服）。 */
    public static String hex(long hash) {
        return String.format(java.util.Locale.ROOT, "%016x", hash);
    }
}
