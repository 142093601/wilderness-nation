package com.wildernessnation.statecraft.core.era;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            Era e = sorted.get(i);
            if (e.ordinal() != i) {
                throw new IllegalArgumentException(
                        "时代序号必须从 0 连续：期望 " + i + "，实际 " + e.ordinal());
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

    /** 按累计在线小时取当前时代；超出末尾停在最后一个，负数按 0 处理。 */
    public Era currentFor(double elapsedOnlineHours) {
        double t = Math.max(0.0, elapsedOnlineHours);
        double acc = 0.0;
        for (Era e : eras) {
            acc += e.durationHours();
            if (t < acc) {
                return e;
            }
        }
        return eras.get(eras.size() - 1);
    }

    /** 该能力是否已在 current（含）之前的任何时代被声明解锁。 */
    public boolean isUnlocked(String unlockKey, Era current) {
        for (Era e : eras) {
            if (e.ordinal() > current.ordinal()) {
                break;
            }
            if (e.unlocks().contains(unlockKey)) {
                return true;
            }
        }
        return false;
    }

    /** 存档里出现未知 eraId 时的归位：钳到 [0, size-1]。 */
    public Era fallbackForUnknownId(String unknownId, int hintedOrdinal) {
        return byOrdinal(hintedOrdinal);
    }
}
