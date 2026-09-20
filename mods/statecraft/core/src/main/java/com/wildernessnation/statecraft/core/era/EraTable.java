package com.wildernessnation.statecraft.core.era;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 时代表：按"累计在线小时"取当前时代、判定解锁、把未知 eraId 归位。
 *
 * <p>刻意的设计：<strong>解锁用 era id 判定，不用序号</strong>。
 * 如果写成"第 3 个时代解锁情报站"，那用户把六时代改成九时代时，解锁点就会挪位置。
 */
public final class EraTable {

    private final List<Era> eras;

    public EraTable(List<Era> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("时代表不能为空");
        }
        List<Era> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingInt(Era::ordinal));

        Set<Integer> ordinals = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            Era e = sorted.get(i);
            if (!ordinals.add(e.ordinal())) {
                throw new IllegalArgumentException("重复的时代序号：" + e.ordinal());
            }
            if (e.ordinal() != i) {
                throw new IllegalArgumentException(
                        "时代序号必须从 0 连续：第 " + i + " 个的 ordinal 是 " + e.ordinal());
            }
            if (!ids.add(e.id())) {
                throw new IllegalArgumentException("重复的时代 id：" + e.id());
            }
        }
        this.eras = List.copyOf(sorted);
    }

    public int size() {
        return eras.size();
    }

    public List<Era> eras() {
        return eras;
    }

    /** 越界（含负数）一律钳到有效范围，不抛异常——存档里的脏序号不该炸掉游戏。 */
    public Era byOrdinal(int ordinal) {
        int clamped = Math.max(0, Math.min(eras.size() - 1, ordinal));
        return eras.get(clamped);
    }

    public Optional<Era> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return eras.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** 按累计在线小时取当前时代；超出末尾停在最后一个，负数与 NaN 按 0 处理。 */
    public Era currentFor(double elapsedOnlineHours) {
        double t = elapsedOnlineHours > 0.0 ? elapsedOnlineHours : 0.0;   // NaN 也会落到 0
        double acc = 0.0;
        for (Era e : eras) {
            acc += e.durationHours();
            if (t < acc) {
                return e;
            }
        }
        return eras.get(eras.size() - 1);
    }

    /**
     * 该能力是否已在 current（含）之前被任何时代声明解锁。
     *
     * <p>null 一律返回 false（不抛）——调用方多半来自存档/命令，不该因为空值崩。
     *
     * <p>不属于本表的 Era（存档里换了时代表就会这样）按 {@link #byOrdinal} 的钳位语义处理：
     * 序号 99 当作"最后一个时代"，而不是拿一个表外序号去和表内比较。
     */
    public boolean isUnlocked(String unlockKey, Era current) {
        if (unlockKey == null || current == null) {
            return false;
        }
        int limit = byOrdinal(current.ordinal()).ordinal();
        for (Era e : eras) {
            if (e.ordinal() > limit) {
                break;
            }
            if (e.unlocks().contains(unlockKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 某个时代**结束时**的累计小时数（§七 的时钟口径）。
     *
     * <p>已经到最后一个时代了就返回空 —— "没有下一个时代"和"下一个时代在 0 小时处"是两件事，
     * 混成 0 会让调用方以为马上要跨时代（{@link SettlementClock} 就是靠它区分这两者的）。
     */
    public OptionalDouble boundaryAfter(Era current) {
        if (current == null) {
            return OptionalDouble.empty();
        }
        Era normalized = byOrdinal(current.ordinal());
        double acc = 0.0;
        for (Era e : eras) {
            acc += e.durationHours();
            if (e.ordinal() == normalized.ordinal()) {
                return e.ordinal() == eras.get(eras.size() - 1).ordinal()
                        ? OptionalDouble.empty()          // 最后一个时代：没有下一个边界
                        : OptionalDouble.of(acc);
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * 存档里出现未知 eraId 时的归位。
     * 已知 id 直接返回对应时代；未知 id 才用提示序号钳位（并在返回值上体现"是猜的"由调用方决定是否记日志）。
     */
    public Era fallbackForUnknownId(String unknownId, int hintedOrdinal) {
        return byId(unknownId).orElseGet(() -> byOrdinal(hintedOrdinal));
    }
}
