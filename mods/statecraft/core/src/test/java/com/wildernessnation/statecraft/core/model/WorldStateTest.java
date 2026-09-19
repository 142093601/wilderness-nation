package com.wildernessnation.statecraft.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldStateTest {

    private static final int V = WorldState.CURRENT_SCHEMA_VERSION;

    private static final Nation DUMMY =
            new Nation("n0", "赤沙", "bandit", 5, 60.0, -10.0, 20.0, 50.0, 0.0, 0.0, false, 0.0);

    @Test
    void acceptsAValidState() {
        WorldState s = new WorldState(V, 1L, List.of(DUMMY), "landing", 0, 0L);
        assertEquals(1L, s.seed());
        assertEquals(1, s.nations().size());
        assertEquals("landing", s.eraId());
        assertEquals(0, s.eraOrdinal());
        assertEquals(0L, s.seq());
    }

    /** 审查发现：WorldState 原本零校验，null 国家表抛的是 NPE 而不是 IAE。 */
    @Test
    void rejectsNullNationsWithIaeNotNpe() {
        assertThrows(IllegalArgumentException.class, () -> new WorldState(V, 1L, null, "landing", 0, 0L));
    }

    @Test
    void rejectsBlankEraId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, List.of(DUMMY), null, 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, List.of(DUMMY), "   ", 0, 0L));
    }

    @Test
    void rejectsNegativeEraOrdinalOrSeq() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, List.of(DUMMY), "landing", -7, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, List.of(DUMMY), "landing", 0, -3L));
    }

    /** 内存里的状态永远是"已迁到当前版本"的，所以版本号只能是 >= 1 的合法值。 */
    @Test
    void rejectsNonPositiveSchemaVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(0, 1L, List.of(DUMMY), "landing", 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(-1, 1L, List.of(DUMMY), "landing", 0, 0L));
        assertEquals(V, new WorldState(V, 1L, List.of(DUMMY), "landing", 0, 0L).schemaVersion());
    }

    @Test
    void nationListIsADefensiveCopy() {
        List<Nation> mutable = new ArrayList<>();
        mutable.add(DUMMY);
        WorldState s = new WorldState(V, 1L, mutable, "landing", 0, 0L);
        mutable.clear();
        assertEquals(1, s.nations().size(), "外部改动不能影响已建好的状态");
        assertThrows(UnsupportedOperationException.class, () -> s.nations().clear());
    }
}
