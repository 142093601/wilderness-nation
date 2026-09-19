package com.wildernessnation.statecraft.core.era;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EraTableTest {

    private static Era era(String id, int ordinal, int hours, Set<String> unlocks) {
        return new Era(id, "时代" + ordinal, ordinal, hours, 1.0 + ordinal * 0.15, unlocks);
    }

    private static EraTable sixEras() {
        return new EraTable(List.of(
                era("landing", 0, 12, Set.of("intel_book")),
                era("founding", 1, 20, Set.of()),
                era("infra", 2, 25, Set.of("intel_station", "declaration")),
                era("expedition", 3, 25, Set.of()),
                era("defense", 4, 30, Set.of()),
                era("civilization", 5, 38, Set.of("intel_station_2"))));
    }

    private static EraTable threeEras() {
        return new EraTable(List.of(
                era("early", 0, 10, Set.of("intel_book")),
                era("mid", 1, 20, Set.of("intel_station")),
                era("late", 2, 40, Set.of("intel_station_2"))));
    }

    private static EraTable nineEras() {
        List<Era> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            list.add(era("e" + i, i, 10 + i, i == 8 ? Set.of("intel_station_2") : Set.of()));
        }
        return new EraTable(list);
    }

    @Test
    void worksForAnyEraCount() {
        assertEquals(6, sixEras().size());
        assertEquals(3, threeEras().size());
        assertEquals(9, nineEras().size());
    }

    @Test
    void currentEraFollowsCumulativeOnlineHours() {
        EraTable t = sixEras();
        assertEquals("landing", t.currentFor(0.0).id());
        assertEquals("landing", t.currentFor(11.9).id());
        assertEquals("founding", t.currentFor(12.0).id());
        assertEquals("founding", t.currentFor(31.9).id());
        assertEquals("infra", t.currentFor(32.0).id());
    }

    @Test
    void outOfRangeHoursAreClamped() {
        EraTable t = sixEras();
        assertEquals("civilization", t.currentFor(9999.0).id(), "超出末尾停在最后一个时代");
        assertEquals("landing", t.currentFor(-5.0).id(), "负数按 0 处理 → 第 0 个时代");
    }

    @Test
    void unlockIsDeclaredByEraIdNotOrdinal() {
        EraTable t = sixEras();
        assertTrue(t.isUnlocked("intel_book", t.byId("landing").orElseThrow()));
        assertFalse(t.isUnlocked("intel_station", t.byId("landing").orElseThrow()));
        assertTrue(t.isUnlocked("intel_station", t.byId("infra").orElseThrow()));
        assertTrue(t.isUnlocked("intel_station", t.byId("civilization").orElseThrow()),
                "后期时代应保持已解锁");
    }

    @Test
    void unknownEraIdFallsBackToNearestOrdinal() {
        EraTable t = sixEras();
        assertEquals("infra", t.fallbackForUnknownId("legacy_era_x", 2).id());
        assertEquals("civilization", t.fallbackForUnknownId("legacy_era_x", 99).id());
        assertEquals("landing", t.fallbackForUnknownId("legacy_era_x", -3).id());
    }

    @Test
    void rejectsNonContiguousOrdinals() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(List.of(
                era("a", 0, 10, Set.of()),
                era("b", 2, 10, Set.of()))));
    }

    /** 重复序号必然破坏连续性，但诊断信息必须说"重复"而不是说"不连续"。 */
    @Test
    void rejectsDuplicateOrdinalsWithTheRightDiagnosis() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new EraTable(List.of(
                        era("a", 0, 10, Set.of()),
                        era("b", 0, 10, Set.of()))));
        assertTrue(ex.getMessage().contains("重复"), "诊断要指向重复序号：" + ex.getMessage());
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(List.of(
                era("a", 0, 10, Set.of()),
                era("a", 1, 10, Set.of()))));
    }

    @Test
    void rejectsEmptyTable() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(List.of()));
    }

    @Test
    void rejectsNullTable() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(null));
    }

    @Test
    void byOrdinalClampsInsteadOfThrowing() {
        EraTable t = sixEras();
        assertEquals("landing", t.byOrdinal(-4).id(), "负数钳到第 0 个");
        assertEquals("civilization", t.byOrdinal(99).id(), "超界钳到最后一个");
    }

    @Test
    void currentForTreatsNaNAsZero() {
        assertEquals("landing", sixEras().currentFor(Double.NaN).id());
    }

    @Test
    void totalDurationIsTheSumOfAllEras() {
        EraTable t = sixEras();
        assertEquals("expedition", t.currentFor(81.9).id(), "12+20+25+25=82 之前的最后一刻");
        assertEquals("defense", t.currentFor(82.0).id(), "正好 82 小时进入第 5 个时代");
        assertEquals("defense", t.currentFor(111.9).id(), "12+20+25+25+30=112 之前仍是第 5 个");
        assertEquals("civilization", t.currentFor(112.0).id());
        assertEquals("civilization", t.currentFor(150.0).id(), "正好走完总时长仍停在最后一个");
    }

    @Test
    void isUnlockedReturnsFalseForNulls() {
        EraTable t = sixEras();
        assertFalse(t.isUnlocked(null, t.byOrdinal(5)));
        assertFalse(t.isUnlocked("intel_station", null));
    }

    /** 存档换了时代表时，表外 Era 的序号要按 byOrdinal 的钳位语义处理，而不是拿 99 去比。 */
    @Test
    void isUnlockedClampsEraFromAnotherTable() {
        EraTable t = sixEras();
        Era foreign = new Era("alien", "外来", 99, 10, 1.0, Set.of("nothing"));
        assertTrue(t.isUnlocked("intel_station", foreign), "按最后一个时代处理 → 已解锁");
        Era foreignLow = new Era("alien0", "外来", 0, 10, 1.0, Set.of("nothing"));
        assertFalse(t.isUnlocked("intel_station", foreignLow), "按第 0 个时代处理 → 未解锁");
    }

    /** 已知 id 优先于提示序号——归位不能把已知时代搬走。 */
    @Test
    void knownEraIdWinsOverTheHintedOrdinal() {
        EraTable t = sixEras();
        assertEquals("infra", t.fallbackForUnknownId("infra", 5).id());
    }

    @Test
    void eraRejectsItsOwnInvalidFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Era(null, "无名", 0, 10, 1.0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Era("  ", "无名", 0, 10, 1.0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Era("a", "无名", -1, 10, 1.0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Era("a", "无名", 0, 0, 1.0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Era("a", "无名", 0, -5, 1.0, Set.of()));
    }

    @Test
    void eraUnlocksAreAnImmutableCopyAndNullBecomesEmpty() {
        Era e = new Era("a", "甲", 0, 10, 1.0, null);
        assertEquals(Set.of(), e.unlocks());
        assertThrows(UnsupportedOperationException.class, () -> e.unlocks().add("x"));
    }
}
