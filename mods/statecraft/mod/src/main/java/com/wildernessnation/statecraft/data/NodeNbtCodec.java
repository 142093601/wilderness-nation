package com.wildernessnation.statecraft.data;

import com.wildernessnation.statecraft.core.persist.StateNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * {@link StateNode} ↔ NBT：core 与 Minecraft 之间<strong>唯一</strong>的翻译层。
 *
 * <p>刻意做成纯粹的双向映射，<strong>不带任何业务判断</strong>——一旦这里开始"顺手修数据"，
 * 那些规则就跑到 Minecraft 类路径上、再也测不了了。校验与迁移全在 core。
 *
 * <p>{@code LongTag} 承载 core 的 {@code Int}（long）：世界种子超过 2^53 时必须原样过去。
 * {@code ByteTag} 只在 0/1 时当布尔，别的值仍然当整数——不猜。
 */
public final class NodeNbtCodec {

    private NodeNbtCodec() {}

    /** 根必须是对象（存档的根就是 {@code world} 那个 Obj）。 */
    public static CompoundTag toNbt(StateNode root) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, StateNode> e : root.asObj("state").fields().entrySet()) {
            tag.put(e.getKey(), toTag(e.getValue(), "state." + e.getKey()));
        }
        return tag;
    }

    private static Tag toTag(StateNode node, String path) {
        return switch (node) {
            case StateNode.Obj o -> {
                CompoundTag t = new CompoundTag();
                for (Map.Entry<String, StateNode> e : o.fields().entrySet()) {
                    t.put(e.getKey(), toTag(e.getValue(), path + "." + e.getKey()));
                }
                yield t;
            }
            case StateNode.Arr a -> {
                ListTag t = new ListTag();
                for (int i = 0; i < a.items().size(); i++) {
                    t.add(toTag(a.items().get(i), path + "[" + i + "]"));
                }
                yield t;
            }
            case StateNode.Str s -> StringTag.valueOf(s.value());
            case StateNode.Int i -> LongTag.valueOf(i.value());
            case StateNode.Dec d -> DoubleTag.valueOf(d.value());
            case StateNode.Bool b -> ByteTag.valueOf(b.value());
        };
    }

    public static StateNode fromNbt(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            Map<String, StateNode> fields = StateNode.fields();
            for (String key : compound.getAllKeys()) {
                fields.put(key, fromNbt(compound.get(key)));
            }
            return new StateNode.Obj(fields);
        }
        if (tag instanceof ListTag list) {
            List<StateNode> items = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                items.add(fromNbt(list.get(i)));
            }
            return new StateNode.Arr(items);
        }
        if (tag instanceof StringTag s) {
            return new StateNode.Str(s.getAsString());
        }
        if (tag instanceof DoubleTag d) {
            return new StateNode.Dec(d.getAsDouble());
        }
        if (tag instanceof ByteTag b && (b.getAsByte() == 0 || b.getAsByte() == 1)) {
            return new StateNode.Bool(b.getAsByte() != 0);
        }
        if (tag instanceof LongTag l) {
            return new StateNode.Int(l.getAsLong());
        }
        if (tag instanceof IntTag i) {
            return new StateNode.Int(i.getAsInt());
        }
        if (tag instanceof ShortTag s) {
            return new StateNode.Int(s.getAsShort());
        }
        // 到这里说明遇到了我们没写过的类型——宁可响亮地失败，也不要猜一个数字出来
        throw new IllegalArgumentException("不认识的 NBT 类型：" + tag.getClass().getName());
    }
}
