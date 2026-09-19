package com.wildernessnation.statecraft.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldStateTest {

    private static final int V = WorldState.CURRENT_SCHEMA_VERSION;

    private static Nation nation(String id) {
        return Nation.spawn(id, "国" + id, "bandit", 5, 60.0, -10.0, 20.0, 50.0, 0.0, 0.0);
    }

    private static Nation dead(String id) {
        return nation(id).withStatus(Nation.Status.DEAD);
    }

    private static Relation peace(String a, String b) {
        return Relation.peace(a, b);
    }

    private static WorldState state(List<Nation> nations) {
        return new WorldState(V, 1L, "landing", 0, 0L, nations, List.of(), List.of(), 0.0);
    }

    @Test
    void acceptsAValidState() {
        WorldState s = new WorldState(V, 1L, "landing", 0, 0L, List.of(nation("n0")),
                List.of(), List.of(), 12.5);
        assertEquals(V, s.schemaVersion());
        assertEquals(1L, s.seed());
        assertEquals("landing", s.eraId());
        assertEquals(1, s.nations().size());
        assertEquals(12.5, s.elapsedOnlineHours(), 0.0);
    }

    /** 审查发现：WorldState 原本零校验，null 国家表抛的是 NPE 而不是 IAE。 */
    @Test
    void rejectsNullNationsWithIaeNotNpe() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "landing", 0, 0L, null, List.of(), List.of(), 0.0));
    }

    @Test
    void rejectsBlankEraId() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, null, 0, 0L, List.of(), List.of(), List.of(), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "   ", 0, 0L, List.of(), List.of(), List.of(), 0.0));
    }

    @Test
    void rejectsNegativeEraOrdinalOrSeq() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "landing", -7, 0L, List.of(), List.of(), List.of(), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "landing", 0, -3L, List.of(), List.of(), List.of(), 0.0));
    }

    /** 内存里的状态永远是"已迁到当前版本"的，所以版本号只能是 >= 1 的合法值。 */
    @Test
    void rejectsNonPositiveSchemaVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(0, 1L, "landing", 0, 0L, List.of(), List.of(), List.of(), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(-1, 1L, "landing", 0, 0L, List.of(), List.of(), List.of(), 0.0));
    }

    @Test
    void rejectsBadElapsedHours() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "landing", 0, 0L, List.of(), List.of(), List.of(), -0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "landing", 0, 0L, List.of(), List.of(), List.of(),
                        Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldState(V, 1L, "landing", 0, 0L, List.of(), List.of(), List.of(),
                        Double.POSITIVE_INFINITY));
    }

    @Test
    void nationListIsADefensiveCopy() {
        List<Nation> mutable = new ArrayList<>();
        mutable.add(nation("n0"));
        WorldState s = new WorldState(V, 1L, "landing", 0, 0L, mutable, List.of(), List.of(), 0.0);
        mutable.clear();
        assertEquals(1, s.nations().size(), "外部改动不能影响已建好的状态");
        assertThrows(UnsupportedOperationException.class, () -> s.nations().clear());
    }

    @Test
    void rejectsDuplicateNationIds() {
        assertThrows(IllegalArgumentException.class,
                () -> state(List.of(nation("n0"), nation("n0"))));
    }

    /**
     * 关系不能指向不存在的国家。灭亡的国家**仍留在 nations 里**（当史料），
     * 所以这条不变量在"国家死掉"之后依然成立。
     */
    @Test
    void rejectsRelationToUnknownNation() {
        assertThrows(IllegalArgumentException.class, () -> new WorldState(V, 1L, "landing", 0, 0L,
                List.of(nation("n0")), List.of(peace("n0", "n9")), List.of(), 0.0));
    }

    @Test
    void relationSurvivesItsNationDying() {
        WorldState s = new WorldState(V, 1L, "landing", 0, 0L,
                List.of(dead("n0"), nation("n1")), List.of(peace("n0", "n1")), List.of(), 0.0);
        assertEquals(1, s.livingNations().size());
        assertTrue(s.relation("n1", "n0").isPresent(), "关系与顺序无关");
        assertTrue(s.nation("n0").isPresent());
    }

    @Test
    void eventsAreTrimmedToTheRetentionLimit() {
        List<Event> many = new ArrayList<>();
        for (int i = 0; i < WorldState.MAX_EVENTS + 25; i++) {
            many.add(new Event(i, EventTypes.BATTLE, List.of("n0"), "battle",
                    List.of(), true, false));
        }
        WorldState s = new WorldState(V, 1L, "landing", 0, 0L, List.of(nation("n0")),
                List.of(), many, 0.0);
        assertEquals(WorldState.MAX_EVENTS, s.events().size(), "只留最近 500 条");
        assertEquals(25L, s.events().get(0).seq(), "丢的是最旧的");
    }

    /** 未读上限：超出的把最旧的标成**已读**而不是删掉（§十二 "归档 ≠ 丢失"）。 */
    @Test
    void unreadBeyondLimitIsArchivedNotDropped() {
        List<Event> many = new ArrayList<>();
        for (int i = 0; i < WorldState.MAX_UNREAD + 30; i++) {
            many.add(new Event(i, EventTypes.RAID, List.of(Event.PARTY_PLAYER), "raid",
                    List.of(), true, false));
        }
        WorldState s = new WorldState(V, 1L, "landing", 0, 0L, List.of(nation("n0")),
                List.of(), many, 0.0);
        assertEquals(many.size(), s.events().size(), "归档不等于删除：条数不变");
        assertEquals(WorldState.MAX_UNREAD, s.unreadEvents().size());
        assertTrue(s.events().get(0).read(), "最旧的 30 条被标成已读");
        assertFalse(s.events().get(s.events().size() - 1).read(), "最新的仍然未读");
    }

    @Test
    void withAllEventsReadClearsTheUnreadQueue() {
        WorldState s = new WorldState(V, 1L, "landing", 0, 0L, List.of(nation("n0")), List.of(),
                List.of(new Event(0, EventTypes.WAR_DECLARED, List.of("n0"), "war", List.of(),
                        true, false)), 0.0);
        assertEquals(1, s.unreadEvents().size());
        assertEquals(0, s.withAllEventsRead().unreadEvents().size());
    }

    @Test
    void withClockAndWithNationsKeepEverythingElse() {
        WorldState s = state(List.of(nation("n0"), nation("n1")));
        WorldState moved = s.withClock(30.0, "infra", 2, 7L);
        assertEquals(30.0, moved.elapsedOnlineHours(), 0.0);
        assertEquals("infra", moved.eraId());
        assertEquals(2, moved.eraOrdinal());
        assertEquals(7L, moved.seq());
        assertEquals(s.nations(), moved.nations());

        WorldState renamed = s.withNations(List.of(nation("n0")));
        assertEquals(1, renamed.nations().size());
        assertEquals("landing", renamed.eraId());
    }
}
