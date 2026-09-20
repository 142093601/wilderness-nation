package com.wildernessnation.statecraft.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §十二 的"玩家行为 → 态度"。
 *
 * <p>这组用例守的是一条**否则整个"国家与玩家"的链是死的**的规则：生成时态度是 0，
 * 每拍态度回归又把它拉向 0 —— 没有这一层，宣战门槛（≤ −40）永远踩不到。
 */
class PlayerActionsTest {

    private static Nation nation(String id, double x, double z) {
        return new Nation(id, id, "bandit", 5, 40.0, 50.0, 20.0, 50.0, x, z,
                true, 0.0, Nation.Status.ALIVE, Nation.PartyStatus.NEUTRAL, 0L, 0.0, 0.0, 0);
    }

    private static WorldState state(Nation... nations) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 1L,
                List.of(nations), List.of(), List.of(), List.of(), List.of(), 0.0);
    }

    private static double attitude(WorldState s, String id) {
        return s.nation(id).orElseThrow().attitudeToParty();
    }

    @Test
    void buildingInsideTheCapitalRadiusAngersThatNation() {
        WorldState s = state(nation("n0", 1000.0, 1000.0), nation("n1", -4000.0, 0.0));
        PlayerActions.Reaction r = PlayerActions.buildingNear(s, 1010.0, 1010.0, 1L);

        assertTrue(r.changed());
        assertEquals(List.of("n0"), r.affected());
        assertEquals(-2.0, attitude(r.state(), "n0"), 1e-9, "§十二：−2/次");
        assertEquals(0.0, attitude(r.state(), "n1"), 1e-9, "4000 格外的那个不受影响");
    }

    @Test
    void buildingOutsideTheRadiusDoesNothingAtAll() {
        WorldState s = state(nation("n0", 0.0, 0.0));
        PlayerActions.Reaction r = PlayerActions.buildingNear(s, 65.0, 0.0, 1L);
        assertFalse(r.changed());
        assertSame(s, r.state(), "没人被影响时必须原样返回（不然每个方块都在改存档）");
    }

    /** §十二 的周期上限：一个结算周期内最多扣 10 点，之后怎么放都不再扣。 */
    @Test
    void thePenaltyIsCappedPerSettlementPeriod() {
        WorldState s = state(nation("n0", 0.0, 0.0));
        for (int i = 0; i < 5; i++) {
            s = PlayerActions.buildingNear(s, 0.0, 0.0, 1L).state();
        }
        assertEquals(-10.0, attitude(s, "n0"), 1e-9, "5 次 × −2 = 上限 −10");

        PlayerActions.Reaction extra = PlayerActions.buildingNear(s, 0.0, 0.0, 1L);
        assertFalse(extra.changed(), "同一周期已经扣满，第 6 次不该再扣");
        assertEquals(-10.0, attitude(extra.state(), "n0"), 1e-9);
        assertEquals(5, extra.state().events().size(), "扣满之后不再记事件（不然账会越记越多）");
    }

    /** 下一个周期（seq 变了）上限重新开始。 */
    @Test
    void theCapResetsInTheNextPeriod() {
        WorldState s = state(nation("n0", 0.0, 0.0));
        for (int i = 0; i < 5; i++) {
            s = PlayerActions.buildingNear(s, 0.0, 0.0, 1L).state();
        }
        assertEquals(-10.0, attitude(s, "n0"), 1e-9);
        s = PlayerActions.buildingNear(s, 0.0, 0.0, 2L).state();
        assertEquals(-12.0, attitude(s, "n0"), 1e-9, "新周期可以再扣");
    }

    /** 态度回归会把它往 0 拉，但**上限只算这一周期扣掉的**，不会因为回归而"腾出额度"。 */
    @Test
    void theCapCountsThisPeriodsPenaltyNotTheCurrentAttitude() {
        WorldState s = state(nation("n0", 0.0, 0.0));
        // 先手动把态度拉回 0（模拟"态度回归"把它拉回来了）
        for (int i = 0; i < 5; i++) {
            s = PlayerActions.buildingNear(s, 0.0, 0.0, 1L).state();
        }
        s = s.withNation(s.nation("n0").orElseThrow().withAttitudeToParty(0.0));
        assertFalse(PlayerActions.buildingNear(s, 0.0, 0.0, 1L).changed(),
                "额度是按'这个周期扣了几次'算的，不是按当前态度算的");
    }

    /** 一次放置落在**两个**都城范围内时，两个都扣（都城可能挨得很近）。 */
    @Test
    void twoNearbyCapitalsBothReact() {
        WorldState s = state(nation("n0", 0.0, 0.0), nation("n1", 20.0, 0.0));
        PlayerActions.Reaction r = PlayerActions.buildingNear(s, 10.0, 0.0, 1L);
        assertEquals(List.of("n0", "n1"), r.affected());
        assertEquals(-2.0, attitude(r.state(), "n0"), 1e-9);
        assertEquals(-2.0, attitude(r.state(), "n1"), 1e-9);
    }

    @Test
    void aDeadNationDoesNotReact() {
        WorldState s = state(nation("n0", 0.0, 0.0).withStatus(Nation.Status.DEAD));
        assertFalse(PlayerActions.buildingNear(s, 0.0, 0.0, 1L).changed());
    }

    @Test
    void capitalsNearReportsCandidatesWithoutChangingAnything() {
        WorldState s = state(nation("n0", 0.0, 0.0), nation("n1", 500.0, 0.0));
        assertEquals(List.of("n0"), PlayerActions.capitalsNear(s, 10.0, 10.0));
        assertTrue(PlayerActions.capitalsNear(s, 900.0, 900.0).isEmpty());
    }

    @Test
    void penaltySoFarIsDerivedFromTheEventLog() {
        WorldState s = state(nation("n0", 0.0, 0.0));
        assertEquals(0.0, PlayerActions.penaltySoFar(s, "n0", 1L), 1e-9);
        s = PlayerActions.buildingNear(s, 0.0, 0.0, 1L).state();
        assertEquals(-2.0, PlayerActions.penaltySoFar(s, "n0", 1L), 1e-9);
        assertEquals(0.0, PlayerActions.penaltySoFar(s, "n0", 2L), 1e-9, "别的周期不算");
        assertEquals(0.0, PlayerActions.penaltySoFar(s, "n1", 1L), 1e-9, "别的国家不算");
    }

    @Test
    void badInputIsRejected() {
        WorldState s = state(nation("n0", 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerActions.buildingNear(null, 0.0, 0.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerActions.buildingNear(s, Double.NaN, 0.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerActions.buildingNear(s, 0.0, 0.0, -1L));
    }

    /** 态度被夹在 −100..100 里（Nation 的构造器也会拒，但这里先夹好）。 */
    @Test
    void attitudeStaysWithinItsRange() {
        WorldState s = state(nation("n0", 0.0, 0.0)
                .withAttitudeToParty(-99.5));
        s = PlayerActions.buildingNear(s, 0.0, 0.0, 1L).state();
        assertEquals(-100.0, attitude(s, "n0"), 1e-9, "不许突破 −100");
    }
}
