package com.wildernessnation.statecraft.core.intel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * "接触"是怎么发生的（§11.1）。
 *
 * <p>这组用例守的是一条**否则整个玩家侧都是死的**的链条：{@code met} 生成时一律 false，
 * 而 §11.2 的六个外交动作、§9.4 的国书、§11.1 的情报册全都以它为前置。
 */
class IntelContactTest {

    private static Nation nation(String id, double x, double z, boolean met) {
        return new Nation(id, id, "bandit", 5, 40.0, 0.0, 20.0, 50.0, x, z,
                met, 0.0, Nation.Status.ALIVE, Nation.PartyStatus.NEUTRAL, 0L, 0.0, 0.0, 0);
    }

    private static WorldState state(Nation... nations) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 0L,
                List.of(nations), List.of(), List.of(), List.of(), List.of(), 0.0);
    }

    @Test
    void walkingNearANationMarksItMet() {
        WorldState s = state(nation("n0", 0.0, 0.0, false), nation("n1", 4000.0, 0.0, false));
        IntelContact.Contact c = IntelContact.byPosition(s, 100.0, 0.0, 512.0);

        assertTrue(c.changed());
        assertEquals(List.of("n0"), c.newly());
        assertTrue(c.state().nation("n0").orElseThrow().met());
        assertFalse(c.state().nation("n1").orElseThrow().met(), "4000 格外的那个不该被顺带认识");
    }

    @Test
    void anAlreadyMetNationIsNotReportedAgain() {
        WorldState s = state(nation("n0", 0.0, 0.0, true));
        IntelContact.Contact c = IntelContact.byPosition(s, 0.0, 0.0, 512.0);
        assertFalse(c.changed());
        assertSame(s, c.state(), "没有新接触时必须原样返回（不然每 tick 都在改存档）");
    }

    @Test
    void nothingHappensOutsideTheRadius() {
        WorldState s = state(nation("n0", 0.0, 0.0, false));
        IntelContact.Contact c = IntelContact.byPosition(s, 513.0, 0.0, 512.0);
        assertFalse(c.changed());
        assertSame(s, c.state());
    }

    @Test
    void aNonPositiveRadiusContactsNobody() {
        WorldState s = state(nation("n0", 0.0, 0.0, false));
        assertFalse(IntelContact.byPosition(s, 0.0, 0.0, 0.0).changed());
        assertFalse(IntelContact.byPosition(s, 0.0, 0.0, -5.0).changed());
    }

    @Test
    void aDeadNationIsNotContacted() {
        WorldState s = state(nation("n0", 0.0, 0.0, false).withStatus(Nation.Status.DEAD));
        assertFalse(IntelContact.byPosition(s, 0.0, 0.0, 512.0).changed(),
                "都亡了就没有\"接触\"这回事（都城是废墟，见 §八.8）");
    }

    @Test
    void badInputIsRejected() {
        WorldState s = state(nation("n0", 0.0, 0.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> IntelContact.byPosition(null, 0.0, 0.0, 512.0));
        assertThrows(IllegalArgumentException.class,
                () -> IntelContact.byPosition(s, Double.NaN, 0.0, 512.0));
        assertThrows(IllegalArgumentException.class, () -> IntelContact.revealAll(null));
    }

    // ---- 情报站建成 = 完整列表 ----

    @Test
    void aBuiltStationRevealsEveryone() {
        WorldState s = state(nation("n0", 0.0, 0.0, true), nation("n1", 9000.0, 0.0, false),
                nation("n2", -9000.0, 0.0, false).withStatus(Nation.Status.DEAD));
        IntelContact.Contact c = IntelContact.revealAll(s);

        assertTrue(c.changed());
        assertEquals(List.of("n1", "n2(已亡)"), c.newly(),
                "只报新接触的；灭亡的也标（都是史料），但要标出来");
        assertTrue(c.state().nation("n2").orElseThrow().met());
        assertEquals(3, IntelContact.metCount(c.state()));
        assertTrue(IntelContact.unmet(c.state()).isEmpty());
    }

    @Test
    void revealingTwiceChangesNothingTheSecondTime() {
        WorldState s = state(nation("n0", 0.0, 0.0, false));
        WorldState once = IntelContact.revealAll(s).state();
        IntelContact.Contact twice = IntelContact.revealAll(once);
        assertFalse(twice.changed());
        assertSame(once, twice.state());
    }

    @Test
    void countsAndUnmetListsAreConsistent() {
        WorldState s = state(nation("n0", 0.0, 0.0, true), nation("n1", 9000.0, 0.0, false));
        assertEquals(1, IntelContact.metCount(s));
        assertEquals(Set.of("n1"), IntelContact.unmet(s));
    }
}
