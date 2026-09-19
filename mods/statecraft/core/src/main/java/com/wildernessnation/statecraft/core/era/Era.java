package com.wildernessnation.statecraft.core.era;

import java.util.Set;

/**
 * 一条时代定义。**所有内容都来自数据（eras.json），代码不假设有多少个时代。**
 *
 * @param id            稳定标识（表意）。存档与事件记它，改时代表也不会失效
 * @param name          中文显示名
 * @param ordinal       序号（排序用，从 0 连续）
 * @param durationHours 该时代时长（累计在线小时）
 * @param coefficient   全局数值系数
 * @param unlocks       该时代解锁的能力键（如 intel_station / declaration）
 */
public record Era(
        String id,
        String name,
        int ordinal,
        int durationHours,
        double coefficient,
        Set<String> unlocks) {

    public Era {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Era.id 不能为空");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("Era.ordinal 不能为负：" + ordinal);
        }
        if (durationHours <= 0) {
            throw new IllegalArgumentException("Era.durationHours 必须 > 0：" + durationHours);
        }
        unlocks = unlocks == null ? Set.of() : Set.copyOf(unlocks);
    }
}
