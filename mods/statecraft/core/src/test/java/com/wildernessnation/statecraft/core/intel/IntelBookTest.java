package com.wildernessnation.statecraft.core.intel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.diplomacy.DiplomacyAction;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 情报册的三档（`NATIONS.md` §11.1）。
 *
 * <p>这一层的意义全在"**档位不够时那些字段根本不在对象里**"：
 * 要是把 {@code Nation} 直接交给界面，§11.1 的三档就退化成"界面上少写几行"，
 * 一条注释就能被绕过。所以用例重点查"该藏的真的藏了"。
 */
class IntelBookTest {

    private static Nation nation(String id, String name, boolean met, double attitude) {
        return new Nation(id, name, "bandit", 5, 40.0, 20.0, 30.0, 50.0, 0.0, 0.0,
                met, attitude, Nation.Status.ALIVE, Nation.PartyStatus.NEUTRAL, 0L, 0.0, 0.0, 0);
    }

    /** 两个已接触的国家（n0 与 n1 交战）+ 一个没接触的 n2。 */
    private static WorldState state() {
        Nation a = nation("n0", "赤沙", true, -20.0);
        Nation b = nation("n1", "白岩", true, 30.0);
        Nation unseen = nation("n2", "黑水", false, 0.0);
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 4L,
                List.of(a, b, unseen),
                List.of(new Relation("n0", "n1", -30.0, Relation.State.WAR, 12.0, 0L, 2)),
                List.of(new Event(1L, EventTypes.BATTLE, List.of("n0", "n1"), "battle",
                                List.of("赤沙", "白岩"), true, false),
                        new Event(2L, EventTypes.RAID, List.of(Event.PARTY_PLAYER, "n0"),
                                "raid", List.of("赤沙", "3"), true, true),
                        new Event(3L, EventTypes.BATTLE, List.of("n2", "n1"), "battle",
                                List.of("黑水", "白岩"), false, false)),
                List.of(), List.of(), 12.0);
    }

    // ---- 档位门槛（§11.1）----

    @Test
    void tiersUnlockByTheEraTableNotByLevel() {
        EraTable eras = new EraTable(List.of(
                new Era("landing", "落地", 0, 10, 1.0, Set.of()),
                new Era("infra", "基建", 1, 10, 1.2,
                        Set.of(IntelTier.UNLOCK_BOOK, IntelTier.UNLOCK_STATION,
                                IntelTier.UNLOCK_DECLARATION)),
                new Era("civilization", "文明", 2, 10, 1.5,
                        Set.of(IntelTier.UNLOCK_UPGRADE))));

        assertEquals(IntelTier.NONE, IntelTier.tierFor(eras, eras.byOrdinal(0)));
        assertEquals(IntelTier.STATION, IntelTier.tierFor(eras, eras.byOrdinal(1)));
        assertEquals(IntelTier.UPGRADED, IntelTier.tierFor(eras, eras.byOrdinal(2)),
                "解锁是累积的：最后一个时代掌握最高档");
        assertEquals(IntelTier.NONE, IntelTier.tierFor(null, eras.byOrdinal(2)));
        assertEquals(IntelTier.NONE, IntelTier.tierFor(eras, null));
    }

    @Test
    void onlyTheStationCanDispatchDiplomacyAndDeclare() {
        assertFalse(IntelTier.NONE.hasBook());
        assertTrue(IntelTier.BOOK.hasBook());
        assertFalse(IntelTier.BOOK.canDispatchDiplomacy(), "情报册只能看，不能遣使");
        assertFalse(IntelTier.BOOK.canDeclare(true), "宣战必须去情报站");

        assertTrue(IntelTier.STATION.canDispatchDiplomacy());
        assertTrue(IntelTier.STATION.canDeclare(true));
        assertFalse(IntelTier.STATION.canDeclare(false),
                "时代还没解锁 declaration 时，情报站也发不了宣战");
    }

    @Test
    void actionsDecideWhetherTheyNeedTheStation() {
        assertTrue(DiplomacyAction.DECLARE_WAR.needsStation(), "§11.2：宣战全服公告");
        assertTrue(DiplomacyAction.TRIBUTE.needsStation(), "§11.2：朝贡全服公告");
        assertFalse(DiplomacyAction.TRADE.needsStation());
        assertFalse(DiplomacyAction.SUE_FOR_PEACE.needsStation());

        assertFalse(IntelTier.BOOK.allows(DiplomacyAction.DECLARE_WAR, true));
        assertTrue(IntelTier.BOOK.allows(DiplomacyAction.TRADE, false),
                "通商只要情报册就能做——它不需要公告天下");
        assertTrue(IntelTier.STATION.allows(DiplomacyAction.DECLARE_WAR, true));
        assertFalse(IntelTier.UPGRADED.allows(DiplomacyAction.DECLARE_WAR, false));
        assertFalse(IntelTier.NONE.allows(DiplomacyAction.TRADE, true), "什么都没有时连看都看不了");
        assertFalse(IntelTier.BOOK.allows(null, true));
    }

    @Test
    void trackingAndDeepDiveOnlyComeWithTheUpgrade() {
        assertEquals(0, IntelTier.BOOK.trackLimit());
        assertEquals(0, IntelTier.STATION.deepDivesPerSettlement());
        assertFalse(IntelTier.STATION.canSeeWars());
        assertEquals(2, IntelTier.UPGRADED.trackLimit());
        assertEquals(1, IntelTier.UPGRADED.deepDivesPerSettlement());
        assertTrue(IntelTier.UPGRADED.canSeeWars());
    }

    // ---- 名单：该藏的真的藏了 ----

    @Test
    void unmetNationsAreNotEvenNamed() {
        List<NationBrief> listing = IntelBook.listing(state(), IntelTier.BOOK, false, Set.of());
        assertEquals(List.of("n0", "n1"), listing.stream().map(NationBrief::id).toList(),
                "没接触过的国家连名字都不给（§11.1 第一档写的是「已接触国家」）");
    }

    @Test
    void theBookShowsBasicsAndTheStationAddsTheNumbers() {
        NationBrief book = IntelBook.listing(state(), IntelTier.BOOK, false, Set.of()).get(0);
        assertEquals("赤沙", book.name());
        assertEquals(5, book.size());
        assertEquals(-20.0, book.attitude(), 1e-9);
        assertFalse(book.detailed(), "第一档不给发展度/军力/国库");
        assertTrue(book.development().isEmpty());
        assertTrue(book.military().isEmpty());
        assertTrue(book.atWarWithParty() == false, "它没在打我们");
        assertEquals(Nation.PartyStatus.NEUTRAL, book.partyStatus());

        NationBrief station = IntelBook.listing(state(), IntelTier.STATION, false, Set.of())
                .get(0);
        assertTrue(station.detailed());
        assertEquals(40.0, station.development().orElseThrow(), 1e-9);
        assertEquals(30.0, station.military().orElseThrow(), 1e-9);
        assertEquals(50.0, station.treasury().orElseThrow(), 1e-9);
        assertFalse(station.showsDiplomacy(), "第二档还看不到各国之间的战争与同盟");
    }

    @Test
    void onlyTheUpgradedStationShowsWarsAndTracking() {
        NationBrief upgraded = IntelBook.listing(state(), IntelTier.UPGRADED, true,
                Set.of("n1")).get(0);
        assertTrue(upgraded.showsDiplomacy());
        assertEquals(List.of("n1"), upgraded.atWarWith(), "赤沙正在打白岩");
        assertTrue(upgraded.alliedWith().isEmpty());
        assertFalse(upgraded.tracked());

        NationBrief tracked = IntelBook.listing(state(), IntelTier.UPGRADED, true,
                Set.of("n1")).get(1);
        assertTrue(tracked.tracked());
        assertEquals(List.of("n0"), tracked.atWarWith(), "关系是无向的，两边都要能看到");
    }

    @Test
    void withoutAnyTierTheListingIsEmpty() {
        assertTrue(IntelBook.listing(state(), IntelTier.NONE, true, Set.of()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> IntelBook.listing(null, IntelTier.BOOK, false, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> IntelBook.listing(state(), null, false, Set.of()));
    }

    // ---- 追踪与深挖 ----

    @Test
    void trackingRespectsTheLimitAndIsIdempotent() {
        assertFalse(IntelBook.track(IntelTier.STATION, Set.of(), "n0").allowed(),
                "第二档还追不了");

        assertTrue(IntelBook.track(IntelTier.UPGRADED, Set.of(), "n0").allowed());
        assertTrue(IntelBook.track(IntelTier.UPGRADED, Set.of("n0"), "n0").allowed(),
                "重复点等于没点，不该被骂");
        assertTrue(IntelBook.track(IntelTier.UPGRADED, Set.of("n0"), "n1").allowed(),
                "第二个还装得下");
        IntelVerdict full = IntelBook.track(IntelTier.UPGRADED, Set.of("n0", "n1"), "n2");
        assertFalse(full.allowed());
        assertTrue(full.reason().contains("最多同时追 2 个"), full.reason());
        assertThrows(IllegalArgumentException.class,
                () -> IntelBook.track(IntelTier.UPGRADED, Set.of(), " "));
    }

    @Test
    void deepDiveIsOncePerSettlement() {
        assertFalse(IntelBook.deepDive(IntelTier.STATION, 0).allowed());
        assertTrue(IntelBook.deepDive(IntelTier.UPGRADED, 0).allowed());
        IntelVerdict used = IntelBook.deepDive(IntelTier.UPGRADED, 1);
        assertFalse(used.allowed(), "§11.1：每结算 1 国");
        assertTrue(used.reason().contains("下一次结算"), used.reason());
        assertThrows(IllegalArgumentException.class,
                () -> IntelBook.deepDive(IntelTier.UPGRADED, -1));
    }

    // ---- 国史 ----

    @Test
    void historyTakesOnlyTheEventsThatInvolveThatNation() {
        List<Event> history = IntelBook.history(state(), "n0", 10);
        assertEquals(2, history.size(), "n2 参与的那条不该混进来");
        assertEquals(1L, history.get(0).seq(), "返回时按时间正序，读起来才顺");
        assertEquals(2L, history.get(1).seq());

        assertEquals(1, IntelBook.history(state(), "n0", 1).size(), "limit 从最近的往回取");
        assertEquals(2L, IntelBook.history(state(), "n0", 1).get(0).seq());
        assertTrue(IntelBook.history(state(), "n0", 0).isEmpty());
        assertTrue(IntelBook.history(state(), "n9", 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> IntelBook.history(state(), "", 10));
    }

    /** 未接触国家的事件是 `visible=false` 的（§五），深挖也不能把别人家的事抖出来。 */
    @Test
    void invisibleEventsStayHiddenEvenInHistory() {
        assertTrue(IntelBook.history(state(), "n2", 10).isEmpty(),
                "n2 的事件是 visible=false —— 没接触过就不该看到");
        assertTrue(IntelBook.history(state(), "n1", 10).stream()
                        .noneMatch(e -> e.parties().contains("n2")),
                "连它参与的那条也不能露出来");
    }

    @Test
    void readingUnreadNeedsABookAndSomethingToRead() {
        assertFalse(IntelBook.readUnread(IntelTier.NONE, 3).allowed());
        assertFalse(IntelBook.readUnread(IntelTier.BOOK, 0).allowed());
        IntelVerdict ok = IntelBook.readUnread(IntelTier.BOOK, 3);
        assertTrue(ok.allowed());
        assertTrue(ok.reason().contains("3"), ok.reason());
        assertThrows(IllegalArgumentException.class,
                () -> IntelBook.readUnread(IntelTier.BOOK, -1));
    }

    @Test
    void normalizeTrackedKeepsAStableCopy() {
        assertEquals(Set.of("n0", "n1"), IntelBook.normalizeTracked(Set.of("n1", "n0")));
        assertEquals(Set.of(), IntelBook.normalizeTracked(null));
    }

    @Test
    void aBriefCannotBeBuiltWithoutAnIdOrName() {
        assertThrows(IllegalArgumentException.class,
                () -> IntelBook.brief(state(), nation("n0", "  ", true, 0.0), IntelTier.BOOK,
                        false));
    }
}
