package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 世界状态（本计划只含生成所需的字段）。
 *
 * <p>后续计划会往里加：relations / events / letters / buildings / domains。
 * 现在刻意不加，避免写出没人用的字段。
 */
public record WorldState(
        long seed,
        List<Nation> nations,
        String eraId,
        int eraOrdinal,
        long seq) {

    public WorldState {
        nations = List.copyOf(nations);
    }

    public WorldState withNations(List<Nation> replaced) {
        return new WorldState(seed, replaced, eraId, eraOrdinal, seq);
    }
}
