package com.wildernessnation.statecraft.data;

import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.persist.LoadOutcome;
import com.wildernessnation.statecraft.core.persist.StateCodec;
import com.wildernessnation.statecraft.core.persist.StateNode;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 存档载体：把 {@link WorldState} 挂到主世界的 {@code DimensionDataStorage} 上。
 *
 * <p>这一层只做三件事：取/存 NBT、把读档交给 core 的迁移器、记住读档警告。
 * <strong>不判断数据对不对</strong>——那是 core 的事，这里判断就测不到了。
 */
public final class StatecraftSavedData extends SavedData {

    public static final String FILE_ID = "statecraft";

    private static final String KEY_STATE = "state";

    /**
     * 调度器攒到一半的"在线小时"（mod 层自己记的，**不进 core 的 schema**）。
     *
     * <p>为什么不放进 {@link WorldState}：它不是世界状态的一部分，而是"调度器上次推到哪儿了"。
     * 放进 core 会让每次调度器的实现变化都牵动存档格式与迁移链。
     */
    private static final String KEY_PENDING_HOURS = "pendingHours";

    private static final SavedData.Factory<StatecraftSavedData> FACTORY =
            new SavedData.Factory<>(StatecraftSavedData::new, StatecraftSavedData::load, null);

    private WorldState state;
    private List<String> loadWarnings = List.of();
    private double pendingHours;

    public static StatecraftSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** 还没生成过（新世界）或读档失败被重置时为 null。 */
    public WorldState state() {
        return state;
    }

    /** 读档时 core 留下的中文警告（版本太新 / 缺迁移 / 内容损坏 / eraId 归位）。 */
    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public void set(WorldState newState) {
        this.state = newState;
        this.loadWarnings = List.of();
        setDirty();
    }

    /** 调度器攒到一半的在线小时（默认 0）。 */
    public double pendingHours() {
        return pendingHours;
    }

    public void setPendingHours(double v) {
        this.pendingHours = Math.max(0.0, v);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (state != null) {
            tag.put(KEY_STATE, NodeNbtCodec.toNbt(StateCodec.write(state)));
        }
        tag.putDouble(KEY_PENDING_HOURS, pendingHours);
        return tag;
    }

    private static StatecraftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StatecraftSavedData data = new StatecraftSavedData();
        data.pendingHours = Math.max(0.0, tag.getDouble(KEY_PENDING_HOURS));
        Tag raw = tag.get(KEY_STATE);
        if (!(raw instanceof CompoundTag stateTag)) {
            return data;
        }
        LoadOutcome outcome =
                StatecraftData.migrator().load(NodeNbtCodec.fromNbt(stateTag), StatecraftData.eras());
        data.loadWarnings = outcome.warnings();
        data.state = outcome.state().orElse(null);
        return data;
    }

    /** 把当前状态原样走一遍 NBT 往返（用于游戏内自检，见 {@code /statecraft roundtrip}）。 */
    public RoundTrip roundTrip() {
        StateNode written = StateCodec.write(state);
        StateNode back = NodeNbtCodec.fromNbt(NodeNbtCodec.toNbt(written));
        LoadOutcome outcome = StatecraftData.migrator().load(back, StatecraftData.eras());
        boolean identical = outcome.state().isPresent() && outcome.state().get().equals(state);
        return new RoundTrip(identical, outcome.warnings());
    }

    public record RoundTrip(boolean identical, List<String> warnings) {}
}
