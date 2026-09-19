package com.wildernessnation.statecraft.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.building.Building.Anchor;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdempotencyTest {

    private static final Anchor ANCHOR = new Anchor("minecraft:overworld", 100, 64, 200);
    private static final IdempotencyConfig CFG = IdempotencyConfig.defaults();
    private static final IdempotencyRules RULES = new IdempotencyRules(CFG);

    private static Building bound(String id, long seq, double hours) {
        return Building.register(id, "intel_station", ANCHOR).withStaff("uuid-" + id, seq, hours);
    }

    // ---- §10.3 幂等：匹配就不生成 ----

    @Test
    void healthyBindingIsSkippedWhichIsTheWholePoint() {
        // 这条就是"不做对会生出三个文书"那个坑的护栏：区块重载 / 服务器重启后反复调用，
        // 只要三件校验都过，永远只会得到 SKIP。
        Building b = bound("b1", 5L, 10.0);
        for (long seq = 5; seq < 12; seq++) {
            assertEquals(MaterializationDecision.SKIP,
                    RULES.decide(b, StaffObservation.healthy(), seq, 10.0 + seq),
                    "seq " + seq + " 时不该重新生成");
        }
    }

    @Test
    void neverMaterializedBuildingRespawnsImmediately() {
        Building fresh = Building.register("b1", "intel_station", ANCHOR);
        assertFalse(fresh.everMaterialized());
        assertTrue(fresh.stalled(), "还没绑定村民 = 停摆");
        assertEquals(MaterializationDecision.RESPAWN,
                RULES.decide(fresh, StaffObservation.staffGone(), 1L, 0.0),
                "第一次实体化不该被冷却挡住");
    }

    // ---- §10.4 锚点：房子没了就是"失效"，不是"重生" ----

    @Test
    void missingAnchorMarksInvalidEvenWhenTheStaffIsHealthy() {
        Building b = bound("b1", 5L, 10.0);
        assertEquals(MaterializationDecision.MARK_INVALID,
                RULES.decide(b, new StaffObservation(true, true, true, false), 9L, 20.0),
                "锚点先判：房子没了，村民的事就不重要了（村民要保留，别重生）");
    }

    // ---- §10.4 村民损失 + 冷却 ----

    @Test
    void lostStaffRespawnsOnlyAfterTheRehireCooldown() {
        Building b = bound("b1", 5L, 10.0);
        double rehire = CFG.rehireCooldownHours();
        assertEquals(MaterializationDecision.WAIT_COOLDOWN,
                RULES.decide(b, StaffObservation.staffGone(), 6L, 10.0 + rehire / 2),
                "冷却没过 → 排队");
        assertEquals(MaterializationDecision.RESPAWN,
                RULES.decide(b, StaffObservation.staffGone(), 7L, 10.0 + rehire + 1e-9),
                "冷却一过就能重雇");
    }

    @Test
    void wrongProfessionUsesTheMaterializationCooldownNotTheRehireOne() {
        Building b = bound("b1", 5L, 10.0);
        // 村民在、但职业不对（比如冒牌货）：既不是"损失"，也不该用重雇冷却
        StaffObservation imposter = new StaffObservation(true, false, true, true);
        double interval = CFG.materializationCooldownHours();
        assertEquals(MaterializationDecision.WAIT_COOLDOWN,
                RULES.decide(b, imposter, 6L, 10.0 + interval / 2));
        assertEquals(MaterializationDecision.RESPAWN,
                RULES.decide(b, imposter, 7L, 10.0 + interval + 1e-9));

        // 反过来说：这个间隔远小于"重雇冷却"，所以此刻若按重雇冷却算，本该还是 WAIT
        assertTrue(interval < CFG.rehireCooldownHours(),
                "前提：§十二 的实体化间隔（5 分钟）确实短于 §10.4 的重雇冷却（占位 1 小时）");
    }

    @Test
    void wrongBindingUsesTheMaterializationCooldown() {
        Building b = bound("b1", 5L, 10.0);
        StaffObservation wrongBuilding = new StaffObservation(true, true, false, true);
        assertEquals(MaterializationDecision.RESPAWN,
                RULES.decide(b, wrongBuilding, 7L, 10.0 + CFG.materializationCooldownHours() + 1e-9));
    }

    /** §10.3 的"同一 `seq` 不重复生成"：同一次结算里再问一遍，必须还是 SKIP。 */
    @Test
    void sameSeqNeverMaterializesTwice() {
        Building b = Building.register("b1", "intel_station", ANCHOR);
        assertEquals(MaterializationDecision.RESPAWN,
                RULES.decide(b, StaffObservation.staffGone(), 7L, 100.0));
        Building justDone = b.withStaff("uuid-b1", 7L, 100.0);
        assertEquals(MaterializationDecision.SKIP,
                RULES.decide(justDone, StaffObservation.staffGone(), 7L, 100.0),
                "同一次结算里刚做过，就不能再做一遍");
    }

    // ---- §十二 节流 ----

    @Test
    void throttleAllowsOnlyOnePerSettlement() {
        MaterializationThrottle throttle = new MaterializationThrottle(CFG);
        List<Building> eligible = List.of(
                Building.register("b2", "intel_station", ANCHOR),
                Building.register("b1", "intel_station", ANCHOR),
                Building.register("b3", "intel_station", ANCHOR));
        List<Building> picked = throttle.select(List.of(), eligible, 0.0, 0);
        assertEquals(1, picked.size(), "§十二：同一次结算最多 1 处");
        assertEquals("b1", picked.get(0).id(), "按 id 升序（确定性），不是哈希顺序");

        assertEquals(List.of(), throttle.select(List.of(), eligible, 0.0, 1),
                "本次结算已经做过 1 处 → 其余排队");
    }

    @Test
    void throttleRespectsTheGlobalInterval() {
        MaterializationThrottle throttle = new MaterializationThrottle(CFG);
        Building done = bound("b0", 4L, 10.0);                 // 10.0 小时时刚做过一处
        List<Building> all = List.of(done);
        List<Building> eligible = List.of(Building.register("b1", "intel_station", ANCHOR));

        assertEquals(List.of(), throttle.select(all, eligible, 10.0 + 1.0 / 60.0, 0),
                "离上次不到 5 分钟 → 排队");
        assertEquals(1, throttle.select(all, eligible, 10.0 + CFG.materializationCooldownHours(), 0).size(),
                "到了 5 分钟就能做");
    }

    @Test
    void throttleHandlesEmptyAndInvalidInputs() {
        MaterializationThrottle throttle = new MaterializationThrottle(CFG);
        assertEquals(List.of(), throttle.select(List.of(), List.of(), 5.0, 0));
        assertThrows(IllegalArgumentException.class, () -> throttle.select(null, List.of(), 5.0, 0));
        assertThrows(IllegalArgumentException.class, () -> throttle.select(List.of(), null, 5.0, 0));
        assertThrows(IllegalArgumentException.class, () -> throttle.select(List.of(), List.of(), -1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> throttle.select(List.of(), List.of(), 5.0, -1));
    }

    // ---- 模型本身 ----

    @Test
    void buildingValidatesItsFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Building(" ", "intel_station", ANCHOR, 1, null, 0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "", ANCHOR, 1, null, 0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "intel_station", null, 1, null, 0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "intel_station", ANCHOR, 0, null, 0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "intel_station", ANCHOR, 1, null, -1, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "intel_station", ANCHOR, 1, null, 0, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "intel_station", ANCHOR, 1, null, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Anchor(" ", 0, 0, 0));
    }

    @Test
    void buildingTimelineMustBeCoherent() {
        // 村民死在实体化**之前**，却又是没绑定的状态：那次实体化为什么没绑上？说不通
        assertThrows(IllegalArgumentException.class,
                () -> new Building("b1", "intel_station", ANCHOR, 1, null, 9L, 5.0, 3L));

        // 反过来是正常的：先建好绑定（seq 5），村民后来才死（seq 9）
        Building lostLater = new Building("b1", "intel_station", ANCHOR, 1, null, 5L, 5.0, 9L);
        assertTrue(lostLater.stalled());
        assertEquals(9L, lostLater.staffDeadAtSeq());
    }

    @Test
    void withMethodsChangeExactlyOneThing() {
        Building b = bound("b1", 5L, 10.0);
        Building leveled = b.withLevel(3);
        assertEquals(3, leveled.level());
        assertEquals(b.staffUUID(), leveled.staffUUID());
        assertEquals(b.materializedAtSeq(), leveled.materializedAtSeq());

        Building moved = b.withAnchor(new Anchor("minecraft:overworld", 1, 2, 3));
        assertEquals(1, moved.anchor().x());
        assertEquals(b.level(), moved.level());

        Building lost = b.withStaffLost(11L);
        assertTrue(lost.stalled(), "村民没了 → 停摆");
        assertEquals(11L, lost.staffDeadAtSeq());
        assertEquals(b.materializedAtSeq(), lost.materializedAtSeq(), "丢掉村民不改上次实体化序号");
        assertEquals(5L, b.materializedAt(), "§五 的 materializedAt 就是那个序号");
    }

    @Test
    void configRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyConfig(-0.1, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyConfig(0.1, -1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyConfig(0.1, 1, Double.NaN));
        assertEquals(5.0 / 60.0, IdempotencyConfig.defaults().materializationCooldownHours(), 1e-12,
                "§十二 的 5 分钟必须换算成小时");
    }
}
