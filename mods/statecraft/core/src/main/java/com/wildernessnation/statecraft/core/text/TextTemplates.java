package com.wildernessnation.statecraft.core.text;

import com.wildernessnation.statecraft.core.persist.StateNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本模板表（`NATIONS.md` §十六 `events/*.json`、`letters/*.json`）。
 *
 * <h2>模板语法（很小，故意的）</h2>
 * <ul>
 *   <li>{@code {0}} {@code {1}} … —— 事件自带的参数（国名这类，由规则层产出）</li>
 *   <li>{@code {p:town}} {@code {p:capital}} {@code {p:pass}} {@code {p:ford}} ——
 *       **从地名池确定性取一个词**（见 {@link PlaceNames}）。这一条是关键：
 *       规则层不需要知道"石口"这种地名，它只管产出"谁攻破了谁"，
 *       地名由模板向 {@link Narrator} 要 —— 于是**规则与文案彻底分开**，改文案不动规则。</li>
 * </ul>
 *
 * <p>缺模板、缺参数、写了不存在的地名种类 —— 全部**响亮的报错**（渲染发生在 core，
 * 报错信息进日志/测试，而不是在游戏里显示半句话）。
 */
public record TextTemplates(Map<String, String> byKey) {

    public TextTemplates {
        if (byKey == null || byKey.isEmpty()) {
            throw new IllegalArgumentException("模板表不能为空");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : byKey.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalArgumentException("模板的键不能为空");
            }
            if (e.getValue() == null || e.getValue().isBlank()) {
                throw new IllegalArgumentException("模板「" + e.getKey() + "」的内容不能为空");
            }
            copy.put(e.getKey(), e.getValue());
        }
        byKey = Map.copyOf(copy);
    }

    public boolean has(String textKey) {
        return byKey.containsKey(textKey);
    }

    public String require(String textKey) {
        String t = byKey.get(textKey);
        if (t == null) {
            throw new IllegalStateException("没有这个模板：" + textKey
                    + "（现有：" + byKey.keySet() + "）");
        }
        return t;
    }

    public List<String> keys() {
        return List.copyOf(byKey.keySet());
    }

    /** 从 `events.json` 读（根是 `{ "battle": "……{0}……", … }`）。 */
    public static TextTemplates fromNode(StateNode root) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, StateNode> e : root.asObj("events.json").fields().entrySet()) {
            map.put(e.getKey(), e.getValue().asString("events.json." + e.getKey()));
        }
        return new TextTemplates(map);
    }
}
