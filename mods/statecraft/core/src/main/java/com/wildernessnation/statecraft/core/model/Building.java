package com.wildernessnation.statecraft.core.model;

/**
 * 一座已登记的建筑（`NATIONS.md` §五 的 Building 字段表）。
 *
 * <p><strong>登记 ≠ 世界里存在</strong>：这里的 {@code materializedAtSeq} 是"上次实体化是哪一次结算"，
 * 用来满足 §10.3 那句"**同一 `seq` 不重复生成**"。世界里到底有没有那个方块、那个村民，
 * 由 mc 层观察后通过 {@link StaffObservation} 告诉规则层。
 *
 * @param id                  建筑标识
 * @param type                建筑类型（{@code intel_station} / {@code warehouse} / …）
 * @param anchor              锚点（维度 + 坐标）
 * @param level               等级（升级件提升）
 * @param staffUUID           绑定的村民 UUID；<strong>null = 还没绑定</strong>（也就是 §10.4 的"建筑停摆"）
 * @param materializedAtSeq   §五：上次实体化序号（**{@link #NEVER} = 从未**）
 * @param materializedAtHours **追加字段**：上次实体化时的累计在线小时。
 *                            为什么 §五 的序号不够用：§十二 的节流是"两次间隔 **≥5 分钟**"，
 *                            而结算序号只在结算时 +1（间隔 2 小时）—— 用序号表达 5 分钟会差 24 倍。
 * @param staffDeadAtSeq      §五：村民死亡/被感染的时间戳（0 = 从未）
 */
public record Building(
        String id,
        String type,
        Anchor anchor,
        int level,
        String staffUUID,
        long materializedAtSeq,
        double materializedAtHours,
        long staffDeadAtSeq) {

    /**
     * "从未实体化"的哨兵值。
     *
     * <p><strong>为什么不是 0</strong>：新世界的当前 seq 就是 0，而建筑是在两次结算**之间**
     * 实体化的 —— 于是"从未实体化"和"在 seq 0 实体化过"会撞成同一个数。
     * 2026-09-20 在真服务端上撞了两处：
     * <ul>
     *   <li>§10.3 的"同一 seq 不重复生成"把**刚登记的建筑**判成"这次已经生成过了"→ 永远不刷村民；</li>
     *   <li>冷却判断里的 {@code everMaterialized()} 为假 → 村民一死就能立刻重雇（绕过 §10.4 的冷却）。</li>
     * </ul>
     * 哨兵取 -1 之后，"合法序号"(>=0) 与"从未"(-1) 在类型上就分得开了。
     */
    public static final long NEVER = -1L;

    /** 锚点：维度 + 坐标。独立成一个小 record，免得建筑上散着 4 个坐标字段。 */
    public record Anchor(String dimension, int x, int y, int z) {
        public Anchor {
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("Anchor.dimension 不能为空");
            }
        }

        public String describe() {
            return dimension + " " + x + "," + y + "," + z;
        }
    }

    public Building {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Building.id 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Building.type 不能为空");
        }
        if (anchor == null) {
            throw new IllegalArgumentException("Building.anchor 不能为 null");
        }
        if (level < 1) {
            throw new IllegalArgumentException("Building.level 必须 >= 1：" + level);
        }
        if (materializedAtSeq < NEVER) {
            throw new IllegalArgumentException(
                    "Building.materializedAtSeq 只能是 " + NEVER + "（从未）或 >= 0 的结算序号，实际 "
                            + materializedAtSeq);
        }
        if (!(materializedAtHours >= 0.0) || !Double.isFinite(materializedAtHours)) {
            throw new IllegalArgumentException(
                    "Building.materializedAtHours 必须 >= 0 且有限：" + materializedAtHours);
        }
        if (staffDeadAtSeq < 0) {
            throw new IllegalArgumentException(
                    "Building.staffDeadAtSeq 不能为负：" + staffDeadAtSeq);
        }
        if ((staffUUID == null || staffUUID.isBlank())
                && staffDeadAtSeq > 0
                && staffDeadAtSeq < materializedAtSeq) {
            // 村民死在"上次实体化"**之前**，却又是没绑定的状态 ——
            // 那次实体化为什么没把村民绑上？时间线说不通。
            // （反过来"死在实体化之后"完全正常：先建好、后来村民死了。）
            throw new IllegalArgumentException(
                    "Building 时间线矛盾：村民死于 " + staffDeadAtSeq
                            + "，却在 " + materializedAtSeq + " 实体化过且至今未重新绑定");
        }
    }

    /** 刚登记、还没实体化过的建筑。 */
    public static Building register(String id, String type, Anchor anchor) {
        return new Building(id, type, anchor, 1, null, NEVER, 0.0, 0L);
    }

    /** 实体化过没有（哨兵见 {@link #NEVER}；**不要**写成 {@code > 0}）。 */
    public boolean everMaterialized() {
        return materializedAtSeq >= 0;
    }

    /** §10.4：村民不在（死了/被感染/从没绑上）→ 建筑停摆。 */
    public boolean stalled() {
        return staffUUID == null || staffUUID.isBlank();
    }

    /** 绑定村民（实体化成功）。 */
    public Building withStaff(String uuid, long seq, double hours) {
        return new Building(id, type, anchor, level, uuid, seq, hours, staffDeadAtSeq);
    }

    /** 村民死亡/被感染（§10.4）：解绑并记时间戳，建筑随之停摆。 */
    public Building withStaffLost(long seq) {
        return new Building(id, type, anchor, level, null, materializedAtSeq, materializedAtHours,
                seq);
    }

    public Building withLevel(int v) {
        return new Building(id, type, anchor, v, staffUUID, materializedAtSeq, materializedAtHours,
                staffDeadAtSeq);
    }

    public Building withAnchor(Anchor v) {
        return new Building(id, type, v, level, staffUUID, materializedAtSeq, materializedAtHours,
                staffDeadAtSeq);
    }

    /** 兼容 §五 的名字（`materializedAt` = 序号）；没实体化过时给 {@link #NEVER}。 */
    public long materializedAt() {
        return materializedAtSeq;
    }
}
