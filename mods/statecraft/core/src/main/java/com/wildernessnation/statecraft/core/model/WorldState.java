package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 世界状态（本计划只含生成与持久化所需的字段）。
 *
 * <p>后续计划会往里加 relations / events / letters / buildings / domains / playerLedger。
 * 现在刻意不加，没人用的字段不写；它们的加入方式就是 {@code schemaVersion} 的下一级迁移。
 *
 * <p>{@code schemaVersion} 是**存档格式**的版本，不是内容版本：读档时先按它选迁移链，
 * 迁到 {@link #CURRENT_SCHEMA_VERSION} 之后才会构造出这个对象。
 */
public record WorldState(
        int schemaVersion,
        long seed,
        List<Nation> nations,
        String eraId,
        int eraOrdinal,
        long seq) {

    /** 当前代码写出的存档格式版本。加字段就 +1，并补一级 {@code StateMigration}。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public WorldState {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("WorldState.schemaVersion 必须 >= 1：" + schemaVersion);
        }
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
