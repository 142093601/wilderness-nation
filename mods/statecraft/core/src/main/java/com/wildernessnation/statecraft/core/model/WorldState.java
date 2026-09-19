package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 世界状态（本计划只含生成所需的字段）。
 *
 * <p>后续计划会往里加 relations / events / letters / buildings / domains。
 * 现在刻意不加，没人用的字段不写。
 */
public record WorldState(
        long seed,
        List<Nation> nations,
        String eraId,
        int eraOrdinal,
        long seq) {

    public WorldState {
        if (nations == null) {
            throw new IllegalArgumentException("WorldState.nations 不能为 null");
        }
        if (eraId == null || eraId.isBlank()) {
            throw new IllegalArgumentException("WorldState.eraId 不能为空");
        }
        if (eraOrdinal < 0) {
            throw new IllegalArgumentException("WorldState.eraOrdinal 不能为负：" + eraOrdinal);
        }
        if (seq < 0) {
            throw new IllegalArgumentException("WorldState.seq 不能为负：" + seq);
        }
        nations = List.copyOf(nations);
    }
}
