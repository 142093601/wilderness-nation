package com.wildernessnation.statecraft.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NarratorTest {

    private static PlaceNames places() {
        return new PlaceNames(Map.of(
                PlaceNames.CAPITAL, List.of("赤沙城", "白岩城"),
                PlaceNames.TOWN, List.of("石口", "枯井镇", "风口"),
                PlaceNames.PASS, List.of("铁门关"),
                PlaceNames.FORD, List.of("浅滩渡")));
    }

    private static TextTemplates templates(Map<String, String> extra) {
        Map<String, String> all = new java.util.LinkedHashMap<>();
        all.put(EventTypes.BATTLE, "{0}攻破{1}边镇『{p:town}』");
        all.put(EventTypes.WAR_DECLARED, "{0}向{1}宣战");
        all.put(EventTypes.ERA_SUMMARY, "天下大势：{1}（第 {0} 时代）");
        all.putAll(extra);
        return new TextTemplates(all);
    }

    private static Narrator narrator(long seed) {
        return new Narrator(templates(Map.of()), places(), seed);
    }

    // ---- 位置参数 ----

    @Test
    void positionalParametersAreSubstituted() {
        Event e = new Event(7L, EventTypes.WAR_DECLARED, List.of("n0", "n1"),
                EventTypes.WAR_DECLARED, List.of("赤沙国", "白岩国"), true, false);
        assertEquals("赤沙国向白岩国宣战", narrator(1L).narrate(e));
    }

    @Test
    void missingParameterFailsLoudlyWithTheTemplateName() {
        Event e = new Event(7L, EventTypes.WAR_DECLARED, List.of("n0"), EventTypes.WAR_DECLARED,
                List.of("赤沙国"), true, false);   // 只给了一个参数，模板要两个
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> narrator(1L).narrate(e));
        assertTrue(ex.getMessage().contains("war_declared"), ex.getMessage());
        assertTrue(ex.getMessage().contains("第 1 个参数"),
                "要说清缺的是第几个参数：" + ex.getMessage());
    }

    @Test
    void unknownTemplateFailsLoudly() {
        Event e = new Event(7L, "no_such_type", List.of(), "no_such_key", List.of(), true, false);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> narrator(1L).narrate(e));
        assertTrue(ex.getMessage().contains("没有这个模板"), ex.getMessage());
    }

    @Test
    void unknownPlaceholderFailsLoudly() {
        Narrator n = new Narrator(templates(Map.of("weird", "前缀{abc}后缀")), places(), 1L);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> n.render("weird", List.of(), 1L));
        assertTrue(ex.getMessage().contains("不认识的占位符"), ex.getMessage());
    }

    // ---- 地名：由模板向地名池要，规则层不需要知道地名 ----

    @Test
    void placeholdersPullFromThePool() {
        Event e = new Event(7L, EventTypes.BATTLE, List.of("n0", "n1"), EventTypes.BATTLE,
                List.of("赤沙国", "白岩国"), true, false);
        String text = narrator(42L).narrate(e);
        assertTrue(text.startsWith("赤沙国攻破白岩国边镇『"), text);
        assertTrue(text.endsWith("』"), text);
        assertTrue(List.of("石口", "枯井镇", "风口").stream().anyMatch(text::contains),
                "取的必须是池子里的词：" + text);
    }

    /** 同一个事件渲染两次必须一模一样 —— 否则"重放得到同一段历史"就不成立了。 */
    @Test
    void sameEventRendersIdenticallyAndDifferentSeedsMayDiffer() {
        Event e = new Event(7L, EventTypes.BATTLE, List.of("n0", "n1"), EventTypes.BATTLE,
                List.of("赤沙国", "白岩国"), true, false);
        assertEquals(narrator(42L).narrate(e), narrator(42L).narrate(e));

        // 换 seed 至少在某些事件上会取到不同地名（池子 3 个词，试 20 个 seq 足够）
        boolean differs = false;
        for (long seq = 0; seq < 20 && !differs; seq++) {
            Event probe = new Event(seq, EventTypes.BATTLE, List.of("n0", "n1"), EventTypes.BATTLE,
                    List.of("赤沙国", "白岩国"), true, false);
            differs = !narrator(42L).narrate(probe).equals(narrator(43L).narrate(probe));
        }
        assertTrue(differs, "不同 seed 应该会取到不同的地名");
    }

    @Test
    void unknownPlaceKindFailsLoudly() {
        Narrator n = new Narrator(templates(Map.of("odd", "打到{p:mountain}")), places(), 1L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> n.render("odd", List.of(), 1L));
        assertTrue(ex.getMessage().contains("mountain"), ex.getMessage());
    }

    // ---- 数据校验 ----

    @Test
    void templateAndPlaceTablesValidateThemselves() {
        assertThrows(IllegalArgumentException.class, () -> new TextTemplates(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new TextTemplates(Map.of(" ", "x")));
        assertThrows(IllegalArgumentException.class, () -> new TextTemplates(Map.of("k", " ")));
        assertThrows(IllegalArgumentException.class, () -> new PlaceNames(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceNames(Map.of(PlaceNames.TOWN, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceNames(Map.of(PlaceNames.TOWN, List.of(" "))));
        assertTrue(new TextTemplates(Map.of("k", "v")).has("k"));
        assertTrue(places().has(PlaceNames.FORD));
        assertEquals(4, places().kinds().size());
    }
}
