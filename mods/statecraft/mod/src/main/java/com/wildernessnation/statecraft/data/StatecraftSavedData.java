package com.wildernessnation.statecraft.data;

import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.persist.LoadOutcome;
import com.wildernessnation.statecraft.core.persist.StateCodec;
import com.wildernessnation.statecraft.core.persist.StateNode;
import com.wildernessnation.statecraft.core.settle.SettlementLog;
import com.wildernessnation.statecraft.core.settle.SettlementLogEntry;
import com.wildernessnation.statecraft.world.PlayerLedger;
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

    /**
     * 玩家侧账本（已购图纸授权 / 追踪清单 / 国库）。
     *
     * <p><b>为什么不进 core 的 schema</b>：core 只做"收事实 → 给判定"，
     * 而"你买过什么、追着谁"是玩家侧状态。放进去会让"同 seed 同输入必得同结果"
     * 变成"还得看它内部数到几"。见 {@link PlayerLedger} 的注释。
     */
    private static final String KEY_LEDGER = "ledger";

    /** 账本里的三个字段（都是 mod 层自己的键）。 */
    private static final String KEY_LEDGER_BLUEPRINTS = "blueprints";
    private static final String KEY_LEDGER_TRACKED = "tracked";
    private static final String KEY_LEDGER_TREASURY = "treasury";
    private static final String KEY_LEDGER_DEEP_SEQ = "lastDeepDiveSeq";

    /**
     * 结算事务日志（`NATIONS.md` §十三：结算序号 + 输入哈希 → 崩溃后能重放）。
     *
     * <p>为什么它**不在** {@link WorldState} 里（core 那边也是这么写的）：它记的是
     * "操作历史"而不是"当前状态"，混进状态会让每次存档都跟着变长。所以它是同一个 `.dat`
     * 里的另一个字段，core 只管它的内容与重放。
     */
    private static final String KEY_LOG = "log";

    private static final SavedData.Factory<StatecraftSavedData> FACTORY =
            new SavedData.Factory<>(StatecraftSavedData::new, StatecraftSavedData::load, null);

    private WorldState state;
    private List<String> loadWarnings = List.of();
    private double pendingHours;
    private PlayerLedger ledger = PlayerLedger.empty();
    private SettlementLog log = SettlementLog.empty();

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

    /** 玩家侧账本（永不返回 null；没买过东西时是空账本）。 */
    public PlayerLedger ledger() {
        return ledger;
    }

    public void setLedger(PlayerLedger v) {
        this.ledger = v == null ? PlayerLedger.empty() : v;
        setDirty();
    }

    /** 结算事务日志（永不返回 null）。 */
    public SettlementLog log() {
        return log;
    }

    public void setLog(SettlementLog v) {
        this.log = v == null ? SettlementLog.empty() : v;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (state != null) {
            tag.put(KEY_STATE, NodeNbtCodec.toNbt(StateCodec.write(state)));
        }
        tag.putDouble(KEY_PENDING_HOURS, pendingHours);
        CompoundTag ledgerTag = new CompoundTag();
        ledgerTag.put(KEY_LEDGER_BLUEPRINTS,
                stringList(PlayerLedger.toList(ledger.grantedBlueprints())));
        ledgerTag.put(KEY_LEDGER_TRACKED, stringList(PlayerLedger.toList(ledger.trackedNations())));
        ledgerTag.putDouble(KEY_LEDGER_TREASURY, ledger.treasury());
        ledgerTag.putLong(KEY_LEDGER_DEEP_SEQ, ledger.lastDeepDiveSeq());
        tag.put(KEY_LEDGER, ledgerTag);
        tag.put(KEY_LOG, NodeNbtCodec.toNbt(log.toNode()));
        return tag;
    }

    /** 字符串集合 → NBT 列表（NBT 的 ListTag 只能放同类型，所以统一用 StringTag）。 */
    private static net.minecraft.nbt.ListTag stringList(List<String> values) {
        net.minecraft.nbt.ListTag out = new net.minecraft.nbt.ListTag();
        for (String v : values) {
            out.add(net.minecraft.nbt.StringTag.valueOf(v));
        }
        return out;
    }

    private static List<String> readStringList(CompoundTag tag, String key) {
        List<String> out = new java.util.ArrayList<>();
        for (Tag t : tag.getList(key, net.minecraft.nbt.Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    private static StatecraftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StatecraftSavedData data = new StatecraftSavedData();
        data.pendingHours = Math.max(0.0, tag.getDouble(KEY_PENDING_HOURS));
        CompoundTag ledgerTag = tag.getCompound(KEY_LEDGER);
        data.ledger = new PlayerLedger(
                PlayerLedger.toSet(readStringList(ledgerTag, KEY_LEDGER_BLUEPRINTS)),
                PlayerLedger.toSet(readStringList(ledgerTag, KEY_LEDGER_TRACKED)),
                Math.max(0.0, ledgerTag.getDouble(KEY_LEDGER_TREASURY)),
                // 老档没有这个键 → getLong 给 0，而 0 是**合法序号**（不是"从未"）
                ledgerTag.contains(KEY_LEDGER_DEEP_SEQ)
                        ? ledgerTag.getLong(KEY_LEDGER_DEEP_SEQ) : -1L);
        Tag raw = tag.get(KEY_STATE);
        if (!(raw instanceof CompoundTag stateTag)) {
            return data;
        }
        LoadOutcome outcome =
                StatecraftData.migrator().load(NodeNbtCodec.fromNbt(stateTag), StatecraftData.eras());
        data.loadWarnings = outcome.warnings();
        data.state = outcome.state().orElse(null);

        // 事务日志：读回来之后**必须和状态对齐**。
        // 崩溃可能发生在"日志已写、状态还没写"之间，于是日志会比状态快一步；
        // 那种情况下的正确做法不是重放（没有可信的基线），而是**把超前的那几步剪掉**
        // —— 它记的本来就是"即将做的事"，没做成就不该留在日志里。
        Tag logTag = tag.get(KEY_LOG);
        if (logTag instanceof CompoundTag logCompound && data.state != null) {
            data.log = SettlementLog.fromNode(NodeNbtCodec.fromNbt(logCompound));
            long stateSeq = data.state.seq();
            SettlementLog trimmed = trimTo(data.log, stateSeq);
            if (trimmed.size() != data.log.size()) {
                data.loadWarnings = new java.util.ArrayList<>(data.loadWarnings);
                data.loadWarnings.add("事务日志比状态超前（状态在第 " + stateSeq + " 次结算，"
                        + "日志记到第 " + data.log.lastEntry().seq() + " 步）→ 已剪掉超前的 "
                        + (data.log.size() - trimmed.size()) + " 步");
                data.log = trimmed;
            }
        }
        return data;
    }

    /** 只留结算序号不超过 {@code seq} 的日志步（日志是按序号严格递增的，所以可以顺序筛）。 */
    private static SettlementLog trimTo(SettlementLog log, long seq) {
        List<SettlementLogEntry> kept = new java.util.ArrayList<>();
        for (SettlementLogEntry e : log.entries()) {
            if (e.seq() <= seq) {
                kept.add(e);
            }
        }
        return new SettlementLog(kept);
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
