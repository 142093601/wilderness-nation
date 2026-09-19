package com.wildernessnation.statecraft.core.settle;

import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.persist.StateHash;
import com.wildernessnation.statecraft.core.persist.StateNode;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * 结算事务日志（`NATIONS.md` §十三："事务日志（结算序号 + 输入哈希）；确定性 + seed →
 * 从上一个完整结算重放"）。
 *
 * <h2>它解决的是什么</h2>
 * 结算跑到一半崩溃时，存档里的状态可能是**半步**的。有了日志就可以：
 * 拿上一个完整结算的状态 + 日志里之后那几步的输入，用同一个 seed 重算出来。
 * 这能成立的前提是引擎的确定性（已由 40 个 seed 的重放用例钉住）。
 *
 * <h2>为什么日志**不放进** WorldState</h2>
 * §五 的字段表里没有它，而且它记的是"操作历史"而不是"当前状态"——
 * 混进状态会让每一次存档都跟着变长。所以它是一个独立对象，由 mod 层和状态存在一起
 * （同一个 `.dat` 里两个字段即可），core 只管它的内容与重放。
 *
 * <p>本类是不可变的：{@link #append} 返回新日志。这样"日志"和"状态"在代码里一样是快照。
 */
public final class SettlementLog {

    public static final String KEY_ENTRIES = "entries";

    /** 事件保留上限之外，日志也该有个上限，否则长命存档会一直长 —— 只留最近这些步。 */
    public static final int MAX_ENTRIES = 512;

    private final List<SettlementLogEntry> entries;

    public SettlementLog(List<SettlementLogEntry> entries) {
        List<SettlementLogEntry> copy = List.copyOf(entries == null ? List.of() : entries);
        long previous = 0;
        for (SettlementLogEntry e : copy) {
            if (e.seq() <= previous) {
                throw new IllegalArgumentException(
                        "日志必须按结算序号严格递增：第 " + e.seq() + " 条接在 " + previous + " 之后");
            }
            previous = e.seq();
        }
        this.entries = copy.size() > MAX_ENTRIES
                ? List.copyOf(copy.subList(copy.size() - MAX_ENTRIES, copy.size()))
                : copy;
    }

    public static SettlementLog empty() {
        return new SettlementLog(List.of());
    }

    /**
     * 记下"即将用 {@code input} 结算 {@code before} 这个状态"这一步。
     *
     * <p>哈希算的是**动手之前**的状态，所以调用顺序必须是"先 append 再 settle"——
     * 写反过来就会记住一个已经算过的状态，日志立刻报废。
     */
    public SettlementLog append(WorldState before, SettlementInput input) {
        SettlementLogEntry entry = new SettlementLogEntry(
                before.seq() + 1, input.trigger(), input.elapsedHoursDelta(),
                StateHash.of(before));
        List<SettlementLogEntry> next = new ArrayList<>(entries);
        next.add(entry);
        return new SettlementLog(next);
    }

    public List<SettlementLogEntry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 最后一步（崩溃恢复时从它之后接着跑）。 */
    public SettlementLogEntry lastEntry() {
        if (entries.isEmpty()) {
            throw new IllegalStateException("日志是空的，没有「最后一步」");
        }
        return entries.get(entries.size() - 1);
    }

    /** 只保留结算序号大于 {@code seq} 的那些步（从一个快照接着重放时用）。 */
    public SettlementLog since(long seq) {
        List<SettlementLogEntry> out = new ArrayList<>();
        for (SettlementLogEntry e : entries) {
            if (e.seq() > seq) {
                out.add(e);
            }
        }
        return new SettlementLog(out);
    }

    /**
     * 从 {@code start} 开始照着日志重放。
     *
     * <p>每一步都先校验"日志记的输入哈希 == 我手上这个状态的哈希"：
     * 对不上就**立刻停下**并报出第几步分歧，而不是继续算出一段看起来合理、实际是假的历史。
     */
    public ReplayResult replay(WorldState start, SettlementEngine engine) {
        WorldState current = start;
        int steps = 0;
        for (SettlementLogEntry entry : entries) {
            if (entry.seq() != current.seq() + 1) {
                return new ReplayResult(current, steps, OptionalLong.of(entry.seq()));
            }
            if (StateHash.of(current) != entry.inputHash()) {
                return new ReplayResult(current, steps, OptionalLong.of(entry.seq()));
            }
            current = engine.settle(current, entry.input());
            steps++;
        }
        return new ReplayResult(current, steps, OptionalLong.empty());
    }

    /**
     * 重放并**核对最终结果**（{@link #replay} 只能发现"中间对不上"；
     * 如果被篡改的正好是最后一条，中间全是好的）。
     */
    public ReplayResult verify(WorldState start, SettlementEngine engine, WorldState expected) {
        ReplayResult replayed = replay(start, engine);
        if (!replayed.ok()) {
            return replayed;
        }
        if (!replayed.state().equals(expected)) {
            long lastSeq = entries.isEmpty() ? 0 : lastEntry().seq();
            return new ReplayResult(replayed.state(), replayed.stepsApplied(),
                    OptionalLong.of(lastSeq));
        }
        return replayed;
    }

    public StateNode toNode() {
        List<StateNode> items = new ArrayList<>(entries.size());
        for (SettlementLogEntry e : entries) {
            items.add(e.toNode());
        }
        java.util.Map<String, StateNode> root = StateNode.fields();
        root.put(KEY_ENTRIES, new StateNode.Arr(items));
        return new StateNode.Obj(root);
    }

    public static SettlementLog fromNode(StateNode root) {
        List<StateNode> items = root.field("log", KEY_ENTRIES).asArr("log." + KEY_ENTRIES).items();
        List<SettlementLogEntry> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            out.add(SettlementLogEntry.fromNode(items.get(i)));
        }
        return new SettlementLog(out);
    }
}
