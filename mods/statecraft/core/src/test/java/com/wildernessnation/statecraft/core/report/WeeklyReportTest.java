package com.wildernessnation.statecraft.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.text.Narrator;
import com.wildernessnation.statecraft.core.text.PlaceNames;
import com.wildernessnation.statecraft.core.text.TextTemplates;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeeklyReportTest {

    private static final Narrator NARRATOR = new Narrator(
            new TextTemplates(Map.of(
                    EventTypes.WAR_DECLARED, "{0}向{1}宣战",
                    EventTypes.BATTLE, "{0}在『{p:town}』击败{1}",
                    EventTypes.COLLAPSE, "{0}崩解了",
                    EventTypes.ABSORBED, "{0}吞并了{1}",
                    EventTypes.RAID, "{0}的 {1} 人小队来袭",
                    EventTypes.ERA_SUMMARY, "天下大势：{1}")),
            new PlaceNames(Map.of(PlaceNames.TOWN, List.of("石口", "枯井镇"))),
            42L);

    private static final WeeklyReport REPORT = new WeeklyReport(NARRATOR);

    private static Nation nation(String id, Nation.Status status) {
        return Nation.spawn(id, "国" + id, "bandit", 5, 60.0, 0.0, 20.0, 50.0, 0.0, 0.0)
                .withStatus(status);
    }

    private static Event event(long seq, String type, List<String> params, boolean visible) {
        return new Event(seq, type, List.of("n0"), type, params, visible, false);
    }

    private static WorldState state(List<Event> events) {
        return new WorldState(WorldState.CURRENT_SCHEMA_VERSION, 42L, "landing", 0, 9L,
                List.of(nation("n0", Nation.Status.ALIVE), nation("n1", Nation.Status.DEAD)),
                List.of(new Relation("n0", "n1", 0.0, Relation.State.WAR, 10.0, 0L, 1)),
                events, List.of(), List.of(), 0.0);
    }

    @Test
    void eventsAreGroupedIntoSections() {
        List<Event> events = List.of(
                event(1L, EventTypes.WAR_DECLARED, List.of("赤沙国", "白岩国"), true),
                event(1L, EventTypes.BATTLE, List.of("赤沙国", "白岩国"), true),
                event(2L, EventTypes.COLLAPSE, List.of("白岩国"), true),
                event(3L, EventTypes.RAID, List.of("沙盗国", "6"), true),
                event(4L, EventTypes.ERA_SUMMARY, List.of("infra", "基建"), true));

        WeeklyReport.Report r = REPORT.build(state(events), 100);
        List<String> headings = r.sections().stream().map(WeeklyReport.Section::heading).toList();
        assertEquals(List.of("战事", "兴亡", "与你有关", "其余动静"), headings);

        assertEquals(2, sectionOf(r, "战事").lines().size(), "宣战 + 交战都算战事");
        assertEquals(1, sectionOf(r, "兴亡").lines().size());
        assertEquals(1, sectionOf(r, "与你有关").lines().size(), "袭击算与玩家有关");
        assertEquals(1, sectionOf(r, "其余动静").lines().size());
    }

    private static WeeklyReport.Section sectionOf(WeeklyReport.Report r, String heading) {
        return r.sections().stream().filter(s -> s.heading().equals(heading)).findFirst()
                .orElseThrow(() -> new AssertionError("没有这一节：" + heading));
    }

    /** 地名是渲染时向地名池要的 —— 规则层只给国名。 */
    @Test
    void reportTextPullsPlaceNamesFromThePool() {
        WeeklyReport.Report r = REPORT.build(
                state(List.of(event(1L, EventTypes.BATTLE, List.of("赤沙国", "白岩国"), true))), 10);
        String text = r.body();
        assertTrue(text.contains("赤沙国在『"), text);
        assertTrue(text.contains("石口") || text.contains("枯井镇"), text);
    }

    @Test
    void invisibleEventsAreWithheld() {
        WeeklyReport.Report r = REPORT.build(state(List.of(
                event(1L, EventTypes.WAR_DECLARED, List.of("甲国", "乙国"), false))), 10);
        assertTrue(r.isEmpty(), "没见过面的国家的事先不告诉玩家：" + r.body());
    }

    @Test
    void onlyTheLastWindowIsReported() {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(event(i, EventTypes.WAR_DECLARED, List.of("甲" + i, "乙" + i), true));
        }
        WeeklyReport.Report r = REPORT.build(state(events), 3);
        String text = r.body();
        assertTrue(text.contains("甲9"), text);
        assertFalse(text.contains("甲6"), "窗口只取最后 3 条：" + text);
    }

    @Test
    void sameStateGivesTheSameReport() {
        List<Event> events = List.of(
                event(1L, EventTypes.BATTLE, List.of("赤沙国", "白岩国"), true),
                event(2L, EventTypes.RAID, List.of("沙盗国", "6"), true));
        assertEquals(REPORT.build(state(events), 10).body(), REPORT.build(state(events), 10).body());
    }

    @Test
    void statsAndHeadlineMatchTheState() {
        WeeklyReport.Report r = REPORT.build(
                state(List.of(event(1L, EventTypes.ABSORBED, List.of("甲", "乙"), true))), 10);
        assertEquals(1, r.stats().living(), "一个活着一 (n0)");
        assertEquals(1, r.stats().dead(), "一个死了 (n1)");
        assertEquals(1, r.stats().wars(), "一对在打仗");
        assertEquals(1, r.stats().absorbed());

        String headline = REPORT.headline(state(List.of()));
        assertTrue(headline.contains("尚存 1 国"), headline);
        assertTrue(headline.contains("覆灭 1 国"), headline);
        assertTrue(headline.contains("1 场战争未了"), headline);
    }

    @Test
    void emptyStateGivesAnEmptyReport() {
        WeeklyReport.Report r = REPORT.build(state(List.of()), 10);
        assertTrue(r.isEmpty());
        assertEquals("本周情报", r.title());
        assertEquals("", r.body());
    }

    @Test
    void rejectsBadInputs() {
        assertThrows(IllegalArgumentException.class, () -> REPORT.build(null, 10));
        assertThrows(IllegalArgumentException.class, () -> REPORT.build(state(List.of()), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyReport.Section(" ", List.of()));
    }
}
